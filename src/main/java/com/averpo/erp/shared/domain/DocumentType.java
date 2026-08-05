package com.averpo.erp.shared.domain;

/**
 * Рақамланадиган ҳужжат турлари каталоги - {@link DocumentSequence}
 * қаторлари шу enum бўйича топилади (DB'да string сифатида сақланади).
 *
 * <p>Янги ҳужжат тури киритилганда: аввал шу enum'га қиймат, кейин
 * document_sequence жадвалига seed changeset (prefix/padding билан) -
 * акс ҳолда рақам сўралганда BR-SEQ-001 отилади.
 *
 * @author Zafar
 */
public enum DocumentType {

    /** Журнал проводкаси (JE-2026-000001, ledger модули). */
    JOURNAL_ENTRY,

    /** Сотув ҳисобварағи (INV-2026-00001, 7-босқич). */
    INVOICE,

    /** Сотув чеки - сотув + тўлов бир ҳужжатда (SR-2026-00001,
     *  posting-rules «Сотув чеки»; invoice'нинг AR'сиз кўзгуси). */
    SALES_RECEIPT,

    /** Харид ҳисобварағи (BILL-2026-00001, 6-босқич). */
    BILL,

    /** Тўлов ҳужжати (PAY-2026-00001, 6-7-босқичлар). */
    PAYMENT,

    /** Landed cost тақсимоти (LC-2026-00001, 6-босқич 5-туртки). */
    LANDED_COST,

    /** Мижоз тўлови/тушум (RCPT-2026-00001, 7-босқич). */
    RECEIPT,

    /** Банк транзакцияси (BT-2026-00001, 8-босқич). */
    BANK_TXN,

    /** Мижоз кредит-нотаси (CM-2026-00001, returns.md). */
    CREDIT_MEMO,

    /** Таъминотчи кредит-нотаси (VC-2026-00001, returns.md). */
    VENDOR_CREDIT,

    /** Мижозга пул қайтариш чеки (RR-2026-00001, returns.md). */
    REFUND_RECEIPT,

    /** Мижозга таклиф/смета (EST-2026-00001, GL'сиз - estimates-po.md). */
    ESTIMATE,

    /** Таъминотчига буюртма (PO-2026-00001, GL'сиз - estimates-po.md). */
    PURCHASE_ORDER,

    /**
     * Ойлик иш ҳақи ҳисоблаши (PAYR-2026-00001, payroll.md).
     * Spec'даги PAY префикси PAYMENT'га банд (014) - шунга PAYR.
     */
    PAYROLL_RUN,

    /**
     * Иш ҳақи тўлови - аванс/ойлик (PAYP-2026-00001, payroll.md 23в).
     * PAYR (run) ва PAY (BillPayment) префикслари банд - шунга PAYP.
     */
    PAYROLL_PAYMENT,

    /**
     * Ҳужжатли инвентаризация акти (ADJ-2026-00001, inventory.md,
     * Arbitr-093): кўп сатрли, битта омбор, дарҳол POSTED, актга битта JE.
     */
    STOCK_ADJUSTMENT,

    /**
     * Ҳужжатли омборлараро кўчириш акти (WTR-2026-00001, inventory.md,
     * Arbitr-093): кўп сатрли, манба/манзил омбор, GL'сиз.
     */
    STOCK_TRANSFER,

    /**
     * Фойдаланувчи профил расми (аватар, Arbitr-101). Рақамланмайди -
     * {@link DocumentSequence} қатори ЙЎҚ (рақам сўралмайди), фақат
     * {@code Attachment} target тури: {@code app_user} қаторига боғланади
     * (AttachmentService.DOCUMENT_TABLES), BR-ATT-003 EXISTS текшируви шу
     * жадвалда бўлади. Юклаш {@code uploadImage} орқали (BR-ATT-005/006).
     */
    APP_USER,

    /**
     * Компания логоси (Arbitr-112). APP_USER каби рақамланмайди - фақат
     * {@code Attachment} target тури: {@code company_settings} қаторига
     * боғланади (singleton, битта лого). Юклаш {@code uploadImage} орқали.
     */
    COMPANY
}
