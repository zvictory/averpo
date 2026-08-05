package com.averpo.erp.purchase.domain;

/**
 * PurchaseOrder ҳолатлари (docs/modules/estimates-po.md, Finance.xsd
 * POStatus кўзгуси): GL'сиз ҳужжат - ҳаёт цикли фақат шу status билан.
 * Ўтишлар қоидаси PurchaseOrder entity'сида.
 *
 * @author Zafar
 */
public enum PurchaseOrderStatus {

    /** Очиқ буюртма (default) - таҳрир/айлантириш мумкин. */
    OPEN,

    /** Ёпилган: bill'га айлантирилган (автоматик) ёки қўлда бекор. */
    CLOSED;

    /** i18n калити (po.status.OPEN ...). */
    public String titleKey() {
        return "po.status." + name();
    }
}
