package com.averpo.erp.payroll.domain;

/**
 * Иш ҳақи тўлови тури (docs/modules/payroll.md 23в) - ФАҚАТ белги:
 * ведомость ва рўйхатда аванс/ойлик тўловни фарқлаш учун. Проводкаси
 * (posting-rules «Иш ҳақи») иккисида АЙНАН бир хил: Dr PAYROLL_CLEARING
 * (ходим кесимида) / Cr банк-касса - шунга тур GL мантиғига таъсир
 * қилмайди, фақат кўриниш/ҳисобот белгиси.
 *
 * @author Zafar
 */
public enum PayrollPaymentType {

    /** Ой ўртасидаги аванс - run (ҳисоблаш)дан олдин ҳам тўланиши мумкин. */
    ADVANCE,

    /** Ой охирги иш ҳақи - clearing қолдиғини ёпувчи асосий тўлов. */
    SALARY
}
