package com.averpo.erp.payroll.web;

import com.averpo.erp.payroll.domain.PayrollPayment;
import com.averpo.erp.payroll.domain.PayrollPaymentLine;
import com.averpo.erp.shared.web.Fmt;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Иш ҳақи тўлови формаси (payroll.md 23в). Ҳамма сон String - бузуқ
 * киритишда тушунарли BR хабари билан қийматлар сақланиб қайтади
 * (PayrollRunForm/BillForm паттерни). Home валютада (курс майдони йўқ).
 */
@Getter
@Setter
@NoArgsConstructor
public class PayrollPaymentForm {

    /** Мавжуд DRAFT id'си (таҳрирда, hidden) ёки бўш - янги. */
    private String id;

    /** Тури - ADVANCE/SALARY (select). */
    private String paymentType = "SALARY";

    /** Тўлов санаси - controller компания zoneId «бугун»и билан тўлдиради (JVM tz эмас, қоида 12). */
    private LocalDate paymentDate;

    /** Банк/касса счёти id'си. */
    private String accountId;

    /** Эркин изоҳ. */
    private String memo;

    /** Сатрлар - Spring indexed binding. */
    private List<LineForm> lines = new ArrayList<>();

    /** Бошланғич форма учун n та бўш сатр. */
    public static PayrollPaymentForm empty(int rows) {
        PayrollPaymentForm form = new PayrollPaymentForm();
        for (int i = 0; i < rows; i++) {
            form.lines.add(new LineForm());
        }
        return form;
    }

    /** Мавжуд DRAFT'дан форма (таҳрир учун). */
    public static PayrollPaymentForm from(PayrollPayment payment) {
        PayrollPaymentForm form = new PayrollPaymentForm();
        form.id = payment.getId().toString();
        form.paymentType = payment.getPaymentType().name();
        form.paymentDate = payment.getPaymentDate();
        form.accountId = payment.getAccountId().toString();
        form.memo = payment.getMemo();
        for (PayrollPaymentLine line : payment.getLines()) {
            LineForm lf = new LineForm();
            lf.employeeId = line.getEmployeeId().toString();
            lf.amount = Fmt.n(line.getAmount());
            form.lines.add(lf);
        }
        return form;
    }

    /** Битта форма сатри - PayrollPaymentService LineData кўзгуси. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class LineForm {

        /** Ходим id'си. */
        private String employeeId;

        /** Тўлов суммаси (home валютада). */
        private String amount;

        /** Тўлиқ бўш сатр - request'га киритилмайди. */
        public boolean isEmpty() {
            return (employeeId == null || employeeId.isBlank())
                    && (amount == null || amount.isBlank());
        }
    }
}
