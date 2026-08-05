package com.averpo.erp.payroll.domain;

import com.averpo.erp.shared.domain.BaseEntity;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ойлик иш ҳақи ҳисоблаши (docs/modules/payroll.md «PayrollRun»):
 * DRAFT таҳрирланади, POSTED ўзгармас - фақат reverse (invoice
 * қолипи, темир қоида 3). Ҳамма суммалар home валютада (BR-PYR-001) -
 * валюта/курс майдонлари атайлаб йўқ.
 *
 * <p>Битта period'га биттагина POSTED run (BR-PYR-002 - service
 * текшируви + ux_payroll_run_period_posted partial unique). entryId -
 * post'да ёзилган JE ҳаваласи (кўриш экрани linki).
 */
@Entity
@Table(name = "payroll_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayrollRun extends BaseEntity {

    /** Ҳаёт цикли - invoice қолипи (DRAFT таҳрирланади). */
    public enum Status {
        /** Қоралама - таҳрирланади, GL'га тегмаган. */
        DRAFT,
        /** Ўтказилган - GL'да акс этган, ўзгармас. */
        POSTED,
        /** Сторно қилинган. */
        REVERSED
    }

    /** Ҳужжат рақами - DocumentSequence PAYR-2026-NNNNN (unique). */
    @Column(name = "run_number", nullable = false, unique = true, length = 20)
    private String runNumber;

    /** Ҳисобланаётган ой «YYYY-MM» (BR-PYR-004 формат гарови service'да). */
    @Column(nullable = false, length = 7)
    private String period;

    /** Проводка санаси (одатда ой охири; closing date текшируви шу сана бўйича). */
    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;

    /** Ҳолат. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.DRAFT;

    /** Post'да ёзилган JE id'си - кўриш экранидаги ҳавола (audit нақши). */
    @Column(name = "entry_id")
    private UUID entryId;

    /** Эркин изоҳ. */
    @Column(length = 500)
    private String memo;

    /** Сатрлар - ҳужжат билан бирга сақланади. */
    @OneToMany(mappedBy = "payrollRun", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<PayrollRunLine> lines = new ArrayList<>();

    /** Янги DRAFT ҳисоблаш - валидация PayrollRunService'да. */
    public PayrollRun(String runNumber, String period, LocalDate runDate, String memo) {
        this.runNumber = runNumber;
        this.period = period;
        this.runDate = runDate;
        this.memo = memo;
    }

    /** POSTED/REVERSED ҳужжат ўзгармас (темир қоида №3) - domain guard. */
    private void requireDraft() {
        if (status != Status.DRAFT) {
            throw new BusinessRuleException(BusinessRule.BR_PYR_005,
                    "Ҳисоблаш ўзгармас ҳолатда: " + runNumber + " - " + status);
        }
    }

    /** Draft сарлавҳасини янгилайди (валидация service'да). */
    public void updateHeader(String period, LocalDate runDate, String memo) {
        requireDraft();
        this.period = period;
        this.runDate = runDate;
        this.memo = memo;
    }

    /** Draft'га сатр қўшади (суммалар service'да ҳисобланган snapshot). */
    public PayrollRunLine addLine(UUID employeeId, BigDecimal gross,
                                  BigDecimal incomeTax, BigDecimal pension,
                                  BigDecimal socialTax, BigDecimal net,
                                  UUID classId, String memo) {
        requireDraft();
        PayrollRunLine line = new PayrollRunLine(this, lines.size() + 1, employeeId,
                gross, incomeTax, pension, socialTax, net, classId, memo);
        lines.add(line);
        return line;
    }

    /** Draft сатрларини тозалайди (қайта териш - форма таҳрири). */
    public void clearLines() {
        requireDraft();
        lines.clear();
    }

    /** Жами gross - экран/JE учун (сатрлар йиғиндиси). */
    public BigDecimal totalGross() {
        BigDecimal sum = BigDecimal.ZERO;
        for (PayrollRunLine line : lines) {
            sum = sum.add(line.getGross());
        }
        return sum;
    }

    /** Жами net - экран учун. */
    public BigDecimal totalNet() {
        BigDecimal sum = BigDecimal.ZERO;
        for (PayrollRunLine line : lines) {
            sum = sum.add(line.getNet());
        }
        return sum;
    }

    /** DRAFT'дан POSTED'га + JE ҳаваласи (фақат PayrollRunService). */
    public void markPosted(UUID entryId) {
        requireDraft();
        this.status = Status.POSTED;
        this.entryId = entryId;
    }

    /** POSTED'дан REVERSED'га (фақат PayrollRunService). */
    public void markReversed() {
        if (status != Status.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_PYR_006,
                    "Фақат POSTED ҳисоблаш reverse қилинади: " + runNumber
                    + " - " + status);
        }
        this.status = Status.REVERSED;
    }
}
