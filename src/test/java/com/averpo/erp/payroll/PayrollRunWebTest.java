package com.averpo.erp.payroll;

import com.averpo.erp.ledger.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.averpo.erp.security.domain.UserRole;
import com.averpo.erp.testsupport.WithMockRole;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PayrollRun web тестлари (payroll.md «Тестлар» 8-банд): VIEWER ҳужжат
 * яратолмайди (SecurityConfig POST чекловидан 403), лекин кўради.
 * Экранлар render текшируви ҳам шу ерда - ScreenSmokeTest'га
 * тегилмайди (параллел агентлар ҳам ўша файлга ёзади).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PayrollRunWebTest {

    @Autowired WebApplicationContext context;
    @Autowired AccountService accountService;

    /** Security filter chain уланган MockMvc (ScreenSmokeTest қолипи). */
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /** Spec 8-банд (кўриш): VIEWER рўйхатни очади - 200. */
    @Test
    @WithMockRole(value = UserRole.VIEWER_AUDITOR, username = "viewer")
    void viewer_canSeePayrollList() throws Exception {
        mockMvc.perform(get("/payroll"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Иш ҳақи ҳисоблашлари")));
    }

    /** Spec 8-банд (яратиш): VIEWER POST'и 403 (SecurityConfig POST /** чеклови). */
    @Test
    @WithMockRole(value = UserRole.VIEWER_AUDITOR, username = "viewer")
    void viewer_cannotCreateRun() throws Exception {
        mockMvc.perform(post("/payroll").with(csrf())
                        .param("action", "draft")
                        .param("period", "2026-07")
                        .param("runDate", "2026-07-31"))
                .andExpect(status().isForbidden());
    }

    /**
     * Форма PAYROLL EDIT эгасига очилади: сарлавҳа + prefill тугмаси render.
     * Arbitr-092: payroll энди CHIEF_ACCOUNTANT иши - янги ACCOUNTANT'да
     * PAYROLL=NONE (матрица), эски тест роли онгли алмаштирилди.
     */
    @Test
    @WithMockRole(value = UserRole.CHIEF_ACCOUNTANT, username = "acc")
    void form_rendersForAccountant() throws Exception {
        mockMvc.perform(get("/payroll/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Иш ҳақи ҳисоблаш")))
                .andExpect(content().string(containsString("Ходимларни тўлдириш")));
    }
}
