package com.averpo.erp.security;

import com.averpo.erp.security.domain.UserRole;
import com.averpo.erp.testsupport.TestRoles;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

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
 * Сессия муддати + CSRF 403 UX (Arbitr-096) web тестлари:
 * <ol>
 *   <li>КИРГАН фойдаланувчи CSRF'сиз POST (эскирган токен симуляцияси) -
 *       хом 403 Whitelabel эмас, {@code /login?expired} га redirect;</li>
 *   <li>login саҳифаси {@code ?expired}да хабар кўрсатади;</li>
 *   <li>соҳа-даража рад (092, CSRF бор) - ЭСКИЧА 403 (expired ЭМАС);</li>
 *   <li>{@code /error} диспетчери Whitelabel эмас, error.jte render
 *       (404/403 статусга мос сарлавҳа).</li>
 * </ol>
 *
 * <p><b>CSRF йўли:</b> {@code CsrfFilter} ЎЗ accessDeniedHandler'ини
 * ишлатади (ExceptionTranslationFilter'га етказмайди), лекин
 * {@code CsrfConfigurer} exceptionHandling'даги handler'ни унга улайди -
 * шунга CSRF ради тўғридан-тўғри {@link
 * com.averpo.erp.security.config.CsrfAwareAccessDeniedHandler}'га
 * тушади. Тест кирган фойдаланувчи билан - карта кузатган реал 403
 * Whitelabel айнан шунда (CSRF токени login ротациясидан кейин эскирган).
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SessionCsrfUxWebTest {

    @Autowired WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    // ---- 1. КИРГАН фойдаланувчи CSRF'сиз POST → /login?expired ----

    @Test
    void authenticatedPostWithoutCsrf_redirectsToExpired_notRawForbidden() throws Exception {
        // .with(csrf()) АТАЙЛАБ йўқ - CsrfFilter Invalid/MissingCsrfToken
        // отади (амалда: токен login ротациясидан ёки сессия муддатидан
        // кейин эскирган). CsrfConfigurer exceptionHandling'даги
        // CsrfAwareAccessDeniedHandler'ни CsrfFilter'га улайди → CSRF ради
        // /login?expired га боради (хом 403 Whitelabel эмас). Кирган
        // фойдаланувчи - карта кузатган реал 403 сценарийси.
        mockMvc.perform(post("/invoices")
                        .with(TestRoles.as("sotuvchi", UserRole.SALES_MANAGER)))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?expired"));
    }

    @Test
    void authenticatedPostWithoutCsrf_otherEndpoint_alsoExpired() throws Exception {
        // Бошқа соҳа эгаси, бошқа endpoint - қоида умумий (CSRF'сиз ҳар
        // ёзувчи сўров эскирган сессия деб қаралади)
        mockMvc.perform(post("/settings/closing-date")
                        .param("closingDate", "2026-06-30")
                        .with(TestRoles.as("chief", UserRole.CHIEF_ACCOUNTANT)))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?expired"));
    }

    // ---- 2. login?expired хабари ----

    @Test
    void loginPage_showsExpiredMessage() throws Exception {
        mockMvc.perform(get("/login").param("expired", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Сессия муддати тугади")));
    }

    // ---- 3. Соҳа-даража рад (092) CSRF бор - эскича 403, expired ЭМАС ----

    @Test
    void areaDenied_withCsrf_stays403_notExpired() throws Exception {
        // SALES_MANAGER /bills'га POST (PURCHASE соҳаси йўқ), CSRF БОР -
        // бу CsrfException ЭМАС, оддий AccessDenied → default 403 (092
        // семантикаси ўзгармаган)
        mockMvc.perform(post("/bills").with(csrf())
                        .with(TestRoles.as("sotuvchi", UserRole.SALES_MANAGER)))
                .andExpect(status().isForbidden());
    }

    // ---- 4. ErrorController: Whitelabel эмас, error.jte ----

    @Test
    void errorDispatch_404_rendersErrorPage_notWhitelabel() throws Exception {
        // Контейнер /error диспетчери симуляцияси: статус атрибути қўйилади
        // (реалда sendError/фильтр хатоси қўяди). ErrorController уни
        // error.jte'га айлантиради - Whitelabel «This application has no
        // explicit mapping» ҲЕЧ ҚАЧОН чиқмайди.
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .with(TestRoles.as("admin", UserRole.SUPER_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Саҳифа топилмади")))
                .andExpect(content().string(not(containsString("Whitelabel"))))
                .andExpect(content().string(not(containsString("no explicit mapping"))));
    }

    @Test
    void errorDispatch_403_rendersForbiddenTitle() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 403)
                        .with(TestRoles.as("admin", UserRole.SUPER_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Рухсат йўқ")))
                .andExpect(content().string(not(containsString("Whitelabel"))));
    }
}
