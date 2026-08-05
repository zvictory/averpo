package com.averpo.erp.purchase.domain;

/**
 * Bill'нинг тўланганлик ҳолати - allocation'лардан денормализация
 * (old-erp-ideas §4: рўйхат экранлари JOIN'сиз тез ишлаши учун).
 * Ҳисоблаш формуласи BillPaymentService'да битта жойда.
 */
public enum PaymentStatus {

    /** Ҳали ҳеч нарса тўланмаган. */
    UNPAID,

    /** Қисман тўланган (0 < paid < total). */
    PARTIAL,

    /** Тўлиқ тўланган (balance_due = 0). */
    PAID;

    /** i18n сарлавҳа калити (bill.paymentStatus.*). */
    public String titleKey() { return "bill.paymentStatus." + name(); }
}
