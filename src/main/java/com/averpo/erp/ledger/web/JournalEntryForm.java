package com.averpo.erp.ledger.web;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Қўлда проводка формаси. Суммалар атайлаб String - бузуқ киритишда
 * BindException ўрнига ўзимиз тушунарли (кирилл) хабар билан
 * PostingException кўтарамиз ва киритилган қийматлар сақланиб қолади.
 *
 * @author Zafar
 */
@Getter
@Setter
@NoArgsConstructor
public class JournalEntryForm {

    /**
     * Проводка санаси - формада date input, ISO форматда боғланади.
     * Default'ни controller компания zoneId «бугун»и билан беради
     * (JVM tz эмас, қоида 12/Arbitr-044).
     */
    private LocalDate entryDate;

    /** Эркин тавсиф. */
    private String description;

    /**
     * Ҳужжат валютаси ISO коди (QBO parity, Arbitr-107) - бўш бўлса home
     * currency. QBO'да JournalEntry битта валютада (CurrencyRef header'да):
     * бу қиймат ҲАММА сатрга тарқатилади, шунда бутун проводка ягона
     * валютада бўлади (entities.md:97-98 эталони).
     */
    private String currency;

    /**
     * Курс (1 currency = rate home) - КАНОНИК, home валютада 1. rateBlock
     * компонентининг hidden name="exchangeRate" каноник қиймати; ҳамма
     * сатрга тарқатилади (курс энди сатрда эмас, header'да - Arbitr-107).
     */
    private String exchangeRate;

    /** PER_TXN режимида сарлавҳадаги битта Йўналиш - сатрларга тарқатилади. */
    private String classId;

    /** Проводка сатрлари - Spring indexed binding (lines[0].accountId...). */
    private List<LineForm> lines = new ArrayList<>();

    /** Бошланғич форма учун n та бўш сатр. */
    public static JournalEntryForm empty(int rows) {
        JournalEntryForm form = new JournalEntryForm();
        for (int i = 0; i < rows; i++) {
            form.lines.add(new LineForm());
        }
        return form;
    }

    /** Битта форма сатри - счёт id, валюта, курс, дебет/кредит суммаси, изоҳ. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class LineForm {

        /** Танланган счёт id'си (UUID матн кўринишида). */
        private String accountId;

        // Arbitr-107: валюта/курс энди сатрда ЭМАС - header'да (QBO parity).
        // Controller header currency/exchangeRate'ни ҳар сатрга тарқатади.

        /** Дебет суммаси - кредит билан бирга тўлдирилмайди (XOR). */
        private String debitAmount;

        /** Кредит суммаси. */
        private String creditAmount;

        /** Сатр изоҳи. */
        private String memo;

        /** PER_LINE режимида сатрнинг Йўналиши (class-tracking.md). */
        private String classId;

        /** Тўлиқ бўш сатр - request'га киритилмайди. */
        public boolean isEmpty() {
            return isBlank(accountId) && isBlank(debitAmount) && isBlank(creditAmount);
        }

        /** null ёки бўш сатрми. */
        private static boolean isBlank(String s) {
            return s == null || s.isBlank();
        }
    }
}
