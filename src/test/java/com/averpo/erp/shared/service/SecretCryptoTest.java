package com.averpo.erp.shared.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Сир шифрлаш примитиви тести (DEC-103, лойиҳа қарори:
 * токен базада очиқ ётмайди). Spring контексти йўқ - соф unit тест
 * (CbuRestClientTest нақши): муҳит {@link StandardEnvironment} билан
 * қўлда қурилади, шунда «production'да калит йўқ» ҳолатини ҳам
 * текшириб бўлади.
 */
class SecretCryptoTest {

    /** Намунавий сир - реал токен ФОРМАТИ (қиймати сохта). */
    private static final String SECRET = "123456789:AAFakeTokenForTestOnly-not-real";

    /** Dev/test белгили муҳит - DEV_KEY ишлайди (AdminUserInitializer нақши). */
    private static SecretCrypto devCrypto() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("test");
        return new SecretCrypto(environment);
    }

    /** Профилсиз муҳит - «номаълум муҳит = production» (fail-safe). */
    private static SecretCrypto prodCrypto() {
        return new SecretCrypto(new StandardEnvironment());
    }

    /** Берилган base64 калит билан (env қиймати - @Value майдони). */
    private static SecretCrypto cryptoWithKey(String base64Key) {
        SecretCrypto crypto = prodCrypto();
        ReflectionTestUtils.setField(crypto, "configuredKey", base64Key);
        return crypto;
    }

    /** 32 байтли тасодифий калит (base64) - «бошқа калит» ҳолати учун. */
    private static String randomKey(byte fill) {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, fill);
        return Base64.getEncoder().encodeToString(key);
    }

    /** Dev муҳитда: шифрлаб-очиш айнан ўша матнни қайтаради. */
    @Test
    void encryptDecrypt_roundTrips_inDevProfile() {
        SecretCrypto crypto = devCrypto();
        assertThat(crypto.available()).isTrue();

        String encrypted = crypto.encrypt(SECRET).orElseThrow();

        // Шифрланган ёзувда очиқ сир ЙЎҚ - база dump'и уни кўрсатмайди
        assertThat(encrypted).doesNotContain(SECRET).doesNotContain("AAFakeToken");
        assertThat(crypto.decrypt(encrypted)).contains(SECRET);
    }

    /**
     * Ҳар шифрлашда IV янги - бир хил матн ҲАР САФАР бошқа ёзув беради
     * (GCM'да IV такрори калитни очиб қўяди; бир хил ciphertext эса
     * «токен ўзгармаган»лигини ошкор қиларди).
     */
    @Test
    void encrypt_usesFreshIv_sameInputDifferentOutput() {
        SecretCrypto crypto = devCrypto();

        String first = crypto.encrypt(SECRET).orElseThrow();
        String second = crypto.encrypt(SECRET).orElseThrow();

        assertThat(first).isNotEqualTo(second);
        // Иккови ҳам ўша сирни беради - фарқ фақат IV'да
        assertThat(crypto.decrypt(first)).contains(SECRET);
        assertThat(crypto.decrypt(second)).contains(SECRET);
    }

    /**
     * Production'да (dev белгиси йўқ) калит берилмаса - шифрлаш ЙЎҚ:
     * чақирувчи BR-TG-004 отади, сир ҳеч қачон очиқ сақланмайди.
     */
    @Test
    void noKeyInProduction_encryptRefused() {
        SecretCrypto crypto = prodCrypto();

        assertThat(crypto.available()).isFalse();
        assertThat(crypto.encrypt(SECRET)).isEmpty();
        assertThat(crypto.decrypt("nimadir")).isEmpty();
    }

    /** Env калити билан (production қолипи): round-trip ишлайди. */
    @Test
    void configuredEnvKey_roundTrips() {
        SecretCrypto crypto = cryptoWithKey(randomKey((byte) 7));

        String encrypted = crypto.encrypt(SECRET).orElseThrow();

        assertThat(crypto.available()).isTrue();
        assertThat(crypto.decrypt(encrypted)).contains(SECRET);
    }

    /**
     * Калит АЛМАШСА эски ёзув очилмайди - бўш Optional (сир «йўқ»
     * бўлади: бот созланмаган ҳолатга тушади, тизим йиқилмайди).
     */
    @Test
    void rotatedKey_oldRecordUnreadable_noCrash() {
        String encrypted = cryptoWithKey(randomKey((byte) 7)).encrypt(SECRET).orElseThrow();

        Optional<String> decrypted = cryptoWithKey(randomKey((byte) 9)).decrypt(encrypted);

        assertThat(decrypted).isEmpty();
    }

    /**
     * Бузилган/алмаштирилган ёзув GCM tag текширувида йиқилади -
     * жимгина «бошқа қиймат» бўлиб чиқмайди (authenticated encryption).
     */
    @Test
    void tamperedRecord_rejected() {
        SecretCrypto crypto = devCrypto();
        byte[] packed = Base64.getDecoder().decode(crypto.encrypt(SECRET).orElseThrow());
        packed[packed.length - 1] ^= 0x01; // охирги байтни ўзгартирамиз

        assertThat(crypto.decrypt(Base64.getEncoder().encodeToString(packed))).isEmpty();
    }

    /** Нотўғри узунликдаги калит (16 байт) - шифрлаш йўқ, жим ишламайди. */
    @Test
    void wrongLengthKey_refused() {
        SecretCrypto crypto = cryptoWithKey(
                Base64.getEncoder().encodeToString(new byte[16]));

        assertThat(crypto.available()).isFalse();
        assertThat(crypto.encrypt(SECRET)).isEmpty();
    }

    /** Бузуқ base64 калит - шифрлаш йўқ (boot йиқилмайди). */
    @Test
    void malformedKey_refused() {
        SecretCrypto crypto = cryptoWithKey("bu base64 emas!!!");

        assertThat(crypto.available()).isFalse();
        assertThat(crypto.encrypt(SECRET)).isEmpty();
    }

    /** DEV калит айнан 32 байт - AES-256 талаби (константа тузоғи). */
    @Test
    void devKey_is32Bytes() {
        assertThat(Base64.getDecoder().decode(SecretCrypto.DEV_KEY)).hasSize(32);
    }
}
