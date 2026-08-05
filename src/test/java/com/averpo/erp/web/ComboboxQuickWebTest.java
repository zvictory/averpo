package com.averpo.erp.web;

import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.averpo.erp.security.domain.UserRole;
import com.averpo.erp.testsupport.TestRoles;
import com.averpo.erp.testsupport.WithMockRole;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Combobox quick-add endpoint'лари (Arbitr-066) - spec docs/modules/
 * combobox.md «Тестлар» рўйхати:
 * <ol>
 *   <li>муваффақиятли create - жавобда {id, label};</li>
 *   <li>BR хато оқими - 422 + {message} (модал ичида кўрсатилади);</li>
 *   <li>VIEWER'га 403 (SecurityConfig умумий POST қоидаси);</li>
 *   <li>fragment формалар render - CSRF hidden input бор.</li>
 * </ol>
 *
 * <p>Ном танловлари seed билан тўқнашмайдиган бетакрор сатрлар
 * (умумий тест DB, seed номлари: Касса счёти/Асосий омбор/UZS...).
 * Роллар Arbitr-092 матрицасига мос: contact quick'лар ACCOUNTANT
 * (SALES/PURCHASE EDIT), account quick - CHIEF_ACCOUNTANT (GL EDIT,
 * янги ACCOUNTANT'да GL йўқ), warehouse quick create -
 * WAREHOUSE_MANAGER (INVENTORY EDIT; ACCOUNTANT фақат кўради) -
 * quick endpoint'лар соҳа қоидасига тушганининг исботи ҳам шу.
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockRole(value = UserRole.ACCOUNTANT, username = "acc")
class ComboboxQuickWebTest {

    @Autowired WebApplicationContext context;
    @Autowired ContactService contactService;
    @Autowired com.averpo.erp.shared.service.CurrencyService currencyService;
    @Autowired com.averpo.erp.shared.service.CompanySettingsService settingsService;

    /** Security filter chain уланган MockMvc (VIEWER override учун ҳам). */
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ---- 1. Муваффақиятли create: {id, label} ----

    @Test
    void customerQuickReturnsIdAndLabel() throws Exception {
        mockMvc.perform(post("/customers/quick").with(csrf())
                        .param("displayName", "Комбо Мижоз 066"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.label").value("Комбо Мижоз 066"));
        // Тип тўғри тайинланганини текширамиз - CUSTOMER рўйхатида кўринади
        assertThat(contactService.activeRefsByType(ContactType.CUSTOMER))
                .anyMatch(ref -> ref.displayName().equals("Комбо Мижоз 066"));
    }

    @Test
    void vendorQuickReturnsIdAndLabel() throws Exception {
        mockMvc.perform(post("/vendors/quick").with(csrf())
                        .param("displayName", "Комбо Етказувчи 066"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.label").value("Комбо Етказувчи 066"));
        assertThat(contactService.activeRefsByType(ContactType.VENDOR))
                .anyMatch(ref -> ref.displayName().equals("Комбо Етказувчи 066"));
    }

    @Test
    @WithMockRole(value = UserRole.CHIEF_ACCOUNTANT, username = "chief")
    void accountQuickReturnsIdAndLabel() throws Exception {
        mockMvc.perform(post("/accounts/quick").with(csrf())
                        .param("name", "Комбо Банк 066")
                        .param("detailType", "CHECKING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.label").value("Комбо Банк 066"));
    }

    @Test
    @WithMockRole(value = UserRole.WAREHOUSE_MANAGER, username = "omborchi")
    void warehouseQuickReturnsIdAndLabel() throws Exception {
        mockMvc.perform(post("/warehouses/quick").with(csrf())
                        .param("name", "Комбо Омбор 066")
                        .param("code", "CMB66"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.label").value("Комбо Омбор 066"));
    }

    // ---- 2. BR хато оқими: 422 + {message} ----

    @Test
    void customerQuickBlankNameReturns422WithMessage() throws Exception {
        mockMvc.perform(post("/customers/quick").with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @WithMockRole(value = UserRole.CHIEF_ACCOUNTANT, username = "chief")
    void accountQuickMissingDetailTypeReturns422WithMessage() throws Exception {
        // BR-COA-008 - detail type танланмаган
        mockMvc.perform(post("/accounts/quick").with(csrf())
                        .param("name", "Комбо Хато Счёт 066"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @WithMockRole(value = UserRole.WAREHOUSE_MANAGER, username = "omborchi")
    void warehouseQuickBlankNameReturns422WithMessage() throws Exception {
        // BR-WH-001 - ном киритилиши шарт
        mockMvc.perform(post("/warehouses/quick").with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // ---- 3. VIEWER 403 (умумий POST қоидаси quick'ларни ҳам ёпади) ----

    @Test
    void viewerGetsForbiddenOnQuickEndpoints() throws Exception {
        var viewer = TestRoles.as("viewer", UserRole.VIEWER_AUDITOR);
        mockMvc.perform(post("/customers/quick").with(viewer).with(csrf())
                        .param("displayName", "Рухсатсиз"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/vendors/quick").with(viewer).with(csrf())
                        .param("displayName", "Рухсатсиз"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/accounts/quick").with(viewer).with(csrf())
                        .param("name", "Рухсатсиз").param("detailType", "CHECKING"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/warehouses/quick").with(viewer).with(csrf())
                        .param("name", "Рухсатсиз"))
                .andExpect(status().isForbidden());
    }

    // ---- 4. Fragment render - CSRF hidden input бор ----

    @Test
    void customerQuickFormRendersWithCsrf() throws Exception {
        mockMvc.perform(get("/customers/quick-form"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/customers/quick\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("data-combo-quick")))
                .andExpect(content().string(containsString("name=\"displayName\"")));
    }

    @Test
    void vendorQuickFormRendersWithCsrf() throws Exception {
        mockMvc.perform(get("/vendors/quick-form"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/vendors/quick\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    @WithMockRole(value = UserRole.CHIEF_ACCOUNTANT, username = "chief")
    void accountQuickFormRendersWithCsrfAndGroupedDetailTypes() throws Exception {
        // GL соҳаси: /accounts/** янги ACCOUNTANT'га ёпиқ (Arbitr-092)
        mockMvc.perform(get("/accounts/quick-form"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/accounts/quick\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                // Группаланган select - optgroup'лар билан (spec: детал тури)
                .andExpect(content().string(containsString("name=\"detailType\"")))
                .andExpect(content().string(containsString("<optgroup")));
    }

    @Test
    void warehouseQuickFormRendersWithCsrf() throws Exception {
        mockMvc.perform(get("/warehouses/quick-form"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/warehouses/quick\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("name=\"code\"")));
    }

    // ---- Combobox render маркерлари (Arbitr-123, shared/combobox.jte):
    //      value input эски select name'ини кўтаради (POST шакли ўзгармас),
    //      quick-add банди openAdd('<url>') билан фақат canEdit'га чиқади ----

    @Test
    void invoiceFormRendersCustomerComboboxWithQuickAdd() throws Exception {
        mockMvc.perform(get("/invoices/new"))
                .andExpect(status().isOk())
                // Value input ДОМ'да (data-combobox ўрамида), номи айнан
                .andExpect(content().string(containsString("name=\"customerId\"")))
                .andExpect(content().string(containsString("data-combobox")))
                // Quick-add банди (ACCOUNTANT - SALES EDIT)
                .andExpect(content().string(containsString("openAdd('/customers/quick')")));
    }

    @Test
    void viewerDoesNotSeeQuickAddMarkers() throws Exception {
        // VIEWER'га quick-add банди server томонда render қилинмайди
        mockMvc.perform(get("/invoices/new").with(TestRoles.as("viewer", UserRole.VIEWER_AUDITOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("openAdd("))));
    }

    // ---- Arbitr-161: quick-add валюта ----

    @Test
    void customerQuickWithCurrencyStoresAndReturnsChosen() throws Exception {
        // 161: контакт quick-add валютани қабул қилади ва жавобда ҲАҚИҚИЙ
        // (танланган) валютани қайтаради - олдин доим home эди
        String home = settingsService.homeCurrency();
        String chosen = currencyService.active().stream()
                .map(c -> c.getCode())
                .filter(code -> !code.equals(home))
                .findFirst().orElse(home);
        mockMvc.perform(post("/customers/quick").with(csrf())
                        .param("displayName", "Комбо Валютали Мижоз 161")
                        .param("currency", chosen))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value(chosen));
        assertThat(contactService.activeRefsByType(ContactType.CUSTOMER))
                .anyMatch(ref -> ref.displayName().equals("Комбо Валютали Мижоз 161"));
    }

    @Test
    @WithMockRole(value = UserRole.CHIEF_ACCOUNTANT, username = "chief")
    void accountQuickBankWithCurrencyReturnsCurrency() throws Exception {
        // 161: валютага боғланган турда (банк) quick-add валюта қабул қилади
        String chosen = currencyService.active().get(0).getCode();
        mockMvc.perform(post("/accounts/quick").with(csrf())
                        .param("name", "Комбо Валютали Банк 161")
                        .param("detailType", "CHECKING")
                        .param("currency", chosen))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.currency").value(chosen));
    }

    @Test
    @WithMockRole(value = UserRole.CHIEF_ACCOUNTANT, username = "chief")
    void accountQuickIncomeWithCurrencyReturns422() throws Exception {
        // 161 BR-COA-011: валютага боғланмаган турга (даромад) валюта = рад
        String chosen = currencyService.active().get(0).getCode();
        mockMvc.perform(post("/accounts/quick").with(csrf())
                        .param("name", "Комбо Даромад Валютали 161")
                        .param("detailType", "SALES_OF_PRODUCT_INCOME")
                        .param("currency", chosen))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void customerQuickFormRendersCurrencySelect() throws Exception {
        // 161: контакт quick модалда валюта select бор
        mockMvc.perform(get("/customers/quick-form"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"currency\"")));
    }

    @Test
    @WithMockRole(value = UserRole.CHIEF_ACCOUNTANT, username = "chief")
    void accountQuickFormRendersCurrencySelect() throws Exception {
        // 161: счёт quick модалда валюта select + тур-шартли x-show манбаси бор
        mockMvc.perform(get("/accounts/quick-form"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"currency\"")))
                .andExpect(content().string(containsString("accountCurrencyTypes")));
    }
}
