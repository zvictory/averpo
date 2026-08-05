package com.averpo.erp.sales.web;

import com.averpo.erp.sales.domain.Estimate;
import com.averpo.erp.sales.domain.EstimateLine;
import com.averpo.erp.shared.web.Fmt;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Estimate формаси. Суммалар атайлаб String - бузуқ киритишда
 * BindException ўрнига тушунарли (кирилл) BR хабари билан қайтамиз ва
 * киритилган қийматлар формада сақланиб қолади (InvoiceForm паттерни).
 *
 * @author Zafar
 */
@Getter
@Setter
@NoArgsConstructor
public class EstimateForm {

    /** Таҳрирда мавжуд ҳужжат id'си (яратишда бўш). */
    private String id;

    /** Танланган мижоз id'си (UUID матн кўринишида). */
    private String customerId;

    /**
     * Ҳужжат санаси - date input, ISO форматда боғланади. Default'ни
     * controller компания zoneId «бугун»и билан беради (JVM tz эмас,
     * қоида 12); таҳрирда {@code from()} мавжуд ҳужжат санасини қўяди.
     */
    private LocalDate estimateDate;

    /** Таклифнинг амал қилиш муддати (ихтиёрий). */
    private LocalDate expirationDate;

    /** Ҳужжат валютаси ISO коди - бўш бўлса home currency. */
    private String currency;

    /** Ҳужжат курси - home валютада 1 (сервер текширади). */
    private String exchangeRate;

    /** Нархлар режими: true - ҚҚС ичида (docs/modules/tax.md). */
    private boolean amountsInclusive = false;

    /** Эркин изоҳ. */
    private String memo;

    /** Сатрлар - Spring indexed binding (lines[0].itemId...). */
    private List<LineForm> lines = new ArrayList<>();

    /** Бошланғич форма учун n та бўш сатр. */
    public static EstimateForm empty(int rows) {
        EstimateForm form = new EstimateForm();
        for (int i = 0; i < rows; i++) {
            form.lines.add(new LineForm());
        }
        return form;
    }

    /** Таҳрир формаси: мавжуд ҳужжатдан тўлдирилади. */
    public static EstimateForm from(Estimate estimate) {
        EstimateForm form = new EstimateForm();
        form.id = estimate.getId().toString();
        form.customerId = estimate.getCustomerId().toString();
        form.estimateDate = estimate.getEstimateDate();
        form.expirationDate = estimate.getExpirationDate();
        form.currency = estimate.getCurrency().getCode();
        form.exchangeRate = Fmt.n(estimate.getExchangeRate());
        form.amountsInclusive = estimate.isAmountsInclusive();
        form.memo = estimate.getMemo();
        for (EstimateLine line : estimate.getLines()) {
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

    /** Битта форма сатри - омбор/счёт йўқ (GL'сиз ҳужжат). */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class LineForm {

        /** Таклиф қилинаётган item id'си. */
        private String itemId;

        /** Миқдор. */
        private String quantity;

        /** Бирлик нархи (ҳужжат валютасида, танланган бирликка). */
        private String unitPrice;

        /** Киритиш бирлиги id'си (UoM) ёки бўш - item base. */
        private String unitId;

        /** Танланган ҚҚС ставкаси id'си ёки бўш - солиқсиз. */
        private String taxRateId;

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
