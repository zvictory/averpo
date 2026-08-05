package com.averpo.erp.plugins.telegram;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.averpo.erp.audit.domain.AuditEvent;
import com.averpo.erp.audit.domain.AuditEventType;
import com.averpo.erp.audit.repo.AuditEventRepository;
import com.averpo.erp.security.domain.AppUser;
import com.averpo.erp.plugins.telegram.service.TelegramBotClient;
import com.averpo.erp.plugins.telegram.service.TelegramService;
import com.averpo.erp.security.service.UserService;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.plugins.core.domain.PluginKey;
import com.averpo.erp.plugins.core.service.PluginService;
import com.averpo.erp.testsupport.WithMockRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Telegram улаш хизмати тестлари (docs/modules/user-profile.md 3-бўлим
 * «Тестлар» + карта тест кутилмаси): токен getMe валидацияси ва
 * маскаси, улаш коди TTL/бир марталиги, poller ишлови (/start код),
 * аудитда ФАҚАТ факт, токен log/аудитда ЙЎҚ, плагин гейти.
 *
 * <p>Bot API порти мок (ExchangeRateServiceTest нақши) - тармоққа
 * чиқилмайди. Poller thread'и {@code @Profile("!test")} - тест
 * контекстида умуман йўқ, ишлов мантиғи шу ерда тўғридан текширилади.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockRole(username = "tgadmin")
class TelegramServiceTest {

    /** Сохта, лекин реал ФОРМАТдаги токен (қиймати ҳақиқий эмас). */
    private static final String TOKEN = "123456789:AAFakeTokenForTestOnly-not-real";

    /** Токеннинг сир қисми - лог/аудитда изланадиган намуна. */
    private static final String SECRET_PART = "AAFakeTokenForTestOnly-not-real";

    /** getMe жавоби - бот номи ШУ ердан олинади (қўлда эмас, тузоқ 3). */
    private static final TelegramBotClient.BotInfo BOT =
            new TelegramBotClient.BotInfo(123456789L, "averpo_test_bot");

    @Autowired TelegramService telegramService;
    @Autowired UserService userService;
    @Autowired PluginService pluginService;
    @Autowired AuditEventRepository auditRepository;
    @Autowired JdbcClient jdbcClient;

    /** JPA ёзувини хом SQL кўриши учун flush қилишда керак. */
    @jakarta.persistence.PersistenceContext
    jakarta.persistence.EntityManager entityManager;

    /** Bot API - мок (тармоқ йўқ). */
    @MockitoBean TelegramBotClient client;

    @BeforeEach
    void setUp() {
        // @WithMockRole фақат SecurityContext беради - улаш коди app_user
        // қаторига ёзилгани учун реал фойдаланувчи ҳам керак (аудит актори
        // ва updated_by ҳам шу қатордан топилади - плагиндан ОЛДИН)
        jdbcClient.sql("INSERT INTO app_user (id, username, password_hash, display_name, role) "
                        + "VALUES (?, 'tgadmin', 'x', 'Телеграм админ', 'SUPER_ADMIN')")
                .param(UUID.randomUUID()).update();
        // Плагин гейти (Arbitr-113): Telegram оқимлари фақат ёқиқда
        pluginService.setEnabled(PluginKey.TELEGRAM, true);
    }

    /** Токенни ўрнатади (getMe мокини тайёрлаб) - улаш тестлари учун асос. */
    private void configureBot() {
        when(client.getMe(TOKEN)).thenReturn(Optional.of(BOT));
        telegramService.saveToken(TOKEN);
    }

    /** Тур бўйича аудит ёзувлари (AuditLogTest нақши - тест тўплами кичик). */
    private List<AuditEvent> eventsOfType(AuditEventType type) {
        return auditRepository.findAll().stream()
                .filter(e -> e.getEventType() == type).toList();
    }

    // ---- Токен: сақлаш, валидация, маска, ўчириш ----

    /** Тўғри токен: getMe ўтади → сақланади, бот номи ЖАВОБДАН олинади. */
    @Test
    void saveToken_validToken_storesAndTakesUsernameFromGetMe() {
        configureBot();

        assertThat(telegramService.configured()).isTrue();
        assertThat(telegramService.botUsername()).isEqualTo("averpo_test_bot");
    }

    /**
     * Токен базада ШИФРЛАНГАН: устунда очиқ қиймат ЙЎҚ (арбитр қарори).
     * JPA ёзувини хом SQL кўриши учун аввал flush (FactoryResetServiceTest'да
     * ҳужжатланган JPA/JdbcClient аралашуви).
     */
    @Test
    void saveToken_storesEncrypted_plainTokenNotInDatabase() {
        configureBot();
        entityManager.flush();

        String stored = jdbcClient.sql("SELECT token_enc FROM telegram_settings")
                .query(String.class).single();

        assertThat(stored).isNotBlank()
                .doesNotContain(TOKEN)
                .doesNotContain(SECRET_PART);
    }

    /** Нотўғри токен: getMe рад этди → BR-TG-001, ҳеч нарса сақланмайди. */
    @Test
    void saveToken_getMeRejects_brTg001_nothingStored() {
        when(client.getMe(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> telegramService.saveToken("yomon-token"))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-TG-001"));
        assertThat(telegramService.configured()).isFalse();
    }

    /** Бўш токен: тармоққа умуман чиқилмайди (getMe чақирилмайди) → BR-TG-001. */
    @Test
    void saveToken_blank_brTg001_withoutNetworkCall() {
        assertThatThrownBy(() -> telegramService.saveToken("   "))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-TG-001"));
        verify(client, never()).getMe(anyString());
    }

    /** Маска: бот id + сирнинг 2 белгиси; тўлиқ токен ЭКРАНГА чиқмайди. */
    @Test
    void maskedToken_showsBotIdAndTwoChars_only() {
        configureBot();

        String masked = telegramService.maskedToken();

        assertThat(masked).isEqualTo("123456789:AA●●●●");
        assertThat(masked).doesNotContain(SECRET_PART);
    }

    /**
     * Токен ўчирилди: бот узилади, лекин уланган фойдаланувчининг
     * chat_id'си ТЕГИЛМАЙДИ (спец: токен қайта киритилса тикланади).
     */
    @Test
    void deleteToken_disconnectsBot_keepsLinkedUsers() {
        configureBot();
        TelegramService.LinkInfo link = telegramService.startLink();
        telegramService.handleUpdate(TOKEN, update(1, 42, "aliyev", "/start " + link.code()));
        assertThat(userService.current().telegramLinked()).isTrue();

        telegramService.deleteToken();

        assertThat(telegramService.configured()).isFalse();
        assertThat(telegramService.botUsername()).isNull();
        assertThat(userService.current().getTelegramChatId()).isEqualTo(42);
    }

    // ---- Аудит ва сир гигиенаси (карта 7-банд) ----

    /**
     * Аудитда ФАҚАТ факт: тур, ким, бот номи - токеннинг ўзи ҳам,
     * маскаси ҳам details'га ЁЗИЛМАЙДИ (logging.md сир қоидаси).
     */
    @Test
    void saveAndDeleteToken_auditRecordsFactOnly_noTokenAnywhere() {
        configureBot();
        telegramService.deleteToken();

        List<AuditEvent> events = eventsOfType(AuditEventType.TELEGRAM_TOKEN_CHANGED);

        // Тартибга таянмаймиз (findAll кафолат бермайди) - мазмун бўйича
        assertThat(events).hasSize(2)
                .allSatisfy(event -> assertThat(event.getUsername()).isEqualTo("tgadmin"))
                .extracting(AuditEvent::getDetails)
                .containsExactlyInAnyOrder("Bot token янгиланди: @averpo_test_bot",
                        "Bot token ўчирилди");
        // Бирорта ёзувда токен изи бўлмасин (details ҳам, doc_number ҳам)
        for (AuditEvent event : auditRepository.findAll()) {
            assertThat(String.valueOf(event.getDetails())).doesNotContain(SECRET_PART);
            assertThat(String.valueOf(event.getDocNumber())).doesNotContain(SECRET_PART);
        }
    }

    /**
     * Улаш оқимининг ҳеч бир қаторида токен логланмайди (карта: «токен
     * log'да ЙЎҚ - тест билан текшир»). Клиент қатламидаги ҳимоя
     * PengradTelegramClientTest'да - бу ерда service қатлами.
     */
    @Test
    void linkFlow_neverLogsToken() {
        Logger logger = (Logger) LoggerFactory.getLogger(TelegramService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            configureBot();
            TelegramService.LinkInfo link = telegramService.startLink();
            telegramService.handleUpdate(TOKEN, update(1, 42, "aliyev", "/start " + link.code()));
            telegramService.handleUpdate(TOKEN, update(2, 43, "begona", "/start yomon-kod"));
        } finally {
            logger.detachAppender(appender);
        }
        for (ILoggingEvent event : appender.list) {
            assertThat(event.getFormattedMessage()).doesNotContain(SECRET_PART);
        }
    }

    // ---- Улаш оқими: код, TTL, бир марталик ----

    /** Бот созланмаган: улаш коди сўралса BR-TG-003 (профил блоки огоҳлантиради). */
    @Test
    void startLink_botNotConfigured_brTg003() {
        assertThatThrownBy(() -> telegramService.startLink())
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-TG-003"));
    }

    /** Улаш коди: deep link бот номи билан, код app_user'га ёзилади. */
    @Test
    void startLink_returnsDeepLink_andStoresCode() {
        configureBot();

        TelegramService.LinkInfo link = telegramService.startLink();

        assertThat(link.code()).isNotBlank();
        assertThat(link.deepLink())
                .isEqualTo("https://t.me/averpo_test_bot?start=" + link.code());
        // Код Telegram deep link алифбосида (A-Z a-z 0-9 _ -)
        assertThat(link.code()).matches("[A-Za-z0-9_-]+");
        assertThat(userService.current().getTelegramLinkCode()).isEqualTo(link.code());
    }

    /** «Улаш»ни қайта босиш: эски код ЎЛАДИ (тузоқ 4 - бир эгада бир код). */
    @Test
    void startLink_twice_oldCodeStopsWorking() {
        configureBot();
        TelegramService.LinkInfo first = telegramService.startLink();
        TelegramService.LinkInfo second = telegramService.startLink();

        // Эски код билан улаш - ишламайди
        telegramService.handleUpdate(TOKEN, update(1, 42, "aliyev", "/start " + first.code()));
        assertThat(userService.current().telegramLinked()).isFalse();

        // Янги код билан - уланади
        telegramService.handleUpdate(TOKEN, update(2, 42, "aliyev", "/start " + second.code()));
        assertThat(userService.current().telegramLinked()).isTrue();
    }

    /** /start <код>: чат ва username сақланади, ботга жавоб юборилади. */
    @Test
    void handleUpdate_startWithCode_linksAndReplies() {
        configureBot();
        TelegramService.LinkInfo link = telegramService.startLink();

        telegramService.handleUpdate(TOKEN, update(7, 42, "aliyev", "/start " + link.code()));

        AppUser user = userService.current();
        assertThat(user.getTelegramChatId()).isEqualTo(42);
        assertThat(user.getTelegramUsername()).isEqualTo("aliyev");
        verify(client).sendMessage(eq(TOKEN), eq(42L),
                eq("Профиль уланди: Телеграм админ"));
    }

    /** Код бир марталик: ўша код билан иккинчи чат уланмайди (тузоқ 4). */
    @Test
    void handleUpdate_codeReuse_rejected() {
        configureBot();
        TelegramService.LinkInfo link = telegramService.startLink();
        telegramService.handleUpdate(TOKEN, update(1, 42, "aliyev", "/start " + link.code()));

        telegramService.handleUpdate(TOKEN, update(2, 99, "boshqa", "/start " + link.code()));

        // Биринчи чат ўз ўрнида - иккинчиси уни босиб ололмади
        assertThat(userService.current().getTelegramChatId()).isEqualTo(42);
        verify(client).sendMessage(eq(TOKEN), eq(99L), eq("Код нотўғри ёки эскирган"));
    }

    /**
     * Муддати ўтган код (TTL 10 дақ): улаш рад этилади + ботда хабар.
     * Код service орқали ЭСКИ муддат билан қўйилади - соатни сурмасдан
     * ва jdbc/JPA аралаштирмасдан (jdbc UPDATE қилинса persistence
     * context'даги эски entity барибир янги муддатни кўрсатарди -
     * FactoryResetServiceTest'да ҳужжатланган тузоқ).
     */
    @Test
    void handleUpdate_expiredCode_rejectedWithReply() {
        configureBot();
        userService.setTelegramLinkCode("eskirgan-kod", Instant.now().minusSeconds(60));

        telegramService.handleUpdate(TOKEN, update(1, 42, "aliyev", "/start eskirgan-kod"));

        assertThat(userService.current().telegramLinked()).isFalse();
        verify(client).sendMessage(eq(TOKEN), eq(42L), eq("Код нотўғри ёки эскирган"));
    }

    /** Нотанилган код: улаш йўқ, ботда кириллча хабар (BR коди чиқмайди). */
    @Test
    void handleUpdate_unknownCode_repliesWithoutBrCode() {
        configureBot();

        telegramService.handleUpdate(TOKEN, update(1, 42, "aliyev", "/start yoq-kod"));

        assertThat(userService.current().telegramLinked()).isFalse();
        verify(client).sendMessage(eq(TOKEN), eq(42L), eq("Код нотўғри ёки эскирган"));
    }

    /** Бегона хабарлар (оддий матн, кодсиз /start, матнсиз) - бот ЖИМ (спец). */
    @Test
    void handleUpdate_otherMessages_silent() {
        configureBot();

        telegramService.handleUpdate(TOKEN, update(1, 42, "aliyev", "Салом"));
        telegramService.handleUpdate(TOKEN, update(2, 42, "aliyev", "/start"));
        telegramService.handleUpdate(TOKEN, update(3, 42, "aliyev", null));
        telegramService.handleUpdate(TOKEN, update(4, 42, "aliyev", "/help"));

        verify(client, never()).sendMessage(anyString(), anyLong(), anyString());
    }

    /** Узиш: чат ва кутиб турган код тозаланади. */
    @Test
    void unlink_clearsChatAndPendingCode() {
        configureBot();
        TelegramService.LinkInfo link = telegramService.startLink();
        telegramService.handleUpdate(TOKEN, update(1, 42, "aliyev", "/start " + link.code()));

        telegramService.unlink();

        AppUser user = userService.current();
        assertThat(user.telegramLinked()).isFalse();
        assertThat(user.getTelegramUsername()).isNull();
        assertThat(user.getTelegramLinkCode()).isNull();
    }

    // ---- Плагин гейти (Arbitr-113) ----

    /**
     * Плагин ўчиқ: гейт ёпиқ ва poller УМУМАН тармоққа чиқмайди
     * (pollTarget бўш) - «ўчиқ плагин трафик ҳосил қилмайди».
     */
    @Test
    void pluginDisabled_gateClosed_pollerHasNoTarget() {
        configureBot();
        assertThat(telegramService.pollTarget()).isPresent(); // ёқиқда бор

        pluginService.setEnabled(PluginKey.TELEGRAM, false);

        assertThat(telegramService.enabled()).isFalse();
        assertThat(telegramService.pollTarget()).isEmpty();
    }

    /** Бот созланмаган: poller мақсади йўқ (токенсиз getUpdates маъносиз). */
    @Test
    void botNotConfigured_pollerHasNoTarget() {
        assertThat(telegramService.pollTarget()).isEmpty();
    }

    /** Курсор: poller силжитган offset сақланади (рестартда давом - тузоқ 1). */
    @Test
    void advanceOffset_persistsCursor() {
        configureBot();
        assertThat(telegramService.pollTarget().orElseThrow().offset()).isZero();

        telegramService.advanceOffset(42);

        assertThat(telegramService.pollTarget().orElseThrow().offset()).isEqualTo(42);
    }

    // ---- Webhook (Arbitr-138) ----

    /** AVERPO_PUBLIC_URL'ни тест ичида ўрнатади (@Value майдони). */
    private void setPublicUrl(String url) {
        ReflectionTestUtils.setField(telegramService, "publicUrl", url);
    }

    /**
     * registerWebhookIfReady тўлиқ оқим: URL нормаллаштирилиб
     * WEBHOOK_PATH қўшилади, секрет узатилади, isOk қайтади.
     */
    @Test
    void registerWebhookIfReady_ready_registersWithNormalizedUrl() {
        configureBot();
        setPublicUrl("https://app.averpo.com/"); // охирида «/» - нормаллаштирилсин
        when(client.registerWebhook(eq(TOKEN), anyString(), anyString())).thenReturn(true);

        boolean ok = telegramService.registerWebhookIfReady();

        assertThat(ok).isTrue();
        // Қўш-слэшсиз тўлиқ URL
        verify(client).registerWebhook(eq(TOKEN),
                eq("https://app.averpo.com/telegram/webhook"), anyString());
    }

    /**
     * Секрет БАРҚАРОР: икки марта рўйхатдан ўтказишда бир хил секрет
     * (базада шифрланган - очиқ қиймат устунда ЙЎҚ).
     */
    @Test
    void registerWebhookIfReady_secretStableAndEncrypted() {
        configureBot();
        setPublicUrl("https://app.averpo.com");
        java.util.List<String> secrets = new java.util.ArrayList<>();
        when(client.registerWebhook(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> { secrets.add(inv.getArgument(2)); return true; });

        telegramService.registerWebhookIfReady();
        telegramService.registerWebhookIfReady();

        assertThat(secrets).hasSize(2);
        assertThat(secrets.get(0)).isEqualTo(secrets.get(1)); // барқарор
        // Базада ШИФРЛАНГАН: устунда очиқ секрет ЙЎҚ
        entityManager.flush();
        String storedEnc = jdbcClient.sql("SELECT webhook_secret_enc FROM telegram_settings")
                .query(String.class).single();
        assertThat(storedEnc).isNotBlank().doesNotContain(secrets.get(0));
    }

    /** AVERPO_PUBLIC_URL бўш: webhook рўйхатдан ўтмайди, client чақирилмайди. */
    @Test
    void registerWebhookIfReady_noPublicUrl_falseAndNoCall() {
        configureBot();
        setPublicUrl("");

        assertThat(telegramService.registerWebhookIfReady()).isFalse();
        verify(client, never()).registerWebhook(anyString(), anyString(), anyString());
    }

    /** Плагин ўчиқ: webhook рўйхатдан ўтмайди (гейт). */
    @Test
    void registerWebhookIfReady_pluginDisabled_false() {
        configureBot();
        setPublicUrl("https://app.averpo.com");
        pluginService.setEnabled(PluginKey.TELEGRAM, false);

        assertThat(telegramService.registerWebhookIfReady()).isFalse();
        verify(client, never()).registerWebhook(anyString(), anyString(), anyString());
    }

    /** Бот созланмаган: webhook рўйхатдан ўтмайди. */
    @Test
    void registerWebhookIfReady_noToken_false() {
        setPublicUrl("https://app.averpo.com");

        assertThat(telegramService.registerWebhookIfReady()).isFalse();
        verify(client, never()).registerWebhook(anyString(), anyString(), anyString());
    }

    /** removeWebhookRegistration: токен бор бўлса client.deleteWebhook чақирилади. */
    @Test
    void removeWebhookRegistration_callsDeleteWebhook() {
        configureBot();
        when(client.deleteWebhook(TOKEN)).thenReturn(true);

        assertThat(telegramService.removeWebhookRegistration()).isTrue();
        verify(client).deleteWebhook(TOKEN);
    }

    /** deleteToken токенни ўчиришдан ОЛДИН Telegram webhook'ни бекор қилади. */
    @Test
    void deleteToken_alsoDeletesWebhook() {
        configureBot();

        telegramService.deleteToken();

        verify(client).deleteWebhook(TOKEN);
    }

    // ---- webhookSecretValid (constant-time таққос) ----

    /** Секрет тўғри → true; нотўғри/null → false. */
    @Test
    void webhookSecretValid_matchesStoredSecret() {
        configureBot();
        setPublicUrl("https://app.averpo.com");
        String[] captured = new String[1];
        when(client.registerWebhook(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> { captured[0] = inv.getArgument(2); return true; });
        telegramService.registerWebhookIfReady(); // секрет яратилиб сақланади

        assertThat(telegramService.webhookSecretValid(captured[0])).isTrue();
        assertThat(telegramService.webhookSecretValid("boshqa-sekret")).isFalse();
        assertThat(telegramService.webhookSecretValid(null)).isFalse();
    }

    /** Секрет умуман сақланмаган: ҳар қандай header (null ҳам) → false. */
    @Test
    void webhookSecretValid_noStoredSecret_false() {
        configureBot(); // токен бор, лекин webhook рўйхатдан ўтмаган → секрет йўқ

        assertThat(telegramService.webhookSecretValid("nimadir")).isFalse();
        assertThat(telegramService.webhookSecretValid(null)).isFalse();
    }

    // ---- handleWebhookBody ----

    /**
     * Тўғри тана: parseWebhookUpdate → handleUpdate занжири - улаш коди
     * ишлайди (polling билан бир мантиқ).
     */
    @Test
    void handleWebhookBody_validUpdate_linksProfile() {
        configureBot();
        TelegramService.LinkInfo link = telegramService.startLink();
        String json = "{\"update_id\":1}"; // мазмуни муҳим эмас - порт мок
        when(client.parseWebhookUpdate(json)).thenReturn(
                Optional.of(update(1, 42, "aliyev", "/start " + link.code())));

        telegramService.handleWebhookBody(json);

        assertThat(userService.current().telegramLinked()).isTrue();
        verify(client).sendMessage(eq(TOKEN), eq(42L), eq("Профиль уланди: Телеграм админ"));
    }

    /** Бузуқ тана (parse empty): жим - handleUpdate таъсири йўқ. */
    @Test
    void handleWebhookBody_unparseable_silent() {
        configureBot();
        when(client.parseWebhookUpdate(anyString())).thenReturn(Optional.empty());

        telegramService.handleWebhookBody("garbage");

        verify(client, never()).sendMessage(anyString(), anyLong(), anyString());
    }

    /** Токен йўқ (ўчирилган): парс бўлса ҳам жавоб юборилмайди (жим). */
    @Test
    void handleWebhookBody_noToken_silent() {
        // Плагин ёқиқ, лекин токен йўқ (configureBot чақирилмади)
        when(client.parseWebhookUpdate(anyString())).thenReturn(
                Optional.of(update(1, 42, "aliyev", "/start abc")));

        telegramService.handleWebhookBody("{}");

        verify(client, never()).sendMessage(anyString(), anyLong(), anyString());
    }

    /** Тест намунаси - битта келган хабар. */
    private static TelegramBotClient.Update update(long updateId, long chatId,
                                                   String username, String text) {
        return new TelegramBotClient.Update(updateId, chatId, username, text);
    }
}
