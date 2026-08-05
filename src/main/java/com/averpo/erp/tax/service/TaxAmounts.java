package com.averpo.erp.tax.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Сатр суммасини нетто/ҚҚСга ажратиш - соф арифметика (Money /
 * MoneyAllocation паттерни, docs/modules/tax.md «Ҳисоблаш»). Bill ва
 * Invoice service'лари шу ягона жойни чақиради - формула иккига
 * бўлинмайди.
 *
 * <p>Иккала нарх режими:
 * <ul>
 *   <li><b>Exclusive</b> (default): киритилган raw = НЕТТО;
 *       tax = round(net × r); gross = net + tax.</li>
 *   <li><b>Inclusive</b>: киритилган raw = GROSS; net = round(gross ÷
 *       (1 + r)); tax = gross − net - АНИҚ КОМПЛЕМЕНТ, шунинг учун
 *       {@code net + tax == gross} айнан (яхлитлаш дрейфи йўқ).</li>
 * </ul>
 * Ставка null/0 (NO_TAX): tax = 0, иккала режимда ҳам net = raw.
 * Барча ҳисоб ҳужжат валютасида, scale 4 HALF_UP.
 *
 * @param net нетто сумма (GL дебет/даромад асоси)
 * @param tax сатр ҚҚСи (SALES_TAX_PAYABLE)
 */
public record TaxAmounts(BigDecimal net, BigDecimal tax) {

    /** Суммалар аниқлиги - Money.AMOUNT_SCALE билан бир хил. */
    private static final int SCALE = 4;

    /** gross = net + tax (тўлов/AP/AR шу устида ишлайди). */
    public BigDecimal gross() {
        return net.add(tax);
    }

    /**
     * Raw суммани ставка ва режим бўйича нетто/ҚҚСга ажратади.
     *
     * @param raw         киритилган сумма (exclusive'да net, inclusive'да gross)
     * @param ratePercent ставка фоизи (12 = 12%) ёки null/0 - солиқсиз
     * @param inclusive   true - raw ичида ҚҚС бор
     */
    public static TaxAmounts of(BigDecimal raw, BigDecimal ratePercent, boolean inclusive) {
        BigDecimal base = raw.setScale(SCALE, RoundingMode.HALF_UP);
        if (ratePercent == null || ratePercent.signum() == 0) {
            return new TaxAmounts(base, BigDecimal.ZERO.setScale(SCALE));
        }
        BigDecimal r = ratePercent.movePointLeft(2); // фоиз → улуш
        if (inclusive) {
            BigDecimal net = base.divide(BigDecimal.ONE.add(r), SCALE, RoundingMode.HALF_UP);
            // Аниқ комплемент: net + tax == gross айнан
            return new TaxAmounts(net, base.subtract(net));
        }
        BigDecimal tax = base.multiply(r).setScale(SCALE, RoundingMode.HALF_UP);
        return new TaxAmounts(base, tax);
    }
}
