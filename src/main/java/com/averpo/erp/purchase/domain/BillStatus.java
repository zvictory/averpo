package com.averpo.erp.purchase.domain;

/**
 * Bill ҳаёт цикли - ledger entry модели билан бир хил (темир қоида №3):
 * POSTED ҳужжат ўзгартирилмайди, фақат reverse қилинади. Old-erp'даги
 * approve workflow атайлаб олинмаган (spec, «Қатъий қарорлар»).
 */
public enum BillStatus {

    /** Қоралама - таҳрирланади, GL/омборга таъсири йўқ. */
    DRAFT,

    /** Ўтказилган - GL ва омборда акс этган, ўзгармас. */
    POSTED,

    /** Сторно қилинган - GL/омбор қайтарилган, тарихда қолади. */
    REVERSED;

    /** i18n сарлавҳа калити (bill.status.*). */
    public String titleKey() { return "bill.status." + name(); }
}
