package com.averpo.erp.shared.web;

import com.averpo.erp.ledger.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Excel import web қатлами (spec тест 6 - ScreenSmokeTest /settings/import
 * GET) + шаблон юклаб олиш + роль гарови (ADMIN, /settings/** нақши).
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ImportWebTest {

    @Autowired WebApplicationContext context;

    /** POST'да шаблонни apply қилиш item default счётларини талаб қилади. */
    @Autowired AccountService accountService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /** Тест 6: GET /settings/import 200 + саҳифа маркери. */
    @Test
    @WithMockRole(username = "admin")
    void importScreen_rendersForAdmin() throws Exception {
        mockMvc.perform(get("/settings/import"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Excel'дан юклаш")));
    }

    /** Шаблон юклаб олинади - attachment ва .xlsx MIME. */
    @Test
    @WithMockRole(username = "admin")
    void template_downloadsAsXlsx() throws Exception {
        mockMvc.perform(get("/settings/import/template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("import-template.xlsx")))
                .andExpect(header().string("Content-Type", containsString("spreadsheetml")));
    }

    /** /settings/** ADMIN'га - ACCOUNTANT'га 403. */
    @Test
    @WithMockRole(value = UserRole.ACCOUNTANT, username = "acc")
    void accountantForbidden() throws Exception {
        mockMvc.perform(get("/settings/import"))
                .andExpect(status().isForbidden());
    }

    /** Тоза файл юкланса apply бўлади ва PRG билан redirect қилинади. */
    @Test
    @WithMockRole(username = "admin")
    void upload_cleanTemplate_redirects() throws Exception {
        accountService.importDefaultChart();
        byte[] bytes = new ClassPathResource("static/import-template.xlsx").getInputStream().readAllBytes();
        MockMultipartFile file = new MockMultipartFile("file", "import-template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        mockMvc.perform(multipart("/settings/import").file(file).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings/import"));
    }
}
