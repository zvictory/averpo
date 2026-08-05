package com.averpo.erp.web;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.purchase.domain.Bill;
import com.averpo.erp.purchase.domain.BillLineType;
import com.averpo.erp.purchase.service.BillService;
import com.averpo.erp.purchase.service.BillService.BillData;
import com.averpo.erp.purchase.service.BillService.LineData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Arbitr-088 (ҳужжат формалари полиши) render тестлари.
 *
 * <p>Нима гарантияланади:
 * <ul>
 *   <li>жами блоки {@code .table-wrap} скролл зонасидан ТАШҚАРИДА
 *     (сатрлар жадвали ёпилгандан КЕЙИН) render бўлади ва жонли
 *     recompute скриптлари таянадиган {@code *Live} id'лар айнан
 *     сақланган - id контракти бузилса жонли жамилар жимгина 0.00
 *     бўлиб қоларди;</li>
 *   <li>курс майдони server render'ида ҳужжат валютасига қараб фарқ
 *     қилади: home валютада {@code display:none}, чет валютада очиқ
 *     (QBO хулқи, JS фақат валюта алмашганда янгилайди);</li>
 *   <li>home жами қатори ({@code *TotalHomeRow}) чет валютада
 *     кўринади, home'да яширин.</li>
 * </ul>
 *
 * <p>Жонли JS хулқи (қиймат ҳисоблари, сатр қўшиш/ўчириш) MockMvc'да
 * текширилмайди - улар браузер smoke сценарийларида; бу ерда фақат
 * server томон render контракти.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockRole(username = "admin")
class DocFormPolishWebTest {

    /** Тест ҳужжатлар санаси (мавжуд BillServiceTest қолипи). */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 11);

    @Autowired WebApplicationContext context;
    @Autowired BillService billService;
    @Autowired ContactService contactService;
    @Autowired AccountService accountService;
    @Autowired AccountRepository accountRepository;
    @Autowired com.averpo.erp.shared.service.CompanySettingsService settingsService;

    /** Security filter chain уланган MockMvc (ScreenSmokeTest қолипи). */
    private MockMvc mockMvc;

    /** springSecurity() уланмаса ҳар GET 302 login'га кетади. */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /** Route'ни очиб HTML матнини қайтаради (200 кутилади). */
    private String html(String route) throws Exception {
        return mockMvc.perform(get(route))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /**
     * Саккиз сатрли ҳужжат формасида жами блоки жадвалдан ташқарида,
     * учала жонли id сақланган, tfoot қолмаган; янги форма home
     * валютада очилгани учун курс майдони ҳам, home жами қатори ҳам
     * server render'ида яширин.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = ';', textBlock = """
            /bills/new;            bill
            /invoices/new;         inv
            /credit-memos/new;     cm
            /estimates/new;        est
            /refund-receipts/new;  rr
            /sales-receipts/new;   sr
            /purchase-orders/new;  po
            /vendor-credits/new;   vc
            """)
    void totalsBlock_outsideTable_idsPreserved_newFormHomeCurrency(
            String route, String prefix) throws Exception {
        String body = html(route);

        // Жамилар энди жадвал ичида эмас - tfoot умуман қолмаган
        assertThat(body).as("tfoot қолмаслиги керак").doesNotContain("<tfoot");

        // Сатрлар жадвалининг ёпилиш нуқтаси: id="lineRows" дан кейинги
        // биринчи </table>. Жонли id'лар ундан КЕЙИН туриши шарт.
        int lineRows = body.indexOf("id=\"lineRows\"");
        assertThat(lineRows).as("lineRows жадвали бор").isGreaterThan(-1);
        int tableEnd = body.indexOf("</table>", lineRows);
        assertThat(tableEnd).as("жадвал ёпилади").isGreaterThan(lineRows);

        for (String id : List.of(prefix + "SubtotalLive",
                prefix + "TaxLive", prefix + "TotalLive")) {
            int pos = body.indexOf("id=\"" + id + "\"");
            assertThat(pos).as(id + " мавжуд").isGreaterThan(-1);
            assertThat(pos).as(id + " жадвалдан ТАШҚАРИДА (кейин)")
                    .isGreaterThan(tableEnd);
        }

        // Arbitr-097: курс блоки shared.rateBlock компонентида (эски
        // ${prefix}RateWrap/${prefix}Rate id'лар ЙЎҚ - data-атрибутлар)
        assertThat(body).as("rateBlock компоненти уланган")
                .contains("data-rate-block")
                .contains("data-rate-currency")
                .contains("name=\"exchangeRate\" data-rate-canonical");
        assertThat(body).as("эски курс id'лари олиб ташланган")
                .doesNotContain("id=\"" + prefix + "RateWrap\"")
                .doesNotContain("id=\"" + prefix + "Rate\"");
        // Home валютада: home жами қатори яширин (rate блоки ҳам)
        assertThat(body).as("home жами қатори home валютада яширин")
                .contains("id=\"" + prefix + "TotalHomeRow\" data-rate-home-row style=\"display:none");
    }

    /**
     * JE формасида Дт/Кт жамилари ва баланс фарқи ҳам жадвалдан
     * ташқарига кўчган - jeDtLive/jeCtLive/jeDiffLive id'лари айнан
     * сақланган (Nargiza-005 жонли баланс скрипти контракти).
     */
    @Test
    void journalEntryForm_totalsOutsideTable_idsPreserved() throws Exception {
        String body = html("/journal-entries/new");

        assertThat(body).doesNotContain("<tfoot");
        int lineRows = body.indexOf("id=\"lineRows\"");
        int tableEnd = body.indexOf("</table>", lineRows);
        for (String id : List.of("jeDtLive", "jeCtLive", "jeDiffLive")) {
            int pos = body.indexOf("id=\"" + id + "\"");
            assertThat(pos).as(id + " мавжуд").isGreaterThan(-1);
            assertThat(pos).as(id + " жадвалдан ташқарида").isGreaterThan(tableEnd);
        }
    }

    /**
     * Тўлов формаларида (жадвал жамиси йўқ) ҳам курс майдони home
     * валютада server render'ида яширин - 088 2-банд қамрови.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = ';', textBlock = """
            /payments/new;         pay
            /invoice-payments/new; rcpt
            """)
    void paymentForms_rateHidden_onHomeCurrency(String route, String prefix)
            throws Exception {
        // Arbitr-097: тўлов формалари ҳам shared.rateBlock компонентида
        // (prefill + ориентация); эски ${prefix}RateWrap id'си ЙЎҚ. Home
        // валютада курс блоки яширин (data-rate-flipped=0 + display:none).
        String body = html(route);
        assertThat(body).as("rateBlock компоненти уланган")
                .contains("data-rate-block")
                .contains("name=\"exchangeRate\" data-rate-canonical");
        assertThat(body).as("эски курс id олиб ташланган")
                .doesNotContain("id=\"" + prefix + "RateWrap\"");
    }

    /**
     * Render ФАРҚИ: чет валютали (USD) bill таҳрир формасида курс
     * майдони ОЧИҚ (display:none йўқ - JTE null-атрибут қолипи style'ни
     * умуман чиқармайди) ва home жами қатори кўринади (display:flex).
     * Home'дагиси юқоридаги тестда - иккиси биргаликда QBO шартли
     * кўрсатишнинг server томонини қулфлайди.
     */
    @Test
    void billEditForm_foreignCurrency_rateVisible_homeRowShown() throws Exception {
        accountService.importDefaultChart();
        // Arbitr-087: USD ҳужжат учун контакт валютаси ҳам USD бўлиши шарт
        Contact vendor = contactService.create(ContactType.VENDOR, new ContactData(
                "Арбитр-088 USD таъминотчи", null, null, null, null, null,
                "USD", null, null, null, null));
        UUID rentAccountId = accountRepository.findByName("Ижара").orElseThrow().getId();
        Bill draft = billService.createDraft(new BillData(
                vendor.getId(), null, DATE, null, "USD", new BigDecimal("12600"), null,
                List.of(new LineData(BillLineType.EXPENSE, null, null, null, null,
                        rentAccountId, new BigDecimal("100"), null))));

        String body = html("/bills/" + draft.getId() + "/edit");

        // Arbitr-097: home=UZS + USD ҳужжат - USD аллақачон кучли (базис),
        // ФЛИП ЙЎҚ: ёрлиқ «1 USD = ? UZS», каноник 12600 ЎЗГАРМАГАН
        assertThat(body).as("курс ориентация: 1 USD = ? UZS (кучли базис)")
                .contains("data-rate-base>USD</span>")
                .contains("data-rate-quote>UZS</span>");
        assertThat(body).as("каноник курс home-per-doc ЎЗГАРМАГАН (12600)")
                .contains("name=\"exchangeRate\" data-rate-canonical value=\"12600\"");
        assertThat(body).as("home жами қатори чет валютада кўринади")
                .contains("id=\"billTotalHomeRow\" data-rate-home-row style=\"display:flex");
    }

    /**
     * ЖОНЛИ BUG тузатуви (Arbitr-097, SABOQLAR иккала home қоидаси):
     * home=USD, UZS ҳужжат - аввал «1 UZS = 0.000083 USD» кўринарди
     * (ўқилмас). Энди ФЛИП: базис USD (кучли), ёрлиқ «1 USD = ? UZS»;
     * лекин САҚЛАНАДИГАН каноник (1 UZS = x USD) ЎЗГАРМАЙДИ (server/servis
     * тегилмайди). Render server ориентацияси - JS'siz ҳам тўғри.
     */
    @Test
    void billEditForm_homeUsd_uzsDoc_flipsOrientation_canonicalPreserved() throws Exception {
        accountService.importDefaultChart();
        // home валютани USD га (POSTED йўқ - қулф йўқ; @Transactional rollback)
        settingsService.update("Тест USD home", "USD", "Asia/Tashkent", null, null);
        // UZS ҳужжат учун контакт валютаси ҳам UZS (Arbitr-087)
        Contact vendor = contactService.create(ContactType.VENDOR, new ContactData(
                "Арбитр-097 UZS таъминотчи", null, null, null, null, null,
                "UZS", null, null, null, null));
        UUID rentAccountId = accountRepository.findByName("Ижара").orElseThrow().getId();
        Bill draft = billService.createDraft(new BillData(
                vendor.getId(), null, DATE, null, "UZS", new BigDecimal("0.00008"), null,
                List.of(new LineData(BillLineType.EXPENSE, null, null, null, null,
                        rentAccountId, new BigDecimal("100"), null))));

        String body = html("/bills/" + draft.getId() + "/edit");

        // Флип: базис USD (кучли), quote UZS - «1 UZS = 0.00008 USD» кўринмайди
        assertThat(body).as("флип: 1 USD = ? UZS (кучли базис USD)")
                .contains("data-rate-base>USD</span>")
                .contains("data-rate-quote>UZS</span>")
                .contains("data-rate-flipped=\"1\"");
        // Каноник (1 UZS = 0.00008 USD) ЎЗГАРМАГАН - server/servis шуни олади
        assertThat(body).as("каноник курс home-per-doc ЎЗГАРМАГАН (0.00008)")
                .contains("name=\"exchangeRate\" data-rate-canonical value=\"0.00008\"");
    }
}
