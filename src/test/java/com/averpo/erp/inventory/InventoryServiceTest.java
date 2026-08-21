package com.averpo.erp.inventory;

import com.averpo.erp.inventory.domain.CostLayer;
import com.averpo.erp.inventory.domain.CostLayerConsumption;
import com.averpo.erp.inventory.domain.StockBalance;
import com.averpo.erp.inventory.domain.StockMovement;
import com.averpo.erp.inventory.domain.Warehouse;
import com.averpo.erp.inventory.repo.CostLayerConsumptionRepository;
import com.averpo.erp.inventory.repo.CostLayerRepository;
import com.averpo.erp.inventory.repo.StockBalanceRepository;
import com.averpo.erp.inventory.service.InventoryService;
import com.averpo.erp.inventory.service.WarehouseService;
import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.domain.ItemType;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.item.service.ItemService.ItemData;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.shared.domain.InventoryValuationMethod;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.CompanySettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Valuation ядроси тестлари: docs/modules/inventory.md → «Тестлар»,
 * 2-туртки. Иккала метод (AVCO/FIFO) тўлиқ қопланади, метод тест
 * ичида CompanySettings орқали танланади (қулф биринчи ҳаракатгача
 * очиқ - ҳар тест rollback билан изоляцияда).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InventoryServiceTest {

    /** Барча тест ҳаракатлар санаси. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 6);

    @Autowired InventoryService inventoryService;
    @Autowired WarehouseService warehouseService;
    @Autowired ItemService itemService;
    @Autowired AccountService accountService;
    @Autowired CompanySettingsService settingsService;
    @Autowired StockBalanceRepository balanceRepository;
    @Autowired CostLayerRepository layerRepository;
    @Autowired CostLayerConsumptionRepository consumptionRepository;

    /** Тест товари (INVENTORY тип). */
    private Item item;

    /** Асосий тест омбори (seed'дан). */
    private Warehouse warehouse;

    /** Default chart + INVENTORY item + seed омбор тайёрланади. */
    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        ItemService.DefaultAccounts defaults = itemService.defaultsFor(ItemType.INVENTORY);
        item = itemService.create(ItemType.INVENTORY, new ItemData(
                "Тест товар (inventory)", null, null, null, null, null,
                defaults.income(), null, null, defaults.expense(),
                defaults.inventoryAsset(), null));
        warehouse = warehouseService.all().stream()
                .filter(w -> "Асосий омбор".equals(w.getName()))
                .findFirst().orElseThrow();
    }

    /** Valuation методини созлайди (қулф очиқлигида). */
    private void method(InventoryValuationMethod method) {
        var settings = settingsService.get();
        settingsService.update(settings.getName(), settings.homeCurrencyCode(),
                settings.getTimezone(), method, settings.getClosingDate());
    }

    /** Жорий қолдиқ ёзуви. */
    private StockBalance balance() {
        return balanceRepository.findByWarehouseIdAndItemId(
                warehouse.getId(), item.getId()).orElseThrow();
    }

    // ---- AVCO ----

    @Test
    void avco_receiveMixedPrices_recomputesAverage_issueAtAverage() {
        method(InventoryValuationMethod.AVCO);
        inventoryService.receive(item.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("1000"), DATE, "TEST", null, null);
        inventoryService.receive(item.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("2000"), DATE, "TEST", null, null);

        // (10×1000 + 10×2000) / 20 = 1500
        assertThat(balance().getQty()).isEqualByComparingTo("20");
        assertThat(balance().getAvgCost()).isEqualByComparingTo("1500");

        InventoryService.IssueResult result = inventoryService.issue(
                item.getId(), warehouse.getId(), new BigDecimal("5"),
                DATE, "TEST", null, null);

        // Таннарх 5 × 1500 = 7500; ўртача ўзгармайди
        assertThat(result.totalCost()).isEqualByComparingTo("7500");
        assertThat(balance().getQty()).isEqualByComparingTo("15");
        assertThat(balance().getAvgCost()).isEqualByComparingTo("1500");
    }

    // ---- FIFO ----

    @Test
    void fifo_layersConsumedInReceivedOrder_withAuditTrail() {
        method(InventoryValuationMethod.FIFO);
        inventoryService.receive(item.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("1000"),
                DATE.minusDays(2), "TEST", null, null);
        inventoryService.receive(item.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("2000"),
                DATE.minusDays(1), "TEST", null, null);

        InventoryService.IssueResult result = inventoryService.issue(
                item.getId(), warehouse.getId(), new BigDecimal("15"),
                DATE, "TEST", null, null);

        // FIFO: 10×1000 (эски партия тўлиқ) + 5×2000 = 20 000
        assertThat(result.totalCost()).isEqualByComparingTo("20000");

        List<CostLayer> layers = layerRepository.findAll().stream()
                .filter(l -> l.getItemId().equals(item.getId()))
                .sorted((a, b) -> a.getReceivedDate().compareTo(b.getReceivedDate()))
                .toList();
        assertThat(layers).hasSize(2);
        assertThat(layers.get(0).isExhausted()).isTrue();
        assertThat(layers.get(0).getRemainingQty()).isEqualByComparingTo("0");
        assertThat(layers.get(1).getRemainingQty()).isEqualByComparingTo("5");

        // Ейилиш изи: иккита ёзув - 10 та эски, 5 та янги партиядан
        List<CostLayerConsumption> trail = consumptionRepository
                .findByMovementIdOrderByCreatedAtAsc(result.movement().getId());
        assertThat(trail).hasSize(2);
        assertThat(trail.get(0).getQuantity()).isEqualByComparingTo("10");
        assertThat(trail.get(1).getQuantity()).isEqualByComparingTo("5");

        // Balance FIFO'да қолган партиялардан: 5 дона × 2000
        assertThat(balance().getQty()).isEqualByComparingTo("5");
        assertThat(balance().getAvgCost()).isEqualByComparingTo("2000");
    }

    @Test
    void fifo_receiveUpdatesInformationalAverage() {
        method(InventoryValuationMethod.FIFO);
        inventoryService.receive(item.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("1000"), DATE, "TEST", null, null);
        inventoryService.receive(item.getId(), warehouse.getId(),
                new BigDecimal("30"), new BigDecimal("2000"), DATE, "TEST", null, null);

        // FIFO'да ҳам avg маълумот учун аниқ: (10000 + 60000) / 40 = 1750
        assertThat(balance().getQty()).isEqualByComparingTo("40");
        assertThat(balance().getAvgCost()).isEqualByComparingTo("1750");
    }

    // ---- reverseReceive: BR-INV-010 инварианти (PERF-003) ----

    @Test
    void avco_reverseReceive_blockedWhenNotLastMovement() {
        method(InventoryValuationMethod.AVCO);
        inventoryService.receive(item.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("1000"), DATE, "TEST", null, null);
        StockMovement second = inventoryService.receive(item.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("2000"), DATE, "TEST", null, null);
        inventoryService.issue(item.getId(), warehouse.getId(),
                new BigDecimal("5"), DATE, "TEST", null, null);

        // Топилма сценарийси: чиқимдан кейин киримни қайтариш avg'ни
        // 1500 → 500 га бузар эди - энди инвариант рад этади
        assertThatThrownBy(() -> inventoryService.reverseReceive(second.getId(), DATE))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-INV-010"));

        // Ҳолат ўзгармади: 15 дона, ўртача 1500
        assertThat(balance().getQty()).isEqualByComparingTo("15");
        assertThat(balance().getAvgCost()).isEqualByComparingTo("1500");
    }

    @Test
    void avco_reverseReceive_lastMovement_restoresPriorState() {
        method(InventoryValuationMethod.AVCO);
        inventoryService.receive(item.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("1000"), DATE, "TEST", null, null);
        StockMovement second = inventoryService.receive(item.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("2000"), DATE, "TEST", null, null);

        inventoryService.reverseReceive(second.getId(), DATE);

        // Охирги ҳаракат қайтарилса ҳолат айнан аввалгисига тушади
        assertThat(balance().getQty()).isEqualByComparingTo("10");
        assertThat(balance().getAvgCost()).isEqualByComparingTo("1000");
    }

    @Test
    void fifo_reverseReceive_blockedWhenNotLastMovement() {
        method(InventoryValuationMethod.FIFO);
        inventoryService.receive(item.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("1000"), DATE, "TEST", null, null);
        StockMovement second = inventoryService.receive(item.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("2000"), DATE, "TEST", null, null);
        // Чиқим эски партиядан ейди - иккинчи партия тўлиқ туради, лекин
        // қоида AVCO билан БИР ХИЛ: кейин ҳаракат бор - рад (лойиҳа қарори)
        inventoryService.issue(item.getId(), warehouse.getId(),
                new BigDecimal("5"), DATE, "TEST", null, null);

        assertThatThrownBy(() -> inventoryService.reverseReceive(second.getId(), DATE))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-INV-010"));
    }

    @Test
    void fifo_reverseReceive_lastMovement_removesLayer() {
        method(InventoryValuationMethod.FIFO);
        inventoryService.receive(item.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("1000"), DATE, "TEST", null, null);
        StockMovement second = inventoryService.receive(item.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("2000"), DATE, "TEST", null, null);

        inventoryService.reverseReceive(second.getId(), DATE);

        // Фақат биринчи партия қолди - чиқим тўлиқ 1000 дан кетади
        assertThat(balance().getQty()).isEqualByComparingTo("10");
        InventoryService.IssueResult result = inventoryService.issue(
                item.getId(), warehouse.getId(), new BigDecimal("10"),
                DATE, "TEST", null, null);
        assertThat(result.totalCost()).isEqualByComparingTo("10000");
    }

    // ---- умумий валидациялар ----

    @Test
    void issue_insufficientStock_rejected() {
        method(InventoryValuationMethod.AVCO);

        // Умуман қолдиқ йўқ
        assertThatThrownBy(() -> inventoryService.issue(item.getId(), warehouse.getId(),
                new BigDecimal("1"), DATE, "TEST", null, null))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-INV-003"));

        // Қисман бор, лекин етарли эмас
        inventoryService.receive(item.getId(), warehouse.getId(),
                new BigDecimal("3"), new BigDecimal("1000"), DATE, "TEST", null, null);
        assertThatThrownBy(() -> inventoryService.issue(item.getId(), warehouse.getId(),
                new BigDecimal("5"), DATE, "TEST", null, null))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-INV-003"));
    }

    @Test
    void receive_nonInventoryItem_rejected() {
        ItemService.DefaultAccounts defaults = itemService.defaultsFor(ItemType.SERVICE);
        Item service = itemService.create(ItemType.SERVICE, new ItemData(
                "Хизмат (омборсиз)", null, null, null, null, null,
                defaults.income(), null, null, defaults.expense(), null, null));

        assertThatThrownBy(() -> inventoryService.receive(service.getId(),
                warehouse.getId(), BigDecimal.ONE, BigDecimal.ONE, DATE, "TEST", null, null))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-INV-001"));
    }

    @Test
    void receive_invalidQtyCostDateWarehouse_rejected() {
        // Миқдор нол/манфий - BR-INV-002
        assertThatThrownBy(() -> inventoryService.receive(item.getId(), warehouse.getId(),
                BigDecimal.ZERO, BigDecimal.ONE, DATE, "TEST", null, null))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-INV-002"));

        // Манфий unit cost - BR-INV-004
        assertThatThrownBy(() -> inventoryService.receive(item.getId(), warehouse.getId(),
                BigDecimal.ONE, new BigDecimal("-1"), DATE, "TEST", null, null))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-INV-004"));

        // Сана йўқ - BR-INV-008
        assertThatThrownBy(() -> inventoryService.receive(item.getId(), warehouse.getId(),
                BigDecimal.ONE, BigDecimal.ONE, null, "TEST", null, null))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-INV-008"));

        // Нофаол омбор - BR-INV-006
        Warehouse inactive = warehouseService.create("Ёпиқ омбор", null);
        warehouseService.update(inactive.getId(), "Ёпиқ омбор", null, false);
        assertThatThrownBy(() -> inventoryService.receive(item.getId(), inactive.getId(),
                BigDecimal.ONE, BigDecimal.ONE, DATE, "TEST", null, null))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-INV-006"));
    }

    @Test
    void valuationMethod_lockedAfterFirstMovement() {
        method(InventoryValuationMethod.AVCO);
        inventoryService.receive(item.getId(), warehouse.getId(),
                BigDecimal.ONE, BigDecimal.ONE, DATE, "TEST", null, null);

        // Биринчи ҳаракатдан кейин методни FIFO'га ўтказиш - BR-SET-003
        assertThatThrownBy(() -> method(InventoryValuationMethod.FIFO))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-SET-003"));
    }

    @Test
    void quantityOnHand_zeroWhenNoBalance() {
        assertThat(inventoryService.quantityOnHand(item.getId(), warehouse.getId()))
                .isEqualByComparingTo("0");

        StockMovement movement = inventoryService.receive(item.getId(), warehouse.getId(),
                new BigDecimal("7"), new BigDecimal("500"), DATE, "TEST", null, null);
        assertThat(movement.getTotalCost()).isEqualByComparingTo("3500");
        assertThat(inventoryService.quantityOnHand(item.getId(), warehouse.getId()))
                .isEqualByComparingTo("7");
    }

    // ---- billReceipts: landed cost формасининг номзод рўйхати ----

    @Test
    void billReceipts_onlyBillInMovements_newestFirst() {
        method(InventoryValuationMethod.AVCO);
        // Икки BILL кирими турли саналарда - кутилган тартиб: янгиси биринчи
        StockMovement older = inventoryService.receive(item.getId(), warehouse.getId(),
                new BigDecimal("5"), new BigDecimal("1000"),
                DATE.minusDays(1), "BILL", UUID.randomUUID(), null);
        StockMovement newer = inventoryService.receive(item.getId(), warehouse.getId(),
                new BigDecimal("5"), new BigDecimal("1000"),
                DATE, "BILL", UUID.randomUUID(), null);

        // BILL бўлмаган киримлар: бошқа манбали receive, adjustment (+),
        // transfer in - ҳеч бири номзод эмас (акс ҳолда форма BR-LC-004
        // билан рад этиладиган receipt'ларни кўрсатиб қўяр эди)
        inventoryService.receive(item.getId(), warehouse.getId(),
                new BigDecimal("5"), new BigDecimal("1000"), DATE, "TEST", null, null);
        inventoryService.adjust(item.getId(), warehouse.getId(),
                new BigDecimal("2"), new BigDecimal("1000"), DATE, null);
        Warehouse branch = warehouseService.create("Филиал (billReceipts)", null);
        inventoryService.transfer(item.getId(), warehouse.getId(), branch.getId(),
                BigDecimal.ONE, DATE, null);

        assertThat(inventoryService.billReceipts())
                .extracting(StockMovement::getId)
                .containsExactly(newer.getId(), older.getId());
    }
}
