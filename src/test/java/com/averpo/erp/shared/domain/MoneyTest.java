package com.averpo.erp.shared.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Money қиймат объекти - Spring'сиз соф unit тестлар.
 *
 * @author Zafar
 */
class MoneyTest {

    @Test
    void factories_nullArguments_throwEarlyWithFieldName() {
        // Инвариант constructor'да ҳимояланади: қайси майдон null'лиги
        // хабарда аниқ кўриниши шарт (хабарсиз NPE debug'ни қийинлаштиради)
        assertThatNullPointerException()
                .isThrownBy(() -> Money.ofBase(null, "UZS"))
                .withMessageContaining("amount");
        assertThatNullPointerException()
                .isThrownBy(() -> Money.ofBase(BigDecimal.ONE, null))
                .withMessageContaining("currency");
        assertThatNullPointerException()
                .isThrownBy(() -> Money.of(null, "USD", BigDecimal.ONE))
                .withMessageContaining("amount");
        assertThatNullPointerException()
                .isThrownBy(() -> Money.of(BigDecimal.ONE, null, BigDecimal.ONE))
                .withMessageContaining("currency");
        assertThatNullPointerException()
                .isThrownBy(() -> Money.of(BigDecimal.ONE, "USD", null))
                .withMessageContaining("exchangeRate");
    }

    @Test
    void reverseDirectionRate_keeps12DecimalPrecision() {
        // Home = USD ҳолати: 1 UZS = 1/12200 USD - жуда кичик курс.
        // 8 хонада бу 0.00008197 бўлиб қоларди (4 маъноли рақам).
        Money money = Money.of(new BigDecimal("1000000000"), "UZS",
                new BigDecimal("0.000081967213"));

        assertThat(money.getExchangeRate())
                .isEqualByComparingTo("0.000081967213");
        // 1 000 000 000 * 0.000081967213 = 81 967.213 USD - аниқ
        assertThat(money.getBaseAmount())
                .isEqualByComparingTo("81967.2130");
    }

    @Test
    void rate_roundsHalfUp_toTwelveDecimals() {
        // 13-хона 5 дан катта - юқорига яхлитланади
        Money money = Money.of(BigDecimal.ONE, "UZS",
                new BigDecimal("0.0000819672135678"));

        assertThat(money.getExchangeRate())
                .isEqualByComparingTo("0.000081967214");
    }

    @Test
    void forwardDirectionRate_unchanged() {
        // Home = UZS ҳолати: катта курслар аввалгидек ишлайди
        Money money = Money.of(new BigDecimal("100"), "USD",
                new BigDecimal("12200"));

        assertThat(money.getBaseAmount()).isEqualByComparingTo("1220000");
        assertThat(money.getExchangeRate()).isEqualByComparingTo("12200");
    }
}
