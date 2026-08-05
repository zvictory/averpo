package com.averpo.erp.audit.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UA парс ва IPv6 қисқартириш unit тестлари (Arbitr-091, карта «Тест
 * кутилмаси»: камида 6 реал UA намунаси). Ҳамма намуна ҳақиқий браузер
 * қаторлари - regex/contains занжири реал форматларга мослигини
 * гаровлайди: Chrome-асосли тартиб (Edge Chrome'дан олдин), Android
 * UA'даги «Linux» тузоғи, iPhone UA'даги «like Mac OS X» тузоғи, Safari
 * версияси «Version/» токенида экани.
 */
class AuditClientParseTest {

    /** Windows 10/11 десктоп Chrome 126 (UA reduction: minor доим 0). */
    private static final String WIN_CHROME =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    /** macOS десктоп Safari 17 - версия Version/ токенида. */
    private static final String MAC_SAFARI =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 "
                    + "(KHTML, like Gecko) Version/17.4.1 Safari/605.1.15";

    /** Android мобил Chrome - UA'да «Linux» ҳам бор (тартиб тузоғи). */
    private static final String ANDROID_CHROME =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";

    /** iPhone мобил Safari - UA'да «like Mac OS X» ҳам бор (тартиб тузоғи). */
    private static final String IOS_SAFARI =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) "
                    + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 "
                    + "Mobile/15E148 Safari/604.1";

    /** Linux десктоп Firefox. */
    private static final String LINUX_FIREFOX =
            "Mozilla/5.0 (X11; Linux x86_64; rv:127.0) Gecko/20100101 Firefox/127.0";

    /** CLI восита - браузер ҳам, OS ҳам эмас. */
    private static final String CURL = "curl/8.5.0";

    /** Windows десктоп Edge - UA'да «Chrome/» ҳам бор (тартиб тузоғи). */
    private static final String WIN_EDGE =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.2592.87";

    /** Браузер номи + major версия - олти реал UA бўйича. */
    @Test
    void clientShort_realUserAgents_nameWithMajorVersion() {
        assertThat(AuditLogController.clientShort(WIN_CHROME)).isEqualTo("Chrome 126");
        assertThat(AuditLogController.clientShort(MAC_SAFARI)).isEqualTo("Safari 17");
        assertThat(AuditLogController.clientShort(ANDROID_CHROME)).isEqualTo("Chrome 126");
        assertThat(AuditLogController.clientShort(IOS_SAFARI)).isEqualTo("Safari 17");
        assertThat(AuditLogController.clientShort(LINUX_FIREFOX)).isEqualTo("Firefox 127");
        // Нотаниш UA - биринчи токен ўзгаришсиз (версия ажратилмайди)
        assertThat(AuditLogController.clientShort(CURL)).isEqualTo("curl/8.5.0");
        // Edge Chrome'дан ОЛДИН текширилади - акс ҳолда «Chrome 126» чиқарди
        assertThat(AuditLogController.clientShort(WIN_EDGE)).isEqualTo("Edge 126");
        assertThat(AuditLogController.clientShort(null)).isNull();
    }

    /** OS аниқлаш - тартиб тузоқлари билан (Android/Linux, iOS/macOS). */
    @Test
    void osShort_realUserAgents_detectsOs() {
        assertThat(AuditLogController.osShort(WIN_CHROME)).isEqualTo("Windows");
        assertThat(AuditLogController.osShort(MAC_SAFARI)).isEqualTo("macOS");
        // Android UA'да «Linux» ҳам бор - Android ютади
        assertThat(AuditLogController.osShort(ANDROID_CHROME)).isEqualTo("Android");
        // iPhone UA'да «like Mac OS X» ҳам бор - iOS ютади
        assertThat(AuditLogController.osShort(IOS_SAFARI)).isEqualTo("iOS");
        assertThat(AuditLogController.osShort(LINUX_FIREFOX)).isEqualTo("Linux");
        // CLI UA'да OS йўқ - null (қурилма тури ҳам чиқмайди)
        assertThat(AuditLogController.osShort(CURL)).isNull();
        assertThat(AuditLogController.osShort(null)).isNull();
    }

    /** Қурилма тури: Mobile белгиси бор - мобил, йўқ - десктоп. */
    @Test
    void deviceKey_mobileMarker() {
        assertThat(AuditLogController.deviceKey(WIN_CHROME))
                .isEqualTo("audit.device.desktop");
        assertThat(AuditLogController.deviceKey(ANDROID_CHROME))
                .isEqualTo("audit.device.mobile");
        assertThat(AuditLogController.deviceKey(IOS_SAFARI))
                .isEqualTo("audit.device.mobile");
        assertThat(AuditLogController.deviceKey(LINUX_FIREFOX))
                .isEqualTo("audit.device.desktop");
    }

    /** IPv6 қисқартириш: биринчи группа + … + охирги 4 белги. */
    @Test
    void shortV6_abbreviatesLongAddress_keepsShortAsIs() {
        assertThat(AuditLogController.shortV6("2a05:45c2:1010:3400:c0ff:eeba:d015:40c1"))
                .isEqualTo("2a05:…40c1");
        // Қисқа манзилда қисқартириш фойда бермайди - ўзгаришсиз
        assertThat(AuditLogController.shortV6("::1")).isEqualTo("::1");
        assertThat(AuditLogController.shortV6("fe80::1")).isEqualTo("fe80::1");
    }
}
