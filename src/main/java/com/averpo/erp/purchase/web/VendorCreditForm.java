package com.averpo.erp.purchase.web;

import com.averpo.erp.purchase.domain.Bill;
import com.averpo.erp.purchase.domain.BillLine;
import com.averpo.erp.purchase.domain.BillLineType;
import com.averpo.erp.shared.web.Fmt;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Таъминотчи кредит-нотаси формаси (returns.md) - BillForm'нинг
 * қайтариш кўзгуси (ITEM/EXPENSE сатрлар, LANDED_COST йўқ). Ҳамма сон
 * String - бузуқ киритишда тушунарли BR хабари билан қийматлар
 * сақланиб қайтади (BillForm паттерни).
 */
@Getter
@Setter
@NoArgsConstructor
public class VendorCreditForm {

    /** Таъминотчи id'си. */
    private String vendorId;

    /** Ихтиёрий асл bill ҳаваласи (prefill манбаси, hidden). */
    private String billId;

    /** Ҳужжат санаси - controller компания zoneId «бугун»и билан тўлдиради (JVM tz эмас, қоида 12). */
    private LocalDate vcDate;

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
    public static VendorCreditForm empty(int rows) {
        VendorCreditForm form = new VendorCreditForm();
        for (int i = 0; i < rows; i++) {
            form.lines.add(new LineForm());
        }
        return form;
    }

    /**
     * Асл bill'дан prefill (QBO оқими: кредит bill'дан очилади):
     * ITEM/EXPENSE сатрлар тўлиқ кўчади, ҚҚС ставка ҚИЙМАТИ ҳам
     * snapshot сифатида; LANDED_COST сатрлар ташланади - улар
     * қайтарилмайди (landed cost'нинг ўз reverse механизми бор).
     */
    public static VendorCreditForm from(Bill bill) {
        VendorCreditForm form = new VendorCreditForm();
        form.vendorId = bill.getVendorId().toString();
        form.billId = bill.getId().toString();
        form.currency = bill.getCurrency().getCode();
        form.exchangeRate = Fmt.n(bill.getExchangeRate());
        form.amountsInclusive = bill.isAmountsInclusive();
        for (BillLine line : bill.getLines()) {
            if (line.getType() == BillLineType.LANDED_COST) {
                continue;
            }
            LineForm lf = new LineForm();
            lf.type = line.getType().name();
            lf.itemId = line.getItemId() == null ? null : line.getItemId().toString();
            lf.warehouseId = line.getWarehouseId() == null
                    ? null : line.getWarehouseId().toString();
            lf.quantity = line.getQuantity() == null ? null : Fmt.n(line.getQuantity());
            // Inclusive ҳужжатда фойдаланувчи gross кўради (Bill қолипи)
            lf.unitPrice = line.getUnitPrice() == null ? null : Fmt.n(line.getUnitPrice());
            lf.unitId = line.getUnitId() == null ? null : line.getUnitId().toString();
            lf.accountId = line.getAccountId() == null
                    ? null : line.getAccountId().toString();
            lf.amount = line.getType() == BillLineType.EXPENSE
                    ? Fmt.n(line.getAmount().add(
                            bill.isAmountsInclusive() ? line.getTaxAmount()
                                    : java.math.BigDecimal.ZERO))
                    : null;
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

    /** Битта форма сатри - VendorCredit LineData кўзгуси. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class LineForm {

        /** Сатр тури: ITEM ёки EXPENSE. */
        private String type = "ITEM";

        /** ITEM сатрида item id'си. */
        private String itemId;

        /** Омбор (ITEM сатрида шарт - BR-RET-002). */
        private String warehouseId;

        /** ITEM сатрида миқдор (киритилган бирликда). */
        private String quantity;

        /** ITEM сатрида бирлик нархи. */
        private String unitPrice;

        /** ITEM сатрида киритилган бирлик ёки бўш - base. */
        private String unitId;

        /** EXPENSE сатрида харажат счёти id'си. */
        private String accountId;

        /** EXPENSE сатрида сумма. */
        private String amount;

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
                    && (accountId == null || accountId.isBlank())
                    && (quantity == null || quantity.isBlank())
                    && (amount == null || amount.isBlank());
        }
    }
}
