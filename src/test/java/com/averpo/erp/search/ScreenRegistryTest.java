package com.averpo.erp.search;

import com.averpo.erp.search.service.ScreenRegistry;
import com.averpo.erp.search.service.SearchHit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Экран реестри тестлари (docs/modules/global-search.md «Тестлар» 5):
 * жорий тил бўйича сарлавҳа филтри ва роль филтри (VIEWER'га Settings
 * экранлари чиқмайди).
 */
@SpringBootTest
@ActiveProfiles("test")
class ScreenRegistryTest {

    @Autowired ScreenRegistry registry;

    @BeforeEach
    void uzLocale() {
        LocaleContextHolder.setLocale(Locale.of("uz"));
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    /** Ҳисобот жорий тилдаги номи бўйича топилади («баланс» → Balance Sheet). */
    @Test
    void findsReportByCurrentLanguageName() {
        List<SearchHit> hits = registry.search("баланс", true);
        assertThat(hits).anySatisfy(hit ->
                assertThat(hit.url()).isEqualTo("/reports/balance-sheet"));
    }

    /** Тест 5: Созламалар экрани VIEWER/ACCOUNTANT'дан яширин, ADMIN'га чиқади. */
    @Test
    void settingsHiddenFromNonAdmin() {
        // isAdmin=false: «созлам» ҳеч нарса қайтармайди (SETTINGS - adminOnly)
        assertThat(registry.search("созлам", false)).isEmpty();
        // isAdmin=true: Созламалар экрани чиқади
        assertThat(registry.search("созлам", true))
                .anySatisfy(hit -> assertThat(hit.url()).isEqualTo("/settings"));
    }

    /** Кенг сўровда ҳам кўпи билан 5 та (гуруҳ лимити). */
    @Test
    void limitedToFive() {
        assertThat(registry.search("а", true).size()).isLessThanOrEqualTo(5);
    }
}
