package com.averpo.erp.shared;

import com.averpo.erp.security.domain.UserRole;
import com.averpo.erp.shared.service.CompanySettingsService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Компания маълумотлари web оқими (Arbitr-112): SUPER_ADMIN очади, паст
 * роль 403 (/settings/company SETTINGS соҳаси), реквизит round-trip,
 * лого upload → inline+nosniff view → delete. {@code /settings/company*}
 * SecurityConfig'да алоҳида қатор ТАЛАБ ҚИЛМАЙДИ (SETTINGS остида,
 * автоматик - 101 профилдан фарқли).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CompanyInfoWebTest {

    @Autowired WebApplicationContext context;
    @Autowired CompanySettingsService settingsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private UserRequestPostProcessor admin() {
        return TestRoles.as("bosh", UserRole.SUPER_ADMIN);
    }

    @Test
    void superAdminOpens_lowRoleForbidden() throws Exception {
        mockMvc.perform(get("/settings/company").with(admin()))
                .andExpect(status().isOk());
        // VIEWER_AUDITOR'да SETTINGS соҳаси йўқ - 403 (SecurityConfig автоматик)
        mockMvc.perform(get("/settings/company")
                        .with(TestRoles.as("auditor", UserRole.VIEWER_AUDITOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void requisites_roundTrip() throws Exception {
        mockMvc.perform(post("/settings/company").with(admin()).with(csrf())
                        .param("name", "Смоук Компания")
                        .param("legalName", "МЧЖ Смоук")
                        .param("taxId", "301234567")
                        .param("bankName", "Смоук Банк")
                        .param("directorName", "Каримов А.А.")
                        .param("email", "info@smoke.uz"))
                .andExpect(redirectedUrl("/settings/company"));

        assertThat(settingsService.get().getName()).isEqualTo("Смоук Компания");
        assertThat(settingsService.get().getLegalName()).isEqualTo("МЧЖ Смоук");
        assertThat(settingsService.get().getTaxId()).isEqualTo("301234567");
        assertThat(settingsService.get().getDirectorName()).isEqualTo("Каримов А.А.");
    }

    @Test
    void logoUploadViewDelete_inlineNosniff() throws Exception {
        // Юклаш (реал png - рефайнмент ImageIO ўлчов текшируви)
        mockMvc.perform(multipart("/settings/company/logo")
                        .file(new MockMultipartFile("file", "logo.png", "image/png", pngBytes(64, 64)))
                        .with(admin()).with(csrf()))
                .andExpect(redirectedUrl("/settings/company"));
        assertThat(settingsService.get().getLogoAttachmentId()).isNotNull();

        // Inline + nosniff кўриш (094 нақши)
        mockMvc.perform(get("/settings/company/logo").with(admin()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", containsString("inline")));

        // Ўчириш → placeholder (view 404)
        mockMvc.perform(post("/settings/company/logo/delete").with(admin()).with(csrf()))
                .andExpect(redirectedUrl("/settings/company"));
        assertThat(settingsService.get().getLogoAttachmentId()).isNull();
        mockMvc.perform(get("/settings/company/logo").with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void logoSvg_rejected_noLogoSet() throws Exception {
        // SVG рад (BR-ATT-005) - редирект хато flash билан, лого ўрнатилмайди
        mockMvc.perform(multipart("/settings/company/logo")
                        .file(new MockMultipartFile("file", "logo.svg", "image/svg+xml", "<svg/>".getBytes()))
                        .with(admin()).with(csrf()))
                .andExpect(redirectedUrl("/settings/company"));
        assertThat(settingsService.get().getLogoAttachmentId()).isNull();
    }

    /**
     * Рефайнмент банд 112.4: бренд логоси (топбар WHITE-LABEL) upload
     * (SUPER_ADMIN) → view СОҲАСИЗ /company/brand-logo (admin ҳам, VIEWER
     * ҳам - топбарда ҳар роль кўради) inline+nosniff → delete.
     */
    @Test
    void brandLogoUploadViewDelete_publicView() throws Exception {
        mockMvc.perform(multipart("/settings/company/brand-logo")
                        .file(new MockMultipartFile("file", "brand.png", "image/png", pngBytes(80, 30)))
                        .with(admin()).with(csrf()))
                .andExpect(redirectedUrl("/settings/company"));
        assertThat(settingsService.get().getBrandLogoAttachmentId()).isNotNull();

        // View соҳасиз - admin ҳам, VIEWER_AUDITOR ҳам топбар брендини кўради
        mockMvc.perform(get("/company/brand-logo").with(admin()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", containsString("inline")));
        mockMvc.perform(get("/company/brand-logo")
                        .with(TestRoles.as("auditor", UserRole.VIEWER_AUDITOR)))
                .andExpect(status().isOk());

        // Ўчириш → топбар fallback (view 404)
        mockMvc.perform(post("/settings/company/brand-logo/delete").with(admin()).with(csrf()))
                .andExpect(redirectedUrl("/settings/company"));
        assertThat(settingsService.get().getBrandLogoAttachmentId()).isNull();
        mockMvc.perform(get("/company/brand-logo").with(admin()))
                .andExpect(status().isNotFound());
    }

    /** Реал png bytes (лого ImageIO ўлчов текшируви ўтиши учун). */
    private byte[] pngBytes(int w, int h) throws Exception {
        var img = new java.awt.image.BufferedImage(w, h,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        var baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }
}
