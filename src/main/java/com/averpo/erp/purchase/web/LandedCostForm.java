package com.averpo.erp.purchase.web;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Landed cost тақсимот формаси. Сумма String - бузуқ киритишда
 * тушунарли BR хабари билан қайтамиз (BillForm паттерни). Receipt'лар
 * checkbox'лардан keladi (name="movementIds").
 */
@Getter
@Setter
@NoArgsConstructor
public class LandedCostForm {

    /** Тақсимот санаси - controller компания zoneId «бугун»и билан тўлдиради (JVM tz эмас, қоида 12). */
    private LocalDate allocationDate;

    /** Тарқатиладиган сумма (home валютада). */
    private String totalAmount;

    /** Эркин изоҳ. */
    private String memo;

    /** Танланган receipt movement id'лари (checkbox қийматлари). */
    private List<String> movementIds = new ArrayList<>();
}
