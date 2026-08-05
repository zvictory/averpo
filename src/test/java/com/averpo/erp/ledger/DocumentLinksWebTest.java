package com.averpo.erp.ledger;

import com.averpo.erp.bank.domain.BankTransaction;
import com.averpo.erp.bank.service.BankTransactionService;
import com.averpo.erp.bank.service.BankTransactionService.TransferData;
import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.inventory.service.InventoryService;
import com.averpo.erp.inventory.service.WarehouseService;
import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.domain.ItemType;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.item.service.ItemService.ItemData;
import com.averpo.erp.ledger.domain.JournalEntry;
import com.averpo.erp.ledger.repo.JournalEntryRepository;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.sales.domain.CreditMemo;
import com.averpo.erp.sales.domain.Invoice;
import com.averpo.erp.sales.service.CreditMemoService;
import com.averpo.erp.sales.service.InvoiceService;
import com.averpo.erp.sales.service.InvoiceService.InvoiceData;
import com.averpo.erp.testsupport.WithMockRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ҳужжатлараро линклар (Arbitr-063): кўриш экранларида рақамлар оддий
 * матн эмас, href бўлиши. Банд-ба-банд href assert'лари: 1 - JE кўришида
 * манба ҳужжат линки, 2 - сторно линклари иккала йўналишда, 3 - аудитда
 * Ҳужжат устуни JE'га, 5 - омбор ҳаракатларида тўлиқ тур қамрови,
 * 7 - invoiceView'да шу ҳужжатдан яратилган кредит-нота линки.
 * Банд 4 (statement) - StatementWebTest'да; банд 6 (payroll тўлов→run)
 * ўтказилган: PayrollPayment domain'ида run боғи мавжуд эмас.
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockRole(username = "linkchi")
class DocumentLinksWebTest {

    /** Барча тест ҳужжатлар санаси. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 9);

    @Autowired WebApplicationContext context;
    @Autowired BankTransactionService bankService;
    @Autowired InvoiceService invoiceService;
    @Autowired CreditMemoService creditMemoService;
    @Autowired ContactService contactService;
    @Autowired ItemService itemService;
    @Autowired InventoryService inventoryService;
    @Autowired WarehouseService warehouseService;
    @Autowired AccountService accountService;
    @Autowired AccountRepository accountRepository;
    @Autowired JournalEntryRepository entryRepository;

    private MockMvc mockMvc;

    /** Home валютали банк счёти (transfer манбаси). */
    private UUID bank;

    /** Касса - transfer манзили. */
    private UUID cash;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        accountService.importDefaultChart();
        bank = accountRepository.findByName("Банк ҳисобварағи").orElseThrow().getId();
        cash = accountRepository.findByName("Касса").orElseThrow().getId();
    }

    /** Transfer қилиб фаол GL ёзувини қайтаради (AuditLogTest қолипи). */
    private JournalEntry postTransfer() {
        BankTransaction txn = bankService.transfer(new TransferData(bank, cash, DATE,
                new BigDecimal("100000"), null, null, null, "линк тести"));
        return entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                BankTransactionService.SOURCE_MODULE, txn.getId()).orElseThrow();
    }

    /**
     * Банд 1: JE кўришида манба ҳужжатга «Ҳужжатни очиш» линки + тур
     * номи i18n'дан (хом BANK_TXN enum'и кўринмайди).
     */
    @Test
    void journalEntryView_linksToSourceDocument() throws Exception {
        JournalEntry entry = postTransfer();

        mockMvc.perform(get("/journal-entries/" + entry.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "/bank-transactions/" + entry.getSourceDocumentId())))
                .andExpect(content().string(containsString("Ҳужжатни очиш")))
                .andExpect(content().string(containsString("Банк транзакцияси")));
    }

    /** Банд 2: сторно линклари иккала йўналишда (асл ⇄ сторно JE). */
    @Test
    void journalEntryView_reversalLinksBothDirections() throws Exception {
        JournalEntry entry = postTransfer();
        bankService.reverse(entry.getSourceDocumentId(), DATE, "линк сторноси");
        JournalEntry storno = entry.getReversedBy();
        assertThat(storno).isNotNull();

        // Асл JE'да сторно рақами линк
        mockMvc.perform(get("/journal-entries/" + entry.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "/journal-entries/" + storno.getId())));
        // Сторно JE'да асл проводка линки
        mockMvc.perform(get("/journal-entries/" + storno.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Асл проводка")))
                .andExpect(content().string(containsString(
                        "/journal-entries/" + entry.getId())));
    }

    /** Банд 3: аудит журналида Ҳужжат устуни JE кўришига линк. */
    @Test
    void auditLog_docNumberLinksToJournalEntry() throws Exception {
        JournalEntry entry = postTransfer();

        mockMvc.perform(get("/audit-log"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "/journal-entries/" + entry.getId())));
    }

    /**
     * Банд 5: омбор ҳаракатларида reference тури тўлиқ қамров - аввал
     * фақат BILL эди, энди SALES_RECEIPT (ва бошқалар) ҳам ҳужжатига
     * линк. Ҳаракат тўғри receive API'си билан ёзилади - линк href'и
     * тур→URL харитасидан чиқиши текширилади.
     */
    @Test
    void movements_typedReferencesLinked() throws Exception {
        ItemService.DefaultAccounts inv = itemService.defaultsFor(ItemType.INVENTORY);
        Item goods = itemService.create(ItemType.INVENTORY, new ItemData(
                "Линк тест товари", null, null, null, null, null,
                inv.income(), null, null, inv.expense(), inv.inventoryAsset(), null));
        UUID warehouse = warehouseService.all().stream()
                .filter(w -> "Асосий омбор".equals(w.getName()))
                .findFirst().orElseThrow().getId();
        UUID billId = UUID.randomUUID();
        UUID srId = UUID.randomUUID();
        inventoryService.receive(goods.getId(), warehouse, BigDecimal.ONE,
                new BigDecimal("1000"), DATE, "BILL", billId, null);
        inventoryService.receive(goods.getId(), warehouse, BigDecimal.ONE,
                new BigDecimal("1000"), DATE, "SALES_RECEIPT", srId, null);

        mockMvc.perform(get("/inventory/movements"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/bills/" + billId)))
                .andExpect(content().string(containsString("/sales-receipts/" + srId)));
    }

    /** Банд 7: invoiceView'да шу ҳужжатдан яратилган CM рақами линк. */
    @Test
    void invoiceView_creditsFromThisLinked() throws Exception {
        Contact customer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Линк тест мижози", null, null, null, null, null,
                null, null, null, null, null));
        ItemService.DefaultAccounts def = itemService.defaultsFor(ItemType.SERVICE);
        Item service = itemService.create(ItemType.SERVICE, new ItemData(
                "Линк тест хизмати", null, null, null, null, null,
                def.income(), null, null, def.expense(), null, null));
        Invoice draft = invoiceService.createDraft(new InvoiceData(customer.getId(),
                DATE, null, null, null, null,
                List.of(new InvoiceService.LineData(service.getId(), null,
                        BigDecimal.ONE, new BigDecimal("50000"), null, null))));
        Invoice invoice = invoiceService.post(draft.getId());
        // Invoice'дан яратилган қайтариш (returns.md) - creditsFromThis рўйхати
        CreditMemo memo = creditMemoService.create(new CreditMemoService.CreditMemoData(
                customer.getId(), invoice.getId(), DATE, null, null, false, null,
                List.of(new CreditMemoService.LineData(service.getId(), null,
                        BigDecimal.ONE, new BigDecimal("50000"),
                        null, null, null, null, null))));

        mockMvc.perform(get("/invoices/" + invoice.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "/credit-memos/" + memo.getId())));
    }
}
