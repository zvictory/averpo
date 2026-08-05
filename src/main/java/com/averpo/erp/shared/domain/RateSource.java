package com.averpo.erp.shared.domain;

/**
 * Валюта курси ёзувининг манбаи (docs/modules/transfer.md Т3). Курс
 * тарихи append-only - ҳар ўзгариш алоҳида ёзув, манба билан фарқланади
 * (тарих экранида кўринади).
 *
 * @author Zafar
 */
public enum RateSource {

    /** Марказий банк импорти (scheduler ёки қўлда импорт тугмаси). */
    CBU,

    /** Қўлда киритилган ёки ўтказма орқали қайд этилган ҳақиқий курс. */
    MANUAL
}
