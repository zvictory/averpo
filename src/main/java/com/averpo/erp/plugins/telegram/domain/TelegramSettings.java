package com.averpo.erp.plugins.telegram.domain;

import com.averpo.erp.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Telegram бот созламаси - singleton қатор (CompanySettings нақши:
 * биринчи мурожаатда lazy яратилади, seed changeset'сиз).
 *
 * <p>Биз бир-tenant: ҳар компания/deployment ЎЗ ботини яратади
 * (@BotFather) - умумий платформа боти ЙЎҚ (user-profile.md 3-бўлим,
 * фойдаланувчи қарори 2026-07-12).
 *
 * <p><b>Токен сири:</b> {@link #tokenEnc} - ШИФРЛАНГАН матн (AES-GCM,
 * {@code SecretCrypto}); очиқ токен бу entity'да ҲЕЧ ҚАЧОН турмайди -
 * TelegramService уни фақат Bot API чақируви пайтида очади. Lombok
 * {@code @ToString} бу класда ҲАМ тақиқ (BaseEntity қоидаси) - токен
 * тасодифан логга тушмасин (logging.md).
 *
 * <p>ЁҚИЛИШ ҲОЛАТИ бу ерда ЭМАС - {@code plugin_state}'да (Arbitr-113):
 * токен сақланиб қолади, плагин ўчирилса фақат яширинади ва poller
 * тўхтайди (plugins.md: «маълумот ЎЧМАЙДИ»).
 *
 * @author Zafar
 */
@Entity
@Table(name = "telegram_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TelegramSettings extends BaseEntity {

    /**
     * Шифрланган bot token: {@code base64(IV||ciphertext)} ёки null -
     * бот созланмаган. Очиқ ҳолда ҳеч қаерда сақланмайди/логланмайди
     * (арбитр қарори 2026-07-17 - база захираси токенни очмасин).
     */
    @Column(name = "token_enc", columnDefinition = "text")
    private String tokenEnc;

    /**
     * Бот username'и (@'сиз) - deep link учун. ҚЎЛДА ЭМАС, getMe
     * жавобидан олинади (карта тузоқ 3: фойдаланувчи хато ёзса линк
     * бошқа ботга олиб борарди).
     */
    @Column(name = "bot_username", length = 64)
    private String botUsername;

    /**
     * getUpdates курсори - ишланган охирги update_id + 1. Рестартдан
     * кейин poller ўша жойдан давом этади (эски хабарлар қайта
     * ишланмайди, тузоқ 1).
     */
    @Column(name = "update_offset", nullable = false)
    private long updateOffset;

    /**
     * Webhook сирининг ШИФРЛАНГАН қиймати (Arbitr-138, changeset 068):
     * prod webhook режимда Telegram ҳар POST'да юборадиган
     * {@code X-Telegram-Bot-Api-Secret-Token} шу билан таққосланади.
     * token_enc билан бир хил ҳимоя (AES-GCM); очиқ секрет entity'да
     * ҲЕЧ ҚАЧОН турмайди. null - webhook ҳали рўйхатдан ўтмаган (дев
     * polling ёки токенсиз prod).
     */
    @Column(name = "webhook_secret_enc", columnDefinition = "text")
    private String webhookSecretEnc;

    /**
     * Биринчи токен сақланганда яратилади (CompanySettings нақшидан
     * фарқли - бўш қатор умуман керак эмас: токенсиз ҳолат = қатор йўқ).
     * Курсор 0 дан бошланади - янги ботда эски update'лар йўқ.
     */
    public TelegramSettings(String tokenEnc, String botUsername) {
        this.tokenEnc = tokenEnc;
        this.botUsername = botUsername;
    }

    /**
     * Токенни (шифрланган ҳолда) ва getMe берган бот номини ёзади -
     * иккови доим БИРГА алмашади: янги токен = бошқа бот бўлиши мумкин,
     * эски username линкда қолиб кетмасин.
     */
    public void changeToken(String tokenEnc, String botUsername) {
        this.tokenEnc = tokenEnc;
        this.botUsername = botUsername;
    }

    /**
     * Токенни ўчиради (бот узилади): username ҳам тозаланади - профил
     * блоки «созланмаган» ҳолатга тушади, poller ухлайди. Уланган
     * фойдаланувчиларнинг chat_id'си ТЕГИЛМАЙДИ - токен қайта
     * киритилса улар уланганича қолади.
     */
    public void clearToken() {
        this.tokenEnc = null;
        this.botUsername = null;
    }

    /** getUpdates курсорини силжитади - poller ҳар партиядан кейин чақиради. */
    public void advanceOffset(long nextOffset) {
        this.updateOffset = nextOffset;
    }

    /**
     * Webhook сирини (шифрланган ҳолда) ёзади - registrar SecureRandom
     * билан бир марта яратиб сақлайди, кейинги старт'ларда ўша ишлатилади
     * (барқарор: Telegram ва биз бир хил сирни билишимиз шарт).
     */
    public void changeWebhookSecret(String webhookSecretEnc) {
        this.webhookSecretEnc = webhookSecretEnc;
    }

    /** Бот созланганми - токен бор (шифрланган ёзув мавжуд). */
    public boolean hasToken() {
        return tokenEnc != null && !tokenEnc.isBlank();
    }
}
