package com.averpo.erp.tax;

import com.averpo.erp.tax.service.TaxAmounts;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TaxAmounts (нетто/ҚҚС ажратиш) тестлари: docs/modules/tax.md
 * «Ҳисоблаш» - Spring'сиз соф арифметика. Комплемент аниқлиги
 * (net + tax == gross) inclusive режимнинг калит талаби.
 *
 * @author Zafar
 */
class TaxAmountsTest {

    private static final BigDecimal TWELVE = new BigDecimal("12");

    @Test
    void exclusive_addsTaxOnTop() {
        // 1000 × 12% → net 1000 / tax 120 / gross 1120
        TaxAmounts ta = TaxAmounts.of(new BigDecimal("1000"), TWELVE, false);
        assertThat(ta.net()).isEqualByComparingTo("1000");
        assertThat(ta.tax()).isEqualByComparingTo("120");
        assertThat(ta.gross()).isEqualByComparingTo("1120");
    }

    @Test
    void inclusive_extractsTax_exactComplement() {
        // gross 1120 → net 1000 / tax 120
        TaxAmounts round = TaxAmounts.of(new BigDecimal("1120"), TWELVE, true);
        assertThat(round.net()).isEqualByComparingTo("1000");
        assertThat(round.tax()).isEqualByComparingTo("120");

        // Қолдиқли ҳолат (spec): gross 100, 12% → net 89.2857, tax 10.7143,
        // йиғинди АЙНАН 100 (комплемент - яхлитлаш дрейфи йўқ)
        TaxAmounts frac = TaxAmounts.of(new BigDecimal("100"), TWELVE, true);
        assertThat(frac.net()).isEqualByComparingTo("89.2857");
        assertThat(frac.tax()).isEqualByComparingTo("10.7143");
        assertThat(frac.net().add(frac.tax())).isEqualByComparingTo("100");
    }

    @Test
    void noTax_bothModes_netEqualsRaw() {
        for (BigDecimal rate : new BigDecimal[]{null, BigDecimal.ZERO}) {
            TaxAmounts excl = TaxAmounts.of(new BigDecimal("500"), rate, false);
            assertThat(excl.net()).isEqualByComparingTo("500");
            assertThat(excl.tax()).isEqualByComparingTo("0");
            TaxAmounts incl = TaxAmounts.of(new BigDecimal("500"), rate, true);
            assertThat(incl.net()).isEqualByComparingTo("500");
            assertThat(incl.tax()).isEqualByComparingTo("0");
        }
    }
}
