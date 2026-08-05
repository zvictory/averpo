package com.averpo.erp.sales.domain;

/**
 * Invoice сатри тури - item'нинг ItemType'идан келиб чиқади
 * (docs/modules/sales.md «Қатъий қарорлар»): INVENTORY item - ITEM
 * (омбордан чиқим + COGS), SERVICE/NON_INVENTORY - SERVICE (омборсиз).
 *
 * @author Zafar
 */
public enum InvoiceLineType {

    /** INVENTORY item - омбордан issue + COGS проводкаси. */
    ITEM,

    /** Хизмат/сақланмайдиган товар - фақат даромад, омборга тегмайди. */
    SERVICE
}
