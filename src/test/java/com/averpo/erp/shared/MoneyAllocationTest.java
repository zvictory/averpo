package com.averpo.erp.shared;

import com.averpo.erp.shared.domain.MoneyAllocation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MoneyAllocation (largest-remainder, Asrorxoja-002) кафолатлари:
 * йиғинди айнан target, ҳар сатр четлашиши ≤ 0.0001 (BR-LED-003
 * tolerance) - Spring'сиз соф unit тест.
 */
class MoneyAllocationTest {

    /** BR-LED-003 tolerance - PostingServiceImpl билан бир хил қиймат. */
    private static final BigDecimal TOLERANCE = new BigDecimal("0.0001");

    /** Кафолатларни текширади: йиғинди == target, ҳар сатр tolerance ичида. */
    private void assertAllocation(List<BigDecimal> amounts, String rateText) {
        BigDecimal rate = new BigDecimal(rateText);
        List<BigDecimal> bases = MoneyAllocation.lineBases(amounts, rate);
        assertThat(bases).hasSize(amounts.size());

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal baseSum = BigDecimal.ZERO;
        for (int i = 0; i < amounts.size(); i++) {
            total = total.add(amounts.get(i));
            baseSum = baseSum.add(bases.get(i));
            BigDecimal exact = amounts.get(i).multiply(rate);
            assertThat(bases.get(i).subtract(exact).abs())
                    .isLessThanOrEqualTo(TOLERANCE);
        }
        assertThat(baseSum).isEqualByComparingTo(
                MoneyAllocation.targetBase(total, rate));
    }

    @Test
    void pathologicalHalfUnitFractions_sumEqualsTarget_eachLineWithinTolerance() {
        // Asrorxoja-002 сценарийси: ҳар сатр exact'и 100.12345 - каср
        // айнан ярим бирлик, эски «йиғинди» ечими 0.00015 га четлашарди
        assertAllocation(List.of(new BigDecimal("0.01"), new BigDecimal("0.01"),
                new BigDecimal("0.01")), "10012.345");
        // target = 300.3704, floor йиғиндиси 300.3702 - 2 бирлик тарқатилади
        List<BigDecimal> bases = MoneyAllocation.lineBases(
                List.of(new BigDecimal("0.01"), new BigDecimal("0.01"),
                        new BigDecimal("0.01")), new BigDecimal("10012.345"));
        BigDecimal sum = bases.get(0).add(bases.get(1)).add(bases.get(2));
        assertThat(sum).isEqualByComparingTo("300.3704");
    }

    @Test
    void mixedAmounts_andManyLines_invariantsHold() {
        // Ҳар хил суммалар - қолдиқлар турлича, тартиб детерминизми ишлайди
        assertAllocation(List.of(new BigDecimal("0.03"), new BigDecimal("0.03")),
                "12345.6789");
        assertAllocation(List.of(new BigDecimal("1.11"), new BigDecimal("2.22"),
                new BigDecimal("3.33"), new BigDecimal("4.44"),
                new BigDecimal("5.55")), "10012.345");
        // Каср йўқ ҳолат: base'лар айнан exact, ҳеч нарса тарқатилмайди
        assertAllocation(List.of(new BigDecimal("10"), new BigDecimal("20")), "12600");
    }

    @Test
    void singleLine_baseEqualsTarget() {
        List<BigDecimal> bases = MoneyAllocation.lineBases(
                List.of(new BigDecimal("0.03")), new BigDecimal("12345.6789"));
        // Битта сатр = назорат сатри: иккиси ҳам битта яхлитлашли target
        assertThat(bases.get(0)).isEqualByComparingTo(
                MoneyAllocation.targetBase(new BigDecimal("0.03"),
                        new BigDecimal("12345.6789")));
    }

    @Test
    void emptyList_returnsEmpty() {
        assertThat(MoneyAllocation.lineBases(List.of(), new BigDecimal("12600")))
                .isEmpty();
    }
}
