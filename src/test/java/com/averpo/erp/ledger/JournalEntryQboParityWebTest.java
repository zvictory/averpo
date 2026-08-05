package com.averpo.erp.ledger;

import com.averpo.erp.ledger.domain.JournalEntry;
import com.averpo.erp.ledger.domain.JournalEntryLine;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.repo.JournalEntryRepository;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.shared.service.CompanySettingsService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ARBITR-107: JE формаси QBO parity - валюта/курс ҲУЖЖАТ даражасида.
 *
 * <p>QBO'да JournalEntry битта валютада (CurrencyRef header'да,
 * entities.md:97-98). Бу тест форма энди валюта/курсни header'да
 * (shared.rateBlock компоненти реюзи) сўрашини ва сатрлардан
 * Валюта/Курс устунлари олинганини гаровлайди. КРИТИК: домен/servis
 * ЎЗГАРМАЙДИ - controller header қийматини ҳар сатрга тарқатади, шунда
 * сатр Money'си (валюта+курс) ва BR-LED валидациялари айнан аввалгидек.
 * Тест ИККАЛА home вариантини қамрайди (SABOQLAR: home=UZS + home=USD
 * флип - каноник курс ЎЗГАРМАГАНи assert).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockRole(username = "admin")
class JournalEntryQboParityWebTest {

    @Autowired WebApplicationContext context;
    @Autowired CompanySettingsService settingsService;
    @Autowired AccountService accountService;
    @Autowired AccountRepository accountRepository;
    @Autowired JournalEntryRepository entryRepository;

    private MockMvc mockMvc;
    private UUID cash;
    private UUID bank;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        accountService.importDefaultChart();
        cash = accountRepository.findByName("Касса").orElseThrow().getId();
        bank = accountRepository.findByName("Банк ҳисобварағи").orElseThrow().getId();
    }

    /**
     * home=UZS: форма header'да валюта select + rateBlock (hidden каноник
     * name="exchangeRate"); сатрларда валюта/курс input ЙЎҚ; 8 бўш қатор;
     * Дт/Кт сарлавҳада data-curtag; «Ҳаммасини тозалаш»; жонли баланс
     * id'лари сақланган.
     */
    @Test
    void newForm_headerRateBlock_noLineColumns_homeUzs() throws Exception {
        String body = html("/journal-entries/new");

        // Header rateBlock (097 компонент маркапи)
        assertThat(body).as("header курс блоки + валюта select")
                .contains("data-rate-block")
                .contains("data-rate-currency")
                .contains("name=\"exchangeRate\"")
                .contains("data-rate-canonical");
        // Сатрда валюта/курс устунлари ЙЎҚ (QBO parity)
        assertThat(body).as("сатр валюта/курс input олиб ташланган")
                .doesNotContain("lines[0].currency")
                .doesNotContain("lines[0].exchangeRate");
        // Дт/Кт сарлавҳада жонли валюта тэги
        assertThat(body).contains("data-curtag");
        // 8 бўш қатор (QBO) - индекс 7 бор, 8 йўқ
        assertThat(body).as("8 та бўш қатор")
                .contains("lines[7].accountId")
                .doesNotContain("lines[8].accountId");
        // Ҳаммасини тозалаш тугмаси + жонли баланс контракти
        assertThat(body).contains("jeClearLines")
                .contains("id=\"jeDtLive\"")
                .contains("id=\"jeCtLive\"")
                .contains("id=\"jeDiffLive\"");
    }

    /**
     * home=USD: форма header валюта select'ини USD default билан render
     * қилади; сатрда валюта/курс йўқ (иккала home вариантида тузилиш бир
     * хил). Ориентация математикаси компонент тестларида (RateOrientInputTest).
     */
    @Test
    void newForm_homeUsd_headerSelectRendered() throws Exception {
        settingsService.update("Тест USD home", "USD", "Asia/Tashkent", null, null);

        String body = html("/journal-entries/new");

        assertThat(body).as("home=USD да ҳам header валюта select бор")
                .contains("data-rate-currency")
                .contains("name=\"exchangeRate\"");
        assertThat(body).doesNotContain("lines[0].currency");
    }

    /**
     * home=UZS, USD ҳужжат POST: header валюта/курс ҲАР СATRга тарқатилади -
     * сатр Money'си USD/12000 билан сақланади (домен ўзгармаган исботи).
     */
    @Test
    void postUsdDoc_homeUzs_linesGetHeaderCurrencyRate() throws Exception {
        mockMvc.perform(post("/journal-entries").with(csrf())
                        .param("action", "post")
                        .param("entryDate", "2026-07-08")
                        .param("description", "107 USD ҳужжат тести")
                        .param("currency", "USD")
                        .param("exchangeRate", "12000")
                        .param("lines[0].accountId", cash.toString())
                        .param("lines[0].debitAmount", "100")
                        .param("lines[1].accountId", bank.toString())
                        .param("lines[1].creditAmount", "100"))
                .andExpect(status().is3xxRedirection());

        JournalEntry entry = entryRepository.findAll().stream()
                .filter(e -> "107 USD ҳужжат тести".equals(e.getDescription()))
                .findFirst().orElseThrow();
        assertThat(entry.getLines()).hasSize(2);
        for (JournalEntryLine line : entry.getLines()) {
            var money = line.getDebit() != null ? line.getDebit() : line.getCredit();
            // Header валюта/курс ҳар сатрга тушган: USD, 1 USD = 12000 UZS
            assertThat(money.getCurrency()).isEqualTo("USD");
            assertThat(money.getExchangeRate()).isEqualByComparingTo("12000");
            assertThat(money.getAmount()).isEqualByComparingTo("100");
            // baseAmount = 100 × 12000 = 1 200 000 UZS (ledger home'да балансланади)
            assertThat(money.getBaseAmount()).isEqualByComparingTo("1200000");
        }
    }

    /**
     * home=USD, UZS ҳужжат: POST валидацияда йиқилиб формага қайтганда
     * (баланссиз) rateBlock ФЛИП ориентация билан render бўлади -
     * «1 UZS = 0.000083 USD» эмас, кучли валюта базис (data-rate-base=USD);
     * hidden КАНОНИК курс ЎЗГАРМАЙДИ (server айнан шуни оларди).
     */
    @Test
    void postForeignInvalid_reRenderFlipsOrientation_homeUsd() throws Exception {
        settingsService.update("Тест USD home", "USD", "Asia/Tashkent", null, null);

        // Атайлаб баланссиз (фақат дебет) - createAndPost BR кўтаради, форма
        // қайта render бўлади (currency=UZS, exchangeRate каноник сақланади)
        String body = mockMvc.perform(post("/journal-entries").with(csrf())
                        .param("action", "post")
                        .param("entryDate", "2026-07-08")
                        .param("description", "107 флип re-render")
                        .param("currency", "UZS")
                        .param("exchangeRate", "0.00008")
                        .param("lines[0].accountId", cash.toString())
                        .param("lines[0].debitAmount", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Флип: UZS ҳужжат, home=USD - USD кучли базис (data-rate-base=USD),
        // визуал йўналиш тескари (data-rate-flipped=1)
        assertThat(body).as("кучли-валюта базис USD (флип)")
                .contains("data-rate-flipped=\"1\"")
                .contains("data-rate-base>USD</span>");
        // КАНОНИК курс ЎЗГАРМАГАН - hidden айнан 0.00008 (server шуни олади)
        assertThat(body).as("каноник курс сақланган")
                .contains("data-rate-canonical value=\"0.00008\"");
    }

    /** HTMX сатр-қўшиш partial'ида ҳам валюта/курс input йўқ. */
    @Test
    void lineRowHtmx_hasNoCurrencyRate() throws Exception {
        String body = html("/journal-entries/line-row?index=3");

        assertThat(body).contains("lines[3].debitAmount")
                .doesNotContain("lines[3].currency")
                .doesNotContain("lines[3].exchangeRate");
    }

    /**
     * Иловалар банди (107): draft сақлагач redirect КЎРИШ саҳифасига боради,
     * у ерда JOURNAL_ENTRY иловалар фрагменти on-load юкланади (server тайёр).
     */
    @Test
    void draftEntry_viewShowsAttachmentsFragment() throws Exception {
        mockMvc.perform(post("/journal-entries").with(csrf())
                        .param("action", "draft")
                        .param("entryDate", "2026-07-08")
                        .param("description", "107 илова draft")
                        .param("lines[0].accountId", cash.toString())
                        .param("lines[0].debitAmount", "5000")
                        .param("lines[1].accountId", bank.toString())
                        .param("lines[1].creditAmount", "5000"))
                .andExpect(status().is3xxRedirection());

        JournalEntry entry = entryRepository.findAll().stream()
                .filter(e -> "107 илова draft".equals(e.getDescription()))
                .findFirst().orElseThrow();
        String body = html("/journal-entries/" + entry.getId());

        assertThat(body).as("КЎРИШ саҳифасида JOURNAL_ENTRY иловалар фрагменти")
                .contains("/attachments/JOURNAL_ENTRY/" + entry.getId());
    }

    /** GET саҳифа матни. */
    private String html(String url) throws Exception {
        return mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
