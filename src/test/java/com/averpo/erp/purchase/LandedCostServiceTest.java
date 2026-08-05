package com.averpo.erp.purchase;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.inventory.domain.StockMovement;
import com.averpo.erp.inventory.domain.Warehouse;
import com.averpo.erp.inventory.service.InventoryService;
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
import com.averpo.erp.purchase.domain.Bill;
import com.averpo.erp.purchase.domain.BillLineType;
import com.averpo.erp.purchase.domain.BillStatus;
import com.averpo.erp.purchase.domain.LandedCostAllocation;
import com.averpo.erp.purchase.domain.LandedCostAllocationLine;
import com.averpo.erp.purchase.service.BillService;
import com.averpo.erp.purchase.service.BillService.BillData;
import com.averpo.erp.purchase.service.BillService.LineData;
import com.averpo.erp.purchase.service.LandedCostService;
import com.averpo.erp.purchase.service.LandedCostService.AllocationData;
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
 * Landed cost тақсимоти тестлари: docs/modules/purchases.md «Landed
 * cost» механикаси - қиймат нисбати, сотилган улуш COGS'га, аниқ
 * reverse, яxлитлаш дрейфисиз (ТЕМИР ҚОИДА №7: debit == credit).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LandedCostServiceTest {

    /** Барча тест ҳужжатлар санаси. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 6);

    @Autowired LandedCostService landedCostService;
    @Autowired BillService billService;
    @Autowired ContactService contactService;
    @Autowired ItemService itemService;
    @Autowired WarehouseService warehouseService;
    @Autowired InventoryService inventoryService;
    @Autowired AccountService accountService;
    @Autowired JournalEntryRepository entryRepository;
    @Autowired CompanySettingsService settingsService;

    /** Тест vendor'и. */
    private Contact vendor;

    /** Тест товари (INVENTORY). */
    private Item item;

    /** Асосий омбор (seed). */
    private Warehouse warehouse;

    /** Chart + vendor + item + омбор тайёрланади (rollback тозалайди). */
    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        vendor = contactService.create(ContactType.VENDOR, new ContactData(
                "LC тест етказувчиси", null, null, null, null, null,
                null, null, null, null, null));
        ItemService.DefaultAccounts defaults = itemService.defaultsFor(ItemType.INVENTORY);
        item = itemService.create(ItemType.INVENTORY, new ItemData(
                "LC тест товари", null, null, null, null, null,
                defaults.income(), null, null, defaults.expense(),
                defaults.inventoryAsset(), null));
        warehouse = warehouseService.all().stream()
                .filter(w -> "Асосий омбор".equals(w.getName()))
                .findFirst().orElseThrow();
    }

    /** POSTED bill (битта ITEM сатр) - bill'нинг ўзи қайтади. */
    private Bill postedBill(BigDecimal qty, BigDecimal price) {
        return billService.post(billService.createDraft(new BillData(
                vendor.getId(), null, DATE, null, null, null, null,
                List.of(new LineData(BillLineType.ITEM, item.getId(), warehouse.getId(),
                        qty, price, null, null, null)))).getId());
    }

    /** POSTED bill (битта ITEM сатр) - receipt movement id қайтади. */
    private UUID receiptOf(BigDecimal qty, BigDecimal price) {
        return inventoryService.byReference("BILL", postedBill(qty, price).getId())
                .get(0).getId();
    }

    /** Тақсимот маълумоти ясагич. */
    private AllocationData data(BigDecimal amount, UUID... movementIds) {
        return new AllocationData(DATE, amount, null, List.of(movementIds));
    }

    /** Фаол GL ёзувини топади. */
    private JournalEntry glEntry(UUID allocationId) {
        return entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                LandedCostService.SOURCE_MODULE, allocationId).orElseThrow();
    }

    /** Detail type бўйича дебет/кредит base йиғиндиси. */
    private BigDecimal baseOf(JournalEntry entry, String detailType, boolean debit) {
        BigDecimal sum = BigDecimal.ZERO;
        for (JournalEntryLine line : entry.getLines()) {
            var money = debit ? line.getDebit() : line.getCredit();
            if (money != null && line.getAccount().getDetailType().name().equals(detailType)) {
                sum = sum.add(money.getBaseAmount());
            }
        }
        return sum;
    }

    /** Valuation методини созлайди (қулф очиқлигида). */
    private void valuation(InventoryValuationMethod method) {
        var settings = settingsService.get();
        settingsService.update(settings.getName(), settings.homeCurrencyCode(),
                settings.getTimezone(), method, settings.getClosingDate());
    }

    @Test
    void avco_valueProportional_increasesStockValue() {
        // Икки receipt: 10 000 ва 30 000 қийматли - 4 000 нисбатда 1:3
        UUID receipt1 = receiptOf(new BigDecimal("10"), new BigDecimal("1000"));
        UUID receipt2 = receiptOf(new BigDecimal("10"), new BigDecimal("3000"));

        LandedCostAllocation allocation = landedCostService.create(
                data(new BigDecimal("4000"), receipt1, receipt2));

        assertThat(allocation.getAllocationNumber()).startsWith("LC-2026-");
        List<LandedCostAllocationLine> lines = landedCostService.linesOf(allocation.getId());
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).getAmount()).isEqualByComparingTo("1000");
        assertThat(lines.get(1).getAmount()).isEqualByComparingTo("3000");
        // Ҳеч нарса сотилмаган - ҳаммаси омбор қийматига
        assertThat(lines.get(0).getCogsShare()).isEqualByComparingTo("0");
        assertThat(lines.get(1).getCogsShare()).isEqualByComparingTo("0");

        // GL: INVENTORY Dt 4 000 / CLEARING Cr 4 000, debit == credit
        JournalEntry entry = glEntry(allocation.getId());
        assertThat(baseOf(entry, "INVENTORY", true)).isEqualByComparingTo("4000");
        assertThat(baseOf(entry, "INVENTORY_CLEARING", false)).isEqualByComparingTo("4000");

        // Омбор қиймати: 40 000 + 4 000 = 44 000 (20 дона × 2 200)
        InventoryService.IssueResult issued = inventoryService.issue(
                item.getId(), warehouse.getId(), new BigDecimal("20"),
                DATE, "TEST", null, null);
        assertThat(issued.totalCost()).isEqualByComparingTo("44000");
    }

    @Test
    void fifo_soldPortion_goesToCogs() {
        valuation(InventoryValuationMethod.FIFO);
        UUID receipt = receiptOf(new BigDecimal("10"), new BigDecimal("1000"));

        // 4 дона аллақачон сотилган (эски 1 000 нархда)
        inventoryService.issue(item.getId(), warehouse.getId(),
                new BigDecimal("4"), DATE, "TEST", null, null);

        // 1 000 тақсимланади: delta 100/дона, қолган 6 дона - 600 омборга,
        // сотилган 4 дона улуши 400 - COGS'га
        LandedCostAllocation allocation = landedCostService.create(
                data(new BigDecimal("1000"), receipt));

        LandedCostAllocationLine line = landedCostService.linesOf(allocation.getId()).get(0);
        assertThat(line.getInventoryShare()).isEqualByComparingTo("600");
        assertThat(line.getCogsShare()).isEqualByComparingTo("400");
        assertThat(line.getRemainingQtyAtAlloc()).isEqualByComparingTo("6");

        // Spec GL талаби: inventory_share (600) item asset'га, cogs_share
        // (400) COGS'га, жами клиринг кредити 1 000
        JournalEntry entry = glEntry(allocation.getId());
        assertThat(baseOf(entry, "INVENTORY", true)).isEqualByComparingTo("600");
        assertThat(baseOf(entry, "SUPPLIES_MATERIALS_COGS", true)).isEqualByComparingTo("400");
        assertThat(baseOf(entry, "INVENTORY_CLEARING", false)).isEqualByComparingTo("1000");
        // ТЕМИР ҚОИДА №7: жами debit == credit; debit сатрларда
        // item/warehouse dimension'лари (ҳисоботлар кесими учун)
        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;
        for (JournalEntryLine glLine : entry.getLines()) {
            if (glLine.getDebit() != null) {
                debitTotal = debitTotal.add(glLine.getDebit().getBaseAmount());
                assertThat(glLine.getItemId()).isEqualTo(item.getId());
                assertThat(glLine.getWarehouseId()).isEqualTo(warehouse.getId());
            }
            if (glLine.getCredit() != null) {
                creditTotal = creditTotal.add(glLine.getCredit().getBaseAmount());
            }
        }
        assertThat(debitTotal).isEqualByComparingTo(creditTotal);

        // Қолган 6 дона энди 1 100 дан: чиқарилса 6 600
        InventoryService.IssueResult issued = inventoryService.issue(
                item.getId(), warehouse.getId(), new BigDecimal("6"),
                DATE, "TEST", null, null);
        assertThat(issued.totalCost()).isEqualByComparingTo("6600");
    }

    @Test
    void rounding_sharesSumExactlyToTotal() {
        // Уч тенг қийматли receipt, 100 сумма: 33.33 + 33.34 + 33.33 эмас,
        // кумулятив яxлитлаш - йиғинди АЙНАН 100
        UUID r1 = receiptOf(BigDecimal.ONE, new BigDecimal("500"));
        UUID r2 = receiptOf(BigDecimal.ONE, new BigDecimal("500"));
        UUID r3 = receiptOf(BigDecimal.ONE, new BigDecimal("500"));

        LandedCostAllocation allocation = landedCostService.create(
                data(new BigDecimal("100"), r1, r2, r3));

        BigDecimal sum = landedCostService.linesOf(allocation.getId()).stream()
                .map(LandedCostAllocationLine::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("100");
        assertThat(baseOf(glEntry(allocation.getId()), "INVENTORY_CLEARING", false))
                .isEqualByComparingTo("100");
    }

    @Test
    void reverse_restoresValues_blockedAfterConsumption() {
        valuation(InventoryValuationMethod.FIFO);
        UUID receipt = receiptOf(new BigDecimal("10"), new BigDecimal("1000"));

        // Тақсимот → reverse: нарх айнан ортга қайтади
        LandedCostAllocation first = landedCostService.create(
                data(new BigDecimal("500"), receipt));
        landedCostService.reverse(first.getId(), DATE, "хато");
        assertThat(first.getStatus()).isEqualTo(LandedCostAllocation.Status.REVERSED);
        assertThat(glEntry(first.getId()).getStatus()).isEqualTo(EntryStatus.REVERSED);

        // Иккинчи reverse тақиқ (BR-LC-005)
        assertThatThrownBy(() -> landedCostService.reverse(first.getId(), DATE, null))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-LC-005"));

        // Қиймат асл ҳолида: 10 дона × 1 000
        // (иккинчи тақсимотдан кейин 1 дона сотилса reverse тақиқ)
        LandedCostAllocation second = landedCostService.create(
                data(new BigDecimal("500"), receipt));
        inventoryService.issue(item.getId(), warehouse.getId(),
                BigDecimal.ONE, DATE, "TEST", null, null);
        assertThatThrownBy(() -> landedCostService.reverse(second.getId(), DATE, null))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-LC-006"));

        // Тақсимот кучда қолди: қолган 9 дона 1 050 дан
        InventoryService.IssueResult issued = inventoryService.issue(
                item.getId(), warehouse.getId(), new BigDecimal("9"),
                DATE, "TEST", null, null);
        assertThat(issued.totalCost()).isEqualByComparingTo("9450");
    }

    @Test
    void avco_reverse_restoresAverage() {
        UUID receipt = receiptOf(new BigDecimal("10"), new BigDecimal("1000"));
        LandedCostAllocation allocation = landedCostService.create(
                data(new BigDecimal("500"), receipt));

        landedCostService.reverse(allocation.getId(), DATE, null);

        // Ўртача айнан 1 000 га қайтди
        InventoryService.IssueResult issued = inventoryService.issue(
                item.getId(), warehouse.getId(), new BigDecimal("10"),
                DATE, "TEST", null, null);
        assertThat(issued.totalCost()).isEqualByComparingTo("10000");
    }

    @Test
    void avco_reverse_blockedWhenMovementsAfterAllocation() {
        // LOG-001 сценарийси: qty гарови (15 >= 10) алданиб ўтар,
        // тўлиқ inventoryShare айирилиб R2 қийматидан «ўғирланар» эди
        UUID receipt1 = receiptOf(new BigDecimal("10"), new BigDecimal("1000"));
        LandedCostAllocation allocation = landedCostService.create(
                data(new BigDecimal("1000"), receipt1));
        inventoryService.issue(item.getId(), warehouse.getId(),
                new BigDecimal("5"), DATE, "TEST", null, null);
        receiptOf(new BigDecimal("10"), new BigDecimal("1100"));

        assertThatThrownBy(() -> landedCostService.reverse(allocation.getId(), DATE, null))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-LC-006"));

        // Тақсимот кучда қолди - статус ўзгармаган
        assertThat(allocation.getStatus()).isEqualTo(LandedCostAllocation.Status.POSTED);
    }

    @Test
    void avco_soldReceipt_allocationGoesFullyToCogs() {
        // PERF-004: receipt тўлиқ сотилган - кейинги партия турибди.
        // Аввал remaining = min(бутун қолдиқ, q) = 5 бўлиб, сотилган
        // товар харажати кейинги партия активига ёзилар эди
        UUID receipt1 = receiptOf(new BigDecimal("10"), new BigDecimal("1000"));
        inventoryService.issue(item.getId(), warehouse.getId(),
                new BigDecimal("10"), DATE, "TEST", null, null);
        receiptOf(new BigDecimal("5"), new BigDecimal("2000"));

        LandedCostAllocation allocation = landedCostService.create(
                data(new BigDecimal("1000"), receipt1));

        LandedCostAllocationLine line = landedCostService.linesOf(allocation.getId()).get(0);
        assertThat(line.getInventoryShare()).isEqualByComparingTo("0");
        assertThat(line.getCogsShare()).isEqualByComparingTo("1000");
        assertThat(line.getRemainingQtyAtAlloc()).isEqualByComparingTo("0");

        // GL: ҳаммаси COGS'га, омбор активига ҳеч нарса
        JournalEntry entry = glEntry(allocation.getId());
        assertThat(baseOf(entry, "SUPPLIES_MATERIALS_COGS", true)).isEqualByComparingTo("1000");
        assertThat(baseOf(entry, "INVENTORY", true)).isEqualByComparingTo("0");

        // Кейинги партия нархи ўзгармади: 5 дона × 2 000
        InventoryService.IssueResult issued = inventoryService.issue(
                item.getId(), warehouse.getId(), new BigDecimal("5"),
                DATE, "TEST", null, null);
        assertThat(issued.totalCost()).isEqualByComparingTo("10000");
    }

    @Test
    void avco_partiallySoldReceipt_remainderCapsInventoryShare() {
        // Аралаш ҳолат: R1'дан 2 дона қолган (12 − кейинги кирим 10),
        // «эски аввал сотилади» фарази бўйича фақат шу 2 дона омборга
        UUID receipt1 = receiptOf(new BigDecimal("10"), new BigDecimal("1000"));
        inventoryService.issue(item.getId(), warehouse.getId(),
                new BigDecimal("8"), DATE, "TEST", null, null);
        receiptOf(new BigDecimal("10"), new BigDecimal("2000"));

        LandedCostAllocation allocation = landedCostService.create(
                data(new BigDecimal("1000"), receipt1));

        // delta = 100/дона: 2 × 100 омборга, 8 × 100 COGS'га
        LandedCostAllocationLine line = landedCostService.linesOf(allocation.getId()).get(0);
        assertThat(line.getRemainingQtyAtAlloc()).isEqualByComparingTo("2");
        assertThat(line.getInventoryShare()).isEqualByComparingTo("200");
        assertThat(line.getCogsShare()).isEqualByComparingTo("800");

        // Balance қиймати ҳақиқатан 200 га ошганини исботлайди (share
        // тўғри сақланиб, balance update тушиб қолса шу ерда қизаради):
        // қолган 12 дона = 2×1000 + 10×2000 + 200 = 22 200
        InventoryService.IssueResult issued = inventoryService.issue(
                item.getId(), warehouse.getId(), new BigDecimal("12"),
                DATE, "TEST", null, null);
        assertThat(issued.totalCost()).isEqualByComparingTo("22200");
    }

    @Test
    void avco_billReverse_blockedWhileAllocationActive_thenWorks() {
        // PERF-005: тақсимот кучда туриб bill reverse қилинса юкланган
        // қиймат ва клиринг кредити GL'да «осилиб» қолар эди
        Bill bill = postedBill(new BigDecimal("10"), new BigDecimal("1000"));
        UUID receipt = inventoryService.byReference("BILL", bill.getId()).get(0).getId();
        LandedCostAllocation allocation = landedCostService.create(
                data(new BigDecimal("500"), receipt));

        assertThatThrownBy(() -> billService.reverse(bill.getId(), DATE, null))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BILL-012"));

        // Аввал тақсимот reverse қилинса - bill reverse очилади
        landedCostService.reverse(allocation.getId(), DATE, null);
        billService.reverse(bill.getId(), DATE, null);
        assertThat(bill.getStatus()).isEqualTo(BillStatus.REVERSED);
        assertThat(inventoryService.quantityOnHand(item.getId(), warehouse.getId()))
                .isEqualByComparingTo("0");
    }

    @Test
    void fifo_billReverse_blockedWhileAllocationActive_thenWorks() {
        valuation(InventoryValuationMethod.FIFO);
        Bill bill = postedBill(new BigDecimal("10"), new BigDecimal("1000"));
        UUID receipt = inventoryService.byReference("BILL", bill.getId()).get(0).getId();
        LandedCostAllocation allocation = landedCostService.create(
                data(new BigDecimal("500"), receipt));

        assertThatThrownBy(() -> billService.reverse(bill.getId(), DATE, null))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BILL-012"));

        landedCostService.reverse(allocation.getId(), DATE, null);
        billService.reverse(bill.getId(), DATE, null);
        assertThat(bill.getStatus()).isEqualTo(BillStatus.REVERSED);
        assertThat(inventoryService.quantityOnHand(item.getId(), warehouse.getId()))
                .isEqualByComparingTo("0");
    }

    @Test
    void validation_guards() {
        UUID receipt = receiptOf(BigDecimal.ONE, new BigDecimal("1000"));

        // BR-LC-001: сумма мусбат эмас
        assertThatThrownBy(() -> landedCostService.create(data(BigDecimal.ZERO, receipt)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-LC-001"));
        // BR-LC-002: сана йўқ
        assertThatThrownBy(() -> landedCostService.create(new AllocationData(
                null, BigDecimal.ONE, null, List.of(receipt))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-LC-002"));
        // BR-LC-003: receipt танланмаган / такрор
        assertThatThrownBy(() -> landedCostService.create(new AllocationData(
                DATE, BigDecimal.ONE, null, List.of())))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-LC-003"));
        assertThatThrownBy(() -> landedCostService.create(data(BigDecimal.ONE,
                receipt, receipt)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-LC-003"));

        // BR-LC-004: BILL манбали бўлмаган кирим (adjustment)
        StockMovement adjust = inventoryService.adjust(item.getId(), warehouse.getId(),
                BigDecimal.ONE, new BigDecimal("1000"), DATE, null);
        assertThatThrownBy(() -> landedCostService.create(data(BigDecimal.ONE, adjust.getId())))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-LC-004"));

        // BR-LC-007: нол қийматли receipt'лар - нисбат аниқланмайди
        StockMovement zero = inventoryService.receive(item.getId(), warehouse.getId(),
                BigDecimal.ONE, BigDecimal.ZERO, DATE, "BILL",
                UUID.randomUUID(), null);
        assertThatThrownBy(() -> landedCostService.create(data(BigDecimal.ONE, zero.getId())))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-LC-007"));
    }
}
