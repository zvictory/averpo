package com.averpo.erp.plugins.telegram;

import com.averpo.erp.security.domain.UserRole;
import com.averpo.erp.plugins.telegram.service.TelegramBotClient;
import com.averpo.erp.plugins.telegram.service.TelegramService;
import com.averpo.erp.plugins.core.domain.PluginKey;
import com.averpo.erp.plugins.core.service.PluginService;
import com.averpo.erp.testsupport.WithMockRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Telegram web қатлами (DEC-103): плагин гейти (ўчиқда route ЙЎҚ -
 * 404), SUPER_ADMIN созлама саҳифаси / паст роль 403, профил улаш
 * амаллари ҳар роль учун (VIEWER_AUDITOR ҳам - ЎЗ профили, 092 тузоғи),
 * экранда токен маскаси.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockRole(username = "tgweb")
class TelegramWebTest {

    /** Сохта, лекин реал ФОРМАТдаги токен. */
    private static final String TOKEN = "123456789:AAFakeTokenForTestOnly-not-real";

    /** Токеннинг сир қисми - HTML'да изланадиган намуна. */
    private static final String SECRET_PART = "AAFakeTokenForTestOnly-not-real";

    @Autowired WebApplicationContext context;
    @Autowired PluginService pluginService;
    @Autowired TelegramService telegramService;
    @Autowired JdbcClient jdbcClient;

    /** Bot API - мок (тармоқ йўқ). */
    @MockitoBean TelegramBotClient client;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        jdbcClient.sql("INSERT INTO app_user (id, username, password_hash, display_name, role) "
                        + "VALUES (?, 'tgweb', 'x', 'Телеграм веб', 'SUPER_ADMIN')")
                .param(UUID.randomUUID()).update();
        pluginService.setEnabled(PluginKey.TELEGRAM, true);
        when(client.getMe(anyString())).thenReturn(
                Optional.of(new TelegramBotClient.BotInfo(123456789L, "averpo_test_bot")));
    }

    // ---- Плагин гейти: ўчиқда route УМУМАН йўқ (404) ----

    /**
     * Плагин ўчиқ: созлама саҳифаси 404 - UI яшириш кифоя эмас (092
     * сабоғи), backend ҳам гейтли (plugins.md талаби).
     */
    @Test
    void settingsPage_pluginDisabled_notFound() throws Exception {
        pluginService.setEnabled(PluginKey.TELEGRAM, false);

        mockMvc.perform(get("/settings/telegram"))
                .andExpect(status().isNotFound());
    }

    /** Плагин ўчиқ: токен POST'и ҳам 404 - ҳолат ўзгармайди. */
    @Test
    void saveToken_pluginDisabled_notFound_stateUntouched() throws Exception {
        pluginService.setEnabled(PluginKey.TELEGRAM, false);

        mockMvc.perform(post("/settings/telegram").with(csrf()).param("token", TOKEN))
                .andExpect(status().isNotFound());

        pluginService.setEnabled(PluginKey.TELEGRAM, true);
        assertThat(telegramService.configured()).isFalse();
    }

    /** Плагин ўчиқ: профилдан улаш ҳам 404 (фича уланмаган). */
    @Test
    void profileLink_pluginDisabled_notFound() throws Exception {
        pluginService.setEnabled(PluginKey.TELEGRAM, false);

        mockMvc.perform(post("/profile/telegram/link").with(csrf()))
                .andExpect(status().isNotFound());
    }

    /** Плагин ўчиқ: профил саҳифасида Telegram блоки УМУМАН чиқмайди. */
    @Test
    void profilePage_pluginDisabled_blockHidden() throws Exception {
        pluginService.setEnabled(PluginKey.TELEGRAM, false);

        mockMvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/profile/telegram/link"))));
    }

    // ---- Роллар ----

    /** SUPER_ADMIN созлама саҳифасини кўради. */
    @Test
    void settingsPage_superAdmin_ok() throws Exception {
        mockMvc.perform(get("/settings/telegram"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/settings/telegram")));
    }

    /** Паст роль (SETTINGS йўқ): созлама саҳифаси 403. */
    @Test
    @WithMockRole(value = UserRole.ACCOUNTANT, username = "tgacc")
    void settingsPage_lowerRole_forbidden() throws Exception {
        mockMvc.perform(get("/settings/telegram"))
                .andExpect(status().isForbidden());
    }

    /** Паст роль токен сақлай олмайди (POST /settings/** = SETTINGS_EDIT). */
    @Test
    @WithMockRole(value = UserRole.ACCOUNTANT, username = "tgacc")
    void saveToken_lowerRole_forbidden() throws Exception {
        mockMvc.perform(post("/settings/telegram").with(csrf()).param("token", TOKEN))
                .andExpect(status().isForbidden());
    }

    /**
     * VIEWER_AUDITOR ҳам ЎЗ профилидан Telegram улай олади (092 ТУЗОҒИ:
     * /profile/telegram/* UrlPermissionMap'га кирмайди - SecurityConfig'да
     * аниқ authenticated бўлмаса POST-catchall уни соҳа EDIT талабига
     * ташлаб 403 берарди).
     */
    @Test
    @WithMockRole(value = UserRole.VIEWER_AUDITOR, username = "tgviewer")
    void profileLink_viewerAuditor_allowed() throws Exception {
        jdbcClient.sql("INSERT INTO app_user (id, username, password_hash, display_name, role) "
                        + "VALUES (?, 'tgviewer', 'x', 'Кузатувчи', 'VIEWER_AUDITOR')")
                .param(UUID.randomUUID()).update();
        telegramService.saveToken(TOKEN);

        mockMvc.perform(post("/profile/telegram/link").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    /** VIEWER_AUDITOR узиш амали ҳам ўтади (ўз профили). */
    @Test
    @WithMockRole(value = UserRole.VIEWER_AUDITOR, username = "tgviewer2")
    void profileUnlink_viewerAuditor_allowed() throws Exception {
        jdbcClient.sql("INSERT INTO app_user (id, username, password_hash, display_name, role) "
                        + "VALUES (?, 'tgviewer2', 'x', 'Кузатувчи 2', 'VIEWER_AUDITOR')")
                .param(UUID.randomUUID()).update();

        mockMvc.perform(post("/profile/telegram/unlink").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    // ---- Экран мазмуни ----

    /** Токен сақлангач саҳифада ФАҚАТ маска - тўлиқ токен HTML'га чиқмайди. */
    @Test
    void settingsPage_showsMaskedToken_neverFullToken() throws Exception {
        telegramService.saveToken(TOKEN);

        mockMvc.perform(get("/settings/telegram"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("123456789:AA")))
                .andExpect(content().string(containsString("averpo_test_bot")))
                .andExpect(content().string(not(containsString(SECRET_PART))));
    }

    /** Бот созланмаган: профил блоки graceful огоҳлантириш кўрсатади. */
    @Test
    void profilePage_botNotConfigured_showsWarning() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().isOk())
                // Блок бор (плагин ёқиқ), лекин улаш тугмаси ЙЎҚ
                .andExpect(content().string(containsString("Бот созланмаган")))
                .andExpect(content().string(not(containsString("/profile/telegram/link"))));
    }

    /** Бот созланган: профилда улаш тугмаси чиқади. */
    @Test
    void profilePage_botConfigured_showsLinkButton() throws Exception {
        telegramService.saveToken(TOKEN);

        mockMvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/profile/telegram/link")));
    }

    /** Нотўғри токен: ўша саҳифада кириллча BR хабари (хом 400 эмас). */
    @Test
    void saveToken_invalid_showsBrMessage_notRaw400() throws Exception {
        when(client.getMe(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/settings/telegram").with(csrf()).param("token", "yomon"))
                .andExpect(status().is3xxRedirection());

        // Flash хабар кейинги GET'да кўринади (redirect оқими)
        assertThat(telegramService.configured()).isFalse();
    }
}
