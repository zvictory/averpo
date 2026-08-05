package com.averpo.erp.sales.web;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Сотув чеки формаси (posting-rules «Сотув чеки») - InvoiceForm'нинг
 * кўзгуси + тўлов счёти танлови, лекин due date/invoice ҳаваласисиз
 * (тўлов дарҳол). Ҳамма сон String - бузуқ киритишда тушунарли BR
 * хабари билан қийматлар сақланиб қайтади.
 *
 * @author Zafar
 */
@Getter
@Setter
@NoArgsConstructor
public class SalesReceiptForm {

    /** Мижоз id'си. */
    private String customerId;

    /** Пул тушадиган банк/касса счёти id'си. */
    private String bankAccountId;

    /** Ҳужжат санаси - controller компания zoneId «бугун»и билан тўлдиради (JVM tz эмас, қоида 12). */
    private LocalDate srDate;

    /** Ҳужжат валютаси ISO коди. */
    private String currency;

    /** Ҳужжат курси. */
    private String exchangeRate;

    /** Нархлар режими: ҚҚС ичидами (tax.md). */
    private boolean amountsInclusive = false;

    /** PER_TXN режимида сарлавҳадаги битта Йўналиш. */
    private String classId;

    /** Эркин изоҳ. */
    private String memo;

    /** Сатрлар - Spring indexed binding. */
    private List<LineForm> lines = new ArrayList<>();

    /** Бошланғич форма учун n та бўш сатр. */
    public static SalesReceiptForm empty(int rows) {
        SalesReceiptForm form = new SalesReceiptForm();
        for (int i = 0; i < rows; i++) {
            form.lines.add(new LineForm());
        }
        return form;
    }

    /** Битта форма сатри - SalesReceipt LineData кўзгуси. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class LineForm {

        /** Item id'си. */
        private String itemId;

        /** Омбор (ITEM сатрида шарт - BR-SR-001). */
        private String warehouseId;

        /** Миқдор (киритилган бирликда). */
        private String quantity;

        /** Бирлик нархи. */
        private String unitPrice;

        /** Киритилган бирлик ёки бўш - base. */
        private String unitId;

        /** ҚҚС ставкаси id'си ёки бўш. */
        private String taxRateId;

        /** Ставка ҚИЙМАТИ snapshot'и (hidden) ёки бўш - service жорий қийматни олади. */
        private String taxRateValue;

        /** PER_LINE режимида сатрнинг Йўналиши. */
        private String classId;

        /** Сатр изоҳи. */
        private String memo;

        /** Тўлиқ бўш сатр - request'га киритилмайди. */
        public boolean isEmpty() {
            return (itemId == null || itemId.isBlank())
                    && (quantity == null || quantity.isBlank())
                    && (unitPrice == null || unitPrice.isBlank());
        }
    }
}
