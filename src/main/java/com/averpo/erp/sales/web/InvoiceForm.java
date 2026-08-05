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
 * Invoice формаси. Суммалар атайлаб String - бузуқ киритишда
 * BindException ўрнига тушунарли (кирилл) BR хабари билан қайтамиз ва
 * киритилган қийматлар формада сақланиб қолади (BillForm паттерни).
 */
@Getter
@Setter
@NoArgsConstructor
public class InvoiceForm {

    /** Таҳрирда мавжуд draft id'си (яратишда бўш). */
    private String id;

    /**
     * Айлантириш оқимида манба estimate id'си (hidden, estimates-po.md):
     * тўлдирилган бўлса сақлангач InvoiceController estimate'ни CLOSED +
     * linked қилади.
     */
    private String estimateId;

    /** Танланган мижоз id'си (UUID матн кўринишида). */
    private String customerId;

    /**
     * Ҳужжат санаси - date input, ISO форматда боғланади. Default'ни
     * controller компания zoneId «бугун»и билан беради (JVM tz эмас,
     * қоида 12); таҳрирда {@code from()} мавжуд ҳужжат санасини қўяди.
     */
    private LocalDate invoiceDate;

    /** Тўлов муддати - бўш қолса мижоз тўлов шартидан автоматик. */
    private LocalDate dueDate;

    /** Ҳужжат валютаси ISO коди - бўш бўлса home currency. */
    private String currency;

    /** Ҳужжат курси - home валютада 1 (сервер текширади). */
    private String exchangeRate;

    /** Нархлар режими: true - ҚҚС ичида (inclusive), false - ҚҚСсиз (docs/modules/tax.md). */
    private boolean amountsInclusive = false;

    /** PER_TXN режимида сарлавҳадаги битта Йўналиш - сатрларга тарқатилади. */
    private String classId;

    /** Эркин изоҳ. */
    private String memo;

    /** Сатрлар - Spring indexed binding (lines[0].itemId...). */
    private List<LineForm> lines = new ArrayList<>();

    /** Бошланғич форма учун n та бўш сатр. */
    public static InvoiceForm empty(int rows) {
        InvoiceForm form = new InvoiceForm();
        for (int i = 0; i < rows; i++) {
            form.lines.add(new LineForm());
        }
        return form;
    }

    /** Таҳрир формаси: мавжуд DRAFT'дан тўлдирилади. */
    public static InvoiceForm from(Invoice invoice) {
        InvoiceForm form = new InvoiceForm();
        form.id = invoice.getId().toString();
        form.customerId = invoice.getCustomerId().toString();
        form.invoiceDate = invoice.getInvoiceDate();
        form.dueDate = invoice.getDueDate();
        form.currency = invoice.getCurrency().getCode();
        form.exchangeRate = Fmt.n(invoice.getExchangeRate());
        form.amountsInclusive = invoice.isAmountsInclusive();
        form.memo = invoice.getMemo();
        for (InvoiceLine line : invoice.getLines()) {
            LineForm lf = new LineForm();
            lf.itemId = line.getItemId().toString();
            lf.warehouseId = line.getWarehouseId() == null
                    ? null : line.getWarehouseId().toString();
            lf.quantity = Fmt.n(line.getQuantity());
            lf.unitPrice = Fmt.n(line.getUnitPrice());
            lf.unitId = line.getUnitId() == null ? null : line.getUnitId().toString();
            lf.taxRateId = line.getTaxRateId() == null ? null : line.getTaxRateId().toString();
            lf.classId = line.getClassId() == null ? null : line.getClassId().toString();
            lf.memo = line.getMemo();
            form.lines.add(lf);
        }
        // PER_TXN сарлавҳа select'и учун: ҳамма сатрда бир хил бўлса шу
        // қиймат prefill (тарқатишнинг тескари йўли)
        if (!form.lines.isEmpty() && form.lines.get(0).classId != null
                && form.lines.stream().allMatch(l ->
                        form.lines.get(0).classId.equals(l.classId))) {
            form.classId = form.lines.get(0).classId;
        }
        return form;
    }

    /**
     * Айлантириш prefill'и (estimates-po.md): мижоз/валюта/сатрлар/ҚҚС
     * ставкалари estimate'дан, сана - бугунги (форма default'и).
     * Чет валютада курс АТАЙЛАБ бўш қолдирилади - жим «курс 1» билан
     * сақланиб қолмасин (сервер BR билан сўрайди); омбор ҳам бўш -
     * фойдаланувчи INVENTORY сатрларга формада танлайди.
     */
    public static InvoiceForm fromEstimate(com.averpo.erp.sales.domain.Estimate estimate,
                                           String homeCurrency) {
        InvoiceForm form = new InvoiceForm();
        form.estimateId = estimate.getId().toString();
        form.customerId = estimate.getCustomerId().toString();
        form.currency = estimate.getCurrency().getCode();
        form.exchangeRate = estimate.getCurrency().getCode().equals(homeCurrency)
                ? "1" : "";
        form.amountsInclusive = estimate.isAmountsInclusive();
        form.memo = estimate.getMemo();
        for (com.averpo.erp.sales.domain.EstimateLine line : estimate.getLines()) {
            LineForm lf = new LineForm();
            lf.itemId = line.getItemId().toString();
            lf.quantity = Fmt.n(line.getQuantity());
            lf.unitPrice = Fmt.n(line.getUnitPrice());
            lf.unitId = line.getUnitId() == null ? null : line.getUnitId().toString();
            lf.taxRateId = line.getTaxRateId() == null ? null : line.getTaxRateId().toString();
            lf.memo = line.getMemo();
            form.lines.add(lf);
        }
        return form;
    }

    /**
     * Битта форма сатри - тури item'дан аниқланади (сервер), даромад
     * счёти item default'идан (UI'да алоҳида танланмайди).
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class LineForm {

        /** Сотилаётган item id'си. */
        private String itemId;

        /** INVENTORY item учун омбор id'си. */
        private String warehouseId;

        /** Миқдор. */
        private String quantity;

        /** Бирлик сотув нархи (ҳужжат валютасида, танланган бирликка). */
        private String unitPrice;

        /** Киритиш бирлиги id'си (UoM) ёки бўш - item base. */
        private String unitId;

        /** Танланган ҚҚС ставкаси id'си ёки бўш - солиқсиз (docs/modules/tax.md). */
        private String taxRateId;

        /** PER_LINE режимида сатрнинг Йўналиши (class-tracking.md). */
        private String classId;

        /** Сатр изоҳи. */
        private String memo;

        /** Тўлиқ бўш сатр - request'га киритилмайди (ишлатилмаган HTMX қатор). */
        public boolean isEmpty() {
            return isBlank(itemId) && isBlank(quantity) && isBlank(unitPrice);
        }

        /** null ёки бўш сатрми. */
        private static boolean isBlank(String s) {
            return s == null || s.isBlank();
        }
    }
}
