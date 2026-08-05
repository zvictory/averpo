package com.averpo.erp.purchase.domain;

/**
 * Bill сатрининг тури - проводка йўналишини белгилайди
 * (docs/posting-rules.md «Харид»).
 */
public enum BillLineType {

    /** Товар кирими: item asset счётига Dt + омборга receive. */
    ITEM,

    /** Хизмат/харажат: танланган EXPENSE/COGS счётига Dt. */
    EXPENSE,

    /** Landed cost хизмати (ташиш, божхона): INVENTORY_CLEARING'га Dt,
     * кейин тақсимот операцияси билан receipt'ларга ёйилади. */
    LANDED_COST;

    /** i18n сарлавҳа калити (bill.lineType.*). */
    public String titleKey() { return "bill.lineType." + name(); }
}
