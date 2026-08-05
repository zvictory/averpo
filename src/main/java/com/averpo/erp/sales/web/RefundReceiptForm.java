package com.averpo.erp.sales.web;

import com.averpo.erp.sales.domain.Invoice;
import com.averpo.erp.sales.domain.InvoiceLine;
import com.averpo.erp.shared.web.Fmt;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Пул қайтариш чеки формаси (returns.md) - CreditMemoForm'нинг айнан
 * кўзгуси + пул счёти танлови. Ҳамма сон String - бузуқ киритишда
 * тушунарли BR хабари билан қийматлар сақланиб қайтади.
 *
 * @author Zafar
 */
@Getter
@Setter
@NoArgsConstructor
public class RefundReceiptForm {

    /** Мижоз id'си. */
    private String customerId;

    /** Ихтиёрий асл invoice ҳаваласи (prefill манбаси, hidden). */
    private String invoiceId;

    /** Пул қайтадиган банк/касса счёти id'си. */
    private String bankAccountId;

    /** Ҳужжат санаси - controller компания zoneId «бугун»и билан тўлдиради (JVM tz эмас, қоида 12). */
    private LocalDate rrDate;

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
    public static RefundReceiptForm empty(int rows) {
        RefundReceiptForm form = new RefundReceiptForm();
        for (int i = 0; i < rows; i++) {
            form.lines.add(new LineForm());
        }
        return form;
    }

    /**
     * Асл invoice'дан prefill (QBO оқими): сатрлар тўлиқ кўчади, ҚҚС
     * ставка ҚИЙМАТИ ҳам snapshot сифатида - орада каталог ўзгарган
     * бўлса ҳам асл ставкада қайтим. Пул счётини фойдаланувчи танлайди.
     */
    public static RefundReceiptForm from(Invoice invoice) {
        RefundReceiptForm form = new RefundReceiptForm();
        form.customerId = invoice.getCustomerId().toString();
        form.invoiceId = invoice.getId().toString();
        form.currency = invoice.getCurrency().getCode();
        form.exchangeRate = Fmt.n(invoice.getExchangeRate());
        form.amountsInclusive = invoice.isAmountsInclusive();
        for (InvoiceLine line : invoice.getLines()) {
            LineForm lf = new LineForm();
            lf.itemId = line.getItemId().toString();
            lf.warehouseId = line.getWarehouseId() == null
                    ? null : line.getWarehouseId().toString();
            lf.quantity = Fmt.n(line.getQuantity());
            // Inclusive ҳужжатда фойдаланувчи gross кўради (Bill қолипи)
            lf.unitPrice = Fmt.n(line.getUnitPrice());
            lf.unitId = line.getUnitId() == null ? null : line.getUnitId().toString();
            lf.taxRateId = line.getTaxRateId() == null
                    ? null : line.getTaxRateId().toString();
            lf.taxRateValue = line.getTaxRateValue() == null
                    ? null : line.getTaxRateValue().toPlainString();
            lf.classId = line.getClassId() == null ? null : line.getClassId().toString();
            lf.memo = line.getMemo();
            form.lines.add(lf);
        }
        return form;
    }

    /** Битта форма сатри - RefundReceipt LineData кўзгуси. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class LineForm {

        /** Item id'си. */
        private String itemId;

        /** Омбор (ITEM сатрида шарт - BR-RET-002). */
        private String warehouseId;

        /** Миқдор (киритилган бирликда). */
        private String quantity;

        /** Бирлик нархи. */
        private String unitPrice;

        /** Киритилган бирлик ёки бўш - base. */
        private String unitId;

        /** ҚҚС ставкаси id'си ёки бўш. */
        private String taxRateId;

        /** Ставка ҚИЙМАТИ snapshot'и (prefill'дан, hidden) ёки бўш. */
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
