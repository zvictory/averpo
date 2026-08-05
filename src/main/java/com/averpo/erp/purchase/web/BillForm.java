package com.averpo.erp.purchase.web;

import com.averpo.erp.purchase.domain.Bill;
import com.averpo.erp.purchase.domain.BillLine;
import com.averpo.erp.shared.web.Fmt;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Bill формаси. Суммалар атайлаб String - бузуқ киритишда BindException
 * ўрнига ўзимиз тушунарли (кирилл) BR хабари билан қайтамиз ва
 * киритилган қийматлар формада сақланиб қолади (JournalEntryForm
 * паттерни).
 */
@Getter
@Setter
@NoArgsConstructor
public class BillForm {

    /** Таҳрирда мавжуд draft id'си (яратишда бўш). */
    private String id;

    /**
     * Айлантириш оқимида манба буюртма id'си (hidden, estimates-po.md):
     * тўлдирилган бўлса сақлангач BillController буюртмани CLOSED +
     * linked қилади.
     */
    private String purchaseOrderId;

    /** Танланган vendor id'си (UUID матн кўринишида). */
    private String vendorId;

    /** Vendor'нинг ўз ҳисобварақ рақами (ихтиёрий, дубликат guard). */
    private String vendorInvoiceNumber;

    /**
     * Ҳужжат санаси - date input, ISO форматда боғланади. Default'ни
     * controller компания zoneId «бугун»и билан беради (JVM tz эмас,
     * қоида 12); таҳрирда {@code from()} мавжуд ҳужжат санасини қўяди.
     */
    private LocalDate billDate;

    /** Тўлов муддати - бўш қолса vendor тўлов шартидан автоматик. */
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

    /** Сатрлар - Spring indexed binding (lines[0].type...). */
    private List<LineForm> lines = new ArrayList<>();

    /** Бошланғич форма учун n та бўш сатр. */
    public static BillForm empty(int rows) {
        BillForm form = new BillForm();
        for (int i = 0; i < rows; i++) {
            form.lines.add(new LineForm());
        }
        return form;
    }

    /** Таҳрир формаси: мавжуд DRAFT'дан тўлдирилади. */
    public static BillForm from(Bill bill) {
        BillForm form = new BillForm();
        form.id = bill.getId().toString();
        form.vendorId = bill.getVendorId().toString();
        form.vendorInvoiceNumber = bill.getVendorInvoiceNumber();
        form.billDate = bill.getBillDate();
        form.dueDate = bill.getDueDate();
        form.currency = bill.getCurrency().getCode();
        form.exchangeRate = Fmt.n(bill.getExchangeRate());
        form.amountsInclusive = bill.isAmountsInclusive();
        form.memo = bill.getMemo();
        for (BillLine line : bill.getLines()) {
            LineForm lf = new LineForm();
            lf.type = line.getType().name();
            lf.itemId = line.getItemId() == null ? null : line.getItemId().toString();
            lf.warehouseId = line.getWarehouseId() == null ? null : line.getWarehouseId().toString();
            lf.quantity = Fmt.n(line.getQuantity());
            lf.unitPrice = Fmt.n(line.getUnitPrice());
            lf.unitId = line.getUnitId() == null ? null : line.getUnitId().toString();
            lf.accountId = line.getAccountId() == null ? null : line.getAccountId().toString();
            // Таҳрирда inclusive'да gross кўрсатилади (форма нархлар режими
            // бўйича gross киритади) - inclusive'да net'ни gross'га қайтарамиз
            lf.amount = Fmt.n(bill.isAmountsInclusive() ? line.grossAmount() : line.getAmount());
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
     * Айлантириш prefill'и (estimates-po.md): таъминотчи/валюта/сатрлар/
     * ҚҚС ставкалари буюртмадан, сана - бугунги (форма default'и).
     * Чет валютада курс АТАЙЛАБ бўш - жим «курс 1» билан сақланиб
     * қолмасин; омбор бўш - фойдаланувчи формада танлайди.
     */
    public static BillForm fromPurchaseOrder(
            com.averpo.erp.purchase.domain.PurchaseOrder po, String homeCurrency) {
        BillForm form = new BillForm();
        form.purchaseOrderId = po.getId().toString();
        form.vendorId = po.getVendorId().toString();
        form.currency = po.getCurrency().getCode();
        form.exchangeRate = po.getCurrency().getCode().equals(homeCurrency) ? "1" : "";
        form.amountsInclusive = po.isAmountsInclusive();
        form.memo = po.getMemo();
        for (com.averpo.erp.purchase.domain.PurchaseOrderLine line : po.getLines()) {
            LineForm lf = new LineForm();
            lf.type = "ITEM"; // PO фақат item буюртмаси (estimates-po.md)
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

    /** Битта форма сатри - тури бўйича керакли майдонлар тўлдирилади. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class LineForm {

        /** Сатр тури: ITEM / EXPENSE / LANDED_COST. */
        private String type = "ITEM";

        /** ITEM: товар id'си. */
        private String itemId;

        /** ITEM: омбор id'си. */
        private String warehouseId;

        /** ITEM: миқдор. */
        private String quantity;

        /** ITEM: бирлик нархи (ҳужжат валютасида, танланган бирликка). */
        private String unitPrice;

        /** ITEM: киритиш бирлиги id'си (UoM) ёки бўш - item base. */
        private String unitId;

        /** EXPENSE: харажат счёти id'си. */
        private String accountId;

        /** EXPENSE/LANDED_COST суммаси (ITEM'да сервер qty × нархдан ҳисоблайди). */
        private String amount;

        /** Танланган ҚҚС ставкаси id'си ёки бўш - солиқсиз (docs/modules/tax.md). */
        private String taxRateId;

        /** PER_LINE режимида сатрнинг Йўналиши (class-tracking.md). */
        private String classId;

        /** Сатр изоҳи. */
        private String memo;

        /** Тўлиқ бўш сатр - request'га киритилмайди (ишлатилмаган HTMX қатор). */
        public boolean isEmpty() {
            return isBlank(itemId) && isBlank(accountId)
                    && isBlank(quantity) && isBlank(amount);
        }

        /** null ёки бўш сатрми. */
        private static boolean isBlank(String s) {
            return s == null || s.isBlank();
        }
    }
}
