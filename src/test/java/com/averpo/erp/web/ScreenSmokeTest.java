package com.averpo.erp.web;

import com.averpo.erp.security.domain.UserRole;
import com.averpo.erp.testsupport.TestRoles;
import com.averpo.erp.testsupport.WithMockRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Асосий экранлар smoke пакети (Arbitr-003): ҳар GET route 200 қайтариши
 * ва JTE шаблон саҳифага хос белги матни билан render бўлиши текширилади.
 * Мақсад - controller/шаблон қатламидаги хато қўлда очишни кутмасдан
 * тестда кўринсин; чуқур мазмун тестлари service қатламида қолади.
 * SUPER_ADMIN роли билан кирилади (Arbitr-092) - /settings саҳифалари
 * ҳам фақат шу ролга очиқ.
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockRole(username = "admin")
class ScreenSmokeTest {

    @Autowired WebApplicationContext context;

    /** Тескари давр тестида кутилган default саналар компания зонасида. */
    @Autowired com.averpo.erp.shared.service.CompanySettingsService settingsService;

    /** Arbitr-149: CoA badge тести default chart'ни қўлда юклайди - тест
        профилида DefaultChartInitializer (@Profile("!test")) ишламайди. */
    @Autowired com.averpo.erp.ledger.service.AccountService accountService;

    /** Security filter chain уланган MockMvc (қўлда қурилади). */
    private MockMvc mockMvc;

    /** MockMvc'га springSecurity() уланмаса ҳар GET 302 login'га кетади. */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /**
     * Route 200 қайтаради ва жавобда саҳифага хос белги бор. Белгилар
     * default locale (уз кирилл, messages.properties) сарлавҳаларидан -
     * CookieLocaleResolver cookie'сиз default'ни ишлатади.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = ';', textBlock = """
            /;                      Банк счётлари
            /accounts;              Счётлар режаси
            /journal-entries;       Журнал проводкалари
            /bills;                 Харид ҳисобварақлари
            /payments;              Vendor тўловлари
            /invoices;              Сотув ҳисобварақлари
            /credit-memos;          Кредит-ноталар
            /credit-memos/new;      Кредит-нота (қайтариш)
            /vendor-credits;        Таъминотчи кредит-ноталари
            /vendor-credits/new;    Таъминотчи кредити (қайтариш)
            /refund-receipts;       Пул қайтариш чеклари
            /refund-receipts/new;   Пул қайтариш чеки
            /estimates;             таклифлар
            /estimates/new;         Estimate (таклиф)
            /purchase-orders;       Харид буюртмалари
            /purchase-orders/new;   Харид буюртмаси (PO)
            /landed-costs;          Landed cost тақсимотлари
            /inventory/balances;    Омбор қолдиқлари
            /inventory/movements;   Омбор ҳаракатлари
            /settings;              Компания созламалари
            /settings/currencies;   Валюталар ва курслар
            /settings/units;        Ўлчов бирликлари
            /settings/units/groups; Бирлик гуруҳлари
            /settings/price-lists;  Нарх рўйхатлари
            /settings/tax-rates;    ҚҚС ставкалари
            /settings/payment-methods; Тўлов усуллари
            /settings/classes;      Йўналишлар
            /settings/warehouses;   Омборлар
            /settings/import;       Шаблонни юклаб олиш
            /audit-log;             Аудит журнали
            /expenses;              Чиқимлар
            /reports/profit-and-loss-by-class; йўналишлар кесимида
            /reports/trial-balance; Айланма қолдиқ
            /reports/balance-sheet; Баланс (Balance Sheet)
            /reports/profit-loss;   Фойда ва зарар
            /reports/inventory-valuation; Inventory valuation
            /reports/ap-aging;      Кредитор қарздорлик таҳлили
            """)
    void screen_rendersWithMarker(String route, String marker) throws Exception {
        mockMvc.perform(get(route))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(marker)));
    }

    @org.junit.jupiter.api.Test
    void expenseType_onGenericForm_redirectsToDedicatedScreen() throws Exception {
        // Arbitr-033: эски ?type=EXPENSE линклари синмасин - янги экранга
        mockMvc.perform(get("/bank-transactions/new").param("type", "EXPENSE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrl("/expenses/new"));
    }

    @org.junit.jupiter.api.Test
    void expenseForm_rendersQboOrderedFields() throws Exception {
        // Янги чиқим формаси очилади: тўлов усули select'ида seed усул бор
        mockMvc.perform(get("/expenses/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Тўлов усули")))
                .andExpect(content().string(containsString("Нақд")));
    }

    @org.junit.jupiter.api.Test
    void settingsSetup_showsWelcomeBanner() throws Exception {
        // Arbitr-056: setup=1 да онбординг banner кўринади (уз кирилл маркер;
        // ADMIN роли синф даражасида - /settings ADMIN'га очиқ)
        mockMvc.perform(get("/settings").param("setup", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Хуш келибсиз")));
    }

    @org.junit.jupiter.api.Test
    void factoryReset_step1_rendersForAdmin() throws Exception {
        // factory-reset.md: 1-босқич danger саҳифа ADMIN'га очиқ (GET 200,
        // сарлавҳа маркери). Тўлиқ оқим FactoryResetControllerTest'да.
        mockMvc.perform(get("/settings/reset"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Заводга қайтариш")));
    }

    @org.junit.jupiter.api.Test
    @WithMockRole(value = UserRole.CHIEF_ACCOUNTANT, username = "acc")
    void auditLog_accountantForbidden() throws Exception {
        // audit-log.md 6-банд + user-roles.md: USERS соҳаси фақат
        // SUPER_ADMIN - ҳатто энг кенг бухгалтерия роли CHIEF ҳам 403
        mockMvc.perform(get("/audit-log"))
                .andExpect(status().isForbidden());
    }

    @org.junit.jupiter.api.Test
    @WithMockRole(value = UserRole.ACCOUNTANT, username = "acc")
    void priceListLookup_accountantReachable_gracefulEmpty() throws Exception {
        // Prefill endpoint'и /settings/** ADMIN қулфидан ТАШҚАРИ бўлиши
        // шарт - invoice'ни ACCOUNTANT ҳам киритади (CurrencyController.
        // lookup прецеденти). Номаълум item → 200 + бўш матн (400 эмас,
        // prefill graceful): чақирувчи каталог нархга қайтади.
        mockMvc.perform(get("/price-lists/lookup")
                        .param("itemId", java.util.UUID.randomUUID().toString())
                        .param("currency", "UZS"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @org.junit.jupiter.api.Test
    void profitLoss_reversedPeriod_fallsBackToDefault() throws Exception {
        // Alisa-005: from > to (бузилган URL) - default даврга қайтади
        // (йил боши - бугун, компания timezone'ида), бўш P&L эмас
        java.time.LocalDate today = java.time.LocalDate.now(settingsService.zoneId());
        String yearStart = today.withDayOfYear(1).toString();
        // Саналар атайлаб узоқ ўтмишдан - default билан тасодифан
        // устма-уст тушиб тестни «ёлғон яшил» қилмасин
        mockMvc.perform(get("/reports/profit-loss")
                        .param("from", "2001-12-31")
                        .param("to", "2001-01-01"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"" + yearStart + "\"")))
                .andExpect(content().string(containsString("value=\"" + today + "\"")));
    }

    @org.junit.jupiter.api.Test
    void dashboard_quickActions_visibleForAdmin_hiddenForViewer() throws Exception {
        // Arbitr-036: Тез амаллар картаси фақат ёза оладиганларга -
        // ADMIN (синф даражасидаги user) кўради
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Тез амаллар")));
        // VIEWER'га карта умуман render бўлмайди
        mockMvc.perform(get("/").with(
                        TestRoles.as("kuzatuvchi", UserRole.VIEWER_AUDITOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(containsString("Тез амаллар"))));
    }

    @org.junit.jupiter.api.Test
    void createMegaPanel_visibleForAdmin_hiddenForViewer() throws Exception {
        // «+ Янги» QBO «+ New» услубидаги 3 устунли панел фақат ёза
        // оладиганларга - ADMIN кўради. Маркер `id="createPanel"`
        // (Arbitr-117: панел Penguin/Tailwind'га кўчди - CSS класс маркер
        // ўрнига барқарор id) - панел perms.anyEdit() ичида render бўлади.
        // Arbitr-139: QBO қолипида Payroll (иш ҳақи) ва Счёт яратиш панелда
        // КЎРСАТИЛМАЙДИ (навигацияда қолади); Товар Бошқа устунида. Маркер -
        // панелга хос ҳужжат яратиш href'лари (навигация /accounts, дашборд
        // бошқа href'лар ишлатади; бу /new ёрлиқлар фақат шу панелда)
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"createPanel\"")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(containsString("href=\"/payroll/new\""))))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(containsString("href=\"/accounts/new\""))))
                .andExpect(content().string(containsString("href=\"/items/new\"")));
        // VIEWER'га панел умуман render бўлмайди (Arbitr-092: филтр энди
        // perms.anyEdit() - view-only ролда бирорта EDIT йўқ)
        mockMvc.perform(get("/").with(
                        TestRoles.as("kuzatuvchi", UserRole.VIEWER_AUDITOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(containsString("id=\"createPanel\""))));
    }

    @org.junit.jupiter.api.Test
    void createPanel_columnsGatedByArea() throws Exception {
        // Arbitr-139: QBO 3 устун (Мижозлар/Таъминотчилар/Бошқа) ҳар бири
        // ЎЗ соҳаси EDIT'и бўйича гейтланади - тор роль фақат ўз устунини
        // кўради. Маркер панелга хос /new href'лари.
        // SALES_MANAGER (фақат SALES=E): фақат Мижозлар устуни
        mockMvc.perform(get("/").with(TestRoles.as("sotuvchi", UserRole.SALES_MANAGER)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/customers/new\"")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(containsString("href=\"/vendors/new\""))))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(containsString("href=\"/items/new\""))));
        // PURCHASE_MANAGER (PURCHASE=E, INVENTORY=V): фақат Таъминотчилар -
        // Бошқа устун edit(INVENTORY) талаб қилади, VIEW даражаси етмайди
        mockMvc.perform(get("/").with(TestRoles.as("taminotchi", UserRole.PURCHASE_MANAGER)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/vendors/new\"")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(containsString("href=\"/customers/new\""))))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(containsString("href=\"/items/new\""))));
        // WAREHOUSE_MANAGER (INVENTORY=E): Бошқа устуни очилади (Товар кўринади)
        mockMvc.perform(get("/").with(TestRoles.as("omborchi", UserRole.WAREHOUSE_MANAGER)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/items/new\"")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(containsString("href=\"/customers/new\""))))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(containsString("href=\"/vendors/new\""))));
    }

    @org.junit.jupiter.api.Test
    void accountsList_typeColumn_showsClassificationBadge() throws Exception {
        // Arbitr-149: CoA Type устуни AccountType номи + classification badge.
        // Тест базаси бўш старт (DefaultChartInitializer @Profile("!test")) -
        // default chart'ни қўлда юклаймиз (51 счёт, ичида ASSET «Касса» бор).
        // ASSET → soft primary tone `bg-primary/15 text-primary` (badge'га хос;
        // createButton тўлиқ `bg-primary text-on-primary` ишлатади). Badge
        // label инглизча «Asset» - locale uz бўлса ҳам (фойдаланувчи қарори)
        accountService.importDefaultChart();
        mockMvc.perform(get("/accounts"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("bg-primary/15 text-primary")))
                .andExpect(content().string(containsString(">Asset</span>")));
    }

    @org.junit.jupiter.api.Test
    void missingStaticResource_returns404_not500() throws Exception {
        // Arbitr-021: браузер сўраган йўқ favicon/эски bookmark
        // NoResourceFoundException беради - махсус handler'сиз catch-all
        // уни 500 + ERROR log қиларди; энди 404 бўлиши шарт
        mockMvc.perform(get("/definitely-missing-xyz.ico"))
                .andExpect(status().isNotFound());
    }
}
