package com.averpo.erp.purchase.web;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Vendor тўлови формаси. Суммалар String - бузуқ киритишда тушунарли
 * BR хабари билан қайтамиз, қийматлар сақланади (BillForm паттерни).
 * Allocation қаторлари очиқ bill'лардан HTMX билан юкланади.
 */
@Getter
@Setter
@NoArgsConstructor
public class BillPaymentForm {

    /** Танланган vendor id'си (UUID матн кўринишида). */
    private String vendorId;

    /** Тўлов санаси - controller компания zoneId «бугун»и билан тўлдиради (JVM tz эмас, қоида 12). */
    private LocalDate paymentDate;

    /** Банк счёти id'си (BANK тури). */
    private String bankAccountId;

    /** Тўлов валютаси ISO коди - бўш бўлса home currency. */
    private String currency;

    /** Тўлов курси - home валютада 1. */
    private String exchangeRate;

    /** Тўлиқ тўлов суммаси (тақсимотдан ортиғи - аванс). */
    private String totalAmount;

    /** Эркин изоҳ. */
    private String memo;

    /** Тақсимотлар - Spring indexed binding (allocations[0].billId...). */
    private List<AllocationForm> allocations = new ArrayList<>();

    /** Битта тақсимот қатори - очиқ bill'га сумма. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class AllocationForm {

        /** Очиқ bill id'си (қаторда hidden). */
        private String billId;

        /** Тақсимот суммаси - бўш қолса қатор ташланади. */
        private String amount;
    }
}
