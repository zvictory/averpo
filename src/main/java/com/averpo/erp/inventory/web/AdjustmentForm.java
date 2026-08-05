package com.averpo.erp.inventory.web;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Ҳужжатли инвентаризация акти формаси (DEC-093): битта омбор, кўп
 * сатр. Ҳар сатрда ЯНГИ qty киритилади (QBO «New quantity») - delta
 * server'да ҳисобланади. Ҳамма сон String - бузуқ киритишда тушунарли BR
 * хабари билан қийматлар сақланиб қайтади (SalesReceiptForm қолипи).
 */
@Getter
@Setter
@NoArgsConstructor
public class AdjustmentForm {

    /** Инвентаризация омбори id'си. */
    private String warehouseId;

    /** Акт санаси - controller компания zoneId «бугун»и билан тўлдиради. */
    private LocalDate date;

    /** Эркин изоҳ. */
    private String memo;

    /** Ташқи ҳужжат рақами (DEC-109, ихтиёрий - қоғоз акт №). */
    private String externalRef;

    /** Сатрлар - Spring indexed binding (auto-grow). */
    private List<LineForm> lines = new ArrayList<>();

    /** Бошланғич/reverse форма учун n та бўш сатр. */
    public static AdjustmentForm empty(int rows) {
        AdjustmentForm form = new AdjustmentForm();
        for (int i = 0; i < rows; i++) {
            form.lines.add(new LineForm());
        }
        return form;
    }

    /** Битта форма сатри - AdjustLineData кўзгуси. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class LineForm {

        /** Item id'си. */
        private String itemId;

        /** ЯНГИ қолдиқ (QBO New quantity) - delta = new − жорий. */
        private String newQty;

        /** Кўпайиш нархи (home) ёки бўш - жорий қиймат (BR-INV-007). */
        private String unitCost;

        /** Сатр изоҳи. */
        private String memo;

        /** Тўлиқ бўш сатр - request'га киритилмайди. */
        public boolean isEmpty() {
            return (itemId == null || itemId.isBlank())
                    && (newQty == null || newQty.isBlank())
                    && (unitCost == null || unitCost.isBlank());
        }
    }
}
