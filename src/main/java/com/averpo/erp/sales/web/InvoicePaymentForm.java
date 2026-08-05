package com.averpo.erp.sales.web;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Мижоз тўлови (тушум) формаси. Суммалар String - бузуқ киритишда
 * тушунарли BR хабари билан қайтамиз, қийматлар сақланади
 * (BillPaymentForm паттерни). Allocation қаторлари очиқ invoice'лардан
 * HTMX билан юкланади.
 *
 * @author Zafar
 */
@Getter
@Setter
@NoArgsConstructor
public class InvoicePaymentForm {

    /** Танланган мижоз id'си (UUID матн кўринишида). */
    private String customerId;

    /** Тўлов санаси - controller компания zoneId «бугун»и билан тўлдиради (JVM tz эмас, қоида 12). */
    private LocalDate paymentDate;

    /** Қабул счёти id'си (банк/касса/UNDEPOSITED_FUNDS). */
    private String depositAccountId;

    /** Тўлов валютаси ISO коди - бўш бўлса home currency. */
    private String currency;

    /** Тўлов курси - home валютада 1. */
    private String exchangeRate;

    /** Тўлиқ тўлов суммаси (тақсимотдан ортиғи - аванс). */
    private String totalAmount;

    /** Эркин изоҳ. */
    private String memo;

    /** Тақсимотлар - Spring indexed binding (allocations[0].invoiceId...). */
    private List<AllocationForm> allocations = new ArrayList<>();

    /** Битта тақсимот қатори - очиқ invoice'га сумма. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class AllocationForm {

        /** Очиқ invoice id'си (қаторда hidden). */
        private String invoiceId;

        /** Тақсимот суммаси - бўш қолса қатор ташланади. */
        private String amount;
    }
}
