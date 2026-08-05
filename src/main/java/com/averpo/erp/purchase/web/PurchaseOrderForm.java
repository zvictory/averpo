package com.averpo.erp.purchase.web;

import com.averpo.erp.purchase.domain.PurchaseOrder;
import com.averpo.erp.purchase.domain.PurchaseOrderLine;
import com.averpo.erp.shared.web.Fmt;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * PurchaseOrder формаси - EstimateForm'нинг харид томонидаги кўзгуси.
 * Суммалар String (бузуқ киритишда тушунарли BR хабари, BillForm
 * паттерни).
 *
 * @author Zafar
 */
@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrderForm {

    /** Таҳрирда мавжуд ҳужжат id'си (яратишда бўш). */
    private String id;

    /** Танланган таъминотчи id'си (UUID матн кўринишида). */
    private String vendorId;

    /**
     * Ҳужжат санаси - date input, ISO форматда боғланади. Default'ни
     * controller компания zoneId «бугун»и билан беради (JVM tz эмас,
     * қоида 12); таҳрирда {@code from()} мавжуд ҳужжат санасини қўяди.
     */
    private LocalDate poDate;

    /** Кутилган етказиб бериш санаси (ихтиёрий). */
    private LocalDate expectedDate;

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
    public static PurchaseOrderForm empty(int rows) {
        PurchaseOrderForm form = new PurchaseOrderForm();
        for (int i = 0; i < rows; i++) {
            form.lines.add(new LineForm());
        }
        return form;
    }

    /** Таҳрир формаси: мавжуд ҳужжатдан тўлдирилади. */
    public static PurchaseOrderForm from(PurchaseOrder po) {
        PurchaseOrderForm form = new PurchaseOrderForm();
        form.id = po.getId().toString();
        form.vendorId = po.getVendorId().toString();
        form.poDate = po.getPoDate();
        form.expectedDate = po.getExpectedDate();
        form.currency = po.getCurrency().getCode();
        form.exchangeRate = Fmt.n(po.getExchangeRate());
        form.amountsInclusive = po.isAmountsInclusive();
        form.memo = po.getMemo();
        for (PurchaseOrderLine line : po.getLines()) {
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

        /** Буюртма қилинаётган item id'си. */
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
