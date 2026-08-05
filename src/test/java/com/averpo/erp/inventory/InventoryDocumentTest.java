package com.averpo.erp.inventory;

import com.averpo.erp.inventory.domain.MovementType;
import com.averpo.erp.inventory.domain.StockAdjustment;
import com.averpo.erp.inventory.domain.StockMovement;
import com.averpo.erp.inventory.domain.StockTransfer;
import com.averpo.erp.inventory.domain.Warehouse;
import com.averpo.erp.inventory.repo.StockBalanceRepository;
import com.averpo.erp.inventory.service.InventoryService;
import com.averpo.erp.inventory.service.InventoryService.AdjustLineData;
import com.averpo.erp.inventory.service.InventoryService.DocumentAdjustData;
import com.averpo.erp.inventory.service.InventoryService.DocumentTransferData;
import com.averpo.erp.inventory.service.InventoryService.TransferLineData;
import com.averpo.erp.inventory.service.WarehouseService;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ҳужжатли Adjustment/Transfer тестлари (DEC-093, docs/modules/
 * inventory.md «Тестлар»): кўп сатрли актга БИТТА JE (аралаш акт -
 * иккала жуфт бир JE'да, debit==credit ТЕМИР ҚОИДА №7); ҳар сатр
 * StockMovement (reference=акт id); transfer балансларни ўзгартиради
 * GL'сиз; сатр гаровлари (BR-INV-011/012); филтрлар.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InventoryDocumentTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 6);

    @Autowired InventoryService inventoryService;
    @Autowired WarehouseService warehouseService;
    @Autowired ItemService itemService;
    @Autowired AccountService accountService;
    @Autowired CompanySettingsService settingsService;
    @Autowired StockBalanceRepository balanceRepository;
    @Autowired JournalEntryRepository entryRepository;

    /** Биринчи тест товари (INVENTORY). */
    private Item itemA;

    /** Иккинчи тест товари - аралаш акт учун. */
    private Item itemB;

    /** Асосий омбор (seed). */
    private Warehouse main;

    /** Иккинчи омбор - transfer учун. */
    private Warehouse branch;

    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        ItemService.DefaultAccounts def = itemService.defaultsFor(ItemType.INVENTORY);
        itemA = itemService.create(ItemType.INVENTORY, new ItemData(
                "Ҳужжат тест товар А", null, null, null, null, null,
                def.income(), null, null, def.expense(), def.inventoryAsset(), null));
        itemB = itemService.create(ItemType.INVENTORY, new ItemData(
                "Ҳужжат тест товар Б", null, null, null, null, null,
                def.income(), null, null, def.expense(), def.inventoryAsset(), null));
        main = warehouseService.all().stream()
                .filter(w -> "Асосий омбор".equals(w.getName()))
                .findFirst().orElseThrow();
        branch = warehouseService.create("Ҳужжат филиали", "HFI");
        method(InventoryValuationMethod.AVCO);
    }

    private void method(InventoryValuationMethod method) {
        var s = settingsService.get();
        settingsService.update(s.getName(), s.homeCurrencyCode(), s.getTimezone(),
                method, s.getClosingDate());
    }

    private BigDecimal qty(Item item, Warehouse warehouse) {
        return inventoryService.quantityOnHand(item.getId(), warehouse.getId());
    }

    /** Актнинг GL ёзувини топади (sourceDocumentId = АКТ id). */
    private java.util.Optional<JournalEntry> actEntry(java.util.UUID actId) {
        return entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                InventoryService.SOURCE_MODULE, actId);
    }

    // ---- Adjustment акти ----

    @Test
    void adjustDocument_mixedAct_singleBalancedJe_perLineMovements() {
        inventoryService.receive(itemA.getId(), main.getId(),
                new BigDecimal("10"), new BigDecimal("1000"), DATE, "SEED", null, null);
        inventoryService.receive(itemB.getId(), main.getId(),
                new BigDecimal("8"), new BigDecimal("500"), DATE, "SEED", null, null);

        // Аралаш акт: A 10→15 (+5×1000=+5000), B 8→5 (−3×500=−1500)
        StockAdjustment act = inventoryService.adjustDocument(new DocumentAdjustData(
                main.getId(), DATE, "инвентаризация",
                List.of(new AdjustLineData(itemA.getId(), new BigDecimal("15"), null, null),
                        new AdjustLineData(itemB.getId(), new BigDecimal("5"), null, null))));

        // Рақам + нетто таъсир (+5000 − 1500 = +3500)
        assertThat(act.getAdjNumber()).startsWith("ADJ-2026-");
        assertThat(act.getStatus()).isEqualTo(StockAdjustment.Status.POSTED);
        assertThat(act.getTotalCost()).isEqualByComparingTo("3500");
        assertThat(qty(itemA, main)).isEqualByComparingTo("15");
        assertThat(qty(itemB, main)).isEqualByComparingTo("5");

        // Ҳар сатр StockMovement (reference=акт id): A кирим, B чиқим
        List<StockMovement> moves = inventoryService.byReference(
                InventoryService.ADJUSTMENT_REFERENCE, act.getId());
        assertThat(moves).hasSize(2);
        assertThat(moves).extracting(StockMovement::getType)
                .containsExactlyInAnyOrder(MovementType.ADJUST_IN, MovementType.ADJUST_OUT);

        // БИТТА JE, debit==credit (ТЕМИР ҚОИДА №7), POSTED
        JournalEntry entry = actEntry(act.getId()).orElseThrow();
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.POSTED);
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (JournalEntryLine line : entry.getLines()) {
            if (line.getDebit() != null) debit = debit.add(line.getDebit().getBaseAmount());
            if (line.getCredit() != null) credit = credit.add(line.getCredit().getBaseAmount());
        }
        assertThat(debit).isEqualByComparingTo(credit);
        assertThat(debit).isEqualByComparingTo("5000");
    }

    @Test
    void adjustDocument_deltaFromNewQty_increaseAndDecrease() {
        inventoryService.receive(itemA.getId(), main.getId(),
                new BigDecimal("10"), new BigDecimal("1000"), DATE, "SEED", null, null);
        // Янги qty 4 < жорий 10 → delta −6 (камайиш)
        StockAdjustment act = inventoryService.adjustDocument(new DocumentAdjustData(
                main.getId(), DATE, null,
                List.of(new AdjustLineData(itemA.getId(), new BigDecimal("4"), null, null))));
        assertThat(act.getLines().get(0).getDeltaQty()).isEqualByComparingTo("-6");
        assertThat(act.getLines().get(0).getLineCost()).isEqualByComparingTo("-6000");
        assertThat(qty(itemA, main)).isEqualByComparingTo("4");
    }

    @Test
    void adjustDocument_lineGuards() {
        // BR-INV-011: сатр йўқ
        assertThatThrownBy(() -> inventoryService.adjustDocument(new DocumentAdjustData(
                main.getId(), DATE, null, List.of())))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-INV-011"));

        // BR-INV-012: битта актда item такрор
        assertThatThrownBy(() -> inventoryService.adjustDocument(new DocumentAdjustData(
                main.getId(), DATE, null,
                List.of(new AdjustLineData(itemA.getId(), new BigDecimal("1"), new BigDecimal("10"), null),
                        new AdjustLineData(itemA.getId(), new BigDecimal("2"), new BigDecimal("10"), null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-INV-012"));

        // BR-INV-007: қолдиқ нол, кўпайиш нархсиз
        assertThatThrownBy(() -> inventoryService.adjustDocument(new DocumentAdjustData(
                main.getId(), DATE, null,
                List.of(new AdjustLineData(itemA.getId(), new BigDecimal("5"), null, null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-INV-007"));
    }

    @Test
    void adjustDocument_zeroCostLine_noJe() {
        // Нархсиз кирим (unit cost 0) → кейин кўпайиш нархи 0 → лег йўқ, JE йўқ
        inventoryService.receive(itemA.getId(), main.getId(),
                new BigDecimal("5"), BigDecimal.ZERO, DATE, "SEED", null, null);
        StockAdjustment act = inventoryService.adjustDocument(new DocumentAdjustData(
                main.getId(), DATE, null,
                List.of(new AdjustLineData(itemA.getId(), new BigDecimal("8"), null, null))));
        assertThat(qty(itemA, main)).isEqualByComparingTo("8");
        assertThat(act.getTotalCost()).isEqualByComparingTo("0");
        // Нол қийматли акт - GL'га ёзилмайди (BR-LED-002 XOR)
        assertThat(actEntry(act.getId())).isEmpty();
    }

    // ---- Transfer акти ----

    @Test
    void transferDocument_multiLine_movesBalances_noGl() {
        inventoryService.receive(itemA.getId(), main.getId(),
                new BigDecimal("10"), new BigDecimal("1000"), DATE, "SEED", null, null);
        inventoryService.receive(itemB.getId(), main.getId(),
                new BigDecimal("8"), new BigDecimal("500"), DATE, "SEED", null, null);
        long entriesBefore = entryRepository.count();

        StockTransfer act = inventoryService.transferDocument(new DocumentTransferData(
                main.getId(), branch.getId(), DATE, "филиалга",
                List.of(new TransferLineData(itemA.getId(), new BigDecimal("4"), null),
                        new TransferLineData(itemB.getId(), new BigDecimal("3"), null))));

        assertThat(act.getWtrNumber()).startsWith("WTR-2026-");
        assertThat(act.getStatus()).isEqualTo(StockTransfer.Status.POSTED);
        // Кўчган қиймат: 4×1000 + 3×500 = 5500
        assertThat(act.getTotalCost()).isEqualByComparingTo("5500");
        assertThat(qty(itemA, main)).isEqualByComparingTo("6");
        assertThat(qty(itemA, branch)).isEqualByComparingTo("4");
        assertThat(qty(itemB, main)).isEqualByComparingTo("5");
        assertThat(qty(itemB, branch)).isEqualByComparingTo("3");

        // Ҳар сатр TRANSFER_OUT+IN жуфти (2 сатр × 2 = 4 ҳаракат)
        assertThat(inventoryService.byReference(
                InventoryService.TRANSFER_REFERENCE, act.getId())).hasSize(4);
        // GL'сиз: journal_entry сони ЎЗГАРМАЙДИ (posting-rules)
        assertThat(entryRepository.count()).isEqualTo(entriesBefore);
    }

    @Test
    void transferDocument_guards() {
        // BR-INV-005: манба = манзил
        assertThatThrownBy(() -> inventoryService.transferDocument(new DocumentTransferData(
                main.getId(), main.getId(), DATE, null,
                List.of(new TransferLineData(itemA.getId(), BigDecimal.ONE, null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-INV-005"));

        // BR-INV-012: item такрор
        inventoryService.receive(itemA.getId(), main.getId(),
                new BigDecimal("10"), new BigDecimal("1000"), DATE, "SEED", null, null);
        assertThatThrownBy(() -> inventoryService.transferDocument(new DocumentTransferData(
                main.getId(), branch.getId(), DATE, null,
                List.of(new TransferLineData(itemA.getId(), BigDecimal.ONE, null),
                        new TransferLineData(itemA.getId(), BigDecimal.ONE, null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-INV-012"));

        // BR-INV-011: сатр йўқ
        assertThatThrownBy(() -> inventoryService.transferDocument(new DocumentTransferData(
                main.getId(), branch.getId(), DATE, null, List.of())))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-INV-011"));
    }

    // ---- Рўйхат/филтр ----

    /**
     * Deploy 4 hotfix регресси (арбитр, 2026-07-12): филтрсиз (ҳаммаси
     * null) рўйхат чақируви истисно бермасин. transfers() да nullable
     * Specification allOf'га берилиб Spring Data 4 «Other specification
     * must not be null» IAE отарди - саҳифа default киришда доим 400
     * эди. Эталон: ListSpecs no-op конвенцияси (Specification ўзи null
     * эмас, toPredicate null қайтаради).
     */
    @Test
    void documentLists_noFilter_noException() {
        var empty = new InventoryService.DocumentFilter(null, null, null);
        assertThat(inventoryService.adjustments(empty, 0).getTotalElements()).isNotNegative();
        assertThat(inventoryService.transfers(empty, 0).getTotalElements()).isNotNegative();
    }

    /**
     * DEC-109: ташқи ҳужжат рақами (external_ref) иккала актда
     * сақланади; бўш/бланк қиймат null (blankToNull). GL/movement
     * мантиғига тегмайди - фақат аудит майдони.
     */
    @Test
    void documents_externalRef_saved() {
        inventoryService.receive(itemA.getId(), main.getId(),
                new BigDecimal("10"), new BigDecimal("1000"), DATE, "SEED", null, null);

        StockAdjustment adj = inventoryService.adjustDocument(new DocumentAdjustData(
                main.getId(), DATE, null, "AKT-777",
                List.of(new AdjustLineData(itemA.getId(), new BigDecimal("12"), null, null))));
        assertThat(adj.getExternalRef()).isEqualTo("AKT-777");

        StockTransfer wtr = inventoryService.transferDocument(new DocumentTransferData(
                main.getId(), branch.getId(), DATE, null, "TXN-42",
                List.of(new TransferLineData(itemA.getId(), new BigDecimal("2"), null))));
        assertThat(wtr.getExternalRef()).isEqualTo("TXN-42");

        // Бланк ташқи рақам → null сақланади
        StockAdjustment noRef = inventoryService.adjustDocument(new DocumentAdjustData(
                main.getId(), DATE, null, "  ",
                List.of(new AdjustLineData(itemA.getId(), new BigDecimal("11"), null, null))));
        assertThat(noRef.getExternalRef()).isNull();
    }

    @Test
    void adjustments_and_movementFilters() {
        inventoryService.receive(itemA.getId(), main.getId(),
                new BigDecimal("10"), new BigDecimal("1000"), DATE, "SEED", null, null);
        StockAdjustment act = inventoryService.adjustDocument(new DocumentAdjustData(
                main.getId(), DATE, null,
                List.of(new AdjustLineData(itemA.getId(), new BigDecimal("13"), null, null))));

        // Актлар рўйхати филтри (омбор) - акт кўринади, бошқа омборда йўқ
        assertThat(inventoryService.adjustments(
                new InventoryService.DocumentFilter(main.getId(), null, null), 0).getTotalElements())
                .isEqualTo(1);
        assertThat(inventoryService.adjustments(
                new InventoryService.DocumentFilter(branch.getId(), null, null), 0).getTotalElements())
                .isZero();

        // Ҳаракат филтри: тур ADJUST_IN + ҳужжат рақами кесими
        assertThat(inventoryService.movements(new InventoryService.MovementFilter(
                MovementType.ADJUST_IN, null, null, null, null, null), 0).getTotalElements())
                .isEqualTo(1);
        assertThat(inventoryService.movements(new InventoryService.MovementFilter(
                null, null, null, null, null, act.getAdjNumber()), 0).getTotalElements())
                .isEqualTo(1);
        // Мавжуд бўлмаган рақам - бўш натижа
        assertThat(inventoryService.movements(new InventoryService.MovementFilter(
                null, null, null, null, null, "ADJ-2099-99999"), 0).getTotalElements())
                .isZero();
        // Сана оралиғи: кейинги кун - ҳеч нарса
        assertThat(inventoryService.movements(new InventoryService.MovementFilter(
                null, null, null, DATE.plusDays(1), null, null), 0).getTotalElements())
                .isZero();
    }
}
