package com.averpo.erp.sales.domain;

/**
 * Estimate ҳолатлари (docs/modules/estimates-po.md, Finance.xsd
 * TxnStatus кўзгуси): GL'сиз ҳужжатда POSTED йўқ - ҳаёт цикли фақат
 * шу status билан бошқарилади. Ўтишлар қоидаси Estimate entity'сида.
 *
 * @author Zafar
 */
public enum EstimateStatus {

    /** Мижоз жавоби кутилмоқда (default). */
    PENDING,

    /** Мижоз қабул қилган - айлантиришга тайёр. */
    ACCEPTED,

    /** Мижоз рад этган - таҳрир/айлантириш тақиқ (BR-EST-002). */
    REJECTED,

    /** Ёпилган: invoice'га айлантирилган (автоматик) ёки қўлда. */
    CLOSED;

    /** i18n калити (est.status.PENDING ...). */
    public String titleKey() {
        return "est.status." + name();
    }
}
