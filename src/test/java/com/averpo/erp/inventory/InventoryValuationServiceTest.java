package com.averpo.erp.inventory;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.inventory.domain.Warehouse;
import com.averpo.erp.inventory.service.InventoryService;
import com.averpo.erp.inventory.service.InventoryValuationService;
import com.averpo.erp.inventory.service.WarehouseService;
import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.domain.ItemType;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.item.service.ItemService.ItemData;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.ledger.service.JournalEntryRequest;
import com.averpo.erp.ledger.service.JournalEntryRequest.Line;
import com.averpo.erp.ledger.service.PostingService;
import com.averpo.erp.purchase.domain.Bill;
import com.averpo.erp.purchase.domain.BillLineType;
import com.averpo.erp.purchase.service.BillService;
import com.averpo.erp.purchase.service.BillService.BillData;
import com.averpo.erp.purchase.service.BillService.LineData;
import com.averpo.erp.purchase.service.LandedCostService;
import com.averpo.erp.purchase.service.LandedCostService.AllocationData;
import com.averpo.erp.shared.domain.Money;
import jakarta.persistence.EntityManager;
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

/**
 * Inventory valuation ҳисоботи: «санага» тиклаш, омбор фильтри,
 * landed cost улушлари (сторно санаси билан) ва GL солиштируви.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InventoryValuationServiceTest {

    /** Асосий тест санаси. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 6);

    @Autowired InventoryValuationService valuationService;
    @Autowired InventoryService inventoryService;
    @Autowired WarehouseService warehouseService;
    @Autowired BillService billService;
    @Autowired LandedCostService landedCostService;
    @Autowired ContactService contactService;
    @Autowired ItemService itemService;
    @Autowired AccountService accountService;
    @Autowired PostingService postingService;
    @Autowired EntityManager em;

    /** Тест vendor'и (bill'лар учун). */
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
                "Valuation тест етказувчиси", null, null, null, null, null,
                null, null, null, null, null));
        ItemService.DefaultAccounts defaults = itemService.defaultsFor(ItemType.INVENTORY);
        item = itemService.create(ItemType.INVENTORY, new ItemData(
                "Valuation тест товари", null, null, null, null, null,
                defaults.income(), null, null, defaults.expense(),
                defaults.inventoryAsset(), null));
        warehouse = warehouseService.all().stream()
                .filter(w -> "Асосий омбор".equals(w.getName()))
                .findFirst().orElseThrow();
    }

    /** POSTED bill (битта ITEM сатр) - receipt movement id қайтади. */
    private UUID receiptOf(LocalDate date, BigDecimal qty, BigDecimal price) {
        Bill bill = billService.post(billService.createDraft(new BillData(
                vendor.getId(), null, date, null, null, null, null,
                List.of(new LineData(BillLineType.ITEM, item.getId(), warehouse.getId(),
                        qty, price, null, null, null)))).getId());
        return inventoryService.byReference("BILL", bill.getId()).get(0).getId();
    }

    /** Шу тест товарига оид ягона сатрни топади. */
    private InventoryValuationService.Row row(InventoryValuationService.Report report,
                                              UUID warehouseId) {
        return report.rows().stream()
                .filter(r -> r.itemId().equals(item.getId())
                        && r.warehouseId().equals(warehouseId))
                .findFirst().orElseThrow();
    }

    @Test
    void build_reconstructsAsOfDate() {
        receiptOf(DATE, new BigDecimal("10"), new BigDecimal("1000"));
        // Икки кун кейин камомад 4 дона - GL проводкали чиқим
        inventoryService.adjust(item.getId(), warehouse.getId(),
                new BigDecimal("-4"), null, DATE.plusDays(2), null);
        em.flush();

        // Кирим кунида: тўлиқ 10 дона / 10 000
        InventoryValuationService.Report atReceipt = valuationService.build(DATE, null);
        assertThat(row(atReceipt, warehouse.getId()).qty()).isEqualByComparingTo("10");
        assertThat(row(atReceipt, warehouse.getId()).value()).isEqualByComparingTo("10000");

        // Камомаддан кейин: 6 дона / 6 000
        InventoryValuationService.Report after = valuationService.build(DATE.plusDays(2), null);
        assertThat(row(after, warehouse.getId()).qty()).isEqualByComparingTo("6");
        assertThat(row(after, warehouse.getId()).value()).isEqualByComparingTo("6000");
        // Bill + adjustment иккиси ҳам GL'га ёзади - мос бўлиши шарт
        assertThat(after.matchesGl()).isTrue();

        // Киримдан олдинги санада бу товар йўқ
        InventoryValuationService.Report before = valuationService.build(DATE.minusDays(1), null);
        assertThat(before.rows()).noneMatch(r -> r.itemId().equals(item.getId()));
    }

    @Test
    void build_warehouseFilter_andTransfer() {
        receiptOf(DATE, new BigDecimal("10"), new BigDecimal("1000"));
        Warehouse second = warehouseService.create("Иккинчи омбор", null);
        inventoryService.transfer(item.getId(), warehouse.getId(), second.getId(),
                new BigDecimal("4"), DATE.plusDays(1), null);
        em.flush();

        // Иккала омбор кесими: 6 000 + 4 000, компания жамиси ўзгармаган
        InventoryValuationService.Report all = valuationService.build(DATE.plusDays(1), null);
        assertThat(row(all, warehouse.getId()).value()).isEqualByComparingTo("6000");
        assertThat(row(all, second.getId()).value()).isEqualByComparingTo("4000");
        assertThat(all.companyValue()).isEqualByComparingTo("10000");
        // Transfer GL ёзмайди - лекин жами қиймат ҳам ўзгармайди, мослик сақланади
        assertThat(all.matchesGl()).isTrue();

        // Фильтр: фақат иккинчи омбор, лекин companyValue тўлиқлигича
        InventoryValuationService.Report filtered =
                valuationService.build(DATE.plusDays(1), second.getId());
        assertThat(filtered.rows()).hasSize(1);
        assertThat(filtered.totalValue()).isEqualByComparingTo("4000");
        assertThat(filtered.companyValue()).isEqualByComparingTo("10000");
    }

    @Test
    void build_landedCost_includedByDate_andExactReverseWindow() {
        UUID receipt = receiptOf(DATE, new BigDecimal("10"), new BigDecimal("1000"));
        landedCostService.create(new AllocationData(
                DATE.plusDays(1), new BigDecimal("2000"), null, List.of(receipt)));
        em.flush();

        // Тақсимотдан олдин: 10 000; кейин: 12 000 - GL билан мос
        assertThat(valuationService.build(DATE, null).companyValue())
                .isEqualByComparingTo("10000");
        InventoryValuationService.Report withLc = valuationService.build(DATE.plusDays(1), null);
        assertThat(withLc.companyValue()).isEqualByComparingTo("12000");
        assertThat(withLc.matchesGl()).isTrue();

        // Сторно кейинроқ санада - орадаги давр 12 000'лигича қолади
        var allocation = landedCostService.list(
                new com.averpo.erp.purchase.service.LandedCostService.ListFilter(
                        null, null, null, null)).get(0);
        landedCostService.reverse(allocation.getId(), DATE.plusDays(3), "тест");
        em.flush();

        InventoryValuationService.Report window = valuationService.build(DATE.plusDays(1), null);
        assertThat(window.companyValue()).isEqualByComparingTo("12000");
        assertThat(window.matchesGl()).isTrue();

        InventoryValuationService.Report afterReverse =
                valuationService.build(DATE.plusDays(3), null);
        assertThat(afterReverse.companyValue()).isEqualByComparingTo("10000");
        assertThat(afterReverse.matchesGl()).isTrue();
    }

    @Test
    void build_glMismatch_onManualInventoryPosting() {
        receiptOf(DATE, new BigDecimal("10"), new BigDecimal("1000"));
        // INVENTORY счётига қўлда JE - омбор ҳаракатисиз (мослик бузилади)
        var inventoryAccount = accountService.requireSystemAccount(AccountDetailType.INVENTORY);
        var equityAccount = accountService.requireSystemAccount(
                AccountDetailType.OPENING_BALANCE_EQUITY);
        postingService.createAndPost(JournalEntryRequest.manual(DATE, "Қўлда", List.of(
                Line.debit(inventoryAccount.getId(),
                        Money.ofBase(new BigDecimal("5000"), "UZS"), null),
                Line.credit(equityAccount.getId(),
                        Money.ofBase(new BigDecimal("5000"), "UZS"), null))));
        em.flush();

        InventoryValuationService.Report report = valuationService.build(DATE, null);
        assertThat(report.companyValue()).isEqualByComparingTo("10000");
        assertThat(report.glBalance()).isEqualByComparingTo("15000");
        assertThat(report.matchesGl()).isFalse();
    }
}
