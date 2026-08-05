package com.averpo.erp.inventory.web;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Ҳужжатли омборлараро кўчириш акти формаси (Arbitr-093): манба/манзил
 * омбор, кўп сатр (item + qty). Ҳамма сон String - бузуқ киритишда BR
 * хабари билан қийматлар сақланиб қайтади (SalesReceiptForm қолипи).
 */
@Getter
@Setter
@NoArgsConstructor
public class TransferForm {

    /** Манба омбор id'си (қаердан). */
    private String fromWarehouseId;

    /** Манзил омбор id'си (қаерга). */
    private String toWarehouseId;

    /** Акт санаси - controller компания zoneId «бугун»и билан тўлдиради. */
    private LocalDate date;

    /** Эркин изоҳ. */
    private String memo;

    /** Ташқи ҳужжат рақами (Arbitr-109, ихтиёрий - қоғоз акт №). */
    private String externalRef;

    /** Сатрлар - Spring indexed binding (auto-grow). */
    private List<LineForm> lines = new ArrayList<>();

    /** Бошланғич/reverse форма учун n та бўш сатр. */
    public static TransferForm empty(int rows) {
        TransferForm form = new TransferForm();
        for (int i = 0; i < rows; i++) {
            form.lines.add(new LineForm());
        }
        return form;
    }

    /** Битта форма сатри - TransferLineData кўзгуси. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class LineForm {

        /** Item id'си. */
        private String itemId;

        /** Кўчириладиган миқдор. */
        private String qty;

        /** Сатр изоҳи. */
        private String memo;

        /** Тўлиқ бўш сатр - request'га киритилмайди. */
        public boolean isEmpty() {
            return (itemId == null || itemId.isBlank())
                    && (qty == null || qty.isBlank());
        }
    }
}
