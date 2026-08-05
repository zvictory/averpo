package com.averpo.erp.inventory;

import com.averpo.erp.inventory.domain.MovementType;
import com.averpo.erp.inventory.domain.StockMovement;
import com.averpo.erp.inventory.repo.StockBalanceRepository;
import com.averpo.erp.inventory.service.InventoryService;
import com.averpo.erp.inventory.service.WarehouseService;
import com.averpo.erp.inventory.domain.Warehouse;
import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.domain.ItemType;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.item.service.ItemService.ItemData;
import com.averpo.erp.ledger.domain.EntryStatus;
import com.averpo.erp.ledger.domain.JournalEntry;
import com.averpo.erp.ledger.domain.JournalEntryLine;
import com.averpo.erp.ledger.repo.JournalEntryRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Adjustment + Transfer тестлари: docs/modules/inventory.md →
 * «Тестлар», 3-туртки. Adjustment GL проводкаси posting-rules «Омбор»
 * жадвалига мослиги ҳам шу ерда текширилади (ТЕМИР ҚОИДА №7:
 * debit == credit assert).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InventoryAdjustTransferTest {

    /** Барча тест ҳаракатлар санаси. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 6);

    @Autowired InventoryService inventoryService;
    @Autowired WarehouseService warehouseService;
    @Autowired ItemService itemService;
    @Autowired AccountService accountService;
    @Autowired CompanySettingsService settingsService;
    @Autowired StockBalanceRepository balanceRepository;
    @Autowired JournalEntryRepository entryRepository;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired jakarta.persistence.EntityManagerFactory emf;

    /** Тест товари (INVENTORY тип). */
    private Item item;

    /** Асосий омбор (seed'дан). */
    private Warehouse main;

    /** Иккинчи омбор - transfer тестлари учун. */
    private Warehouse branch;

    /** Default chart + item + иккита омбор тайёрланади. */
    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        ItemService.DefaultAccounts defaults = itemService.defaultsFor(ItemType.INVENTORY);
        item = itemService.create(ItemType.INVENTORY, new ItemData(
                "Кўчириш тест товари", null, null, null, null, null,
                defaults.income(), null, null, defaults.expense(),
                defaults.inventoryAsset(), null));
        main = warehouseService.all().stream()
                .filter(w -> "Асосий омбор".equals(w.getName()))
                .findFirst().orElseThrow();
        branch = warehouseService.create("Филиал омбори", "FIL");
    }

    /** Valuation методини созлайди (қулф очиқлигида). */
    private void method(InventoryValuationMethod method) {
        var settings = settingsService.get();
        settingsService.update(settings.getName(), settings.homeCurrencyCode(),
                settings.getTimezone(), method, settings.getClosingDate());
    }

    /** (омбор, item) қолдиқ миқдори. */
    private BigDecimal qty(Warehouse warehouse) {
        return inventoryService.quantityOnHand(item.getId(), warehouse.getId());
    }

    /** Adjustment'нинг GL ёзувини манбасидан топади. */
    private JournalEntry glEntry(StockMovement movement) {
        return entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                InventoryService.SOURCE_MODULE, movement.getId()).orElseThrow();
    }

    // ---- Ҳаракатлар рўйхати (саҳифалаш, PERF-perf1 2-босқич) ----

    @Test
    void movements_pagination_secondPageSlice_allFilterCombos() {
        // findTop100 функционал ТЕШИК эди - 100-ёзувдан эскиси УМУМАН
        // кўринмасди. size+1 receipt'да 26-ёзув энди 2-саҳифада кўринади;
        // саналар ҳар хил - тартиб детерминистик текширилади.
        method(InventoryValuationMethod.AVCO);
        StockMovement oldest = null;
        StockMovement newest = null;
        for (int i = InventoryService.MOVEMENTS_PAGE_SIZE; i >= 0; i--) {
            StockMovement m = inventoryService.receive(item.getId(), main.getId(),
                    new BigDecimal("1"), new BigDecimal("1000"),
                    DATE.minusDays(i), "TEST", null, null);
            if (oldest == null) {
                oldest = m; // биринчи яратилгани энг эски санали
            }
            newest = m;
        }

        // Филтрсиз: 1-саҳифа тўла, 2-саҳифада ЯГОНА эски ёзув (тешик ёпилди)
        var page0 = inventoryService.movements(null, null, 0);
        assertThat(page0.getContent()).hasSize(InventoryService.MOVEMENTS_PAGE_SIZE);
        assertThat(page0.getTotalElements()).isEqualTo(InventoryService.MOVEMENTS_PAGE_SIZE + 1);
        assertThat(page0.getTotalPages()).isEqualTo(2);
        // Барқарор тартиб: энг янги санали биринчи (аввалги ORDER BY)
        assertThat(page0.getContent().get(0).getId()).isEqualTo(newest.getId());

        var page1 = inventoryService.movements(null, null, 1);
        assertThat(page1.getContent()).hasSize(1);
        assertThat(page1.getContent().get(0).getId()).isEqualTo(oldest.getId());
        assertThat(page1.hasNext()).isFalse();

        // Тўртала филтр комбинацияси - ҳамма ёзув айнан (item, main) кесимида,
        // жами доим size+1; item+омбор комбинацияси 2-саҳифада ўша эски ёзув
        assertThat(inventoryService.movements(main.getId(), null, 0).getTotalElements())
                .isEqualTo(InventoryService.MOVEMENTS_PAGE_SIZE + 1);
        assertThat(inventoryService.movements(null, item.getId(), 0).getTotalElements())
                .isEqualTo(InventoryService.MOVEMENTS_PAGE_SIZE + 1);
        assertThat(inventoryService.movements(main.getId(), item.getId(), 1).getContent())
                .extracting(StockMovement::getId).containsExactly(oldest.getId());
        // Ҳаракатсиз омбор (branch) - бўш саҳифа
        assertThat(inventoryService.movements(branch.getId(), null, 0).getTotalElements())
                .isZero();
    }

    /**
     * DEC-049 (PERF-037): ҳаракатлар саҳифаси warehouse'ни битта
     * сўровда юклайди (@EntityGraph) - N+1 йўқ. Ҳар қаторнинг
     * getWarehouse()'и қўшимча SELECT қилмаслигини Hibernate Statistics
     * билан ўлчаймиз (PriceListServiceTest услуби).
     */
    @Test
    void movements_page_loadsWarehouse_noNPlus1() {
        method(InventoryValuationMethod.AVCO);
        for (int i = 0; i < 6; i++) {
            inventoryService.receive(item.getId(), main.getId(),
                    new BigDecimal("1"), new BigDecimal("1000"),
                    DATE.minusDays(i), "TEST", null, null);
        }
        em.flush();
        em.clear();

        var stats = emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        boolean wasEnabled = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        stats.clear();
        try {
            var page = inventoryService.movements(null, null, 0);
            // Ҳар қаторнинг warehouse номига тегиш - lazy бўлса қўшимча SELECT
            for (StockMovement m : page.getContent()) {
                m.getWarehouse().getName();
            }
            // @EntityGraph: маълумот + count = 2 сўров (warehouse JOIN
            // FETCH ичкарида); N+1 бўлса 6 қаторга 6 қўшимча SELECT бўларди
            assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(3);
        } finally {
            stats.setStatisticsEnabled(wasEnabled);
        }
    }

    // ---- Adjustment ----

    @Test
    void adjustDecrease_postsShrinkageDebit_inventoryCredit() {
        method(InventoryValuationMethod.AVCO);
        inventoryService.receive(item.getId(), main.getId(),
                new BigDecimal("10"), new BigDecimal("1000"), DATE, "TEST", null, null);

        StockMovement movement = inventoryService.adjust(item.getId(), main.getId(),
                new BigDecimal("-3"), null, DATE, "инвентаризация камомади");

        assertThat(movement.getType()).isEqualTo(MovementType.ADJUST_OUT);
        assertThat(movement.getTotalCost()).isEqualByComparingTo("3000");
        assertThat(qty(main)).isEqualByComparingTo("7");

        // GL: shrinkage Dt / INVENTORY Cr, POSTED, балансда (қоида №7)
        JournalEntry entry = glEntry(movement);
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.POSTED);
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        String debitDetail = null;
        String creditDetail = null;
        for (JournalEntryLine line : entry.getLines()) {
            if (line.getDebit() != null) {
                debit = debit.add(line.getDebit().getBaseAmount());
                debitDetail = line.getAccount().getDetailType().name();
            }
            if (line.getCredit() != null) {
                credit = credit.add(line.getCredit().getBaseAmount());
                creditDetail = line.getAccount().getDetailType().name();
            }
        }
        assertThat(debit).isEqualByComparingTo(credit);
        assertThat(debit).isEqualByComparingTo("3000");
        assertThat(debitDetail).isEqualTo("OTHER_COSTS_OF_SERVICE_COS");
        assertThat(creditDetail).isEqualTo("INVENTORY");
    }

    @Test
    void adjustIncrease_defaultsToCurrentCost_postsInventoryDebit() {
        method(InventoryValuationMethod.AVCO);
        inventoryService.receive(item.getId(), main.getId(),
                new BigDecimal("10"), new BigDecimal("1000"), DATE, "TEST", null, null);

        // Нарх берилмади - жорий ўртача (1000) олинади
        StockMovement movement = inventoryService.adjust(item.getId(), main.getId(),
                new BigDecimal("5"), null, DATE, "ортиқча топилди");

        assertThat(movement.getType()).isEqualTo(MovementType.ADJUST_IN);
        assertThat(movement.getUnitCost()).isEqualByComparingTo("1000");
        assertThat(movement.getTotalCost()).isEqualByComparingTo("5000");
        assertThat(qty(main)).isEqualByComparingTo("15");

        // GL: INVENTORY Dt / shrinkage Cr, POSTED, балансда (қоида №7)
        JournalEntry entry = glEntry(movement);
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.POSTED);
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        String debitDetail = null;
        String creditDetail = null;
        for (JournalEntryLine line : entry.getLines()) {
            // Dimension'лар иккала сатрда ҳам - кейинги ҳисоботлар учун
            assertThat(line.getItemId()).isEqualTo(item.getId());
            assertThat(line.getWarehouseId()).isEqualTo(main.getId());
            if (line.getDebit() != null) {
                debit = debit.add(line.getDebit().getBaseAmount());
                debitDetail = line.getAccount().getDetailType().name();
            }
            if (line.getCredit() != null) {
                credit = credit.add(line.getCredit().getBaseAmount());
                creditDetail = line.getAccount().getDetailType().name();
            }
        }
        assertThat(debit).isEqualByComparingTo(credit);
        assertThat(debit).isEqualByComparingTo("5000");
        assertThat(debitDetail).isEqualTo("INVENTORY");
        assertThat(creditDetail).isEqualTo("OTHER_COSTS_OF_SERVICE_COS");
    }

    @Test
    void adjustIncrease_zeroStockWithoutCost_rejected_withCostAccepted() {
        method(InventoryValuationMethod.AVCO);

        // Қолдиқ нол - жорий нарх аниқланмайди, нарх шарт (BR-INV-007)
        assertThatThrownBy(() -> inventoryService.adjust(item.getId(), main.getId(),
                new BigDecimal("5"), null, DATE, null))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-INV-007"));

        // Нарх берилса - муаммосиз
        StockMovement movement = inventoryService.adjust(item.getId(), main.getId(),
                new BigDecimal("5"), new BigDecimal("1200"), DATE, null);
        assertThat(movement.getTotalCost()).isEqualByComparingTo("6000");
        assertThat(qty(main)).isEqualByComparingTo("5");
    }

    @Test
    void adjust_zeroDelta_rejected() {
        assertThatThrownBy(() -> inventoryService.adjust(item.getId(), main.getId(),
                BigDecimal.ZERO, null, DATE, null))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-INV-002"));
    }

    @Test
    void adjust_closedPeriod_rejectedByLedgerLock() {
        method(InventoryValuationMethod.AVCO);
        inventoryService.receive(item.getId(), main.getId(),
                new BigDecimal("10"), new BigDecimal("1000"), DATE, "TEST", null, null);

        // Давр ёпилди - adjustment GL'га ёзилолмайди (BR-LED-020),
        // бутун транзакция (movement билан бирга) бекор бўлади
        var settings = settingsService.get();
        settingsService.update(settings.getName(), settings.homeCurrencyCode(),
                settings.getTimezone(), null, DATE);

        assertThatThrownBy(() -> inventoryService.adjust(item.getId(), main.getId(),
                new BigDecimal("-1"), null, DATE, null))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-LED-020"));
    }

    // ---- Transfer ----

    @Test
    void transfer_avco_movesValueWithoutGl() {
        method(InventoryValuationMethod.AVCO);
        inventoryService.receive(item.getId(), main.getId(),
                new BigDecimal("10"), new BigDecimal("1000"), DATE, "TEST", null, null);

        InventoryService.TransferResult result = inventoryService.transfer(
                item.getId(), main.getId(), branch.getId(),
                new BigDecimal("4"), DATE, "филиалга");

        assertThat(result.totalCost()).isEqualByComparingTo("4000");
        assertThat(result.outbound().getType()).isEqualTo(MovementType.TRANSFER_OUT);
        assertThat(result.inbound().getType()).isEqualTo(MovementType.TRANSFER_IN);
        // Counterpart'лар ўзаро боғланган
        assertThat(result.outbound().getCounterpartWarehouse().getId())
                .isEqualTo(branch.getId());
        assertThat(result.inbound().getCounterpartWarehouse().getId())
                .isEqualTo(main.getId());

        assertThat(qty(main)).isEqualByComparingTo("6");
        assertThat(qty(branch)).isEqualByComparingTo("4");
        assertThat(balanceRepository.findByWarehouseIdAndItemId(
                branch.getId(), item.getId()).orElseThrow().getAvgCost())
                .isEqualByComparingTo("1000");

        // Transfer GL'га ёзилмайди (posting-rules)
        assertThat(entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                InventoryService.SOURCE_MODULE, result.outbound().getId())).isEmpty();
        assertThat(entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                InventoryService.SOURCE_MODULE, result.inbound().getId())).isEmpty();
    }

    @Test
    void transfer_fifo_preservesLayerCostsAndOrder() {
        method(InventoryValuationMethod.FIFO);
        inventoryService.receive(item.getId(), main.getId(),
                new BigDecimal("10"), new BigDecimal("1000"),
                DATE.minusDays(2), "TEST", null, null);
        inventoryService.receive(item.getId(), main.getId(),
                new BigDecimal("10"), new BigDecimal("2000"),
                DATE.minusDays(1), "TEST", null, null);

        // 15 дона кўчади: 10×1000 + 5×2000 = 20 000
        InventoryService.TransferResult result = inventoryService.transfer(
                item.getId(), main.getId(), branch.getId(),
                new BigDecimal("15"), DATE, null);
        assertThat(result.totalCost()).isEqualByComparingTo("20000");
        assertThat(qty(main)).isEqualByComparingTo("5");
        assertThat(qty(branch)).isEqualByComparingTo("15");

        // Манзилдан чиқим FIFO тартибини сақлаганини исботлайди:
        // 12 дона = 10×1000 (эски партия аввал!) + 2×2000 = 14 000
        InventoryService.IssueResult issued = inventoryService.issue(
                item.getId(), branch.getId(), new BigDecimal("12"),
                DATE, "TEST", null, null);
        assertThat(issued.totalCost()).isEqualByComparingTo("14000");
    }

    @Test
    void transfer_sameWarehouseOrInsufficient_rejected() {
        method(InventoryValuationMethod.AVCO);

        assertThatThrownBy(() -> inventoryService.transfer(item.getId(),
                main.getId(), main.getId(), BigDecimal.ONE, DATE, null))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-INV-005"));

        assertThatThrownBy(() -> inventoryService.transfer(item.getId(),
                main.getId(), branch.getId(), BigDecimal.ONE, DATE, null))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-INV-003"));
    }
}
