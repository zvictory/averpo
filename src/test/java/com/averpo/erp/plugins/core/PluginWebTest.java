package com.averpo.erp.plugins.core;

import com.averpo.erp.plugins.core.domain.PluginKey;
import com.averpo.erp.plugins.core.service.PluginService;
import com.averpo.erp.security.domain.UserRole;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /settings/plugins web қатлами (docs/modules/plugins.md «BR/хавфсизлик»):
 * саҳифа ва toggle POST фақат SUPER_ADMIN (route /settings/** SETTINGS
 * соҳасида - матрицада фақат унда), паст роль 403 - UI яшириш эмас,
 * server ҳақиқати (092 нақши). Toggle оқими: POST → redirect → ҳолат
 * ўзгарган. Гейт/аудит мантиғи {@link PluginServiceTest}да.
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockRole(username = "plgweb")
class PluginWebTest {

    @Autowired WebApplicationContext context;
    @Autowired PluginService pluginService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /** SUPER_ADMIN рўйхатни кўради - TELEGRAM қатори toggle формаси билан. */
    @Test
    void list_superAdmin_rendersRegistryWithToggleForm() throws Exception {
        mockMvc.perform(get("/settings/plugins"))
                .andExpect(status().isOk())
                // i18n'га боғланмаган барқарор далил: toggle формасининг action'и
                .andExpect(content().string(containsString("/settings/plugins/TELEGRAM/toggle")));
    }

    /** Паст роль (SETTINGS йўқ) саҳифага кира олмайди - 403. */
    @Test
    @WithMockRole(value = UserRole.ACCOUNTANT, username = "plgacc")
    void list_lowerRole_forbidden() throws Exception {
        mockMvc.perform(get("/settings/plugins"))
                .andExpect(status().isForbidden());
    }

    /** Toggle POST: ёқиш → redirect, гейт янги ҳолатни кўради; ўчириш ҳам шу йўл. */
    @Test
    void toggle_superAdmin_flipsState() throws Exception {
        mockMvc.perform(post("/settings/plugins/TELEGRAM/toggle").with(csrf())
                        .param("enabled", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings/plugins"));
        assertThat(pluginService.isEnabled(PluginKey.TELEGRAM)).isTrue();

        mockMvc.perform(post("/settings/plugins/TELEGRAM/toggle").with(csrf())
                        .param("enabled", "false"))
                .andExpect(status().is3xxRedirection());
        assertThat(pluginService.isEnabled(PluginKey.TELEGRAM)).isFalse();
    }

    /** Паст роль toggle қила олмайди (POST /settings/** = SETTINGS_EDIT) - ҳолат ўзгармайди. */
    @Test
    @WithMockRole(value = UserRole.ACCOUNTANT, username = "plgacc2")
    void toggle_lowerRole_forbidden_stateUntouched() throws Exception {
        mockMvc.perform(post("/settings/plugins/TELEGRAM/toggle").with(csrf())
                        .param("enabled", "true"))
                .andExpect(status().isForbidden());
        assertThat(pluginService.isEnabled(PluginKey.TELEGRAM)).isFalse();
    }
}
