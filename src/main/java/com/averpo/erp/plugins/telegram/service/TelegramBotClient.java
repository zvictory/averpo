package com.averpo.erp.plugins.telegram.service;

import java.util.List;
import java.util.Optional;

/**
 * Telegram Bot API порти (CbuRateClient нақши: интерфейс + package-private
 * импл) - тестлар шу интерфейсни мок қилади, тармоққа чиқмайди.
 *
 * <p><b>Нега порт кутубхона устида</b>: Bot API билан мулоқот
 * {@code com.github.pengrad:java-telegram-bot-api} зиммасида, лекин
 * кутубхонанинг типлари ШУ ПОРТДАН ТАШҚАРИГА чиқмайди - қуйидаги кичик
 * record'лар бизнинг контракт. Шунда: (1) тестлар тармоқсиз мок
 * қилади, (2) кутубхона алмашса ўзгариш битта импл файлида қолади.
 *
 * <p>Токен ҳар чақиришда параметр сифатида берилади (клиент ҳолат
 * сақламайди): токен фақат TelegramService'да - у SecretCrypto орқали
 * очади ва дарҳол ишлатади, ҳеч қаерда кэшланмайди/логланмайди.
 */
public interface TelegramBotClient {

    /** getMe жавобининг бизга кераклиси - бот идентификацияси. */
    record BotInfo(long id, String username) { }

    /**
     * Битта келган хабар (getUpdates элементи) - бизга керакли қисми.
     *
     * @param updateId  курсор учун (offset = updateId + 1)
     * @param chatId    жавоб юбориладиган чат
     * @param username  Telegram @username ёки null (профилда яширин бўлса)
     * @param text      хабар матни ёки null (стикер/расм - жим ўтади)
     */
    record Update(long updateId, long chatId, String username, String text) { }

    /**
     * Токен ҳақиқийлигини текширади ва бот маълумотини қайтаради;
     * рад этилса (Unauthorized) ёки тармоқ узилса - бўш Optional
     * (чақирувчи BR-TG-001 отади).
     */
    Optional<BotInfo> getMe(String token);

    /**
     * Long polling: {@code offset}'дан бошлаб янги хабарлар (сервер
     * {@code timeoutSeconds}гача ушлаб туради - бўш поллинг трафиги
     * бўлмасин).
     *
     * <p>Нега {@link Optional}: «хабар йўқ» (long poll муддати тинч
     * тугади) ва «хато» (тармоқ/429/5xx) ФАРҚЛАНИШИ шарт - иккови ҳам
     * бўш рўйхат бўлса poller хато ҳолатда тез айланиб (hot loop) API'ни
     * дўлайтирарди. Бўш Optional = хато → poller backoff қилади (карта
     * тузоқ 5); мавжуд, лекин бўш рўйхат = тинч давом.
     */
    Optional<List<Update>> getUpdates(String token, long offset, int timeoutSeconds);

    /**
     * Чатга матн юборади (poller жавоблари: «Профиль уланди» ва ҳ.к.;
     * келажакда билдиришнома канали - auth-security-policy lockout
     * огоҳлантириши). Хато жимгина ютилади (WARN): жавоб етиб бормаса
     * ҳам улаш амали бажарилган бўлади.
     */
    void sendMessage(String token, long chatId, String text);

    /**
     * Webhook'ни рўйхатдан ўтказади (Arbitr-138, prod режим): Telegram
     * шу {@code url}'га POST қила бошлайди ва ҳар сўровга {@code secret}
     * ни {@code X-Telegram-Bot-Api-Secret-Token} header'ида қўшади.
     * Фақат {@code message} янгиликлари сўралади (қолгани трафик
     * бермасин). setWebhook getUpdates'ни Telegram томонда ЎЗИ тўхтатади
     * (иккови ўзаро истисно).
     *
     * @return муваффақият ({@code isOk}); хато/тармоқ узилиши - false
     *         (лог'да токенли URL ЙЎҚ - registrar факт ёзади)
     */
    boolean registerWebhook(String token, String url, String secret);

    /**
     * Webhook рўйхатини бекор қилади (плагин ўчганда/токен олинганда/дев
     * polling старт'ида - қолдиқ webhook getUpdates'ни 409 билан
     * тўсмасин). Хато - false + WARN (токенсиз); оқимни тўхтатмайди.
     */
    boolean deleteWebhook(String token);

    /**
     * Webhook POST танасини порт {@link Update}'ига парслайди. Бузуқ ёки
     * бўш JSON - бўш Optional (лог'да ТАНА ЙЎҚ - у ишончсиз кириш).
     * pengrad типлари бу метод ортидан чиқмайди (132 изоляцияси).
     */
    Optional<Update> parseWebhookUpdate(String json);
}
