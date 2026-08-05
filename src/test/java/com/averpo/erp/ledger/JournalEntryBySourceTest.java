package com.averpo.erp.ledger;

import com.averpo.erp.bank.domain.BankTransaction;
import com.averpo.erp.bank.service.BankTransactionService;
import com.averpo.erp.bank.service.BankTransactionService.TransferData;
import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.domain.ItemType;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.item.service.ItemService.ItemData;
import com.averpo.erp.ledger.domain.JournalEntry;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.repo.JournalEntryRepository;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.ledger.service.JournalEntryRequest;
import com.averpo.erp.ledger.service.JournalEntryRequest.Line;
import com.averpo.erp.ledger.service.PostingService;
import com.averpo.erp.sales.domain.Invoice;
import com.averpo.erp.sales.service.InvoiceService;
import com.averpo.erp.sales.service.InvoiceService.InvoiceData;
import com.averpo.erp.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.averpo.erp.testsupport.WithMockRole;
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
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ҳужжатдан GL ёзувига (JE) ўтиш (DEC-080): by-source endpoint +
 * document view линклари + reverseBySource репост hardening'и.
 *
 * <p>063 (JE → ҳужжат) нинг тескари симметрияси текширилади: (1) by-source
 * POSTED ҳужжатнинг JE'сига redirect; (2) топилмаган манба 404; (3) DRAFT
 * ҳужжат view'ида линк яширин, POSTED'да кўринади; (4) reverse→репост→
 * reverse кетма-кетлиги битта манбага иккита {@code reversalOf=null} ёзув
 * тўплаганда ҳам 500 (NonUniqueResult) бермайди - findFirst энг охиргисини
 * олади.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockRole(username = "je080")
class JournalEntryBySourceTest {

    /** Тест ҳужжатлар санаси. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 11);

    @Autowired WebApplicationContext context;
    @Autowired BankTransactionService bankService;
    @Autowired InvoiceService invoiceService;
    @Autowired ContactService contactService;
    @Autowired ItemService itemService;
    @Autowired AccountService accountService;
    @Autowired AccountRepository accountRepository;
    @Autowired JournalEntryRepository entryRepository;
    @Autowired PostingService postingService;

    private MockMvc mockMvc;

    /** Home валютали банк счёти (transfer манбаси + репост дебети). */
    private UUID bank;

    /** Касса - transfer манзили + репост кредити. */
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

    /** Банд 1: by-source POSTED манбанинг айнан ўз JE'сига redirect. */
    @Test
    void bySource_redirectsToActiveEntry() throws Exception {
        BankTransaction txn = bankService.transfer(new TransferData(bank, cash, DATE,
                new BigDecimal("100000"), null, null, null, "080 by-source"));
        JournalEntry entry = entryRepository
                .findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                        BankTransactionService.SOURCE_MODULE, txn.getId()).orElseThrow();

        mockMvc.perform(get("/journal-entries/by-source/"
                        + BankTransactionService.SOURCE_MODULE + "/" + txn.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/journal-entries/" + entry.getId()));
    }

    /** Банд 2: манба бўйича JE топилмаса - тушунарли 404 (мавжуд error нақши). */
    @Test
    void bySource_unknownSource_returns404() throws Exception {
        mockMvc.perform(get("/journal-entries/by-source/BANK_TXN/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    /**
     * Банд 3: DRAFT invoice view'ида «GL ёзуви» линки render бўлмайди
     * (проводкаси йўқ); post қилингач кўринади ва by-source URL'ига боради.
     */
    @Test
    void invoiceView_draftHidesJeLink_postedShows() throws Exception {
        Contact customer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "080 линк мижози", null, null, null, null, null,
                null, null, null, null, null));
        ItemService.DefaultAccounts def = itemService.defaultsFor(ItemType.SERVICE);
        Item service = itemService.create(ItemType.SERVICE, new ItemData(
                "080 линк хизмати", null, null, null, null, null,
                def.income(), null, null, def.expense(), null, null));
        Invoice draft = invoiceService.createDraft(new InvoiceData(customer.getId(),
                DATE, null, null, null, null,
                List.of(new InvoiceService.LineData(service.getId(), null,
                        BigDecimal.ONE, new BigDecimal("50000"), null, null))));

        // DRAFT - линк яширин
        mockMvc.perform(get("/invoices/" + draft.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(
                        "/journal-entries/by-source/INVOICE/"))));

        // POSTED - линк кўринади ва айнан шу invoice docId'сига
        Invoice invoice = invoiceService.post(draft.getId());
        mockMvc.perform(get("/invoices/" + invoice.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "/journal-entries/by-source/INVOICE/" + invoice.getId())));
    }

    /**
     * Банд 4 (hardening): reverse→репост→reverse - битта манбада REVERSED
     * асл + POSTED репост иккови {@code reversalOf=null} бўлади. Эски
     * Optional lookup бу ерда NonUniqueResultException (500) берарди;
     * findFirst энг охирги (POSTED репост) ёзувни олиб детерминистик
     * reverse қилади.
     */
    @Test
    void reverseBySource_afterRepost_reversesLatestWithout500() {
        String module = "TEST_080_REPOST";
        UUID docId = UUID.randomUUID();
        JournalEntryRequest request = new JournalEntryRequest(DATE, "080 репост",
                module, docId, List.of(
                        Line.debit(bank, Money.ofBase(new BigDecimal("50000"), "UZS"), "dt"),
                        Line.credit(cash, Money.ofBase(new BigDecimal("50000"), "UZS"), "ct")));

        JournalEntry first = postingService.createAndPost(request);
        postingService.reverse(first.getId(), DATE, "биринчи сторно");
        // Репост: айнан шу (module, docId) - REVERSED асл index'дан чиққани
        // учун янги POSTED ёзув муаммосиз киради (idempotency guard ўтади)
        JournalEntry repost = postingService.createAndPost(request);

        // Энди reversalOf=null бўйича иккита: first (REVERSED) + repost (POSTED)
        assertThat(entryRepository.findAll().stream()
                .filter(e -> module.equals(e.getSourceModule())
                        && docId.equals(e.getSourceDocumentId())
                        && e.getReversalOf() == null)
                .count()).isEqualTo(2);

        // Hardening: 500 эмас, энг охирги (repost) ёзув reverse қилинади
        JournalEntry storno = postingService.reverseBySource(module, docId, DATE, "иккинчи сторно");
        assertThat(storno.getReversalOf().getId()).isEqualTo(repost.getId());
    }
}
