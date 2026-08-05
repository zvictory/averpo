package com.averpo.erp.sales.domain;

/**
 * Invoice'нинг тўланганлик ҳолати - денормализация (рўйхат экрани
 * учун). Purchase'даги PaymentStatus такрорланмайди - модуллар
 * бир-бирининг domain'ига боғланмайди (қоида №6).
 *
 * @author Zafar
 */
public enum InvoicePaymentStatus {

    /** Ҳали ҳеч нарса тўланмаган. */
    UNPAID,

    /** Қисман тўланган - қолдиқ бор. */
    PARTIAL,

    /** Тўлиқ тўланган. */
    PAID
}
