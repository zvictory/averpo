package com.averpo.erp.purchase;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.inventory.domain.Warehouse;
import com.averpo.erp.inventory.service.InventoryService;
import com.averpo.erp.inventory.service.WarehouseService;
import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.domain.ItemType;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.item.service.ItemService.ItemData;
import com.averpo.erp.ledger.domain.JournalEntry;
import com.averpo.erp.ledger.domain.JournalEntryLine;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.repo.JournalEntryRepository;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.purchase.domain.Bill;
import com.averpo.erp.purchase.domain.BillLineType;
import com.averpo.erp.purchase.service.BillService;
import com.averpo.erp.purchase.service.BillService.BillData;
import com.averpo.erp.purchase.service.BillService.LineData;
import com.averpo.erp.tax.domain.TaxRate;
import com.averpo.erp.tax.service.TaxRateService;
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
 * Bill'да ҚҚС интеграцияси тестлари: docs/modules/tax.md «Тестлар».
 * GL posting-rules «Харид» (Солиқ) бандларига мослиги (ТЕМИР ҚОИДА №7:
 * debit == credit) ва net/tax/gross бўлиниши шу ерда.
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BillTaxTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 7);

    @Autowired BillService billService;
    @Autowired TaxRateService taxRateService;
    @Autowired ContactService contactService;
    @Autowired ItemService itemService;
    @Autowired WarehouseService warehouseService;
    @Autowired InventoryService inventoryService;
    @Autowired AccountService accountService;
    @Autowired AccountRepository accountRepository;
    @Autowired JournalEntryRepository entryRepository;

    private Contact vendor;
    private Item item;
    private Warehouse warehouse;
    private UUID rentAccountId;
    private UUID qqs12Id;

    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        vendor = contactService.create(ContactType.VENDOR, new ContactData(
                "Солиқ vendor", null, null, null, null, null,
                null, null, null, null, null));
        ItemService.DefaultAccounts defaults = itemService.defaultsFor(ItemType.INVENTORY);
        item = itemService.create(ItemType.INVENTORY, new ItemData(
                "Солиқ товари", null, null, null, null, null,
                defaults.income(), null, null, defaults.expense(),
                defaults.inventoryAsset(), null));
        warehouse = warehouseService.all().stream()
                .filter(w -> "Асосий омбор".equals(w.getName()))
                .findFirst().orElseThrow();
        rentAccountId = accountRepository.findByName("Ижара").orElseThrow().getId();
        qqs12Id = taxRateService.all().stream()
                .filter(r -> r.getCode().equals("QQS12")).findFirst().orElseThrow().getId();
    }

    /** Bill'нинг фаол GL ёзувини топади. */
    private JournalEntry glEntry(Bill bill) {
        return entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                BillService.SOURCE_MODULE, bill.getId()).orElseThrow();
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

    /** Дебет == кредит (ТЕМИР ҚОИДА №7) - home base. */
    private void assertBalanced(JournalEntry entry) {
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (JournalEntryLine line : entry.getLines()) {
            if (line.getDebit() != null) debit = debit.add(line.getDebit().getBaseAmount());
            if (line.getCredit() != null) credit = credit.add(line.getCredit().getBaseAmount());
        }
        assertThat(debit).isEqualByComparingTo(credit);
    }

    /** EXPENSE сатр (raw сумма) + ставка. */
    private LineData expenseLine(String amount, UUID taxRateId) {
        return new LineData(BillLineType.EXPENSE, null, null, null, null,
                rentAccountId, new BigDecimal(amount), null, null, null,
                taxRateId, null, null);
    }

    /** Home валютали bill (режим танланади). */
    private Bill postBill(boolean inclusive, LineData... lines) {
        BillData data = new BillData(vendor.getId(), null, DATE, null,
                null, null, null, inclusive, List.of(lines));
        return billService.post(billService.createDraft(data).getId());
    }

    @Test
    void exclusive_taxOnTop_glBalanced() {
        // 1000 × 12% → net 1000 / tax 120 / gross 1120
        Bill bill = postBill(false, expenseLine("1000", qqs12Id));

        assertThat(bill.getTotal()).isEqualByComparingTo("1120"); // gross
        JournalEntry entry = glEntry(bill);
        assertBalanced(entry);
        assertThat(baseOf(entry, "ACCOUNTS_PAYABLE", false)).isEqualByComparingTo("1120");
        assertThat(baseOf(entry, "SALES_TAX_PAYABLE", true)).isEqualByComparingTo("120");
        // Ижара - EXPENSE сатр net 1000
        BigDecimal expenseDebit = entry.getLines().stream()
                .filter(l -> l.getDebit() != null && l.getAccount().getId().equals(rentAccountId))
                .map(l -> l.getDebit().getBaseAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(expenseDebit).isEqualByComparingTo("1000");
    }

    @Test
    void inclusive_taxExtracted_glBalanced() {
        // gross 1120 → net 1000 / tax 120 (комплемент аниқ)
        Bill bill = postBill(true, expenseLine("1120", qqs12Id));

        assertThat(bill.getTotal()).isEqualByComparingTo("1120");
        JournalEntry entry = glEntry(bill);
        assertBalanced(entry);
        assertThat(baseOf(entry, "ACCOUNTS_PAYABLE", false)).isEqualByComparingTo("1120");
        assertThat(baseOf(entry, "SALES_TAX_PAYABLE", true)).isEqualByComparingTo("120");
    }

    @Test
    void mixed_taxedAndNoTax_twoRates_separateTaxLines() {
        TaxRate qqs20 = taxRateService.create("QQS20", "ҚҚС 20%", new BigDecimal("20"));
        // Уч сатр: 1000@12%, 500@20%, 300 солиқсиз
        Bill bill = postBill(false,
                expenseLine("1000", qqs12Id),
                expenseLine("500", qqs20.getId()),
                expenseLine("300", null));

        JournalEntry entry = glEntry(bill);
        assertBalanced(entry);
        // ҚҚС жами = 120 + 100 = 220, лекин ставка кесимида ИККИ сатр
        assertThat(baseOf(entry, "SALES_TAX_PAYABLE", true)).isEqualByComparingTo("220");
        long taxLines = entry.getLines().stream()
                .filter(l -> l.getDebit() != null
                        && l.getAccount().getDetailType().name().equals("SALES_TAX_PAYABLE"))
                .count();
        assertThat(taxLines).isEqualTo(2);
        // gross = 1120 + 600 + 300 = 2020
        assertThat(bill.getTotal()).isEqualByComparingTo("2020");
        assertThat(baseOf(entry, "ACCOUNTS_PAYABLE", false)).isEqualByComparingTo("2020");
    }

    @Test
    void itemLine_inventoryGetsNet_notGross() {
        // ITEM 10 × 100 = net 1000, 12% → gross 1120; омборга НЕТТО
        LineData itemLine = new LineData(BillLineType.ITEM, item.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("100"), null, null, null, null, null,
                qqs12Id, null, null);
        Bill bill = postBill(false, itemLine);

        JournalEntry entry = glEntry(bill);
        assertBalanced(entry);
        // Омбор INVENTORY счётига net 1000 (ҚҚС таннархга кирмайди)
        assertThat(baseOf(entry, "INVENTORY", true)).isEqualByComparingTo("1000");
        assertThat(baseOf(entry, "SALES_TAX_PAYABLE", true)).isEqualByComparingTo("120");
        // Омборга кирган унит нарх = net/qty = 100 (gross-per-unit 112 эмас)
        InventoryService.IssueResult issued = inventoryService.issue(
                item.getId(), warehouse.getId(), new BigDecimal("10"),
                DATE, "TEST", null, null);
        assertThat(issued.totalCost()).isEqualByComparingTo("1000");
    }

    @Test
    void snapshot_rateEditedAfterDraft_postUsesDraftValue() {
        BillData data = new BillData(vendor.getId(), null, DATE, null,
                null, null, null, false, List.of(expenseLine("1000", qqs12Id)));
        Bill draft = billService.createDraft(data);

        // Каталогда ставка 12 → 20 га ўзгарди
        taxRateService.update(qqs12Id, "QQS12", "ҚҚС 12%", new BigDecimal("20"), true);

        // Пост draft'даги SNAPSHOT (12%)ни ишлатади - tax 120, 200 эмас
        Bill posted = billService.post(draft.getId());
        assertThat(posted.getTotal()).isEqualByComparingTo("1120");
        assertThat(baseOf(glEntry(posted), "SALES_TAX_PAYABLE", true))
                .isEqualByComparingTo("120");
    }

    @Test
    void foreignCurrency_withTax_moneyInvariantAndBalanced() {
        // USD bill, курс 12345.6789 - ҳар GL сатр BR-LED-003 ичида,
        // йиғинди BR-LED-006 (MoneyAllocation ҚҚС легларини ҳам қамрайди).
        // Arbitr-087: чет валюта ҳужжати USD валютали контактга ёзилади
        Contact usdVendor = contactService.create(ContactType.VENDOR, new ContactData(
                "Солиқ USD vendor", null, null, null, null, null,
                "USD", null, null, null, null));
        BillData data = new BillData(usdVendor.getId(), null, DATE, null,
                "USD", new BigDecimal("12345.6789"), null, false, List.of(
                expenseLine("0.03", qqs12Id), expenseLine("0.03", qqs12Id)));
        Bill bill = billService.post(billService.createDraft(data).getId());

        JournalEntry entry = glEntry(bill);
        assertBalanced(entry); // BR-LED-006
        for (JournalEntryLine line : entry.getLines()) {
            for (var money : new com.averpo.erp.shared.domain.Money[]{
                    line.getDebit(), line.getCredit()}) {
                if (money != null) {
                    BigDecimal expected = money.getAmount().multiply(money.getExchangeRate());
                    assertThat(money.getBaseAmount().subtract(expected).abs())
                            .isLessThanOrEqualTo(new BigDecimal("0.0001")); // BR-LED-003
                }
            }
        }
    }

    @Test
    void balanceDue_onGross_reverseWorks() {
        Bill bill = postBill(false, expenseLine("1000", qqs12Id));
        // Balance due = gross 1120 (тўловлар gross устида)
        assertThat(bill.getBalanceDue()).isEqualByComparingTo("1120");

        billService.reverse(bill.getId(), DATE, "тест");
        assertThat(glEntry(bill).getStatus())
                .isEqualTo(com.averpo.erp.ledger.domain.EntryStatus.REVERSED);
    }
}
