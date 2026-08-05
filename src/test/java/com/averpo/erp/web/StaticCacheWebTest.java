package com.averpo.erp.web;

import com.averpo.erp.security.domain.UserRole;
import com.averpo.erp.testsupport.TestRoles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Arbitr-131 + 137: статикларга кэш ва asset версияси тестлари.
 * <ol>
 *   <li>барча статик гуруҳ 30 кун + immutable (137 дан кейин ўз
 *       css/js'имиз ҳам) - Security'нинг no-store'и статикларда ЙЎҚ;</li>
 *   <li>шаблонлардаги ҳар локал css/js линки {@code ?v=} билан
 *       (137: узун кэш фақат шу билан хавфсиз);</li>
 *   <li>HTML/динамик саҳифаларда no-store САҚЛАНАДИ - молия маълумоти
 *       браузер кэшига тушмайди (131 карта 2-банд: бу ўзгармаслиги
 *       шарт);</li>
 *   <li>Last-Modified revalidation ишлайди (304) - браузер сўраса
 *       арзон жавоб (immutable сўрашни камайтиради, тақиқламайди);</li>
 *   <li>йўқ статикнинг аноним 404 оқими (021/127) бузилмаган ва 404
 *       жавоби ўзи кэшланмайди.</li>
 * </ol>
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StaticCacheWebTest {

    @Autowired WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    // ---- 1. Узун гуруҳ: шрифт, вендор, favicon ----

    @Test
    void font_anonymous_longCacheImmutable_noStoreYoq() throws Exception {
        // Аноним - login саҳифаси ҳам шрифт ишлатади (permitAll ўзгармаган)
        mockMvc.perform(get("/fonts/inter-cyrillic-400-normal.woff2"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", allOf(
                        containsString("max-age=2592000"),
                        containsString("immutable"),
                        not(containsString("no-store")))));
    }

    @Test
    void vendorHtmx_longCache() throws Exception {
        mockMvc.perform(get("/vendor/htmx.min.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", allOf(
                        containsString("max-age=2592000"),
                        containsString("immutable"))));
    }

    @Test
    void jsVendor_longCache() throws Exception {
        // /js/vendor/** /js/** ичида, лекин ўз handler'и бор - вендор
        // файллари /js/** сиёсатидан қатъи назар узун кэшда қолади
        mockMvc.perform(get("/js/vendor/alpine.min.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", allOf(
                        containsString("max-age=2592000"),
                        containsString("immutable"))));
    }

    @Test
    void favicon_longCache() throws Exception {
        mockMvc.perform(get("/favicon.svg"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control",
                        containsString("max-age=2592000")));
    }

    // ---- 2. Ўз ресурсларимиз: 137 дан кейин ҳам узун кэш ----

    @Test
    void appCss_uzunKesh_noStoreYoq() throws Exception {
        // 137: ?v= бор - янги build янги URL беради, шунга 1 соатлик
        // муросага ҳожат йўқ
        mockMvc.perform(get("/css/app.css"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", allOf(
                        containsString("max-age=2592000"),
                        containsString("immutable"),
                        not(containsString("no-store")))));
    }

    @Test
    void ownJs_uzunKesh() throws Exception {
        mockMvc.perform(get("/js/money-input.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", allOf(
                        containsString("max-age=2592000"),
                        containsString("immutable"))));
    }

    @Test
    void appCss_lastModifiedRevalidation_304() throws Exception {
        // Биринчи жавобдаги Last-Modified билан шартли сўров 304 бериши
        // шарт: immutable браузерни сўрашдан ТЎХТАТАДИ, лекин сўраса
        // (масалан қаттиқ reload) жавоб арзон бўлиб қолаверади
        MvcResult first = mockMvc.perform(get("/css/app.css"))
                .andExpect(status().isOk())
                .andReturn();
        String lastModified = first.getResponse().getHeader("Last-Modified");
        assertThat(lastModified).isNotBlank();
        mockMvc.perform(get("/css/app.css")
                        .header("If-Modified-Since", lastModified))
                .andExpect(status().isNotModified());
    }

    // ---- 2б. Шаблонлардаги ?v= (Arbitr-137) ----

    /**
     * Саҳифадаги локал css/js референслари. Шрифт preload'лари
     * (/fonts/...) ва favicon атайлаб қамровда эмас - улар
     * бустланмайди (файллари ўзгармайди).
     */
    private static final Pattern LOKAL_ASSET =
            Pattern.compile("(?:href|src)=\"(/(?:css|js|vendor)/[^\"]*)\"");

    @Test
    void assetVersiyasi_raqamli_fallbackBilanHam() {
        // Тест контекстида BuildProperties bean ЙЎҚ (bootBuildInfo
        // build-info.properties'ни фақат bootJar/bootRun classpath'ига
        // қўшади) - демак бу айни fallback (илова старт вақти) йўли
        assertThat(Assets.version()).matches("\\d+");
    }

    @Test
    void loginSahifasi_assetlariVersiyali() throws Exception {
        assertHarAssetVersiyali(mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void mainLayout_assetlariVersiyali() throws Exception {
        String html = mockMvc.perform(get("/journal-entries")
                        .with(TestRoles.as("admin", UserRole.SUPER_ADMIN)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // Учала оила ҳам: ўз css'имиз, вендор js, ўз js'имиз
        assertThat(html).contains("/css/app.css?v=" + Assets.version());
        assertThat(html).contains("/vendor/htmx.min.js?v=" + Assets.version());
        assertThat(html).contains("/js/vendor/alpine.min.js?v=" + Assets.version());
        assertThat(html).contains("/js/money-input.js?v=" + Assets.version());
        assertHarAssetVersiyali(html);
    }

    /**
     * Карта 1-мезони тест шаклида: саҳифада версиясиз қолган локал
     * css/js референси БЎЛМАСЛИГИ шарт - акс ҳолда 30 кунлик кэшдаги
     * эски нусха deploy'дан кейин ҳам ишлатиларди.
     */
    private void assertHarAssetVersiyali(String html) {
        Matcher matcher = LOKAL_ASSET.matcher(html);
        int topildi = 0;
        while (matcher.find()) {
            topildi++;
            assertThat(matcher.group(1)).contains("?v=" + Assets.version());
        }
        // Нақш умуман тутмаса тест сохта яшил бўлиб қолмасин
        assertThat(topildi).isPositive();
    }

    // ---- 3. HTML/динамик саҳифаларда no-store ҚОЛАДИ ----

    @Test
    void loginSahifa_noStoreSaqlangan() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control",
                        containsString("no-store")));
    }

    @Test
    void dinamikSahifa_noStoreSaqlangan() throws Exception {
        // Молия маълумотли рўйхат саҳифаси - браузер кэшига тушмаслиги шарт
        mockMvc.perform(get("/journal-entries")
                        .with(TestRoles.as("admin", UserRole.SUPER_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control",
                        containsString("no-store")));
    }

    // ---- 4. Йўқ статик: аноним 404 оқими бузилмаган ----

    @Test
    void yoqStatik_anonim404_vaJavobKeshlanmaydi() throws Exception {
        // 021/127 оқими: NoResourceFound → 404 саҳифа; error жавобининг
        // ўзи кэшланмайди (handler Cache-Control қўймайди - Security
        // no-store'и қайтади)
        mockMvc.perform(get("/js/yoq-fayl-131.js"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control",
                        containsString("no-store")));
    }
}
