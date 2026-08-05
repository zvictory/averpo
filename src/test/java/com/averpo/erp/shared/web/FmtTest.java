package com.averpo.erp.shared.web;

import com.averpo.erp.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fmt кўрсатиш helper'лари - Spring'сиз соф unit тестлар (HALF_UP,
 * trailing ноллар, NBSP минг ажратгич, чегара ҳолатлар). Пул формати
 * DEC-011 қарори бўйича: 2 хона + НУҚТА каср ажратгичи (QBO
 * стандарти: 12 600.50).
 */
class FmtTest {

    /** Минг ажратгич - оддий бўшлиқ ЭМАС, айнан U+00A0 бўлиши шарт. */
    private static final String NBSP = String.valueOf((char) 0x00A0);

    // ---- money(BigDecimal): қатъий 2 хона + нуқта + NBSP гуруҳлаш ----

    @Test
    void money_fixedTwoDecimals_dotSeparator_withNbspGrouping() {
        // DEC-011 намунаси: 12 600.50 (QBO стандарти)
        assertThat(Fmt.money(new BigDecimal("12600.50")))
                .isEqualTo("12" + NBSP + "600.50");
        // 2 хонага тўлдирилади - trailing ноллар олиб ташланМАЙДИ
        assertThat(Fmt.money(new BigDecimal("10"))).isEqualTo("10.00");
        assertThat(Fmt.money(new BigDecimal("5.5"))).isEqualTo("5.50");
        // 3-хона HALF_UP билан 2 хонага яхлитланади
        assertThat(Fmt.money(new BigDecimal("12600.505")))
                .isEqualTo("12" + NBSP + "600.51");
    }

    @Test
    void money_halfUpRounding() {
        assertThat(Fmt.money(new BigDecimal("1.005"))).isEqualTo("1.01");
        assertThat(Fmt.money(new BigDecimal("1.004"))).isEqualTo("1.00");
        // HALF_UP манфийда нолдан узоқлашади
        assertThat(Fmt.money(new BigDecimal("-1.005"))).isEqualTo("-1.01");
        // Яхлитлашдан кейин разряд кўтарилса гуруҳлаш ҳам тўғри қолади
        assertThat(Fmt.money(new BigDecimal("999.995")))
                .isEqualTo("1" + NBSP + "000.00");
    }

    @Test
    void money_boundaryValues() {
        assertThat(Fmt.money(BigDecimal.ZERO)).isEqualTo("0.00");
        assertThat(Fmt.money(new BigDecimal("25000000")))
                .isEqualTo("25" + NBSP + "000" + NBSP + "000.00");
        // Манфийда минус олдинда, гуруҳлаш белгидан кейинги қисмга
        assertThat(Fmt.money(new BigDecimal("-1234567.89")))
                .isEqualTo("-1" + NBSP + "234" + NBSP + "567.89");
        assertThat(Fmt.money((BigDecimal) null)).isEqualTo("");
    }

    @Test
    void moneyDisplayScale_isTwo() {
        // DEC-011: QBO'дагидек 2 хона - тест жорий келишувни қайд этади
        assertThat(Fmt.MONEY_DISPLAY_SCALE).isEqualTo(2);
    }

    // ---- money(Money): сумма + валюта коди ----

    @Test
    void moneyWithCurrency_amountPlusCode() {
        // Ҳужжат валютасидаги amount чиқади, baseAmount ЭМАС
        Money usd = Money.of(new BigDecimal("100"), "USD", new BigDecimal("12650"));
        assertThat(Fmt.money(usd)).isEqualTo("100.00" + NBSP + "USD");

        Money uzs = Money.ofBase(new BigDecimal("1265000"), "UZS");
        assertThat(Fmt.money(uzs))
                .isEqualTo("1" + NBSP + "265" + NBSP + "000.00" + NBSP + "UZS");
    }

    @Test
    void moneyWithCurrency_null_returnsEmpty() {
        assertThat(Fmt.money((Money) null)).isEqualTo("");
    }

    // ---- qty: trailing нолсиз, макс 4 хона, нуқта каср ----

    @Test
    void qty_stripsTrailingZeros() {
        assertThat(Fmt.qty(new BigDecimal("10.0000"))).isEqualTo("10");
        assertThat(Fmt.qty(new BigDecimal("5.5000"))).isEqualTo("5.5");
        assertThat(Fmt.qty(new BigDecimal("0.0000"))).isEqualTo("0");
    }

    @Test
    void qty_maxFourDecimals_halfUp() {
        assertThat(Fmt.qty(new BigDecimal("1.23456"))).isEqualTo("1.2346");
        // Яхлитлашдан кейин ноллар қолса улар ҳам олиб ташланади
        assertThat(Fmt.qty(new BigDecimal("2.00004"))).isEqualTo("2");
    }

    @Test
    void qty_boundaryValues() {
        assertThat(Fmt.qty(new BigDecimal("10000"))).isEqualTo("10" + NBSP + "000");
        // stripTrailingZeros катта яхлит сонни 2.5E+7 га айлантиради -
        // илмий кўриниш экранга чиқиб кетмаслиги текширилади
        assertThat(Fmt.qty(new BigDecimal("25000000.0000")))
                .isEqualTo("25" + NBSP + "000" + NBSP + "000");
        assertThat(Fmt.qty(new BigDecimal("-7.5000"))).isEqualTo("-7.5");
        assertThat(Fmt.qty(null)).isEqualTo("");
    }

    @Test
    void qtyWithUnit_appendsUnitName() {
        // Spec B қисми: миқдор доим бирлиги билан - 10 дона, 5.5 кг
        assertThat(Fmt.qty(new BigDecimal("10"), "дона"))
                .isEqualTo("10" + NBSP + "дона");
        assertThat(Fmt.qty(new BigDecimal("5.5"), "кг"))
                .isEqualTo("5.5" + NBSP + "кг");
        // Бирлиги йўқ item - фақат миқдор, думсиз
        assertThat(Fmt.qty(new BigDecimal("10"), null)).isEqualTo("10");
        assertThat(Fmt.qty(new BigDecimal("10"), " ")).isEqualTo("10");
        // Қиймат null бўлса бирлик ҳам ёзилмайди
        assertThat(Fmt.qty(null, "дона")).isEqualTo("");
    }

    // ---- rate (DEC-135): >= 1 → қатъий 2 хона; < 1 → макс 8 хона ----

    @Test
    void rate_atLeastOne_fixedTwoDecimals_nbspGrouping() {
        // DEC-135: катта курс пул кўринишидек ўқилади - 12 090.45
        assertThat(Fmt.rate(new BigDecimal("12090.45")))
                .isEqualTo("12" + NBSP + "090.45");
        // 3-хона HALF_UP билан 2 хонага яхлитланади
        assertThat(Fmt.rate(new BigDecimal("12090.456")))
                .isEqualTo("12" + NBSP + "090.46");
        // Қатъий 2 хона - trailing ноллар олиб ташланМАЙДИ
        assertThat(Fmt.rate(new BigDecimal("12650.470000")))
                .isEqualTo("12" + NBSP + "650.47");
        assertThat(Fmt.rate(BigDecimal.ONE)).isEqualTo("1.00");
        // BigDecimal HALF_UP - float'даги 1.00499... тузоғисиз (JS кўзгуси
        // averpoRateFmt стринг яхлитлаш билан айнан шуни такрорлайди)
        assertThat(Fmt.rate(new BigDecimal("1.005"))).isEqualTo("1.01");
    }

    @Test
    void rate_belowOne_maxEightDecimals_stripsTrailingZeros() {
        // Қоида сабаби: аввалги «макс 6» тескари курснинг охирги маъноли
        // рақамларини кесарди - 8 хона тўлиқ кўрсатади
        assertThat(Fmt.rate(new BigDecimal("0.00008334"))).isEqualTo("0.00008334");
        // 9-хона HALF_UP билан 8 хонага яхлитланади
        assertThat(Fmt.rate(new BigDecimal("0.000083339"))).isEqualTo("0.00008334");
        assertThat(Fmt.rate(new BigDecimal("0.0000819672"))).isEqualTo("0.00008197");
        // trailing нолсиз: 0.5 шундайлигича қолади
        assertThat(Fmt.rate(new BigDecimal("0.5"))).isEqualTo("0.5");
    }

    @Test
    void rate_boundaryAndNull() {
        // Тармоқ танлови яхлитлашдан ОЛДИН: 0.999999995 «< 1» тармоғида
        // 8 хонада 1.00000000 бўлиб стрипдан кейин «1» кўринади - карта
        // (DEC-135) ҳужжатлаган чегара хулқи; айнан 1 эса «1.00»
        assertThat(Fmt.rate(new BigDecimal("0.999999995"))).isEqualTo("1");
        assertThat(Fmt.rate(new BigDecimal("1.000000"))).isEqualTo("1.00");
        assertThat(Fmt.rate(null)).isEqualTo("");
    }

    // ---- n: форма prefill'лари учун хом кўриниш ----

    @Test
    void n_rawValueForFormPrefill_noGrouping() {
        // Серверга қайтиб parse қилинади - гуруҳлаш йўқ, нуқта қолади
        assertThat(Fmt.n(new BigDecimal("1000000.0000"))).isEqualTo("1000000");
        assertThat(Fmt.n(new BigDecimal("2.50"))).isEqualTo("2.5");
        assertThat(Fmt.n(null)).isEqualTo("");
    }

    // ---- dt: UTC -> компания вақт минтақаси ----

    @Test
    void dt_rendersInCompanyZone() {
        // Темир қоида №12: базада UTC, экранда компания минтақаси (+05)
        Instant utc = Instant.parse("2026-07-05T16:45:00Z");
        assertThat(Fmt.dt(utc, ZoneId.of("Asia/Tashkent")))
                .isEqualTo("2026-07-05 21:45");
        assertThat(Fmt.dt(null, ZoneId.of("Asia/Tashkent"))).isEqualTo("");
    }

    // ---- orient: курс йўналиши - кучли валюта базис (spec E қисм X) ----

    @Test
    void orient_usdUzs_sameResultBothDirections() {
        // Каталог йўналиши (1 USD = 12500 UZS) - ўз ҳолича, агдарилмайди
        var direct = Fmt.orient("USD", "UZS", new BigDecimal("12500"));
        assertThat(direct.base()).isEqualTo("USD");
        assertThat(direct.quote()).isEqualTo("UZS");
        assertThat(direct.value()).isEqualByComparingTo("12500");

        // Тескари киритилган (1 UZS = 0.00008 USD) - экранда БАРИБИР
        // «1 USD = 12 500 UZS»: иккала йўналишда бир хил натижа
        var inverted = Fmt.orient("UZS", "USD", new BigDecimal("0.00008"));
        assertThat(inverted.base()).isEqualTo("USD");
        assertThat(inverted.quote()).isEqualTo("UZS");
        assertThat(inverted.value()).isEqualByComparingTo("12500");
        assertThat(Fmt.rate(inverted.value())).isEqualTo(Fmt.rate(direct.value()));
    }

    @Test
    void orient_usdEur_listOrderBeatsGreaterThanOneFallback() {
        // Иккиси ҳам рўйхатда: USD олдинроқ - қиймат < 1 бўлса ҳам USD базис
        var oriented = Fmt.orient("EUR", "USD", new BigDecimal("1.08"));
        assertThat(oriented.base()).isEqualTo("USD");
        assertThat(oriented.quote()).isEqualTo("EUR");
        // 1/1.08 = 0.925925925926 (scale 12, HALF_UP) - фақат экран учун
        assertThat(oriented.value()).isEqualByComparingTo("0.925925925926");

        // USD базисда киритилган бўлса ҳам ўша йўналиш сақланади
        var direct = Fmt.orient("USD", "EUR", new BigDecimal("0.93"));
        assertThat(direct.base()).isEqualTo("USD");
        assertThat(direct.quote()).isEqualTo("EUR");
        assertThat(direct.value()).isEqualByComparingTo("0.93");
    }

    @Test
    void orient_singleListedCurrency_becomesBase() {
        // Фақат биттаси рўйхатда (RUB) - у базис, қиймат 1/x билан агдарилади
        var oriented = Fmt.orient("UZS", "RUB", new BigDecimal("0.0064"));
        assertThat(oriented.base()).isEqualTo("RUB");
        assertThat(oriented.quote()).isEqualTo("UZS");
        assertThat(oriented.value()).isEqualByComparingTo("156.25");
    }

    @Test
    void orient_unlistedPair_fallbackKeepsValueAtLeastOne() {
        // Рўйхатда йўқ жуфтлик (KZT/UZS): қиймати >= 1 йўналиш танланади
        var direct = Fmt.orient("KZT", "UZS", new BigDecimal("25"));
        assertThat(direct.base()).isEqualTo("KZT");
        assertThat(direct.value()).isEqualByComparingTo("25");

        var inverted = Fmt.orient("UZS", "KZT", new BigDecimal("0.04"));
        assertThat(inverted.base()).isEqualTo("KZT");
        assertThat(inverted.quote()).isEqualTo("UZS");
        assertThat(inverted.value()).isEqualByComparingTo("25");
    }

    @Test
    void orient_degenerateInputs_passThroughWithoutFlip() {
        // Тенг кодлар (home ҳужжат, курс 1) - агдариш маъносиз
        var same = Fmt.orient("UZS", "UZS", BigDecimal.ONE);
        assertThat(same.base()).isEqualTo("UZS");
        assertThat(same.value()).isEqualByComparingTo("1");

        // null курс - JTE ҳимояси: exception эмас, ўз ҳолича (Fmt.rate(null)="")
        var nullRate = Fmt.orient("UZS", "USD", null);
        assertThat(nullRate.base()).isEqualTo("UZS");
        assertThat(nullRate.value()).isNull();

        // Нол курс - 1/0 бўлинмайди, ўз ҳолича қайтади
        var zero = Fmt.orient("UZS", "USD", BigDecimal.ZERO);
        assertThat(zero.base()).isEqualTo("UZS");
        assertThat(zero.value()).isEqualByComparingTo("0");
    }
}
