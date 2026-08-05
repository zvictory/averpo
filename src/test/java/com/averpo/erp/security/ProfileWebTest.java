package com.averpo.erp.security;

import com.averpo.erp.security.domain.UserRole;
import com.averpo.erp.security.service.UserService;
import com.averpo.erp.testsupport.TestRoles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Профиль web оқими интеграцион тести (DEC-101, user-profile.md
 * «Тестлар»): ҳар роль (жумладан VIEWER_AUDITOR) ЎЗ профилини очади ва
 * сақлайди, эски /profile/password redirect, аватар inline+nosniff.
 *
 * <p>КРИТИК ТУЗОҚ (092): /profile POST'лар UrlPermissionMap'га
 * кирмайди - SecurityConfig'да АНИҚ authenticated. {@code
 * profilePost_viewerAuditorAllowed} шу тузоқни бевосита текширади:
 * тузатиш йўқолса VIEWER_AUDITOR'нинг POST'и 403 бўлиб қизаради.
 *
 * <p>Реал app_user яратилади (TestRoles фақат auth token беради, user'ни
 * DB'га ёзмайди) - {@code current()}/аватар target шу user'ни талаб қилади.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProfileWebTest {

    @Autowired WebApplicationContext context;
    @Autowired UserService userService;

    private MockMvc mockMvc;

    /** Профиль эгаси - VIEWER_AUDITOR (энг кам ҳуқуқли: тузоқ шунда кўринади). */
    private static final String USER = "profiltest";

    @BeforeEach
    void setUp() {
        // Auth контекстисиз (BeforeEach) create - тизим оқими, BR-USR-011 ўтади
        userService.create(USER, "Профиль Тест", UserRole.VIEWER_AUDITOR, "kuchli-parol-123");
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /** Шу user сифатида сессия (username = DB'даги user - current() топади). */
    private UserRequestPostProcessor viewer() {
        return TestRoles.as(USER, UserRole.VIEWER_AUDITOR);
    }

    @Test
    void profileRender_viewerAuditorOpensOwn() throws Exception {
        mockMvc.perform(get("/profile").with(viewer()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Профиль")));
    }

    @Test
    void passwordGet_redirectsToProfile() throws Exception {
        mockMvc.perform(get("/profile/password").with(viewer()))
                .andExpect(redirectedUrl("/profile"));
    }

    @Test
    void profilePost_viewerAuditorAllowed_092Trap() throws Exception {
        // 092 ТУЗОҚ: тузатиш йўқолса бу POST соҳа EDIT талабига тушиб 403
        // бўлиб қизаради. Redirect /profile = security қатлами ўтказди
        // (round-trip сақлаш UserServiceTest'да алоҳида текширилган).
        // displayName required (BR-USR-004) - реал форма кўриниши.
        mockMvc.perform(post("/profile").with(viewer()).with(csrf())
                        .param("displayName", "Профиль Тест")
                        .param("email", "viewer@example.com"))
                .andExpect(redirectedUrl("/profile"));
    }

    /**
     * DEC-148: фойдаланувчи ЎЗ профилида кўрсатиладиган номини «Маълумотлар»
     * формасидан ўзгартиради - POST 302 /profile, кейин янги ном саҳифада
     * (аватар виджет ва форма value'сида) render бўлади.
     */
    @Test
    void displayNameChange_updatesAndRendersNewName() throws Exception {
        mockMvc.perform(post("/profile").with(viewer()).with(csrf())
                        .param("displayName", "Ўзгартирилган Ном"))
                .andExpect(redirectedUrl("/profile"));

        mockMvc.perform(get("/profile").with(viewer()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ўзгартирилган Ном")));
    }

    /**
     * DEC-148: бўш ном (BR-USR-004) рад - профил формаси required, лекин
     * tampered бўш POST сервер гаровида ушланиб, flash error билан /profile'га
     * қайтади (ном ўзгармай қолади - ярим-ёзилиш йўқ).
     */
    @Test
    void displayNameChange_blank_rejectedWithError() throws Exception {
        mockMvc.perform(post("/profile").with(viewer()).with(csrf())
                        .param("displayName", "   ")
                        .param("email", "viewer@example.com"))
                .andExpect(redirectedUrl("/profile"));
        // Ном ўзгармади (BR-USR-004 rollback), эски «Профиль Тест» қолди
        mockMvc.perform(get("/profile").with(viewer()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Профиль Тест")));
    }

    /**
     * DEC-096 CSRF UX (профил POST'и): CSRF токенисиз ёзувчи сўров
     * эскирган сессия деб қаралиб, хом 403 Whitelabel эмас, {@code
     * /login?expired} га redirect бўлади (SessionCsrfUxWebTest семантикаси).
     */
    @Test
    void profilePost_withoutCsrf_redirectsToExpired() throws Exception {
        mockMvc.perform(post("/profile").with(viewer())
                        .param("displayName", "CSRF йўқ"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?expired"));
    }

    @Test
    void avatarUploadViewDelete_inlineNosniff() throws Exception {
        // Юклаш (multipart, реал png - рефайнмент ImageIO ўлчов текшируви)
        mockMvc.perform(multipart("/profile/image")
                        .file(new MockMultipartFile("file", "a.png", "image/png", pngBytes(64, 64)))
                        .with(viewer()).with(csrf()))
                .andExpect(redirectedUrl("/profile"));

        // Inline + nosniff кўриш (094 нақши)
        mockMvc.perform(get("/profile/image").with(viewer()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", containsString("inline")));

        // Ўчириш → placeholder (view 404)
        mockMvc.perform(post("/profile/image/delete").with(viewer()).with(csrf()))
                .andExpect(redirectedUrl("/profile"));
        mockMvc.perform(get("/profile/image").with(viewer()))
                .andExpect(status().isNotFound());
    }

    /** Реал png bytes (аватар ImageIO ўлчов текшируви ўтиши учун). */
    private byte[] pngBytes(int w, int h) throws Exception {
        var img = new java.awt.image.BufferedImage(w, h,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        var baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }
}
