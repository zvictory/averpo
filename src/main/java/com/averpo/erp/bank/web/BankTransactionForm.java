package com.averpo.erp.bank.web;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Банк транзакцияси формаси - учала тур битта формада (Alpine тур
 * бўйича майдонларни алмаштиради). Суммалар String - бузуқ киритишда
 * тушунарли BR хабари билан қайтамиз, қийматлар сақланади
 * (BillForm паттерни).
 */
@Getter
@Setter
@NoArgsConstructor
public class BankTransactionForm {

    /** Транзакция тури: DEPOSIT / EXPENSE / TRANSFER. */
    private String type = "DEPOSIT";

    /** Асосий банк (deposit'да қабул қилувчи, expense'да тўловчи, transfer'да манба). */
    private String bankAccountId;

    /** Transfer манзил банки. */
    private String toBankAccountId;

    /** Транзакция санаси - controller компания zoneId «бугун»и билан тўлдиради (JVM tz эмас, қоида 12). */
    private LocalDate txnDate;

    /** Банк валютаси курси (чет валютали банкда шарт). */
    private String exchangeRate;

    /** Transfer манба томон суммаси. */
    private String fromAmount;

    /** Transfer манзил томон суммаси (конверсияда). */
    private String toAmount;

    /** Transfer манзил томон курси (конверсияда). */
    private String toRate;

    /** Ихтиёрий контрагент (QBO payee). */
    private String contactId;

    /** Ихтиёрий тўлов усули id'си (Arbitr-033, чиқим формаси). */
    private String paymentMethodId;

    /** Ихтиёрий ҳужжат/чек рақами (QBO Ref no, чиқим формаси). */
    private String refNo;

    /** PER_TXN режимида сарлавҳадаги битта Йўналиш - сатрларга тарқатилади. */
    private String classId;

    /** Эркин изоҳ. */
    private String memo;

    /** DEPOSIT/EXPENSE сатрлари - Spring indexed binding. */
    private List<LineForm> lines = new ArrayList<>();

    /** Бошланғич форма учун n та бўш сатр. */
    public static BankTransactionForm empty(int rows) {
        BankTransactionForm form = new BankTransactionForm();
        for (int i = 0; i < rows; i++) {
            form.lines.add(new LineForm());
        }
        return form;
    }

    /** Битта сатр: манба/харажат счёти + сумма + ихтиёрий контакт. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class LineForm {

        /** Сатр счёти id'си. */
        private String accountId;

        /** Сатр суммаси (банк валютасида). */
        private String amount;

        /** Ихтиёрий контрагент (QBO received from). */
        private String contactId;

        /** PER_LINE режимида сатрнинг Йўналиши (class-tracking.md). */
        private String classId;

        /** Сатр изоҳи. */
        private String memo;

        /** Тўлиқ бўш сатр - request'га киритилмайди. */
        public boolean isEmpty() {
            return (accountId == null || accountId.isBlank())
                    && (amount == null || amount.isBlank());
        }
    }
}
