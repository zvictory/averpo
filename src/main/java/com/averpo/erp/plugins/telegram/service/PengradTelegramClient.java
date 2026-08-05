package com.averpo.erp.plugins.telegram.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.request.DeleteWebhook;
import com.pengrad.telegrambot.request.GetMe;
import com.pengrad.telegrambot.request.GetUpdates;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SetWebhook;
import com.pengrad.telegrambot.response.BaseResponse;
import com.pengrad.telegrambot.response.GetMeResponse;
import com.pengrad.telegrambot.response.GetUpdatesResponse;
import com.pengrad.telegrambot.utility.BotUtils;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@link TelegramBotClient} портининг импли - {@code java-telegram-bot-api}
 * (pengrad) кутубхонаси устида. Package-private: ташқарига фақат порт
 * кўринади, pengrad типлари шу файлдан чиқмайди (CbuRestClient нақши).
 *
 * <p><b>ХАВФСИЗЛИК - ЭНГ МУҲИМ ЖОЙ:</b> Bot API токени ҳар сўров
 * URL'ида туради ({@code api.telegram.org/bot<token>/METHOD}) - демак
 * URL логга тушса ТОКЕН СИЗАДИ (logging.md: токен ҳеч қачон логга
 * ёзилмайди). Иккита ҳимоя:
 * <ul>
 *   <li>кутубхонанинг {@code Builder.debug()} ИШЛАТИЛМАЙДИ - у
 *       {@code HttpLoggingInterceptor(BODY)} улаб URL'ни оқизарди;
 *       бошқа интерцептор ҳам қўшилмайди;</li>
 *   <li>тармоқ хатосида кутубхона {@code RuntimeException(IOException)}
 *       отади, OkHttp хабарлари эса URL'ни ўз ичига олиши мумкин -
 *       шунинг учун catch'да {@code e.getMessage()} ҲЕЧ ҚАЧОН
 *       логланмайди, фақат {@code getClass()} номи. Хом exception бу
 *       класдан ЧИҚМАЙДИ: юқорида GlobalExceptionHandler уни стектрейс
 *       билан ERROR ёзиб токенни error.log'га чиқариб қўярди.</li>
 * </ul>
 *
 * <p><b>Хато семантикаси</b> (кутубхона манбасидан): (1) тармоқ узилиши -
 * {@code RuntimeException}; (2) API ради (401 нотўғри токен, 429 лимит) -
 * HTTP status текширилмайди, тана JSON парсланиб {@code isOk()=false}
 * бўлади; (3) бузуқ тана - JSON парс хатоси. Учаласи ҳам бир хил
 * натижага олиб келади: бўш {@link Optional} → чақирувчи BR-TG-001
 * отади ёки poller backoff қилади.
 */
@Component
@Slf4j
class PengradTelegramClient implements TelegramBotClient {

    /** Тармоққа уланиш чегараси - осилиб қолмаслик учун. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Ўқиш чегараси: long polling серверда 25 сониягача ушлаб турилади
     * ({@link TelegramService#POLL_TIMEOUT_SECONDS}) - демак ундан
     * КАТТА бўлиши шарт, акс ҳолда ҳар нормал poll «тармоқ хатоси»
     * бўлиб чиқарди. Кутубхонанинг ўз default'и 75s - биз аниқ
     * қиймат қўямиз (у ҳам {@code readTimeout <= poll timeout} ҳолатида
     * ўзи қўшимча клиент ясарди - бизда бунга ҳожат йўқ).
     */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(35);

    /**
     * ЯГОНА HTTP клиент - ҳар чақиришда {@link TelegramBot} ўрами
     * янгидан қурилади, лекин уланиш ҳовузи (thread pool, connection
     * pool) шу битта объектда қолади: кутубхонанинг {@code Builder}'и
     * клиент берилмаса ҳар сафар ЯНГИСИНИ (ўз ҳовузи билан) ясарди.
     */
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT)
            .readTimeout(READ_TIMEOUT)
            .build();

    /**
     * Bot API базаси ёки null - кутубхонанинг ўз манзили
     * ({@code api.telegram.org/bot}). Тестда локал mock серверга
     * йўналтирилади - тармоққа чиқилмайди.
     */
    private final String apiUrl;

    /** Жонли конфигурация - Telegram'нинг ҳақиқий манзили. */
    @Autowired
    PengradTelegramClient() {
        this(null);
    }

    /** Тестлар учун: локал mock Bot API манзили (CbuRestClient нақши). */
    PengradTelegramClient(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    /**
     * Токен билан ишлайдиган ўрам. Ҳар чақиришда янги: токен объект
     * майдонида КЭШЛАНМАСИН (сир юзаси кичик қолсин - TelegramService
     * уни ҳар сафар шифрдан очади). Нархи - бир нечта объект
     * аллокацияси (ҳовуз ўзимизники, Gson кутубхонада статик), бизнинг
     * ҳажмда (25 сонияда битта сўров) сезилмайди.
     */
    private TelegramBot bot(String token) {
        TelegramBot.Builder builder = new TelegramBot.Builder(token).okHttpClient(httpClient);
        if (apiUrl != null) {
            builder.apiUrl(apiUrl);
        }
        return builder.build();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<BotInfo> getMe(String token) {
        try {
            GetMeResponse response = bot(token).execute(new GetMe());
            if (response == null || !response.isOk() || response.user() == null) {
                // Нотўғри токен - {"ok":false,"description":"Unauthorized"}.
                // description логланмайди: бу оқимда керак эмас (чақирувчи
                // BR-TG-001 беради)
                return Optional.empty();
            }
            return Optional.of(new BotInfo(response.user().id(), response.user().username()));
        } catch (Exception e) {
            // ДИҚҚАТ: e.getMessage() ТАҚИҚ - URL'да токен бор (класс изоҳи)
            log.warn("Telegram getMe амалга ошмади: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** {@inheritDoc} */
    @Override
    public Optional<List<Update>> getUpdates(String token, long offset, int timeoutSeconds) {
        try {
            GetUpdatesResponse response = bot(token).execute(new GetUpdates()
                    // Telegram update_id - Integer (Bot API); порт/жадвал
                    // long юритади. Ошиб кетса жим кесилмасин - хато оқимига
                    // тушсин (амалда бўлмайди)
                    .offset(Math.toIntExact(offset))
                    .timeout(timeoutSeconds));
            if (response == null || !response.isOk() || response.updates() == null) {
                log.warn("Telegram getUpdates рад этилди (токен ёки лимит?)");
                return Optional.empty();
            }
            List<Update> updates = new ArrayList<>(response.updates().size());
            for (com.pengrad.telegrambot.model.Update dto : response.updates()) {
                updates.add(toUpdate(dto));
            }
            return Optional.of(updates);
        } catch (Exception e) {
            // ДИҚҚАТ: e.getMessage() ТАҚИҚ - URL'да токен бор
            log.warn("Telegram getUpdates амалга ошмади: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void sendMessage(String token, long chatId, String text) {
        try {
            bot(token).execute(new SendMessage(chatId, text));
        } catch (Exception e) {
            // Жавоб етиб бормаса ҳам амал (улаш) бажарилган - фақат WARN.
            // ДИҚҚАТ: e.getMessage() ТАҚИҚ - URL'да токен бор
            log.warn("Telegram sendMessage амалга ошмади (chat {}): {}",
                    chatId, e.getClass().getSimpleName());
        }
    }

    /**
     * Фақат {@code message} янгиликлари сўралади - қолгани (edited,
     * callback) трафик бермасин; секрет header ҳар POST'да текширилади.
     */
    private static final String[] WEBHOOK_UPDATES = {"message"};

    /** {@inheritDoc} */
    @Override
    public boolean registerWebhook(String token, String url, String secret) {
        try {
            BaseResponse response = bot(token).execute(new SetWebhook()
                    .url(url)
                    .secretToken(secret)
                    .allowedUpdates(WEBHOOK_UPDATES));
            return response != null && response.isOk();
        } catch (Exception e) {
            // ДИҚҚАТ: e.getMessage() ТАҚИҚ - URL'да токен бор
            log.warn("Telegram setWebhook амалга ошмади: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean deleteWebhook(String token) {
        try {
            BaseResponse response = bot(token).execute(new DeleteWebhook());
            return response != null && response.isOk();
        } catch (Exception e) {
            // ДИҚҚАТ: e.getMessage() ТАҚИҚ - URL'да токен бор
            log.warn("Telegram deleteWebhook амалга ошмади: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Update> parseWebhookUpdate(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            com.pengrad.telegrambot.model.Update dto = BotUtils.parseUpdate(json);
            return dto == null ? Optional.empty() : Optional.of(toUpdate(dto));
        } catch (Exception e) {
            // Бузуқ JSON (JsonSyntaxException) - ТАНА логланмайди
            // (ишончсиз кириш; секрет ўтган бўлса ҳам тана ишончли эмас)
            log.warn("Telegram webhook танасини парслаб бўлмади: {}",
                    e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * Кутубхона моделини порт record'ига ўтказади - pengrad типлари шу
     * методдан нарига ўтмайди. Хабарсиз update (callback_query ва ҳ.к.)
     * ташланмайди: offset силжиши учун керак, лекин матни null -
     * TelegramService уни жим ўтказади.
     */
    private static Update toUpdate(com.pengrad.telegrambot.model.Update dto) {
        Message message = dto.message();
        if (message == null || message.chat() == null) {
            return new Update(dto.updateId(), 0, null, null);
        }
        return new Update(dto.updateId(), message.chat().id(),
                message.from() == null ? null : message.from().username(),
                message.text());
    }

    /**
     * Контекст ёпилганда HTTP ҳовузини бўшатади - Spring тест
     * контекстлари кўп, ҳар бирида осилиб қолган thread/уланма
     * тўпланмасин.
     */
    @PreDestroy
    void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
