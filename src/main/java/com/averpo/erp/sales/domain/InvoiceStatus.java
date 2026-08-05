package com.averpo.erp.sales.domain;

/**
 * Invoice ҳаёт цикли ҳолати - ledger/bill модели билан бир хил
 * (ТЕМИР ҚОИДА №3: POSTED ўзгармас, фақат reverse).
 * Purchase'даги BillStatus такрорланмайди - модуллар бир-бирининг
 * domain'ига боғланмайди (қоида №6).
 *
 * @author Zafar
 */
public enum InvoiceStatus {

    /** Қоралама - эркин таҳрир, ўчириш мумкин. */
    DRAFT,

    /** Ўтказилган - GL ва омборда акс этган, ўзгармас. */
    POSTED,

    /** Сторно қилинган - GL сторноси ёзилган, товар омборга қайтган. */
    REVERSED
}
