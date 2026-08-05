package com.averpo.erp.web;

import com.averpo.erp.security.domain.UserRole;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.testsupport.TestRoles;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DEC-127: чиройли error саҳифалар web тестлари.
 * <ol>
 *   <li>404/403 полиш render: катта статус код + иккита амал тугмаси,
 *       аноним ҳам кўра олади (096 хулқи - /error permitAll);</li>
 *   <li>400/405 энди алоҳида сарлавҳа/матн билан: typeMismatch реал
 *       оқими 400 беради, қўллаб-қувватланмаган усул 405 (аввал
 *       catch-all орқали 500 + умумий матн тушарди);</li>
 *   <li>HTMX partial сўровида тўлиқ саҳифа эмас, ихчам alert фрагмент
 *       қайтади (X-Averpo-Error + HX-Reswap жуфти билан) - тўлиқ error
 *       саҳифа фрагмент ичига swap бўлиб хунук чиқмасин;</li>
 *   <li>stack trace/exception тафсилоти саҳифага ҲЕЧ ҚАЧОН чиқмайди.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ErrorPageWebTest {

    @Autowired WebApplicationContext context;

    /** БР HTMX бранчини handler'да тўғридан-тўғри текшириш учун. */
    @Autowired GlobalExceptionHandler handler;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    // ---- 1. 404/403 полиш: катта код + тугмалар, аноним render ----

    @Test
    void errorDispatch404_anonymous_showsCodeTitleAndActions() throws Exception {
        // Аноним (.with(user) ЙЎҚ) - /error permitAll, login'га қайтармайди
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">404</p>")))
                .andExpect(content().string(containsString("Саҳифа топилмади")))
                // 130: тушунтириш матни сарлавҳадан фарқли бўлиши шарт
                .andExpect(content().string(containsString("Сўралган манзил мавжуд эмас ёки кўчирилган")))
                .andExpect(content().string(containsString("Бош саҳифага")))
                .andExpect(content().string(containsString("Орқага")))
                .andExpect(content().string(containsString("history.back()")))
                .andExpect(content().string(not(containsString("Whitelabel"))));
    }

    @Test
    void errorDispatch403_showsForbiddenTitleAndCode() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 403)
                        .with(TestRoles.as("sotuvchi", UserRole.SALES_MANAGER)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">403</p>")))
                .andExpect(content().string(containsString("Рухсат йўқ")))
                .andExpect(content().string(containsString("Бу саҳифага рухсатингиз йўқ")));
    }

    // ---- 2. 400 ва 405 алоҳида сарлавҳа/матн ----

    @Test
    void typeMismatch_realFlow_rendersBadRequestPage() throws Exception {
        // UUID ўрнига «abc» - MethodArgumentTypeMismatchException → 400;
        // хом қиймат экранга қайтмайди (reflected гигиена), параметр номи қолади
        mockMvc.perform(get("/journal-entries/abc")
                        .with(TestRoles.as("admin", UserRole.SUPER_ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString(">400</p>")))
                .andExpect(content().string(containsString("Нотўғри сўров")))
                .andExpect(content().string(containsString("Нотўғри параметр: id")));
    }

    @Test
    void unsupportedMethod_returns405_withOwnText_not500() throws Exception {
        // /journal-entries фақат GET/POST - DELETE 405 бериши шарт
        // (аввал catch-all Exception handler 500 + умумий матн берарди)
        mockMvc.perform(delete("/journal-entries").with(csrf())
                        .with(TestRoles.as("admin", UserRole.SUPER_ADMIN)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().string(containsString(">405</p>")))
                .andExpect(content().string(containsString("Амал қўллаб-қувватланмайди")));
    }

    // ---- 3. HTMX partial сўровида ихчам alert ----

    @Test
    void htmxNotFound_returnsCompactAlert_notFullPage() throws Exception {
        // Пойга ҳолати симуляцияси: drawer/partial hx-get ўчирилган ёзувга
        // тушди - тўлиқ layout эмас, alert фрагмент қайтади
        mockMvc.perform(get("/journal-entries/" + UUID.randomUUID())
                        .header("HX-Request", "true")
                        .with(TestRoles.as("admin", UserRole.SUPER_ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Averpo-Error", "1"))
                .andExpect(header().string("HX-Reswap", "afterbegin"))
                .andExpect(content().string(containsString("data-hx-error")))
                .andExpect(content().string(containsString("role=\"alert\"")))
                .andExpect(content().string(not(containsString("<html"))));
    }

    @Test
    void htmxErrorDispatch_containerLevel_alsoCompactAlert() throws Exception {
        // Фильтр даражасидаги хато (масалан соҳа 403) HTMX сўровда ҳам
        // контейнер /error'га тушади - у ерда ҳам фрагмент қайтади
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 403)
                        .header("HX-Request", "true")
                        .with(TestRoles.as("sotuvchi", UserRole.SALES_MANAGER)))
                .andExpect(header().string("X-Averpo-Error", "1"))
                .andExpect(content().string(containsString("data-hx-error")))
                .andExpect(content().string(not(containsString("<html"))));
    }

    @Test
    void businessRule_htmxRequest_returnsAlertViewWithMarkerHeaders() {
        // Handler бранчи бевосита: БР рад HTMX сўровда alert view'га боради
        // (оддий сўровда shared/error қолиши BusinessRuleWarnLogTest'да)
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/invoices");
        request.setRequestURI("/invoices");
        request.addHeader("HX-Request", "true");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String view = handler.businessRule(
                new BusinessRuleException(BusinessRule.BR_LED_001, "камида 2 сатр"),
                new ExtendedModelMap(), request, response);
        assertThat(view).isEqualTo("shared/errorAlert");
        assertThat(response.getHeader("X-Averpo-Error")).isEqualTo("1");
        assertThat(response.getHeader("HX-Reswap")).isEqualTo("afterbegin");
    }

    // ---- 4. Stack trace саҳифага чиқмайди ----

    @Test
    void errorPage500_neverExposesExceptionDetails() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .with(TestRoles.as("admin", UserRole.SUPER_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">500</p>")))
                .andExpect(content().string(containsString("Ички хатолик")))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("java.lang"))))
                .andExpect(content().string(not(containsString("\tat "))));
    }
}
