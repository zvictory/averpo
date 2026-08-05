package com.averpo.erp.plugins.telegram;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.averpo.erp.plugins.core.domain.PluginKey;
import com.averpo.erp.plugins.core.service.PluginService;
import com.averpo.erp.plugins.telegram.service.TelegramBotClient;
import com.averpo.erp.plugins.telegram.service.TelegramService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Telegram webhook endpoint web қатлами (DEC-138): POST
 * /telegram/webhook хавфсизлик ва оқими.
 *
 * <p>Текширилади: (1) permitAll + CSRF ўчиқ - аутентификациясиз ва
 * CSRF токенсиз POST ўтади; (2) секрет ОЛДИН - нотўғри/йўқ header → 401
 * (плагин ҳолатидан қатъи назар, handleUpdate таъсирсиз); (3) тўғри
 * секрет → 200 + ишлов; (4) бузуқ тана → 200 (эътиборсиз); (5) плагин
 * ўчиқ + тўғри секрет → 200 жим (404 ЭМАС - гейт ҳолати ошкор
 * бўлмасин); (6) секрет/тана логга чиқмайди.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TelegramWebhookWebTest {

    private static final String TOKEN = "123456789:AAFakeTokenForTestOnly-not-real";

    /** Webhook сир қиймати - тест ичида registrar оқимидан ушланади. */
    private String secret;

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
                        + "VALUES (?, 'whadmin', 'x', 'Webhook админ', 'SUPER_ADMIN')")
                .param(UUID.randomUUID()).update();
        pluginService.setEnabled(PluginKey.TELEGRAM, true);
        when(client.getMe(anyString())).thenReturn(
                Optional.of(new TelegramBotClient.BotInfo(123456789L, "averpo_test_bot")));
        // Токен + webhook сирини ўрнатиш: saveToken (@WithMockRole йўқ - секрет
        // яратиш учун registerWebhookIfReady чақирамиз, сирни мок'дан ушлаймиз)
    }

    /** Токен сақлаб webhook сирини яратади ва уни қайтаради. */
    private String configureWebhookSecret() {
        // saveToken актор талаб қилади (аудит) - SecurityContext'сиз JdbcClient
        // билан токенни тўғридан сақлаймиз эмас; saveToken мок getMe билан ишлайди,
        // лекин актор йўқ. Шунга @WithMockRole ўрнига secret'ни registrar оқими
        // орқали: saveToken'ни username контекстисиз чақириб бўлмайди (аудит null
        // актор - ОК). Токенни ўрнатамиз:
        withActor(() -> telegramService.saveToken(TOKEN));
        String[] captured = new String[1];
        when(client.registerWebhook(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> { captured[0] = inv.getArgument(2); return true; });
        ReflectionTestUtils.setField(telegramService, "publicUrl", "https://app.averpo.com");
        telegramService.registerWebhookIfReady();
        return captured[0];
    }

    /** Актор контекстида бажаради (saveToken аудити username кутади). */
    private void withActor(Runnable action) {
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "whadmin", "x", java.util.List.of());
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(auth);
        try {
            action.run();
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    /** Логгерга уланган йиғувчи (секрет/токен сизишини тутиш). */
    private ListAppender<ILoggingEvent> attachAppender(Class<?> type) {
        Logger logger = (Logger) LoggerFactory.getLogger(type);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    // ---- 401: секрет ОЛДИН (гейтдан қатъи назар) ----

    /** Header йўқ: 401 (аутентификациясиз permitAll ўтди, лекин секрет шарт). */
    @Test
    void noSecretHeader_unauthorized() throws Exception {
        configureWebhookSecret();

        mockMvc.perform(post("/telegram/webhook")
                        .contentType("application/json").content("{\"update_id\":1}"))
                .andExpect(status().isUnauthorized());
        // handleUpdate'га етиб бормади
        verify(client, never()).sendMessage(anyString(), anyLong(), anyString());
    }

    /** Нотўғри header: 401, handleUpdate таъсирсиз. */
    @Test
    void wrongSecret_unauthorized() throws Exception {
        configureWebhookSecret();

        mockMvc.perform(post("/telegram/webhook")
                        .header("X-Telegram-Bot-Api-Secret-Token", "notogri-sekret")
                        .contentType("application/json").content("{\"update_id\":1}"))
                .andExpect(status().isUnauthorized());
        verify(client, never()).parseWebhookUpdate(anyString());
    }

    /**
     * Плагин ЎЧИҚ + нотўғри секрет: барибир 401 (плагин on/off
     * аутентификациясиз ошкор бўлмасин - секрет ОЛДИН).
     */
    @Test
    void wrongSecret_pluginDisabled_still401() throws Exception {
        configureWebhookSecret();
        pluginService.setEnabled(PluginKey.TELEGRAM, false);

        mockMvc.perform(post("/telegram/webhook")
                        .header("X-Telegram-Bot-Api-Secret-Token", "notogri")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- 200: тўғри секрет ----

    /**
     * Тўғри секрет: 200 + ишлов (permitAll + CSRF ўчиқ исботи -
     * аутентификациясиз ва csrf()сиз ўтди). parseWebhookUpdate → linked.
     */
    @Test
    void validSecret_processesUpdate_200() throws Exception {
        String s = configureWebhookSecret();
        // Улаш коди тайёрлаш учун актор контекстида startLink
        String[] code = new String[1];
        withActor(() -> code[0] = telegramService.startLink().code());
        String body = "{\"update_id\":9}";
        when(client.parseWebhookUpdate(body)).thenReturn(Optional.of(
                new TelegramBotClient.Update(9, 42, "aliyev", "/start " + code[0])));

        mockMvc.perform(post("/telegram/webhook")
                        .header("X-Telegram-Bot-Api-Secret-Token", s)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());

        verify(client).sendMessage(eq(TOKEN), eq(42L), anyString());
    }

    /** Тўғри секрет + бузуқ тана: 200 (эътиборсиз - Telegram қайта юбормасин). */
    @Test
    void validSecret_unparseableBody_200() throws Exception {
        String s = configureWebhookSecret();
        when(client.parseWebhookUpdate(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/telegram/webhook")
                        .header("X-Telegram-Bot-Api-Secret-Token", s)
                        .contentType("application/json").content("garbage"))
                .andExpect(status().isOk());
        verify(client, never()).sendMessage(anyString(), anyLong(), anyString());
    }

    /**
     * Тўғри секрет + плагин ЎЧИҚ: 200 ЖИМ (404 ЭМАС - гейт ҳолати
     * ошкор бўлмасин). handleWebhookBody гейтни enabled() орқали эмас,
     * токен орқали текширади - лекин бу оқимда плагин ўчиқ бўлса ҳам
     * секрет тўғри, шунга 200.
     */
    @Test
    void validSecret_pluginDisabled_silent200() throws Exception {
        String s = configureWebhookSecret();
        pluginService.setEnabled(PluginKey.TELEGRAM, false);
        when(client.parseWebhookUpdate(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/telegram/webhook")
                        .header("X-Telegram-Bot-Api-Secret-Token", s)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk());
    }

    // ---- Сир сизиши (ListAppender) ----

    /** Нотўғри секрет POST'ида секрет/тана ҳеч бир лог қаторига тушмайди. */
    @Test
    void wrongSecret_secretAndBodyNotLogged() throws Exception {
        configureWebhookSecret();
        ListAppender<ILoggingEvent> ctrl = attachAppender(
                com.averpo.erp.plugins.telegram.web.TelegramWebhookController.class);
        try {
            mockMvc.perform(post("/telegram/webhook")
                            .header("X-Telegram-Bot-Api-Secret-Token", "MAXFIY-SEKRET-132")
                            .contentType("application/json").content("MAXFIY-TANA-132"))
                    .andExpect(status().isUnauthorized());
        } finally {
            ((Logger) LoggerFactory.getLogger(
                    com.averpo.erp.plugins.telegram.web.TelegramWebhookController.class))
                    .detachAppender(ctrl);
        }
        for (ILoggingEvent event : ctrl.list) {
            org.assertj.core.api.Assertions.assertThat(event.getFormattedMessage())
                    .doesNotContain("MAXFIY-SEKRET-132").doesNotContain("MAXFIY-TANA-132");
        }
    }
}
