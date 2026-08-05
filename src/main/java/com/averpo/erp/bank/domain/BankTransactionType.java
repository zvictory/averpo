package com.averpo.erp.bank.domain;

/**
 * Банк транзакцияси тури - QBO Banking формаларига мос
 * (docs/modules/banking.md): тур проводка йўналишини белгилайди.
 *
 * @author Zafar
 */
public enum BankTransactionType {

    /** Кирим (QBO Bank Deposit): банк Dt / сатр манба счётлари Cr. */
    DEPOSIT,

    /** Чиқим (QBO Expense): сатр счётлари Dt / банк Cr (AP'сиз тўғри тўлов). */
    EXPENSE,

    /** Ўтказма (QBO Transfer): манзил банк Dt / манба банк Cr; конверсияда фарқ FX'га. */
    TRANSFER;

    /** i18n калити: bt.type.DEPOSIT ва ҳ.к. */
    public String titleKey() {
        return "bt.type." + name();
    }
}
