package com.averpo.erp.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Чет валюта ҳужжати base (home) суммаларини сатрларга тақсимлаш -
 * largest-remainder усули (LOG-002).
 *
 * <p>Муаммо: назорат сатри (AP/AR/банк томони) base'и билан сатрлар
 * base йиғиндиси бир вақтда иккита инвариантни қаноатлантириши шарт -
 * BR-LED-003 (ҳар сатрда {@code |base − amount × rate| ≤ 0.0001}) ва
 * BR-LED-006 (дебет base йиғиндиси == кредит base йиғиндиси). Ҳар
 * сатрни алоҳида яхлитлаб йиғиш назорат сатрини N ≥ 3 да BR-LED-003
 * дан чиқариб юборар эди (ҳар ҳадда 0.00005 гача хато тўпланади);
 * назорат сатрини битта яхлитлаш эса BR-LED-006 ни бузар эди
 * (PERF-001/007/009). Ечим - тескари йўналиш:
 * <ol>
 *   <li>назорат сатри base'и = {@link #targetBase} - БИТТА яхлитлаш,
 *       инвариантга аниқ мос;</li>
 *   <li>сатр base'лари {@link #lineBases} билан айнан шу target'га
 *       тақсимланади: аввал ҳар сатр FLOOR билан, кейин етишмаган
 *       0.0001'лар энг катта қолдиқли сатрларга бир донадан.</li>
 * </ol>
 *
 * <p>Кафолатлар (LOG-002 арбитр ҳукми): ҳар сатр четлашиши
 * ≤ 0.0001 (FLOOR пастга &lt; 0.0001, +бир бирлик қўшилгани ≤ 0.0001 -
 * бирлик фақат нолдан фарқли қолдиқли сатрга тушади), йиғинди айнан
 * target. Тарқатиладиган бирликлар сони исботан [0, N] оралиғида:
 * target ≥ Σexact − 0.00005 ва Σfloor ≤ Σexact бўлгани учун манфий
 * эмас; ҳар қолдиқ &lt; 0.0001 бўлгани учун N дан ошмайди.
 *
 * <p>ФАҚАТ ҳужжат GL қуриши ва totalBase учун - бошқа ҳисобларга
 * ишлатиш олдидан шу кафолатлар етарлилиги текширилсин.
 */
public final class MoneyAllocation {

    /** Base суммалар аниқлигидаги энг кичик бирлик: 0.0001. */
    private static final BigDecimal UNIT =
            BigDecimal.ONE.movePointLeft(Money.AMOUNT_SCALE);

    /** Utility класс - instance яратилмайди. */
    private MoneyAllocation() { }

    /**
     * Назорат сатри (AP/AR/банк томони) ва totalBase учун ягона
     * формула: {@code round(total × rate)} - битта яхлитлаш, Money
     * инварианти (BR-LED-003) аниқ сақланади.
     */
    public static BigDecimal targetBase(BigDecimal totalAmount, BigDecimal exchangeRate) {
        return totalAmount.multiply(exchangeRate)
                .setScale(Money.AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Сатр base'ларини largest-remainder билан тақсимлайди: йиғинди
     * айнан {@code targetBase(Σamounts, rate)}, ҳар сатр четлашиши
     * ≤ 0.0001 (класс JavaDoc'идаги кафолатлар).
     *
     * @param amounts сатр суммалари ҳужжат валютасида (тартиб сақланади)
     * @return ҳар сатр учун home base - кириш тартибида
     */
    public static List<BigDecimal> lineBases(List<BigDecimal> amounts,
                                             BigDecimal exchangeRate) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal amount : amounts) {
            total = total.add(amount);
        }
        BigDecimal target = targetBase(total, exchangeRate);

        /** Сатр индекси + FLOOR'дан қолган каср - тарқатиш навбати учун. */
        record Remainder(int index, BigDecimal fraction) { }

        List<BigDecimal> bases = new ArrayList<>(amounts.size());
        List<Remainder> remainders = new ArrayList<>(amounts.size());
        BigDecimal assigned = BigDecimal.ZERO;
        for (int i = 0; i < amounts.size(); i++) {
            BigDecimal exact = amounts.get(i).multiply(exchangeRate);
            BigDecimal floor = exact.setScale(Money.AMOUNT_SCALE, RoundingMode.FLOOR);
            bases.add(floor);
            remainders.add(new Remainder(i, exact.subtract(floor)));
            assigned = assigned.add(floor);
        }
        int units = target.subtract(assigned)
                .movePointRight(Money.AMOUNT_SCALE).intValueExact();
        // Энг катта қолдиқ аввал; тенгликда кичик индекс - детерминизм
        remainders.sort(Comparator.comparing(Remainder::fraction).reversed()
                .thenComparingInt(Remainder::index));
        // units ∈ [0, N] исботланган - лекин айланма тарқатиш ҳар қандай
        // мусбат қийматда тугайди (ҳимоя, exception'сиз)
        int cursor = 0;
        while (units > 0 && !remainders.isEmpty()) {
            int index = remainders.get(cursor % remainders.size()).index();
            bases.set(index, bases.get(index).add(UNIT));
            cursor++;
            units--;
        }
        return bases;
    }
}
