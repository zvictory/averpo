package com.averpo.erp.sales;

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
import com.averpo.erp.ledger.repo.JournalEntryRepository;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.sales.domain.Invoice;
import com.averpo.erp.sales.service.InvoiceService;
import com.averpo.erp.sales.service.InvoiceService.InvoiceData;
import com.averpo.erp.sales.service.InvoiceService.LineData;
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
 * Invoice'да ҚҚС интеграцияси тестлари (docs/modules/tax.md): Bill'нинг
 * кўзгуси - AR Dt gross, даромад Cr net, ҚҚС Cr (ставка кесими).
 * ТЕМИР ҚОИДА №7: debit == credit.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InvoiceTaxTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 7);

    @Autowired InvoiceService invoiceService;
    @Autowired TaxRateService taxRateService;
    @Autowired ContactService contactService;
    @Autowired ItemService itemService;
    @Autowired WarehouseService warehouseService;
    @Autowired InventoryService inventoryService;
    @Autowired AccountService accountService;
    @Autowired JournalEntryRepository entryRepository;

    private Contact customer;
    private Item service;
    private Item invItem;
    private Warehouse warehouse;
    private UUID qqs12Id;

    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        customer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Солиқ мижози", null, null, null, null, null,
                null, null, null, null, null));
        ItemService.DefaultAccounts svc = itemService.defaultsFor(ItemType.SERVICE);
        service = itemService.create(ItemType.SERVICE, new ItemData(
                "ҚҚС хизмати", null, null, null, null, null,
                svc.income(), null, null, svc.expense(), null, null));
        ItemService.DefaultAccounts inv = itemService.defaultsFor(ItemType.INVENTORY);
        invItem = itemService.create(ItemType.INVENTORY, new ItemData(
                "ҚҚС товари", null, null, null, null, null,
                inv.income(), null, null, inv.expense(), inv.inventoryAsset(), null));
        warehouse = warehouseService.all().stream()
                .filter(w -> "Асосий омбор".equals(w.getName()))
                .findFirst().orElseThrow();
        qqs12Id = taxRateService.all().stream()
                .filter(r -> r.getCode().equals("QQS12")).findFirst().orElseThrow().getId();
    }

    private JournalEntry glEntry(Invoice invoice) {
        return entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                InvoiceService.SOURCE_MODULE, invoice.getId()).orElseThrow();
    }

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

    private void assertBalanced(JournalEntry entry) {
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (JournalEntryLine line : entry.getLines()) {
            if (line.getDebit() != null) debit = debit.add(line.getDebit().getBaseAmount());
            if (line.getCredit() != null) credit = credit.add(line.getCredit().getBaseAmount());
        }
        assertThat(debit).isEqualByComparingTo(credit);
    }

    /** SERVICE сатр (омборсиз) - ставка билан. */
    private LineData serviceLine(String qty, String price, UUID taxRateId) {
        return new LineData(service.getId(), null, new BigDecimal(qty),
                new BigDecimal(price), null, null, null, taxRateId, null, null);
    }

    private Invoice postInvoice(boolean inclusive, LineData... lines) {
        InvoiceData data = new InvoiceData(customer.getId(), DATE, null,
                null, null, null, inclusive, List.of(lines));
        return invoiceService.post(invoiceService.createDraft(data).getId());
    }

    @Test
    void exclusive_arGrossIncomeNetTaxCr() {
        // 1000 × 12% → net 1000 / tax 120 / gross 1120
        Invoice invoice = postInvoice(false, serviceLine("1", "1000", qqs12Id));

        assertThat(invoice.getTotal()).isEqualByComparingTo("1120"); // gross
        JournalEntry entry = glEntry(invoice);
        assertBalanced(entry);
        assertThat(baseOf(entry, "ACCOUNTS_RECEIVABLE", true)).isEqualByComparingTo("1120");
        assertThat(baseOf(entry, "SALES_TAX_PAYABLE", false)).isEqualByComparingTo("120");
        // Даромад Cr - net 1000 (SERVICE item → SERVICE_FEE_INCOME)
        assertThat(baseOf(entry, "SERVICE_FEE_INCOME", false)).isEqualByComparingTo("1000");
    }

    @Test
    void inclusive_taxExtracted() {
        Invoice invoice = postInvoice(true, serviceLine("1", "1120", qqs12Id));
        assertThat(invoice.getTotal()).isEqualByComparingTo("1120");
        JournalEntry entry = glEntry(invoice);
        assertBalanced(entry);
        assertThat(baseOf(entry, "ACCOUNTS_RECEIVABLE", true)).isEqualByComparingTo("1120");
        assertThat(baseOf(entry, "SALES_TAX_PAYABLE", false)).isEqualByComparingTo("120");
        assertThat(baseOf(entry, "SERVICE_FEE_INCOME", false)).isEqualByComparingTo("1000");
    }

    @Test
    void inventoryItem_cogsUntouchedByTax_balanced() {
        // Омборга 10 @ 800 кирган; сотув 5 @ 2000, 12% ҚҚС
        inventoryService.receive(invItem.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("800"), DATE, "SEED", null, null);
        LineData itemLine = new LineData(invItem.getId(), warehouse.getId(),
                new BigDecimal("5"), new BigDecimal("2000"), null, null, null,
                qqs12Id, null, null);
        Invoice invoice = postInvoice(false, itemLine);

        // net 10000, tax 1200, gross 11200
        assertThat(invoice.getTotal()).isEqualByComparingTo("11200");
        JournalEntry entry = glEntry(invoice);
        assertBalanced(entry); // COGS/INVENTORY home таннарх ҳам киради
        assertThat(baseOf(entry, "ACCOUNTS_RECEIVABLE", true)).isEqualByComparingTo("11200");
        assertThat(baseOf(entry, "SALES_TAX_PAYABLE", false)).isEqualByComparingTo("1200");
        // COGS = 5 × 800 = 4000 (ҚҚС таъсир қилмайди)
        assertThat(baseOf(entry, "SUPPLIES_MATERIALS_COGS", true)).isEqualByComparingTo("4000");
    }

    @Test
    void foreignCurrency_withTax_balancedAndInvariant() {
        // Arbitr-087: чет валюта ҳужжати USD валютали контактга ёзилади
        Contact usdCustomer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Солиқ USD мижози", null, null, null, null, null,
                "USD", null, null, null, null));
        InvoiceData data = new InvoiceData(usdCustomer.getId(), DATE, null,
                "USD", new BigDecimal("12345.6789"), null, false, List.of(
                serviceLine("1", "0.03", qqs12Id), serviceLine("1", "0.03", qqs12Id)));
        Invoice invoice = invoiceService.post(invoiceService.createDraft(data).getId());

        JournalEntry entry = glEntry(invoice);
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
    void itemDefaults_storedAndReadBack() {
        // Item default'лари (иккала форма prefill манбаси) - service сақлайди
        Item withDefaults = itemService.create(ItemType.SERVICE, new ItemData(
                "Default солиқли", null, null, null, null, null,
                itemService.defaultsFor(ItemType.SERVICE).income(), null, null,
                itemService.defaultsFor(ItemType.SERVICE).expense(), null, null,
                null, null, qqs12Id, qqs12Id));
        Item reloaded = itemService.get(withDefaults.getId());
        assertThat(reloaded.getSalesTaxRateId()).isEqualTo(qqs12Id);
        assertThat(reloaded.getPurchaseTaxRateId()).isEqualTo(qqs12Id);
    }
}
