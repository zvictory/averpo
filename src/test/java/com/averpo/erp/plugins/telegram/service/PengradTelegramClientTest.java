package com.averpo.erp.plugins.telegram.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.averpo.erp.plugins.telegram.service.TelegramBotClient.Update;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Порт имплининг тести (CbuRestClientTest нақши: ҳақиқий URL қолипи ва
 * JSON парсинг локал mock сервер билан текширилади - тармоққа
 * чиқилмайди). Импл package-private бўлгани учун тест шу пакетда туради;
 * Spring контексти йўқ - соф unit тест.
 *
 * <p>Mock сервер - JDK'нинг ўз {@link HttpServer}и (янги тест
 * боғлиқлиги қўшилмайди); кутубхонанинг {@code Builder.apiUrl(...)}
 * билан унга йўналтирилади.
 *
 * <p>Асосий вазифа - карта 7-банди: <b>токен ҳеч қандай log қаторига
 * тушмаслиги</b>. Bot API токени URL'нинг ЎЗИДА туради (тест буни ҳам
 * тасдиқлайди), шунинг учун хато оқимларида (401, тармоқ узилиши)
 * exception хабари логланса токен error.log'га чиқиб кетарди. Ҳар хато
 * сценарийсидан кейин ListAppender'даги ҲАММА қатор текширилади.
 */
class PengradTelegramClientTest {

    /** Сохта, лекин реал ФОРМАТдаги токен - логда изланадиган намуна. */
    private static final String TOKEN = "123456789:AAFakeTokenForTestOnly-not-real";

    /** Токеннинг сир қисми - лог қаторларида шу ҳам изланади. */
    private static final String SECRET_PART = "AAFakeTokenForTestOnly-not-real";

    /** Локал mock Bot API. */
    private HttpServer server;

    /** Mock сервер кўрган йўллар - URL қолипини текшириш учун. */
    private final List<String> seenPaths = new ArrayList<>();

    /** Навбатдаги жавоб танаси (тест ўзи белгилайди). */
    private String responseBody = "{\"ok\":true}";

    /** Навбатдаги жавоб статуси. */
    private int responseStatus = 200;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            seenPaths.add(exchange.getRequestURI().getPath());
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    /** Локал серверга йўналтирилган импл (кутубхона apiUrl'и mock'га). */
    private PengradTelegramClient client() {
        return new PengradTelegramClient("http://127.0.0.1:" + server.getAddress().getPort() + "/bot");
    }

    /**
     * Сервери ЎЧИРИЛГАН импл - тармоқ хатоси (connection refused)
     * сценарийси учун.
     */
    private PengradTelegramClient deadClient() {
        int port = server.getAddress().getPort();
        server.stop(0);
        return new PengradTelegramClient("http://127.0.0.1:" + port + "/bot");
    }

    /** Импл логгерига уланган йиғувчи (токен сизишини тутиш учун). */
    private ListAppender<ILoggingEvent> attachAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(PengradTelegramClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    /** Логгердан йиғувчини узади (тестлар бир-бирига таъсир қилмасин). */
    private void detach(ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(PengradTelegramClient.class)).detachAppender(appender);
    }

    /** ҲАР қаторда (хабар ва стектрейс) токен йўқлигини тасдиқлайди. */
    private void assertNoTokenLeak(ListAppender<ILoggingEvent> appender) {
        assertThat(appender.list).isNotEmpty(); // хато ЁЗИЛГАН бўлиши шарт (жим ютилмасин)
        for (ILoggingEvent event : appender.list) {
            assertThat(event.getFormattedMessage())
                    .doesNotContain(TOKEN).doesNotContain(SECRET_PART);
            // Стектрейс/сабаб занжирида ҳам (кутубхона RuntimeException'и
            // OkHttp хабарини ўраб беради - у URL'ни ўз ичига олиши мумкин,
            // шунинг учун throwable умуман ёзилмайди)
            assertThat(String.valueOf(event.getThrowableProxy()))
                    .doesNotContain(SECRET_PART);
        }
    }

    /** getMe: тўғри токенда бот маълумоти парсланади. */
    @Test
    void getMe_parsesBotInfo() {
        responseBody = """
                {"ok":true,"result":{"id":123456789,"is_bot":true,
                 "first_name":"Averpo","username":"averpo_test_bot"}}""";

        Optional<TelegramBotClient.BotInfo> info = client().getMe(TOKEN);

        assertThat(info).isPresent();
        assertThat(info.get().username()).isEqualTo("averpo_test_bot");
        assertThat(info.get().id()).isEqualTo(123456789L);
    }

    /**
     * URL қолипи: {@code /bot<token>/<метод>} - токен йўлда АЙНАН
     * (кодланмаган) туради. Бу Bot API контракти; айни пайтда токен
     * URL'да экани лог қоидасининг (getMessage ёзилмайди) сабаби.
     */
    @Test
    void requestPath_containsRawTokenAndMethod() {
        responseBody = "{\"ok\":true,\"result\":{\"id\":1,\"username\":\"b\"}}";

        client().getMe(TOKEN);

        assertThat(seenPaths).containsExactly("/bot" + TOKEN + "/getMe");
    }

    /**
     * getMe: нотўғри токен - Telegram 401 + {@code ok:false} беради.
     * Кутубхона HTTP статусни текширмайди, танани парслайди → бўш
     * Optional (чақирувчи BR-TG-001 отади).
     */
    @Test
    void getMe_unauthorized_empty() {
        responseStatus = 401;
        responseBody = "{\"ok\":false,\"error_code\":401,\"description\":\"Unauthorized\"}";

        assertThat(client().getMe(TOKEN)).isEmpty();
    }

    /** getMe: тармоқ узилиши - бўш Optional, логда токен ЙЎҚ. */
    @Test
    void getMe_networkError_emptyAndNoTokenInLog() {
        PengradTelegramClient client = deadClient();
        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            assertThat(client.getMe(TOKEN)).isEmpty();
        } finally {
            detach(appender);
        }
        assertNoTokenLeak(appender);
    }

    /** getUpdates: хабарлар парсланади (update_id, chat, username, матн). */
    @Test
    void getUpdates_parsesMessages() {
        responseBody = """
                {"ok":true,"result":[
                  {"update_id":5,"message":{"message_id":1,
                   "from":{"id":42,"is_bot":false,"username":"aliyev"},
                   "chat":{"id":42,"type":"private"},"text":"/start abc123"}}]}""";

        Optional<List<Update>> updates = client().getUpdates(TOKEN, 5, 25);

        assertThat(updates).isPresent();
        assertThat(updates.get()).hasSize(1);
        Update update = updates.get().get(0);
        assertThat(update.updateId()).isEqualTo(5);
        assertThat(update.chatId()).isEqualTo(42);
        assertThat(update.username()).isEqualTo("aliyev");
        assertThat(update.text()).isEqualTo("/start abc123");
    }

    /**
     * getUpdates: хабарсиз update (масалан callback_query) ташланмайди -
     * offset силжиши учун барибир қайтарилади, лекин матни null (жим
     * ўтади). Акс ҳолда poller ўша update'га қайта-қайта қайтарди.
     */
    @Test
    void getUpdates_nonMessageUpdate_keptForOffsetWithNullText() {
        responseBody = """
                {"ok":true,"result":[{"update_id":9,
                  "callback_query":{"id":"x","from":{"id":1,"is_bot":false}}}]}""";

        Optional<List<Update>> updates = client().getUpdates(TOKEN, 0, 25);

        assertThat(updates).isPresent();
        assertThat(updates.get()).hasSize(1);
        assertThat(updates.get().get(0).updateId()).isEqualTo(9);
        assertThat(updates.get().get(0).text()).isNull();
    }

    /**
     * getUpdates: бўш рўйхат ва ХАТО фарқланади - тинч timeout'да
     * мавжуд-бўш рўйхат (poller дарҳол давом), хатода бўш Optional
     * (poller backoff қилади - hot loop бўлмайди).
     */
    @Test
    void getUpdates_emptyVsError_distinguished() {
        responseBody = "{\"ok\":true,\"result\":[]}";
        assertThat(client().getUpdates(TOKEN, 0, 25)).isPresent().get()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST).isEmpty();

        PengradTelegramClient dead = deadClient();
        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            assertThat(dead.getUpdates(TOKEN, 0, 25)).isEmpty();
        } finally {
            detach(appender);
        }
        assertNoTokenLeak(appender);
    }

    /** getUpdates: API ради (429 лимит) - хато оқими (бўш Optional). */
    @Test
    void getUpdates_apiRejection_empty() {
        responseStatus = 429;
        responseBody = "{\"ok\":false,\"error_code\":429,\"description\":\"Too Many Requests\"}";

        assertThat(client().getUpdates(TOKEN, 0, 25)).isEmpty();
    }

    /** sendMessage: хато чақирувчини ЙИҚИТМАЙДИ ва токен логга чиқмайди. */
    @Test
    void sendMessage_errorSwallowed_noTokenInLog() {
        PengradTelegramClient client = deadClient();
        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            // Exception отилмайди - улаш амали бажарилган, жавоб етмади холос
            client.sendMessage(TOKEN, 42, "Профиль уланди");
        } finally {
            detach(appender);
        }
        assertNoTokenLeak(appender);
    }

    /** sendMessage: муваффақиятли йўлда тўғри метод чақирилади. */
    @Test
    void sendMessage_callsSendMessageEndpoint() {
        responseBody = "{\"ok\":true,\"result\":{\"message_id\":1,"
                + "\"chat\":{\"id\":42,\"type\":\"private\"},\"date\":1}}";

        client().sendMessage(TOKEN, 42, "Профиль уланди");

        assertThat(seenPaths).containsExactly("/bot" + TOKEN + "/sendMessage");
    }

    // ---- Webhook (Arbitr-138) ----

    /** registerWebhook: тўғри йўл (setWebhook) чақирилади, isOk true. */
    @Test
    void registerWebhook_ok_callsSetWebhook() {
        responseBody = "{\"ok\":true,\"result\":true,\"description\":\"Webhook was set\"}";

        boolean ok = client().registerWebhook(TOKEN, "https://app.example.com/telegram/webhook", "sekret");

        assertThat(ok).isTrue();
        assertThat(seenPaths).containsExactly("/bot" + TOKEN + "/setWebhook");
    }

    /** registerWebhook: Telegram рад этса (ok:false) - false. */
    @Test
    void registerWebhook_rejected_false() {
        responseStatus = 400;
        responseBody = "{\"ok\":false,\"error_code\":400,\"description\":\"bad webhook\"}";

        boolean ok = client().registerWebhook(TOKEN, "https://app.example.com/telegram/webhook", "sekret");

        assertThat(ok).isFalse();
    }

    /** registerWebhook: тармоқ хатоси - false, токен логда ЙЎҚ. */
    @Test
    void registerWebhook_networkError_falseAndNoTokenInLog() {
        PengradTelegramClient client = deadClient();
        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            assertThat(client.registerWebhook(TOKEN, "https://x/telegram/webhook", "s")).isFalse();
        } finally {
            detach(appender);
        }
        assertNoTokenLeak(appender);
    }

    /** deleteWebhook: тўғри йўл (deleteWebhook) чақирилади, isOk true. */
    @Test
    void deleteWebhook_ok_callsDeleteWebhook() {
        responseBody = "{\"ok\":true,\"result\":true}";

        boolean ok = client().deleteWebhook(TOKEN);

        assertThat(ok).isTrue();
        assertThat(seenPaths).containsExactly("/bot" + TOKEN + "/deleteWebhook");
    }

    /** deleteWebhook: тармоқ хатоси - false, токен логда ЙЎҚ. */
    @Test
    void deleteWebhook_networkError_falseAndNoTokenInLog() {
        PengradTelegramClient client = deadClient();
        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            assertThat(client.deleteWebhook(TOKEN)).isFalse();
        } finally {
            detach(appender);
        }
        assertNoTokenLeak(appender);
    }

    /** parseWebhookUpdate: тўғри JSON → порт Update (тармоқсиз - соф парс). */
    @Test
    void parseWebhookUpdate_validJson_returnsUpdate() {
        String json = """
                {"update_id":77,"message":{"message_id":1,
                 "from":{"id":42,"is_bot":false,"username":"aliyev"},
                 "chat":{"id":42,"type":"private"},"text":"/start abc"}}""";

        Optional<Update> update = client().parseWebhookUpdate(json);

        assertThat(update).isPresent();
        assertThat(update.get().updateId()).isEqualTo(77);
        assertThat(update.get().chatId()).isEqualTo(42);
        assertThat(update.get().username()).isEqualTo("aliyev");
        assertThat(update.get().text()).isEqualTo("/start abc");
        // Тармоққа чиқмади (соф парс)
        assertThat(seenPaths).isEmpty();
    }

    /** parseWebhookUpdate: бузуқ JSON → empty, лог'да ТАНА ЙЎҚ. */
    @Test
    void parseWebhookUpdate_malformed_emptyAndBodyNotLogged() {
        String garbage = "{not valid json at all :(";
        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            assertThat(client().parseWebhookUpdate(garbage)).isEmpty();
        } finally {
            detach(appender);
        }
        // Тана (ишончсиз кириш) ҳеч бир қаторда йўқ
        for (ILoggingEvent event : appender.list) {
            assertThat(event.getFormattedMessage()).doesNotContain("not valid json");
        }
    }

    /** parseWebhookUpdate: бўш/null тана → empty (лог'сиз). */
    @Test
    void parseWebhookUpdate_blank_empty() {
        assertThat(client().parseWebhookUpdate("")).isEmpty();
        assertThat(client().parseWebhookUpdate(null)).isEmpty();
    }
}
