package com.averpo.erp.plugins.telegram.service;

import com.averpo.erp.audit.domain.AuditEventType;
import com.averpo.erp.audit.service.AuditLogService;
import com.averpo.erp.i18n.Msg;
import com.averpo.erp.plugins.core.domain.PluginKey;
import com.averpo.erp.plugins.core.service.PluginService;
import com.averpo.erp.plugins.telegram.domain.TelegramSettings;
import com.averpo.erp.plugins.telegram.repo.TelegramSettingsRepository;
import com.averpo.erp.security.domain.AppUser;
import com.averpo.erp.security.service.UserService;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.SecretCrypto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Telegram улаш хизмати (docs/modules/user-profile.md 3-бўлим,
 * Arbitr-103) - бот созлаш (токен), профил улаш оқими ва poller учун
 * ишлов мантиғи. Мақсад ҳозирча ФАҚАТ улаш; билдиришнома турлари
 * 2-босқич (auth-security-policy lockout огоҳлантириши шу канални
 * ишлатади).
 *
 * <p><b>Модул чегараси</b>: Telegram - ихтиёрий плагин
 * ({@code plugins.telegram}), ядро эмас. {@code app_user}нинг telegram
 * майдонлари security модулиники - уларга ФАҚАТ
 * {@link UserService}нинг public API'си орқали тегилади (репозиторийга
 * тегиш темир қоида 6 бўйича тақиқ). Тескари йўналиш ЙЎҚ: UserService бу
 * хизматни билмайди - шунда плагин ядрони bean ҳалқасига боғламайди.
 *
 * <p><b>Нега класс даражасида {@code @Transactional} ЙЎҚ</b> (лойиҳадаги
 * бошқа service'лардан фарқли): бу хизмат ТАРМОҚҚА чиқади (Bot API) -
 * long polling 25 сониягача ушлаб туради. Тармоқ чақируви транзакция
 * ичида қолса DB уланмаси шунча вақт банд бўларди. Шунинг учун ҳар
 * метод ўз аниқ чегарасини эълон қилади, {@link #handleUpdate} эса
 * АТАЙЛАБ транзакциясиз (ичкарида UserService ўз қисқа транзакциясини
 * очади, sendMessage эса ундан ташқарида).
 *
 * <p><b>Токен сири</b>: базада ШИФРЛАНГАН ({@link SecretCrypto}, арбитр
 * қарори 2026-07-17); очиқ қиймат фақат Bot API чақируви учун
 * очилади ва ҳеч қаерда кэшланмайди/логланмайди/аудит диффига
 * тушмайди (logging.md; аудитда фақат «янгиланди/ўчирилди» ФАКТи).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramService {

    /** Улаш кодининг амал қилиш муддати (спец: 10 дақиқа). */
    public static final Duration LINK_TTL = Duration.ofMinutes(10);

    /**
     * Бот жавобларининг тили - лойиҳанинг асосий тили (ўзбек кирилл =
     * default bundle; спец жавоб матнларини айнан шу тилда ёзади).
     *
     * <p>АНИҚ белгиланиши ШАРТ: poller thread'ида request/cookie тили
     * йўқ, {@code LocaleContextHolder} эса бундай ҳолда JVM'нинг system
     * locale'ига тушади - сервернинг {@code LANG} созламасига қараб бот
     * инглизча ёки русча гапириб қоларди (тест тутган ҳодиса, 2026-07-17).
     * Фойдаланувчи тилини сақлаш (2-босқич) қўшилса - шу нуқта ўзгаради.
     */
    private static final java.util.Locale BOT_LOCALE = java.util.Locale.of("uz");

    /**
     * Long polling кутиш муддати - клиентнинг read timeout'идан (35s)
     * КИЧИК бўлиши шарт (PengradTelegramClient изоҳи).
     */
    static final int POLL_TIMEOUT_SECONDS = 25;

    /** Улаш коди учун энтропия (9 байт → base64url 12 белги ≈ 2^72). */
    private static final int LINK_CODE_BYTES = 9;

    /** Ботда улашни бошлайдиган ягона буйруқ (спец: фақат шу ишланади). */
    private static final String START_COMMAND = "/start";

    /** Webhook POST йўли - AVERPO_PUBLIC_URL'га шу қўшилиб Telegram'га берилади. */
    static final String WEBHOOK_PATH = "/telegram/webhook";

    /** Webhook сири узунлиги (байт): 32 → base64url 43 белги (Telegram чегараси 256). */
    private static final int WEBHOOK_SECRET_BYTES = 32;

    /** Кодлар тахмин қилинмаслиги учун - криптографик генератор. */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Созлама singleton қатори - фақат шу хизмат ёзади. */
    private final TelegramSettingsRepository repository;

    /** Bot API порти (тестда мок - тармоққа чиқилмайди). */
    private final TelegramBotClient client;

    /** Токенни шифрлаш/очиш (калит env'да, базада эмас). */
    private final SecretCrypto crypto;

    /** Плагин гейти (Arbitr-113) - ўчиқда Telegram умуман ишламайди. */
    private final PluginService pluginService;

    /** app_user'даги улаш коди/чат майдонлари эгаси (тескари боғлиқлик ЙЎҚ). */
    private final UserService userService;

    /** Аудит: токен факти (security → audit рухсатли - LoginAttemptListener нақши). */
    private final AuditLogService auditLogService;

    /**
     * Webhook registrar'ни хабардор қилиш (prod): токен ўзгарса
     * registrar webhook'ни қайта рўйхатдан ўтказади. Event'да СИР ЙЎҚ -
     * фақат ФАКТ (configured boolean). Дев'да тингловчи йўқ (registrar
     * @Profile("!dev & !test")) - event жимгина ўтади.
     */
    private final ApplicationEventPublisher eventPublisher;

    /** Бот жавоблари матни (i18n bundle'да - кодда қаттиқ матн йўқ). */
    private final Msg msg;

    /**
     * Илованинг оммавий базавий URL'и (webhook режимда МАЖБУРИЙ) -
     * Telegram шунга {@link #WEBHOOK_PATH} қўшиб POST қилади. Дев'да
     * керак эмас (polling). Бўш default: «берилмаган»ни аниқлаш учун
     * (AVERPO_SECRET_KEY нақши).
     */
    @Value("${AVERPO_PUBLIC_URL:}")
    private String publicUrl;

    /** Профил блоки учун улаш коди ва тайёр deep link. */
    public record LinkInfo(String code, String deepLink) { }

    /** Poller учун битта айланиш кириши: очилган токен + жорий курсор. */
    public record PollTarget(String token, long offset) { }

    /**
     * Плагин ёқиқми (Arbitr-113 гейти) - Telegram'нинг ҳар бир route/
     * блоки/poller'и шу саволдан бошланади. Ўчиқда UI яширинади ВА
     * backend 404 беради (092 сабоғи: UI яшириш кифоя эмас).
     */
    @Transactional(readOnly = true)
    public boolean enabled() {
        return pluginService.isEnabled(PluginKey.TELEGRAM);
    }

    /**
     * Бот созланганми: токен бор ВА ўқиб бўлади. Калит алмашган бўлса
     * (decrypt рад) - «созланмаган» ҳисобланади: SUPER_ADMIN қайта
     * киритади, тизим йиқилмайди (SecretCrypto изоҳи).
     */
    @Transactional(readOnly = true)
    public boolean configured() {
        return token().isPresent();
    }

    /** Бот username'и (deep link ва экран учун) ёки null - созланмаган. */
    @Transactional(readOnly = true)
    public String botUsername() {
        return repository.findFirstBy().map(TelegramSettings::getBotUsername).orElse(null);
    }

    /**
     * Экранда кўрсатиш учун маскаланган токен: {@code 12345:AB●●●●}
     * (спец) - бот id очиқ (сир эмас, getMe'дан барибир маълум), сирнинг
     * фақат икки белгиси кўринади: SUPER_ADMIN «ўша токенми?» деб
     * таниб олади, лекин экран/скриншотдан токен тикланмайди.
     * null - созланмаган.
     */
    @Transactional(readOnly = true)
    public String maskedToken() {
        return token().map(TelegramService::mask).orElse(null);
    }

    /**
     * Токенни текшириб сақлайди (SUPER_ADMIN, /settings/telegram):
     * getMe муваффақиятли бўлсагина ёзилади, бот username'и ЎША
     * жавобдан олинади (карта тузоқ 3 - қўлда ёзилса линк бошқа ботга
     * олиб борарди). Аудитга фақат ФАКТ ёзилади.
     *
     * <p>Тартиб муҳим: калит текшируви getMe'дан ОЛДИН - шифрлаб
     * сақлай олмайдиган ҳолатда тармоққа чиқишнинг маъноси йўқ.
     *
     * @throws BusinessRuleException BR-TG-004 (шифрлаш калити йўқ),
     *         BR-TG-001 (токен бўш ёки getMe рад этди)
     */
    @Transactional
    public void saveToken(String rawToken) {
        String token = rawToken == null ? "" : rawToken.strip();
        if (token.isEmpty()) {
            throw new BusinessRuleException(BusinessRule.BR_TG_001, "Bot token киритилиши шарт");
        }
        if (!crypto.available()) {
            throw new BusinessRuleException(BusinessRule.BR_TG_004,
                    "Шифрлаш калити созланмаган (AVERPO_SECRET_KEY) - токен сақланмайди");
        }
        TelegramBotClient.BotInfo bot = client.getMe(token)
                .orElseThrow(() -> new BusinessRuleException(BusinessRule.BR_TG_001,
                        "Bot token нотўғри: Telegram уни қабул қилмади"));
        String encrypted = crypto.encrypt(token)
                .orElseThrow(() -> new BusinessRuleException(BusinessRule.BR_TG_004,
                        "Токенни шифрлаб бўлмади - сақланмади"));
        repository.findFirstBy().ifPresentOrElse(
                settings -> settings.changeToken(encrypted, bot.username()),
                () -> repository.save(new TelegramSettings(encrypted, bot.username())));
        // Аудит: ФАКТ + бот номи (сир эмас); токен/маскаси ЁЗИЛМАЙДИ
        auditLogService.record(AuditEventType.TELEGRAM_TOKEN_CHANGED,
                AuditLogService.currentUsername(), null, null,
                "Bot token янгиланди: @" + bot.username(), null);
        // Prod registrar (AFTER_COMMIT) webhook'ни янги токен билан қайта
        // рўйхатдан ўтказади - commit бўлмаса webhook ҳам йўқ (изчиллик)
        eventPublisher.publishEvent(new TelegramTokenChangedEvent(true));
    }

    /**
     * Токенни ўчиради (бот узилади, poller ухлайди). Уланган
     * фойдаланувчилар chat_id'си ва webhook сири ТЕГИЛМАЙДИ - токен
     * қайта киритилса канал ва сир тикланади (спец: маълумот
     * йўқолмайди). Токен йўқ бўлса жимгина ўтади - аудит шовқини бўлмасин.
     *
     * <p><b>deleteWebhook транзакция ИЧИДА</b> (арбитр эслатмаси
     * 2026-07-17 «мураккаблашса ичда, try/catch+WARN билан асосла»):
     * webhook'ни ўчириш учун ОЧИҚ токен керак - у фақат шу ерда мавжуд
     * (event'да сир ташилмайди). Транзакциядан ташқарига чиқариш учун
     * afterCommit синхронизацияси керак бўларди, лекин у @Transactional
     * тестда (rollback) ишламай, оқимни текширилмайдиган қиларди.
     * deleteWebhook getMe каби қисқа (10s connect) ва admin'нинг ноёб
     * амали - қисқа муддат уланма ушланиши мақбул; хато оқимни
     * тўхтатмайди (try/catch WARN, токенсиз).
     */
    @Transactional
    public void deleteToken() {
        repository.findFirstBy().filter(TelegramSettings::hasToken).ifPresent(settings -> {
            // Токен ЎЧИШДАН ОЛДИН Telegram'даги webhook'ни бекор қиламиз -
            // акс ҳолда prod'да «etim» webhook қолиб, токенсиз POST'лар
            // 401 олиб турарди (registrar эса токенсиз уни ўчира олмасди)
            crypto.decrypt(settings.getTokenEnc()).ifPresent(client::deleteWebhook);
            settings.clearToken();
            auditLogService.record(AuditEventType.TELEGRAM_TOKEN_CHANGED,
                    AuditLogService.currentUsername(), null, null,
                    "Bot token ўчирилди", null);
            eventPublisher.publishEvent(new TelegramTokenChangedEvent(false));
        });
    }

    /**
     * Жорий фойдаланувчи учун улаш кодини яратади ва deep link беради
     * (профил блоки). Эски код устидан ёзилади (тузоқ 4).
     *
     * @throws BusinessRuleException BR-TG-003 - бот созланмаган
     */
    @Transactional
    public LinkInfo startLink() {
        String botUsername = token().isPresent() ? botUsername() : null;
        if (botUsername == null) {
            throw new BusinessRuleException(BusinessRule.BR_TG_003,
                    "Telegram бот созланмаган - улаш коди олиб бўлмайди");
        }
        byte[] bytes = new byte[LINK_CODE_BYTES];
        RANDOM.nextBytes(bytes);
        // base64url: Telegram deep link `start` параметри айнан шу
        // алифбони (A-Z a-z 0-9 _ -) қабул қилади; padding'сиз
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        userService.setTelegramLinkCode(code, Instant.now().plus(LINK_TTL));
        return new LinkInfo(code, "https://t.me/" + botUsername + "?start=" + code);
    }

    /** Жорий фойдаланувчи Telegram'ни узади (профил блоки «узиш» тугмаси). */
    @Transactional
    public void unlink() {
        userService.unlinkOwnTelegram();
    }

    /**
     * Poller учун жорий ҳолат: плагин ёқиқ ВА токен ўқилса - очилган
     * токен + курсор; акс ҳолда бўш (poller ухлайди, тармоққа умуман
     * чиқмайди - ўчиқ плагинда трафик ЙЎҚ).
     */
    @Transactional(readOnly = true)
    public Optional<PollTarget> pollTarget() {
        if (!enabled()) {
            return Optional.empty();
        }
        return repository.findFirstBy()
                .flatMap(settings -> token().map(t -> new PollTarget(t, settings.getUpdateOffset())));
    }

    /**
     * Битта келган хабарни ишлайди (poller чақиради): ФАҚАТ
     * {@code /start <код>}; қолган ҳар қандай хабар ЖИМ ўтади (спец).
     *
     * <p>АТАЙЛАБ {@code @Transactional} ЭМАС (класс изоҳи): ичкарида
     * {@link UserService#completeTelegramLink} ЎЗ қисқа транзакциясини
     * очади - BR-TG-002 ўша транзакциядан чиқиб бу ерда тутилади. Агар
     * бу метод ҳам транзакцион бўлганда, ички proxy'дан чиққан хато
     * УМУМИЙ транзакцияни rollback-only қилиб қўярди (Spring
     * globalRollbackOnParticipationFailure) - партиядаги бошқа
     * хабарлар ва offset силжиши ҳам куярди. Тармоқ чақируви
     * (sendMessage) ҳам транзакция ичида қолмайди.
     */
    public void handleUpdate(String token, TelegramBotClient.Update update) {
        String code = parseStartCode(update.text());
        if (code == null) {
            return; // бегона хабар - бот жим (спец)
        }
        try {
            AppUser user = userService.completeTelegramLink(
                    code, update.chatId(), update.username());
            client.sendMessage(token, update.chatId(),
                    msg.getIn(BOT_LOCALE, "telegram.bot.linked", user.getDisplayName()));
            // Ким уланганини кузатиш мумкин бўлсин - лекин код/чат
            // маълумоти логга ёзилмайди (шахсий маълумот гигиенаси)
            log.info("Telegram улаш: {} профили уланди", user.getUsername());
        } catch (BusinessRuleException e) {
            // BR-TG-002 - фойдаланувчи хабарни ботда кўради; BR коди
            // (displayMessage) ботга чиқарилмайди - у ERP экрани тили
            client.sendMessage(token, update.chatId(),
                    msg.getIn(BOT_LOCALE, "telegram.bot.badCode"));
        }
    }

    /** getUpdates курсорини силжитади - poller ҳар партиядан кейин чақиради. */
    @Transactional
    public void advanceOffset(long nextOffset) {
        repository.findFirstBy().ifPresent(settings -> settings.advanceOffset(nextOffset));
    }

    // ---- Webhook (Arbitr-138, prod режим) ----

    /**
     * Webhook сирини таққослайди (endpoint ҳимояси) - Telegram ҳар
     * POST'да юборадиган {@code X-Telegram-Bot-Api-Secret-Token} header'и
     * сақланган (шифрланган) сир билан мос келадими.
     *
     * <p><b>constant-time</b> ({@link MessageDigest#isEqual}): оддий
     * {@code equals} таққос вақти мослик узунлигига боғлиқ бўлиб, сирни
     * байтма-байт тахмин қилиш (timing attack) йўлини очарди. header
     * null ёки сир йўқ (webhook рўйхатдан ўтмаган) - false: секретсиз
     * ҳеч ким кира олмайди.
     */
    @Transactional(readOnly = true)
    public boolean webhookSecretValid(String header) {
        if (header == null) {
            return false;
        }
        return webhookSecret()
                .map(secret -> MessageDigest.isEqual(
                        header.getBytes(StandardCharsets.UTF_8),
                        secret.getBytes(StandardCharsets.UTF_8)))
                .orElse(false);
    }

    /**
     * Webhook POST танасини ишлайди (endpoint - секрет ТЎҒРИ деб
     * тасдиқлангандан КЕЙИН чақирилади). Оқим {@link #handleUpdate}
     * билан айнан бир хил (polling ва webhook бир мантиқдан ўтади):
     * бузуқ тана - жим; токен йўқ (ўчирилган) - жим (жавоб юбориб
     * бўлмайди); акс ҳолда мавжуд ишлов. Транзакциясиз (handleUpdate
     * изоҳи - ички UserService ўз қисқа транзакциясини очади).
     */
    public void handleWebhookBody(String json) {
        Optional<TelegramBotClient.Update> update = client.parseWebhookUpdate(json);
        if (update.isEmpty()) {
            return;
        }
        Optional<String> token = token();
        if (token.isEmpty()) {
            return; // токен ўчган - жавоб юбориб бўлмайди, жим ўтамиз
        }
        handleUpdate(token.get(), update.get());
    }

    /**
     * Webhook'ни рўйхатдан ўтказади (prod registrar чақиради). Шартлар:
     * плагин ёқиқ ВА токен очилади ВА {@code AVERPO_PUBLIC_URL} бор.
     * Секрет: сақланган бўлса очилади, йўғи SecureRandom билан яратилиб
     * шифрланиб сақланади (барқарор - Telegram ва биз бир хил сирни
     * билишимиз шарт).
     *
     * <p>ТРАНЗАКЦИЯСИЗ (арбитр эслатмаси): {@code registerWebhook} тармоқ
     * чақируви DB уланмасини ушламасин. Секрет сақлаш repository орқали
     * ўз қисқа транзакциясини очади ({@link #resolveOrCreateSecret}).
     *
     * @return муваффақият (Telegram қабул қилди); шарт етишмаса/рад
     *         этилса false + факт лог (сирсиз)
     */
    public boolean registerWebhookIfReady() {
        if (!enabled()) {
            return false; // плагин ўчиқ - webhook керак эмас
        }
        TelegramSettings settings = repository.findFirstBy()
                .filter(TelegramSettings::hasToken).orElse(null);
        if (settings == null) {
            return false; // бот созланмаган
        }
        Optional<String> token = crypto.decrypt(settings.getTokenEnc());
        if (token.isEmpty()) {
            return false; // калит алмашган - токен ўқилмади
        }
        String base = normalizedPublicUrl();
        if (base == null) {
            log.error("AVERPO_PUBLIC_URL берилмаган - webhook рўйхатдан ўтмайди "
                    + "(бот POST қабул қилмайди; env қўшиб қайта ишга туширинг)");
            return false;
        }
        Optional<String> secret = resolveOrCreateSecret(settings);
        if (secret.isEmpty()) {
            log.error("Webhook сирини шифрлаб бўлмади (AVERPO_SECRET_KEY?) - "
                    + "webhook рўйхатдан ўтмади");
            return false;
        }
        boolean ok = client.registerWebhook(token.get(), base + WEBHOOK_PATH, secret.get());
        if (ok) {
            log.info("Telegram webhook рўйхатдан ўтди");
        } else {
            log.error("Telegram webhook рўйхатдан ўтмади (Telegram рад этди)");
        }
        return ok;
    }

    /**
     * Webhook рўйхатини бекор қилади (registrar: плагин ўчганда). Токен
     * очилса Telegram'дан webhook олинади; секрет базада ҚОЛАДИ - қайта
     * ёқилса ўша ишлатилади (токен нақши: маълумот ўчмайди).
     */
    public boolean removeWebhookRegistration() {
        return token().map(client::deleteWebhook).orElse(false);
    }

    // ---- ички ёрдамчилар ----

    /**
     * Очиқ токен: сақланган шифрланган ёзувдан. Ҳар чақиришда
     * очилади - очиқ қиймат МАЙДОНДА кэшланмайди (heap dump/дебаг
     * юзаси кичик қолсин).
     */
    private Optional<String> token() {
        return repository.findFirstBy()
                .map(TelegramSettings::getTokenEnc)
                .flatMap(crypto::decrypt);
    }

    /** Очиқ webhook сири: сақланган шифрланган ёзувдан (token() нақши). */
    private Optional<String> webhookSecret() {
        return repository.findFirstBy()
                .map(TelegramSettings::getWebhookSecretEnc)
                .flatMap(crypto::decrypt);
    }

    /**
     * Сақланган сирни очади ёки биринчи марта яратиб шифрлаб сақлайди.
     * detached entity'ни {@code repository.save} билан қайта ёзади
     * (registerWebhookIfReady транзакциясиз - тармоқ уланмани ушламасин;
     * save ўз қисқа транзакциясини очади). Registrar кам чақирилади -
     * параллель яратиш хавфи амалда йўқ (version optimistic lock гаров).
     *
     * @return очиқ сир ёки шифрлаш имконсиз бўлса empty
     */
    private Optional<String> resolveOrCreateSecret(TelegramSettings settings) {
        Optional<String> existing = crypto.decrypt(settings.getWebhookSecretEnc());
        if (existing.isPresent()) {
            return existing;
        }
        byte[] bytes = new byte[WEBHOOK_SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        // base64url: Telegram secret_token алифбоси (A-Za-z0-9_-) га мос
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Optional<String> encrypted = crypto.encrypt(secret);
        if (encrypted.isEmpty()) {
            return Optional.empty();
        }
        settings.changeWebhookSecret(encrypted.get());
        repository.save(settings);
        return Optional.of(secret);
    }

    /**
     * {@code AVERPO_PUBLIC_URL} нормаллаштирилган (охиридаги «/» олинган;
     * WEBHOOK_PATH ўзи «/» билан бошланади - қўш-слэш бўлмасин) ёки null
     * (берилмаган - webhook рўйхатдан ўтмайди).
     */
    private String normalizedPublicUrl() {
        if (publicUrl == null || publicUrl.isBlank()) {
            return null;
        }
        String trimmed = publicUrl.strip();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    /**
     * {@code 12345:AB●●●●} - икки нуқтагача бот id, кейин сирнинг икки
     * белгиси. Формат кутилмаган бўлса (икки нуқта йўқ/жуда калта)
     * умуман очмаймиз - тўлиқ маска.
     */
    private static String mask(String token) {
        int colon = token.indexOf(':');
        if (colon < 0 || token.length() < colon + 3) {
            return "●●●●";
        }
        return token.substring(0, colon + 3) + "●●●●";
    }

    /**
     * {@code /start <код>} дан кодни ажратади; бошқа матн (ёки кодсиз
     * яланғоч {@code /start}) - null (жим ўтади, спец қоидаси).
     */
    private static String parseStartCode(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.strip();
        if (!trimmed.startsWith(START_COMMAND)) {
            return null;
        }
        String code = trimmed.substring(START_COMMAND.length()).strip();
        return code.isEmpty() ? null : code;
    }
}
