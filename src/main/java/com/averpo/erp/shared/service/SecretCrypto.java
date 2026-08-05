package com.averpo.erp.shared.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Сирларни базада шифрланган сақлаш примитиви (AES-GCM 256) - арбитр
 * қарори 2026-07-17 (Arbitr-103): bearer креденшл (Telegram bot token)
 * базада ОЧИҚ ётмаслиги шарт, чунки база dump/захираси (масалан
 * миграция олди backup'и) уни ошкор қиларди. Калит базада ЭМАС -
 * {@code AVERPO_SECRET_KEY} env'да, шунда захира ва калит АЛОҲИДА
 * жойда бўлади (иккови бирга сизмаса токен хавфсиз).
 *
 * <p>Нега {@code shared}: генерик примитив, ҳеч бир модулга боғлиқ эмас -
 * security'га қўйилса бошқа модул ишлата олмас эди (модуллар security'ни
 * import қилмайди). Нега AES-GCM: authenticated encryption - бузилган/
 * алмаштирилган матн жимгина «бошқа қиймат» бўлиб чиқмайди, tag
 * текшируви йиқилади.
 *
 * <p>Нега {@link Optional} қайтарилади (exception ЭМАС): бу примитив
 * BR каталогини БИЛМАЙДИ (shared, ҳар кимга хизмат қилади) - калит йўқ
 * бўлса чақирувчи ЎЗ BR коди билан хато отади (TelegramService →
 * BR-TG-004). Темир қоида 13: service'ларда IllegalState ТАҚИҚ.
 */
@Component
@Slf4j
public class SecretCrypto {

    /** GCM учун стандарт IV узунлиги (12 байт - NIST тавсияси). */
    private static final int IV_BYTES = 12;

    /** GCM authentication tag узунлиги (бит). */
    private static final int TAG_BITS = 128;

    /** AES-256 калит узунлиги (байт). */
    private static final int KEY_BYTES = 32;

    /**
     * Dev/test муҳитда env берилмаганда ишлатиладиган калит (base64,
     * 32 байт). {@link com.averpo.erp.security.config.AdminUserInitializer}
     * DEV_DEFAULT_PASSWORD нақши АЙНАН: аниқ dev белгиси бўлмаса бу калит
     * ИШЛАМАЙДИ - номаълум/профилсиз муҳит доим production деб қаралади
     * (fail-safe). Бу калит ошкор - у билан шифрланган маълумот сир
     * ҳисобланмайди, шунинг учун фақат dev/test базасида.
     */
    static final String DEV_KEY = "ZGV2LW9ubHkta2V5LTMyLWJ5dGVzLWF2ZXJwbyEhISE=";

    /** Ҳар шифрлашга янги IV - GCM'да IV такрори калитни очиб қўяди. */
    private final SecureRandom random = new SecureRandom();

    /** Профиль текшируви - dev белгисисиз DEV калит йўқ. */
    private final Environment environment;

    /**
     * Шифрлаш калити (base64, 32 байт) - production'да МАЖБУРИЙ. Бўш
     * default: «берилмаган»ни аниқлаш учун (AdminUserInitializer нақши).
     */
    @Value("${AVERPO_SECRET_KEY:}")
    private String configuredKey;

    /** Environment конструкторда - @Value майдонлари кейин тўлади. */
    public SecretCrypto(Environment environment) {
        this.environment = environment;
    }

    /**
     * Шифрлаш имкони борми - калит созланганми (ёки dev муҳитми).
     * Чақирувчи буни ОЛДИН текшириб ўз BR хабарини беради (сақлаш
     * формасида - BR-TG-004).
     */
    public boolean available() {
        return key().isPresent();
    }

    /**
     * Матнни шифрлайди: {@code base64(IV || ciphertext+tag)}. Калит йўқ
     * бўлса {@link Optional#empty()} - чақирувчи BR отади (сир ҳеч қачон
     * очиқ сақланмайди: шифрлаб бўлмаса ЁЗМАЙМИЗ).
     */
    public Optional<String> encrypt(String plaintext) {
        if (plaintext == null) {
            return Optional.empty();
        }
        Optional<SecretKeySpec> key = key();
        if (key.isEmpty()) {
            return Optional.empty();
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key.get(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(ciphertext, 0, packed, iv.length, ciphertext.length);
            return Optional.of(Base64.getEncoder().encodeToString(packed));
        } catch (Exception e) {
            // JCE конфигурация хатоси - хабарда СИР йўқ (фақат тур/сабаб)
            log.error("Шифрлаш амалга ошмади: {}", e.toString());
            return Optional.empty();
        }
    }

    /**
     * Шифрланган матнни очади. Калит созланмаган/АЛМАШГАН ёки ёзув
     * бузилган бўлса {@link Optional#empty()} + WARN - чақирувчи учун бу
     * «сир йўқ» дегани (Telegram: бот созланмаган ҳолатга тушади,
     * SUPER_ADMIN қайта киритади). Тизим ЙИҚИЛМАЙДИ: калит алмашуви
     * иловани ишга тушмайдиган қилиб қўймаслиги керак.
     */
    public Optional<String> decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return Optional.empty();
        }
        Optional<SecretKeySpec> key = key();
        if (key.isEmpty()) {
            return Optional.empty();
        }
        try {
            byte[] packed = Base64.getDecoder().decode(stored);
            if (packed.length <= IV_BYTES) {
                log.warn("Шифрланган ёзув жуда калта - бузуқ деб қаралди");
                return Optional.empty();
            }
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(packed, 0, iv, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key.get(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(packed, IV_BYTES, packed.length - IV_BYTES);
            return Optional.of(new String(plain, StandardCharsets.UTF_8));
        } catch (Exception e) {
            // AEADBadTagException (калит алмашган/ёзув бузилган), базавий
            // формат хатоси ва ҳ.к. Хабарда СИР ва ёзувнинг ЎЗИ йўқ.
            log.warn("Шифрланган ёзувни очиб бўлмади (калит алмашганми?): {}",
                    e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * Жорий калит: env'даги base64 (32 байт) ёки dev/test муҳитда
     * {@link #DEV_KEY}. Нотўғри узунлик/формат - калит ЙЎҚ ҳисобланади
     * (ERROR): жимгина «ярим ишлаган» шифрлашдан кўра очиқ рад яхши.
     */
    private Optional<SecretKeySpec> key() {
        String raw = configuredKey;
        if (raw == null || raw.isBlank()) {
            if (!devEnvironment()) {
                return Optional.empty();
            }
            raw = DEV_KEY;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(raw.strip());
            if (bytes.length != KEY_BYTES) {
                log.error("AVERPO_SECRET_KEY нотўғри: base64 очилганда {} байт "
                        + "(кутилган {}) - сир шифрланмайди", bytes.length, KEY_BYTES);
                return Optional.empty();
            }
            return Optional.of(new SecretKeySpec(bytes, "AES"));
        } catch (IllegalArgumentException e) {
            log.error("AVERPO_SECRET_KEY base64 форматида эмас - сир шифрланмайди");
            return Optional.empty();
        }
    }

    /**
     * Муҳит аниқ dev деб белгиланганми (dev/test профили) -
     * AdminUserInitializer'даги ўша fail-safe мантиқ: рўйхат «оқ»,
     * номаълум/профилсиз муҳит доим production.
     */
    private boolean devEnvironment() {
        return environment.acceptsProfiles(Profiles.of("dev", "test"));
    }
}
