package com.averpo.erp.contact;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.contact.web.ContactCardController.ContactCard;
import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.domain.ItemType;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.item.service.ItemService.ItemData;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.ledger.service.StatementService;
import com.averpo.erp.purchase.domain.BillLineType;
import com.averpo.erp.purchase.service.BillService;
import com.averpo.erp.purchase.service.BillService.BillData;
import com.averpo.erp.sales.domain.Invoice;
import com.averpo.erp.sales.service.CreditMemoService;
import com.averpo.erp.sales.service.InvoicePaymentService;
import com.averpo.erp.sales.service.InvoicePaymentService.AllocationData;
import com.averpo.erp.sales.service.InvoicePaymentService.PaymentData;
import com.averpo.erp.sales.service.InvoiceService;
import com.averpo.erp.sales.service.InvoiceService.InvoiceData;
import com.averpo.erp.search.service.GlobalSearchService;
import com.averpo.erp.search.service.SearchHit;
import com.averpo.erp.shared.service.CompanySettingsService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.averpo.erp.security.domain.UserRole;
import com.averpo.erp.testsupport.TestRoles;
import com.averpo.erp.testsupport.WithMockRole;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Контакт карточкаси (DEC-002) тестлари - spec docs/modules/
 * contact-card.md «Тестлар» бўлими (5 мажбурий):
 * <ol>
 *   <li>стат-карта қийматлари controller model'да - қолдиқ=statement
 *       closing, overdue=aging, аванс белгиси салбий қолдиқда;</li>
 *   <li>кўчирма жадвали render + давр филтри (web);</li>
 *   <li>row-click ва қидирув контакт линки ЯНГИ саҳифага;</li>
 *   <li>VIEWER саҳифани кўра олади (read-only);</li>
 *   <li>ScreenSmoke: /customers/{id}, /vendors/{id}.</li>
 * </ol>
 *
 * <p>Стат қийматлар controller {@code card} model attribute'идан ЎҚИЛАДИ
 * (HTML парслаш ўрнига аниқ тасдиқ). Композиция service'лари (statement/
 * aging/dashboard) JdbcClient хом SQL билан ўқийди - тест @Transactional
 * ичида JPA ёзувлари кўринсин деб ҳар GET'дан ОЛДИН {@code flush()}
 * (StatementServiceTest прецеденти).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockRole(username = "admin")
class ContactCardControllerTest {

    @Autowired WebApplicationContext context;
    @Autowired ContactService contactService;
    @Autowired InvoiceService invoiceService;
    @Autowired InvoicePaymentService paymentService;
    @Autowired CreditMemoService creditMemoService;
    @Autowired BillService billService;
    @Autowired ItemService itemService;
    @Autowired AccountService accountService;
    @Autowired AccountRepository accountRepository;
    @Autowired CompanySettingsService settingsService;
    @Autowired GlobalSearchService globalSearchService;
    @Autowired EntityManager entityManager;

    /** Security filter chain уланган MockMvc (VIEWER override учун ҳам). */
    private MockMvc mockMvc;

    /** Хизмат item'и (омборсиз - AR ҳаракати учун етарли). */
    private Item service;

    /** Тўлов қабул банк счёти. */
    private UUID bankAccountId;

    /** Bill EXPENSE сатри учун харажат счёти (Ижара). */
    private UUID rentAccountId;

    /** «Бугун» компания зонасида (aging + давр default). */
    private LocalDate today;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        accountService.importDefaultChart();
        ItemService.DefaultAccounts defaults = itemService.defaultsFor(ItemType.SERVICE);
        service = itemService.create(ItemType.SERVICE, new ItemData(
                "Карта хизмати", null, null, null, null, null,
                defaults.income(), null, null, defaults.expense(), null, null));
        bankAccountId = accountRepository.findByName("Банк ҳисобварағи").orElseThrow().getId();
        rentAccountId = accountRepository.findByName("Ижара").orElseThrow().getId();
        today = LocalDate.now(settingsService.zoneId());
    }

    // ---- фикстура ёрдамчилари ----

    private Contact customer(String name) {
        return contactService.create(ContactType.CUSTOMER, new ContactData(
                name, null, null, null, null, null, null, null, null, null, null));
    }

    private Contact vendor(String name) {
        return contactService.create(ContactType.VENDOR, new ContactData(
                name, null, null, null, null, null, null, null, null, null, null));
    }

    /** Home валютада POSTED invoice (Dr AR gross, Cr даромад). */
    private Invoice postInvoice(UUID customerId, LocalDate date, LocalDate due, String amount) {
        Invoice draft = invoiceService.createDraft(new InvoiceData(customerId, date, due,
                null, null, null, List.of(new InvoiceService.LineData(
                        service.getId(), null, BigDecimal.ONE, new BigDecimal(amount), null, null))));
        return invoiceService.post(draft.getId());
    }

    /** Invoice'га тўлиқ тақсимланган тўлов (Cr AR). */
    private void payInvoice(UUID customerId, LocalDate date, UUID invoiceId, String amount) {
        paymentService.create(new PaymentData(customerId, date, bankAccountId,
                null, null, new BigDecimal(amount), null,
                List.of(new AllocationData(invoiceId, new BigDecimal(amount)))));
    }

    /** Мустақил SERVICE credit memo (Cr AR gross - аванс/кредит ясайди). */
    private void creditMemo(UUID customerId, LocalDate date, String amount) {
        creditMemoService.create(new CreditMemoService.CreditMemoData(customerId,
                null, date, null, null, false, null,
                List.of(new CreditMemoService.LineData(service.getId(), null,
                        BigDecimal.ONE, new BigDecimal(amount), null, null, null, null, null))));
    }

    /** EXPENSE сатрли POSTED bill (Cr AP, Dr харажат). */
    private void postBill(UUID vendorId, LocalDate date, LocalDate due, String amount) {
        BillData data = new BillData(vendorId, null, date, due, null, null, null,
                List.of(new BillService.LineData(BillLineType.EXPENSE, null, null, null, null,
                        rentAccountId, new BigDecimal(amount), null)));
        billService.post(billService.createDraft(data).getId());
    }

    /** GET /{kind}/{id} → 200 + {@code card} model attribute (flush аввал). */
    private ContactCard card(String kind, UUID id) throws Exception {
        entityManager.flush();
        MvcResult res = mockMvc.perform(get("/" + kind + "/" + id))
                .andExpect(status().isOk()).andReturn();
        return (ContactCard) res.getModelAndView().getModel().get("card");
    }

    // ---- 1) стат-карта қийматлари (мижоз) ----

    @Test
    void customerCard_balanceOverdueTotalLastPayment_matchSources() throws Exception {
        Contact c = customer("Стат мижоз");
        Invoice inv = postInvoice(c.getId(), today.minusDays(40), today.minusDays(30), "100000");
        payInvoice(c.getId(), today.minusDays(20), inv.getId(), "40000");

        ContactCard card = card("customers", c.getId());

        // Жорий қолдиқ = statement closing = 100 000 - 40 000
        assertThat(card.balance()).isEqualByComparingTo("60000");
        assertThat(card.advance()).isFalse();
        assertThat(card.statement().closing()).isEqualByComparingTo("60000");
        // Overdue = aging (муддати ўтган invoice қолдиғи) - due 30 кун олдин
        assertThat(card.overdue()).isEqualByComparingTo("60000");
        // Жами сотув = REVENUE (солиқсиз) = 100 000
        assertThat(card.total()).isEqualByComparingTo("100000");
        // Охирги тўлов санаси
        assertThat(card.lastPayment()).isEqualTo(today.minusDays(20));
        assertThat(card.customer()).isTrue();
    }

    // ---- 1) аванс белгиси (салбий қолдиқ) ----

    @Test
    void customerCard_advanceFlag_onNegativeBalance() throws Exception {
        Contact c = customer("Аванс мижоз");
        // Кичик invoice + катта credit memo → манфий AR (мижоз фойдасига кредит)
        Invoice inv = postInvoice(c.getId(), today.minusDays(10), null, "10000");
        creditMemo(c.getId(), today.minusDays(5), "30000");

        ContactCard card = card("customers", c.getId());

        // AR = 10 000 - 30 000 = -20 000 → аванс
        assertThat(card.balance()).isEqualByComparingTo("-20000");
        assertThat(card.advance()).isTrue();
        // Overdue салбий қолдиқда нол (очиқ invoice йўқ)
        assertThat(card.overdue()).isEqualByComparingTo("0");
    }

    // ---- 1) стат-карта + кўчирма инвариант (таъминотчи, AP кўзгуси) ----

    @Test
    void vendorCard_apMirror_balanceOverdueTotal_andStatementInvariant() throws Exception {
        Contact v = vendor("Стат таъминотчи");
        postBill(v.getId(), today.minusDays(40), today.minusDays(30), "80000");

        ContactCard card = card("vendors", v.getId());

        // Қарзимиз (AP) мусбат кўринади (QBO vendor конвенцияси)
        assertThat(card.balance()).isEqualByComparingTo("80000");
        assertThat(card.advance()).isFalse();
        assertThat(card.customer()).isFalse();
        // vendorStatement инвариант: opening + Σ(ҳаракат) == closing == balance
        StatementService.Statement stmt = card.statement();
        BigDecimal sum = stmt.rows().stream().map(StatementService.Row::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(stmt.opening().add(sum)).isEqualByComparingTo(stmt.closing());
        assertThat(stmt.closing()).isEqualByComparingTo("80000");
        // Bill қаторда мусбат amount (қарзни оширади) + BILL тури
        assertThat(stmt.rows()).hasSize(1);
        assertThat(stmt.rows().get(0).amount()).isEqualByComparingTo("80000");
        assertThat(stmt.rows().get(0).sourceModule()).isEqualTo("BILL");
        // Overdue (муддати ўтган bill) = 80 000; Жами харид = EXPENSE = 80 000
        assertThat(card.overdue()).isEqualByComparingTo("80000");
        assertThat(card.total()).isEqualByComparingTo("80000");
        // Тўлов йўқ
        assertThat(card.lastPayment()).isNull();
    }

    // ---- 2) кўчирма render + давр филтри ----

    @Test
    void statement_rendersRows_andPeriodFilterReflectsDates() throws Exception {
        Contact c = customer("Кўчирма мижоз");
        Invoice inv = postInvoice(c.getId(), today.minusDays(10), null, "50000");
        entityManager.flush();

        LocalDate from = today.minusMonths(1);
        mockMvc.perform(get("/customers/" + c.getId())
                        .param("from", from.toString()).param("to", today.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Давр боши қолдиқ")))
                .andExpect(content().string(containsString("Давр охири қолдиқ")))
                .andExpect(content().string(containsString(inv.getInvoiceNumber())))
                .andExpect(content().string(containsString("value=\"" + from + "\"")))
                .andExpect(content().string(containsString("value=\"" + today + "\"")));
    }

    // ---- 3) row-click + қидирув линки янги саҳифага ----

    @Test
    void listRowClick_and_searchHit_pointToContactCard() throws Exception {
        Contact c = customer("Қидирув мижоз");
        entityManager.flush();

        // Рўйхат қатори карточкага (data-drawer'сиз → тўлиқ навигация)
        mockMvc.perform(get("/customers"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("data-href=\"/customers/" + c.getId() + "\"")));

        // Глобал қидирув контакт натижаси ҳам айнан карточкага
        List<SearchHit> hits = globalSearchService.search("Қидирув мижоз", false).contacts();
        assertThat(hits).anyMatch(h -> ("/customers/" + c.getId()).equals(h.url()));
    }

    // ---- 4) VIEWER read-only кўра олади ----

    @Test
    void viewer_canViewCard_readOnly_noEditButton() throws Exception {
        Contact c = customer("Кузатувчи мижоз");
        postInvoice(c.getId(), today.minusDays(5), null, "25000");
        entityManager.flush();

        mockMvc.perform(get("/customers/" + c.getId()).with(
                        TestRoles.as("kuzatuvchi", UserRole.VIEWER_AUDITOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Жорий қолдиқ")))
                .andExpect(content().string(containsString("Кўчирма")))
                // Таҳрир тугмаси (edit линки) VIEWER'га чиқмайди (canEdit false)
                .andExpect(content().string(not(containsString("/customers/" + c.getId() + "/edit"))));
    }

    // ---- 5) ScreenSmoke: /customers/{id}, /vendors/{id} ----

    @Test
    void screenSmoke_customerAndVendorCards_render() throws Exception {
        Contact c = customer("Smoke мижоз");
        Contact v = vendor("Smoke таъминотчи");
        entityManager.flush();

        mockMvc.perform(get("/customers/" + c.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Smoke мижоз")))
                .andExpect(content().string(containsString("Жами сотув")));
        mockMvc.perform(get("/vendors/" + v.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Smoke таъминотчи")))
                .andExpect(content().string(containsString("Жами харид")));
    }

    // ---- тип URL сегментига мос бўлиши шарт (защита) ----

    @Test
    void wrongKind_forContactType_returns404() throws Exception {
        Contact v = vendor("Нотўғри тур");
        entityManager.flush();
        // /customers/{vendorId} - тип мос эмас → 404 (нотўғри AR мантиқ олдини олади)
        mockMvc.perform(get("/customers/" + v.getId()))
                .andExpect(status().isNotFound());
    }
}
