package com.averpo.erp.security;

import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.security.domain.UserRole;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.testsupport.TestRoles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SecurityConfig соҳа қоидалари интеграцион тести (user-roles.md
 * «Тестлар» 2, 3 ва 6-бандлар; 054 сабоғи - қоидалар айнан MockMvc URL
 * даражасида текширилади). Рухсат этилган ёзувчи сўровлар «403 ЭМАС»
 * деб текширилади - бўш форма 400/422 бериши мумкин, муҳими security
 * қатлами ўтказгани.
 *
 * <p>Матчер тартиби гарови: /settings/warehouses (INVENTORY)
 * /settings/** (SETTINGS)дан олдин туриши WAREHOUSE_MANAGER
 * сценарийсида исботланади - тартиб бузилса айнан шу тест қизаради.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RoleAccessWebTest {

    @Autowired WebApplicationContext context;
    @Autowired AccountService accountService;
    @Autowired CompanySettingsService settingsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Payroll/ҳисобот экранлари render'ига тизим счётлари керак
        accountService.importDefaultChart();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /** Сўров security қатламидан ўтганини тасдиқлайди (403/401 эмас). */
    private void assertAllowed(org.springframework.test.web.servlet.RequestBuilder request)
            throws Exception {
        mockMvc.perform(request).andExpect(result -> {
            int status = result.getResponse().getStatus();
            assertThat(status).as("security ўтказиши керак эди").isNotIn(401, 403);
        });
    }

    // ---- Spec 2.1: SALES_MANAGER ----

    @Test
    void salesManager_salesAllowed_purchaseForbidden() throws Exception {
        var salesManager = TestRoles.as("sotuvchi", UserRole.SALES_MANAGER);

        // Ўз соҳаси: рўйхат ва ёзув очиқ
        mockMvc.perform(get("/invoices").with(salesManager))
                .andExpect(status().isOk());
        assertAllowed(post("/invoices").with(salesManager).with(csrf()));

        // Бегона соҳа: GET ҳам POST ҳам 403 (карта live-smoke талаби билан мос)
        mockMvc.perform(get("/bills").with(salesManager))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/bills").with(salesManager).with(csrf()))
                .andExpect(status().isForbidden());

        // Соҳа ҳисоботи SALES орқали, умумий молиявий ҳисобот эса ёпиқ
        mockMvc.perform(get("/reports/ar-aging").with(salesManager))
                .andExpect(status().isOk());
        mockMvc.perform(get("/reports/balance-sheet").with(salesManager))
                .andExpect(status().isForbidden());

        // Товар каталоги INVENTORY соҳасида - SALES_MANAGER'га ёпиқ (матрица)
        mockMvc.perform(get("/items").with(salesManager))
                .andExpect(status().isForbidden());
    }

    /** 066 quick endpoint'лари ҳам соҳа қоидасида (план 6-тузоқ). */
    @Test
    void salesManager_quickEndpoints_areaScoped() throws Exception {
        var salesManager = TestRoles.as("sotuvchi", UserRole.SALES_MANAGER);

        assertAllowed(post("/customers/quick").with(salesManager).with(csrf())
                .param("displayName", "Quick092 мижоз"));
        mockMvc.perform(post("/vendors/quick").with(salesManager).with(csrf())
                        .param("displayName", "Quick092 таъминотчи"))
                .andExpect(status().isForbidden());
    }

    // ---- Spec 2.2: PURCHASE_MANAGER ----

    @Test
    void purchaseManager_purchaseAllowed_salesForbidden() throws Exception {
        var purchaseManager = TestRoles.as("xaridchi", UserRole.PURCHASE_MANAGER);

        mockMvc.perform(post("/invoices").with(purchaseManager).with(csrf()))
                .andExpect(status().isForbidden());
        assertAllowed(post("/bills").with(purchaseManager).with(csrf()));

        // INVENTORY фақат кўриш: рўйхат очиқ, ёзув 403
        mockMvc.perform(get("/settings/warehouses").with(purchaseManager))
                .andExpect(status().isOk());
        mockMvc.perform(post("/settings/warehouses").with(purchaseManager).with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ---- Spec 2.3: WAREHOUSE_MANAGER (матчер тартиби гарови) ----

    @Test
    void warehouseManager_inventorySettingsOpen_generalSettingsForbidden() throws Exception {
        var warehouseManager = TestRoles.as("omborchi", UserRole.WAREHOUSE_MANAGER);

        // /settings/warehouses INVENTORY соҳасида - очиқ (GET ва POST)
        mockMvc.perform(get("/settings/warehouses").with(warehouseManager))
                .andExpect(status().isOk());
        assertAllowed(post("/settings/warehouses").with(warehouseManager).with(csrf())
                .param("name", "Тартиб гарови омбори").param("code", "ORD92"));

        // Умумий /settings (SETTINGS соҳаси) эса ёпиқ - матчер тартиби гарови
        mockMvc.perform(get("/settings").with(warehouseManager))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/settings/tax-rates").with(warehouseManager))
                .andExpect(status().isForbidden());

        // Бирлик/прайс каталоглари ҳам INVENTORY
        mockMvc.perform(get("/settings/units").with(warehouseManager))
                .andExpect(status().isOk());
        mockMvc.perform(get("/settings/price-lists").with(warehouseManager))
                .andExpect(status().isOk());
    }

    // ---- Spec 2.4: ACCOUNTANT'да GL йўқ ----

    @Test
    void accountant_hasNoGl_noFinReports() throws Exception {
        var accountant = TestRoles.as("buxgalter", UserRole.ACCOUNTANT);

        mockMvc.perform(post("/journal-entries").with(accountant).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/journal-entries").with(accountant))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/reports/balance-sheet").with(accountant))
                .andExpect(status().isForbidden());

        // Кундалик иши очиқ: сотув ёзуви ва соҳа ҳисоботи
        assertAllowed(post("/invoices").with(accountant).with(csrf()));
        mockMvc.perform(get("/reports/ar-aging").with(accountant))
                .andExpect(status().isOk());
    }

    // ---- Spec 2.5: VIEWER_AUDITOR - ҳар POST 403, соҳа GET'лари 200 ----

    @Test
    void viewerAuditor_everyPostForbidden_areaGetsAllowed() throws Exception {
        var viewer = TestRoles.as("auditor", UserRole.VIEWER_AUDITOR);

        // Ёзувчи сўровлар: соҳа қоидалари ҳам, харитага кирмаган йўл
        // (/attachments - глобал POST қоидаси мероси) ҳам 403
        String[] postUrls = {"/invoices", "/bills", "/bank-transactions",
                "/journal-entries", "/payroll", "/customers/quick",
                "/attachments/INVOICE/00000000-0000-0000-0000-000000000000"};
        for (String url : postUrls) {
            mockMvc.perform(post(url).with(viewer).with(csrf()))
                    .andExpect(status().isForbidden());
        }

        // Операцион соҳа GET'лари очиқ (матрица: ҳамма соҳа VIEW)
        String[] getUrls = {"/invoices", "/bills", "/accounts", "/journal-entries",
                "/bank-transactions", "/inventory/balances", "/payroll",
                "/employees", "/reports/balance-sheet", "/reports/ar-aging"};
        for (String url : getUrls) {
            mockMvc.perform(get(url).with(viewer))
                    .andExpect(status().isOk());
        }

        // SETTINGS/USERS соҳалари унга умуман кўринмайди
        mockMvc.perform(get("/settings").with(viewer)).andExpect(status().isForbidden());
        mockMvc.perform(get("/users").with(viewer)).andExpect(status().isForbidden());
        mockMvc.perform(get("/audit-log").with(viewer)).andExpect(status().isForbidden());
    }

    // ---- Spec 3: PERIOD_CLOSE ----

    @Test
    void periodClose_chiefAllowed_accountantForbidden() throws Exception {
        var chief = TestRoles.as("boshbux", UserRole.CHIEF_ACCOUNTANT);
        var accountant = TestRoles.as("buxgalter", UserRole.ACCOUNTANT);

        // CHIEF: саҳифа очилади, сана сақланади (ҳақиқий ўзгариш исботи)
        mockMvc.perform(get("/settings/closing-date").with(chief))
                .andExpect(status().isOk());
        mockMvc.perform(post("/settings/closing-date").with(chief).with(csrf())
                        .param("closingDate", "2026-06-30"))
                .andExpect(redirectedUrl("/settings/closing-date"));
        assertThat(settingsService.closingDate()).isEqualTo(LocalDate.of(2026, 6, 30));

        // CHIEF'га умумий /settings барибир ёпиқ (фақат closing-date очиқ)
        mockMvc.perform(get("/settings").with(chief))
                .andExpect(status().isForbidden());

        // ACCOUNTANT'да PERIOD_CLOSE имконияти йўқ - 403
        mockMvc.perform(post("/settings/closing-date").with(accountant).with(csrf())
                        .param("closingDate", "2026-05-31"))
                .andExpect(status().isForbidden());
        assertThat(settingsService.closingDate()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    /**
     * Arbitr-106: «Даврни ёпиш» кириш нуқтаси сайдбардан journalEntries
     * саҳифа тугмасига кўчди. Тугма ФАҚАТ PERIOD_CLOSE эгаларига кўринади
     * (092 дизайн шарти: CHIEF SETTINGS'сиз ҳам даврни айнан шу тугма
     * орқали бошқаради). GL'ни кўрадиган, лекин PERIOD_CLOSE'сиз роль
     * (DIRECTOR_ADMIN) журнал саҳифасини очади, аммо тугма ҳам, (энди
     * сайдбардан олинган) closing-date ҳаволаси ҳам умуман йўқ - шу
     * саҳифада «/settings/closing-date» матни бор/йўқлиги семантик гаров.
     */
    @Test
    void closingDateEntryPoint_onJournalPage_forPeriodCloseOnly() throws Exception {
        var chief = TestRoles.as("boshbux", UserRole.CHIEF_ACCOUNTANT);
        var director = TestRoles.as("direktor", UserRole.DIRECTOR_ADMIN);

        // CHIEF (GL=EDIT + PERIOD_CLOSE): журнал очиқ, тепасида «Даврни
        // ёпиш» тугмаси - closing-date саҳифасига ҳавола бор
        mockMvc.perform(get("/journal-entries").with(chief))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/settings/closing-date")));

        // DIRECTOR_ADMIN (GL=VIEW, PERIOD_CLOSE ЙЎҚ): журнал очиқ, лекин
        // тугма ҳам сайдбар ҳаволаси ҳам йўқ - closing-date матни умуман
        // чиқмайди (сайдбардан олингани + тугма PERIOD_CLOSE-only бўлгани)
        mockMvc.perform(get("/journal-entries").with(director))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/settings/closing-date"))));
    }

    // ---- DIRECTOR_ADMIN: кенг кўриш, ёзув йўқ ----

    @Test
    void directorAdmin_viewsEverything_writesNothing() throws Exception {
        var director = TestRoles.as("direktor", UserRole.DIRECTOR_ADMIN);

        mockMvc.perform(get("/invoices").with(director)).andExpect(status().isOk());
        mockMvc.perform(get("/reports/balance-sheet").with(director)).andExpect(status().isOk());
        mockMvc.perform(post("/invoices").with(director).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/settings").with(director)).andExpect(status().isForbidden());
        mockMvc.perform(get("/users").with(director)).andExpect(status().isForbidden());
    }

    // ---- SUPER_ADMIN: ҳамма нарса очиқ (регресс) ----

    @Test
    void superAdmin_fullAccess() throws Exception {
        var admin = TestRoles.as("bosh", UserRole.SUPER_ADMIN);

        mockMvc.perform(get("/settings").with(admin)).andExpect(status().isOk());
        mockMvc.perform(get("/users").with(admin)).andExpect(status().isOk());
        mockMvc.perform(get("/audit-log").with(admin)).andExpect(status().isOk());
        assertAllowed(post("/settings/closing-date").with(admin).with(csrf())
                .param("closingDate", ""));
    }

    // ---- Spec 6: сайдбар филтри (SALES_MANAGER) ----

    @Test
    void salesManager_sidebar_showsSales_hidesPurchases() throws Exception {
        var salesManager = TestRoles.as("sotuvchi", UserRole.SALES_MANAGER);

        // data-g="..." белгилар фақат сайдбар гуруҳларида бор - матн
        // сарлавҳалардан ишончлироқ маркер
        mockMvc.perform(get("/").with(salesManager))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-g=\"sales\"")))
                .andExpect(content().string(not(containsString("data-g=\"purchases\""))))
                .andExpect(content().string(not(containsString("data-g=\"bank\""))))
                .andExpect(content().string(not(containsString("data-g=\"accounting\""))))
                .andExpect(content().string(not(containsString("data-g=\"settings\""))));

        // Invoice формаси очилади (карта live-smoke талабининг MockMvc акси)
        mockMvc.perform(get("/invoices/new").with(salesManager))
                .andExpect(status().isOk());
    }
}
