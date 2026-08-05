package com.averpo.erp.payroll.web;

import com.averpo.erp.payroll.domain.PayrollRun;
import com.averpo.erp.payroll.domain.PayrollRunLine;
import com.averpo.erp.shared.web.Fmt;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Иш ҳақи ҳисоблаши формаси (payroll.md). Ҳамма сон String - бузуқ
 * киритишда тушунарли BR хабари билан қийматлар сақланиб қайтади
 * (BillForm паттерни). Ушланма/солиқ суммалари формадан келмайди -
 * service ставкалардан ҳисоблайди (экранда фақат жонли кўрсатилади).
 */
@Getter
@Setter
@NoArgsConstructor
public class PayrollRunForm {

    /** Мавжуд DRAFT id'си (таҳрирда, hidden) ёки бўш - янги. */
    private String id;

    /** Ҳисобланаётган ой «YYYY-MM» (input type=month). */
    private String period;

    /** Проводка санаси (одатда ой охири) - controller компания zoneId «бугун»и билан тўлдиради (JVM tz эмас, қоида 12). */
    private LocalDate runDate;

    /** PER_TXN режимида сарлавҳадаги битта Йўналиш (invoice қолипи). */
    private String classId;

    /** Эркин изоҳ. */
    private String memo;

    /** Сатрлар - Spring indexed binding. */
    private List<LineForm> lines = new ArrayList<>();

    /** Бошланғич форма учун n та бўш сатр. */
    public static PayrollRunForm empty(int rows) {
        PayrollRunForm form = new PayrollRunForm();
        // period default'ини controller компания zoneId'даги ой билан беради
        // (JVM YearMonth.now() ой алмашиш кечасида фарқ қиларди - қоида 12/Arbitr-055)
        for (int i = 0; i < rows; i++) {
            form.lines.add(new LineForm());
        }
        return form;
    }

    /** Мавжуд DRAFT'дан форма (таҳрир учун). */
    public static PayrollRunForm from(PayrollRun run) {
        PayrollRunForm form = new PayrollRunForm();
        form.id = run.getId().toString();
        form.period = run.getPeriod();
        form.runDate = run.getRunDate();
        form.memo = run.getMemo();
        for (PayrollRunLine line : run.getLines()) {
            LineForm lf = new LineForm();
            lf.employeeId = line.getEmployeeId().toString();
            lf.gross = Fmt.n(line.getGross());
            lf.classId = line.getClassId() == null ? null : line.getClassId().toString();
            lf.memo = line.getMemo();
            form.lines.add(lf);
        }
        return form;
    }

    /** Битта форма сатри - PayrollRunService LineData кўзгуси. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class LineForm {

        /** Ходим id'си. */
        private String employeeId;

        /** Ҳисобланган ойлик (prefill oklad'дан, таҳрирланади). */
        private String gross;

        /** Ихтиёрий Йўналиш (харажат легига кўчади). */
        private String classId;

        /** Сатр изоҳи. */
        private String memo;

        /** Тўлиқ бўш сатр - request'га киритилмайди. */
        public boolean isEmpty() {
            return (employeeId == null || employeeId.isBlank())
                    && (gross == null || gross.isBlank());
        }
    }
}
