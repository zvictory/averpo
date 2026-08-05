package com.averpo.erp.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Валюта курс фарқи ҳисоблари - ПУЛ ФОРМУЛАСИ битта жойда туради
 * (PERF-backlog5: Bill/Invoice тўлов service'ларида айнан бир хил
 * формула иккита нусхада эди - ажралиб кетиш хавфи).
 */
public final class Fx {

    /** Base суммалар аниқлиги - Money baseAmount scale'и билан мос. */
    private static final int BASE_SCALE = 4;

    /** Utility класс - instance ясалмайди. */
    private Fx() { }

    /**
     * Realized курс фарқининг home (base) қиймати:
     * {@code amount × (rateA - rateB)}, 4 хона HALF_UP.
     *
     * <p>Йўналиш чақирувчиники: AP тўловида (bill курси - тўлов курси),
     * AR тушумида (тўлов курси - invoice курси) - аргументлар шу
     * тартибда берилади, формуланинг ўзи иккала томон учун бир хил.
     * Мусбат натижа фойда томонга ёзилишини posting-rules белгилайди.
     */
    public static BigDecimal realizedFxDifference(BigDecimal amount,
                                                  BigDecimal rateA,
                                                  BigDecimal rateB) {
        return amount.multiply(rateA.subtract(rateB))
                .setScale(BASE_SCALE, RoundingMode.HALF_UP);
    }
}
