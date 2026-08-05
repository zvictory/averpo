package com.averpo.erp.shared.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Fmt#orientInput} unit тести (Arbitr-097): курс блоки компоненти
 * КИРИТИШ ориентацияси - ИККАЛА home вариантида (SABOQLAR янги қоидаси:
 * home=UZS ва home=USD). Каноник (сақланадиган) курс home-per-doc
 * ЎЗГАРМАЙДИ; кўринадиган қиймат кучли-валюта базисида ва Arbitr-135
 * дан бери {@link Fmt#rate} кўрсатиш форматида (>= 1 → 2 хона NBSP
 * билан, &lt; 1 → макс 8 хона).
 */
class RateOrientInputTest {

    /** Минг ажратгич - оддий бўшлиқ ЭМАС, айнан U+00A0 (Fmt қоидаси). */
    private static final String NBSP = String.valueOf((char) 0x00A0);

    @Test
    void homeUzs_usdDoc_noFlip_usdBase() {
        // home=UZS, ҳужжат=USD, каноник «1 USD = 12048 UZS» - USD аллақачон
        // кучли (базис), флип йўқ; кўринадиган каноник қийматнинг Fmt.rate
        // кўриниши (135: >= 1 → 2 хона, NBSP гуруҳ)
        Fmt.RateInput oi = Fmt.orientInput("USD", "UZS", "12048");
        assertThat(oi.foreign()).isTrue();
        assertThat(oi.flipped()).isFalse();
        assertThat(oi.base()).isEqualTo("USD");
        assertThat(oi.quote()).isEqualTo("UZS");
        assertThat(oi.visible()).isEqualTo("12" + NBSP + "048.00");
        assertThat(oi.canonical()).isEqualTo("12048");
    }

    @Test
    void homeUsd_uzsDoc_flip_usdBase_canonicalPreserved() {
        // home=USD, ҳужжат=UZS, каноник «1 UZS = 0.000083 USD» - ЖОНЛИ BUG:
        // аввал «1 UZS = 0.000083 USD» кўринарди. Энди флип: «1 USD = ? UZS»
        // (визуал ~12048), лекин САҚЛАНАДИГАН каноник 0.000083 ЎЗГАРМАЙДИ
        Fmt.RateInput oi = Fmt.orientInput("UZS", "USD", "0.000083");
        assertThat(oi.foreign()).isTrue();
        assertThat(oi.flipped()).isTrue();
        assertThat(oi.base()).isEqualTo("USD");
        assertThat(oi.quote()).isEqualTo("UZS");
        // Каноник ТЕГИЛМАГАН (server/servis шуни олади)
        assertThat(oi.canonical()).isEqualTo("0.000083");
        // Кўринадиган = 1/каноник (кучли базис) Fmt.rate форматида:
        // 1/0.000083 = 12048.192771084337 → 2 хона + NBSP (135)
        assertThat(oi.visible()).isEqualTo("12" + NBSP + "048.19");
    }

    @Test
    void sameCurrency_home_notForeign_rateOne() {
        // Валюта home'га тенг: блок яширин, курс 1
        Fmt.RateInput oi = Fmt.orientInput("UZS", "UZS", null);
        assertThat(oi.foreign()).isFalse();
        assertThat(oi.flipped()).isFalse();
        assertThat(oi.visible()).isEqualTo("1");
        assertThat(oi.canonical()).isEqualTo("1");
    }

    @Test
    void nullDoc_notForeign() {
        // Валюта танланмаган (янги форма): блок яширин
        Fmt.RateInput oi = Fmt.orientInput(null, "UZS", "5");
        assertThat(oi.foreign()).isFalse();
        assertThat(oi.visible()).isEqualTo("1");
    }

    @Test
    void bothInPriority_earlierIsBase() {
        // home=EUR, ҳужжат=USD - иккиси ҳам рўйхатда, USD олдинроқ (базис),
        // флип йўқ (каноник «1 USD = 0.92 EUR» тўғри йўналишда)
        Fmt.RateInput oi = Fmt.orientInput("USD", "EUR", "0.92");
        assertThat(oi.flipped()).isFalse();
        assertThat(oi.base()).isEqualTo("USD");
        assertThat(oi.quote()).isEqualTo("EUR");
        // < 1 тармоқ (135): trailing нолсиз, 0.92 шундайлигича
        assertThat(oi.visible()).isEqualTo("0.92");
    }

    @Test
    void homeEur_usdDoc_flip() {
        // home=USD, ҳужжат=EUR, каноник «1 EUR = 1.08 USD» - USD рўйхатда
        // олдинроқ (index0) EUR (index1)дан → базис USD, флип бор
        Fmt.RateInput oi = Fmt.orientInput("EUR", "USD", "1.08");
        assertThat(oi.flipped()).isTrue();
        assertThat(oi.base()).isEqualTo("USD");
        assertThat(oi.quote()).isEqualTo("EUR");
        assertThat(oi.canonical()).isEqualTo("1.08");
        // 1/1.08 = 0.925925925926 (scale 12) → < 1 тармоқда 8 хона HALF_UP
        assertThat(oi.visible()).isEqualTo("0.92592593");
    }

    @Test
    void neitherInPriority_fallbackGreaterThanOne() {
        // Иккиси ҳам рўйхатда йўқ (масалан home=UZS, ҳужжат=KZT), каноник < 1
        // → фолбэк: қиймати >= 1 йўналиш қолсин (флип)
        Fmt.RateInput oi = Fmt.orientInput("KZT", "UZS", "0.025");
        assertThat(oi.flipped()).isTrue();
        assertThat(oi.base()).isEqualTo("UZS");
        assertThat(oi.quote()).isEqualTo("KZT");
        // 1/0.025 = 40 → >= 1 тармоқда қатъий 2 хона (135)
        assertThat(oi.visible()).isEqualTo("40.00");
    }

    @Test
    void malformedRate_defaultsToOne_noException() {
        // Бузуқ/бўш курс - ориентация йиқилмайди (JTE ичида exception бутун
        // саҳифани йиқитади), 1 деб олинади
        assertThat(Fmt.orientInput("USD", "UZS", "  ").canonical()).isEqualTo("1");
        assertThat(Fmt.orientInput("USD", "UZS", "abc").canonical()).isEqualTo("1");
        assertThat(Fmt.orientInput("USD", "UZS", "-5").canonical()).isEqualTo("1");
        // Нейтрал 1 кўринадиганда 135 форматида - rate-block.js canAutofill
        // «1.00»ни ҳам нейтрал деб танийди (num(v)===1 текшируви)
        assertThat(Fmt.orientInput("USD", "UZS", "  ").visible()).isEqualTo("1.00");
    }
}
