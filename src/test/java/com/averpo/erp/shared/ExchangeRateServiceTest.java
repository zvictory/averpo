package com.averpo.erp.shared;

import com.averpo.erp.shared.domain.Currency;
import com.averpo.erp.shared.domain.ExchangeRate;
import com.averpo.erp.shared.domain.RateSource;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.testsupport.SqlCaptureInspector;
import com.averpo.erp.shared.service.CbuRateClient;
import com.averpo.erp.shared.service.CbuRateClient.CbuRate;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.service.CurrencyService;
import com.averpo.erp.shared.service.ExchangeRateScheduler;
import com.averpo.erp.shared.service.ExchangeRateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * Валюта курслари каталоги тестлари: docs/modules/multi-currency.md →
 * «Тестлар». ЦБ client сохта - тармоққа чиқилмайди.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExchangeRateServiceTest {

    /** Асосий тест санаси (жума). */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 3);

    @Autowired ExchangeRateService service;
    @Autowired ExchangeRateScheduler scheduler;
    @Autowired CurrencyService currencyService;
    @Autowired CompanySettingsService settingsService;

    /** ЦБ порти сохталанади - ҳақиқий CbuRestClient тармоққа чиқарди. */
    @MockitoBean CbuRateClient cbuRateClient;

    @Test
    void record_sameDateDifferentRates_appendsHistory_latestWins() {
        // Append-only тарих (Arbitr-022): устига ёзилмайди
        service.upsert("USD", DATE, new BigDecimal("12600"));
        service.upsert("USD", DATE, new BigDecimal("12650"));

        // Иккала ёзув тарихда, амалдаги курс = энг охиргиси
        assertThat(service.history("USD")).hasSize(2);
        assertThat(service.rateFor("USD", DATE)).contains(new BigDecimal("12650"));

        // Айнан бир хил курс такрор - дубль йўқ (фақат ўзгаришлар сақланади)
        service.upsert("USD", DATE, new BigDecimal("12650"));
        assertThat(service.history("USD")).hasSize(2);
    }

    @Test
    void source_cbuImportThenManual_bothInHistoryWithSource() {
        given(cbuRateClient.rates(DATE)).willReturn(List.of(
                new CbuRate("USD", new BigDecimal("12600"))));
        service.importFromCbu(DATE);                          // ЦБ ёзуви
        service.upsert("USD", DATE, new BigDecimal("12700")); // қўлда ўзгартириш

        List<ExchangeRate> hist = service.history("USD");
        assertThat(hist).hasSize(2);
        // Энг охиргиси (топда) - қўлда MANUAL; амалдаги курс ҳам шу
        assertThat(hist.get(0).getSource()).isEqualTo(RateSource.MANUAL);
        assertThat(hist.get(0).getRate()).isEqualByComparingTo("12700");
        assertThat(service.rateFor("USD", DATE)).contains(new BigDecimal("12700"));
        // Эскиси - ЦБ, тарихда қолди
        assertThat(hist.get(1).getSource()).isEqualTo(RateSource.CBU);
    }

    @Test
    void latestForEachCurrency_singleQuery_newestPerCurrency() {
        // Beruniy-023: Currencies экрани N+1 ўрнига битта window сўрови.
        // EUR seed'да нофаол - аввал фаоллаштирилади (upsert талаби)
        currencyService.setActive("EUR", true);
        service.upsert("USD", DATE, new BigDecimal("12600"));
        service.upsert("USD", DATE.plusDays(1), new BigDecimal("12650"));
        service.upsert("EUR", DATE.minusDays(1), new BigDecimal("14700"));
        service.upsert("EUR", DATE, new BigDecimal("14800"));
        service.upsert("EUR", DATE, new BigDecimal("14850")); // бир кунда иккинчи ёзув

        var latest = service.latestForEachCurrency();

        assertThat(latest.get("USD").getRate()).isEqualByComparingTo("12650");
        // Бир кундаги кейинги ёзув ютади - id (UUIDv7) тартиби ҳал қилади
        assertThat(latest.get("EUR").getRate()).isEqualByComparingTo("14850");
    }

    @Test
    void rateFor_returnsRateOnOrBeforeDate() {
        service.upsert("USD", DATE, new BigDecimal("12600"));

        // Айнан шу кун
        assertThat(service.rateFor("USD", DATE)).contains(new BigDecimal("12600"));
        // Дам олиш куни (якшанба) - жума курси амал қилади
        assertThat(service.rateFor("USD", DATE.plusDays(2)))
                .contains(new BigDecimal("12600"));
        // Курс киритилишидан олдинги сана - бўш (тахмин қилинмайди)
        assertThat(service.rateFor("USD", DATE.minusDays(1))).isEmpty();
        // Умуман киритилмаган валюта - бўш
        assertThat(service.rateFor("EUR", DATE)).isEmpty();
    }

    @Test
    void upsert_homeCurrencyOrInvalidRate_rejected() {
        // Home валютага (UZS) курс киритилмайди - доим 1
        assertThatThrownBy(() -> service.upsert("UZS", DATE, BigDecimal.ONE))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-FX-002"));

        // Нол/манфий курс - тақиқ
        assertThatThrownBy(() -> service.upsert("USD", DATE, BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-FX-001"));
    }

    @Test
    void importFromCbu_updatesActiveForeignCurrencies() {
        // Seed'да фаол: UZS (home) ва USD; EUR нофаол
        given(cbuRateClient.rates(DATE)).willReturn(List.of(
                new CbuRate("USD", new BigDecimal("12600.47")),
                new CbuRate("EUR", new BigDecimal("13800.12"))));

        ExchangeRateService.ImportResult result = service.importFromCbu(DATE);

        // Фақат USD текширилади - EUR нофаол, UZS home; илк импорт → ўзгарди
        assertThat(result.checked()).isEqualTo(1);
        assertThat(result.changed()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
        assertThat(service.rateFor("USD", DATE)).contains(new BigDecimal("12600.47"));
        assertThat(service.rateFor("EUR", DATE)).isEmpty();
    }

    @Test
    void importFromCbu_currencyMissingInCbuFeed_skipped() {
        // GBP фаоллаштирилди, лекин ЦБ жавобида йўқ - импорт тўхтамайди
        currencyService.setActive("GBP", true);
        given(cbuRateClient.rates(DATE)).willReturn(List.of(
                new CbuRate("USD", new BigDecimal("12600"))));

        ExchangeRateService.ImportResult result = service.importFromCbu(DATE);

        assertThat(result.checked()).isEqualTo(1);
        assertThat(result.changed()).isEqualTo(1); // USD илк импорт
        assertThat(result.skipped()).isEqualTo(1);
    }

    /**
     * Arbitr-067: home≠UZS энди импортни ТЎСМАЙДИ - кросс-курс UZS орқали
     * pivot қилинади (фойдаланувчи талаби 2026-07-10). home=USD, ЦБ:
     * USD 12600.47, EUR 13800.12. Кутилма (scale 12, HALF_UP - аниқ
     * қиймат, хом бўлиниш 1.0952067660968.. / 0.000079362119032..):
     * EUR = 13800.12 ÷ 12600.47 = 1.095206766097;
     * UZS (энди чет валюта) = 1 ÷ 12600.47 = 0.000079362119.
     * ЦБ'да йўқ фаол валюта (GBP) аввалгидек skipped.
     */
    @Test
    void importFromCbu_homeUsd_pivotsThroughUzs_exactScale12() {
        settingsService.update("Тест компания", "USD", "Asia/Tashkent", null, null);
        currencyService.setActive("EUR", true);
        currencyService.setActive("GBP", true); // ЦБ жавобида йўқ - skipped
        given(cbuRateClient.rates(DATE)).willReturn(List.of(
                new CbuRate("USD", new BigDecimal("12600.47")),
                new CbuRate("EUR", new BigDecimal("13800.12"))));

        ExchangeRateService.ImportResult result = service.importFromCbu(DATE);

        // EUR + UZS ёзилди (USD - home, ўтказилади), GBP skipped; илк импорт → 2 ўзгарди
        assertThat(result.checked()).isEqualTo(2);
        assertThat(result.changed()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(service.rateFor("EUR", DATE))
                .contains(new BigDecimal("1.095206766097"));
        assertThat(service.rateFor("UZS", DATE))
                .contains(new BigDecimal("0.000079362119"));
        assertThat(service.rateFor("GBP", DATE)).isEmpty();
        // Манба ЦБ бўлиб қолади - pivot қиймат ҳам импорт ёзуви
        assertThat(service.latest("EUR").orElseThrow().getSource())
                .isEqualTo(RateSource.CBU);
    }

    /**
     * Arbitr-067: BR-FX-003 нинг ЯНГИ маъноси - home валюта ЦБ рўйхатида
     * бўлмаса pivot махражи йўқ, импорт аниқ хато билан рад этилади
     * (аввал «home UZS эмас» тақиқи эди - у олиб ташланди).
     */
    @Test
    void importFromCbu_homeMissingInCbuFeed_rejectedFx003() {
        settingsService.update("Тест компания", "USD", "Asia/Tashkent", null, null);
        // ЦБ жавобида home (USD) йўқ - фақат EUR бор
        given(cbuRateClient.rates(DATE)).willReturn(List.of(
                new CbuRate("EUR", new BigDecimal("13800.12"))));

        assertThatThrownBy(() -> service.importFromCbu(DATE))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-FX-003"));
    }

    /**
     * Arbitr-067 (карта 4-банд): scheduler home≠UZS билан ҳам йиқилмайди -
     * муваффақиятли pivot импорти ҳам, home ЦБ рўйхатида йўқ ҳолат
     * (BR-FX-003) ҳам warn log билан ютилади.
     */
    @Test
    void schedulerImportDaily_homeUsd_neverThrows() {
        settingsService.update("Тест компания", "USD", "Asia/Tashkent", null, null);

        // Муваффақиятли pivot - exception йўқ
        given(cbuRateClient.rates(any())).willReturn(List.of(
                new CbuRate("USD", new BigDecimal("12600.47"))));
        assertThatCode(scheduler::importDaily).doesNotThrowAnyException();

        // home ЦБ рўйхатида йўқ (бўш жавоб) - BR-FX-003 ютилади
        given(cbuRateClient.rates(any())).willReturn(List.of());
        assertThatCode(scheduler::importDaily).doesNotThrowAnyException();
    }

    /**
     * Sanjar-011: импортнинг ички batch йўли - аввал ҳар чет валютага
     * учтадан SELECT (require + homeCurrency + current) кетарди (2+3F),
     * энди импорт давомида учта умумий сўров: settings + active рўйхати +
     * шу сананинг current map'и. Хулқ айнан: 7 фаол валюта, home UZS -
     * олтита чет валюта ёзилади; айнан бир хил қийматлар билан такрор
     * импорт тарихга ҳеч нарса қўшмайди (skip-if-same), ImportResult
     * эса аввалгидек updated ҳисоблайди.
     */
    @Test
    void importFromCbu_allSevenActive_threeSelectsAndDedupOnReimport() {
        for (Currency currency : currencyService.all()) {
            if (!currency.isActive()) {
                currencyService.setActive(currency.getCode(), true);
            }
        }
        List<Currency> active = currencyService.active();
        assertThat(active).hasSize(7); // seed каталоги тўлиқ фаоллаштирилди
        List<CbuRate> feed = new ArrayList<>();
        int offset = 0;
        for (Currency currency : active) {
            if (!"UZS".equals(currency.getCode())) {
                feed.add(new CbuRate(currency.getCode(),
                        new BigDecimal(12000 + (++offset))));
            }
        }
        given(cbuRateClient.rates(DATE)).willReturn(feed);
        settingsService.get(); // қатор олдиндан бор - яратилиш ўлчовга кирмасин

        SqlCaptureInspector.start();
        ExchangeRateService.ImportResult first;
        List<String> captured;
        try {
            first = service.importFromCbu(DATE);
        } finally {
            captured = SqlCaptureInspector.stop();
        }

        assertThat(first.checked()).isEqualTo(6);
        assertThat(first.changed()).isEqualTo(6); // илк импорт - ҳаммаси ўзгарди
        assertThat(first.skipped()).isZero();
        // 2 + 3F = 20 эмас, жами 3 SELECT: settings, active каталог, current map
        assertThat(SqlCaptureInspector.selectCount(captured, "company_settings")).isEqualTo(1);
        assertThat(SqlCaptureInspector.selectCount(captured, "currency")).isEqualTo(1);
        assertThat(SqlCaptureInspector.selectCount(captured, "exchange_rate")).isEqualTo(1);

        // Такрор импорт (крон куни икки марта уради): қийматлар ўзгармаган -
        // append-only тарихга дубль ёзилмайди, натижа сони ўзгармайди
        ExchangeRateService.ImportResult second = service.importFromCbu(DATE);
        // Такрор импорт: айнан шу қийматлар - текширилди, лекин ЎЗГАРМАДИ (Arbitr-168)
        assertThat(second.checked()).isEqualTo(6);
        assertThat(second.changed()).isZero();
        assertThat(second.skipped()).isZero();
        for (Currency currency : active) {
            if (!"UZS".equals(currency.getCode())) {
                assertThat(service.history(currency.getCode())).hasSize(1);
            }
        }
    }

    /**
     * Sanjar-011: бир санада олдин MANUAL ёзув турса, фарқли ЦБ қиймати
     * ЯНГИ ёзув бўлиб қўшилади ва id (UUIDv7) тартиби бўйича амалдаги
     * курс бўлади - batch current map якка record() йўлидаги
     * findFirst...OrderByIdDesc қоидасини айнан сақлайди.
     */
    @Test
    void importFromCbu_manualEarlierSameDate_cbuAppendsAndWins() {
        service.upsert("USD", DATE, new BigDecimal("12700"));
        given(cbuRateClient.rates(DATE)).willReturn(List.of(
                new CbuRate("USD", new BigDecimal("12600"))));

        service.importFromCbu(DATE);

        List<ExchangeRate> hist = service.history("USD");
        assertThat(hist).hasSize(2);
        assertThat(hist.get(0).getSource()).isEqualTo(RateSource.CBU);
        assertThat(hist.get(0).getRate()).isEqualByComparingTo("12600");
        assertThat(hist.get(1).getSource()).isEqualTo(RateSource.MANUAL);
        assertThat(service.rateFor("USD", DATE).orElseThrow())
                .isEqualByComparingTo("12600");
    }

    @Test
    void importFromCbu_clientFailure_surfacesAsBrFx004() {
        given(cbuRateClient.rates(any())).willThrow(new BusinessRuleException(
                com.averpo.erp.shared.exception.BusinessRule.BR_FX_004,
                "ЦБ курс хизматига мурожаат амалга ошмади: тест"));

        assertThatThrownBy(() -> service.importFromCbu(DATE))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-FX-004"));
    }

    /**
     * Arbitr-168 (санагич ҳалоллиги): дам олишда ЦБ жума курсини қайтаради -
     * олдинги effective билан ТЕНГ, валюта текширилади, лекин changed ЭМАС.
     * A1: per-date ёзув САҚЛАНАДИ (Currencies экрани бугунги санани кўрсатади,
     * тизим ишлаяпти далили), фақат санагич «ўзгарди» демайди.
     */
    @Test
    void importFromCbu_sameRateAsPreviousDay_checkedNotChanged_stillSavesPerDate() {
        LocalDate friday = LocalDate.of(2026, 7, 17);
        LocalDate sunday = LocalDate.of(2026, 7, 19);
        given(cbuRateClient.rates(friday)).willReturn(List.of(
                new CbuRate("USD", new BigDecimal("12600"))));
        service.importFromCbu(friday); // жума ёзуви

        // Якшанба: ЦБ айнан жума курсини қайтаради (янги курс йўқ)
        given(cbuRateClient.rates(sunday)).willReturn(List.of(
                new CbuRate("USD", new BigDecimal("12600"))));
        ExchangeRateService.ImportResult result = service.importFromCbu(sunday);

        assertThat(result.checked()).isEqualTo(1);
        assertThat(result.changed()).isZero();       // курс ЎЗГАРМАДИ
        assertThat(result.skipped()).isZero();
        // Per-date ёзув сақланади: жума + якшанба (айнан бир хил қиймат ҳам)
        assertThat(service.history("USD")).hasSize(2);
        assertThat(service.rateFor("USD", sunday)).contains(new BigDecimal("12600"));
    }

    /** Arbitr-168: ЦБ янги курс берса - текширилди ҲАМ, ўзгарди ҲАМ (changed=1). */
    @Test
    void importFromCbu_rateDiffersFromPrevious_changedCounted() {
        LocalDate day1 = LocalDate.of(2026, 7, 17);
        LocalDate day2 = LocalDate.of(2026, 7, 20);
        given(cbuRateClient.rates(day1)).willReturn(List.of(
                new CbuRate("USD", new BigDecimal("12600"))));
        service.importFromCbu(day1);

        given(cbuRateClient.rates(day2)).willReturn(List.of(
                new CbuRate("USD", new BigDecimal("12700")))); // ЯНГИ курс
        ExchangeRateService.ImportResult result = service.importFromCbu(day2);

        assertThat(result.checked()).isEqualTo(1);
        assertThat(result.changed()).isEqualTo(1);   // курс ЎЗГАРДИ
        assertThat(result.skipped()).isZero();
    }
}
