package com.averpo.erp.payroll.domain;

import com.averpo.erp.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Иш ҳақи ҳисоблаши сатри (payroll.md «PayrollRunLine»): ходим +
 * gross ва ҳисобланган СУММА snapshot'лари. Ставкалар эмас, суммалар
 * сақланади - кейин CompanySettings ставкаси ўзгарса тарихий POSTED
 * run ўзгармайди (Money.exchangeRate snapshot услуби).
 *
 * <p>net = gross − income_tax − pension (ижтимоий солиқ иш берувчи
 * устига - ходим net'ига таъсир қилмайди). Бир run'да ходим бир марта
 * (BR-PYR-003, DB unique ҳам).
 */
@Entity
@Table(name = "payroll_run_line",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"payroll_run_id", "employee_id"}),
           @UniqueConstraint(columnNames = {"payroll_run_id", "line_no"})})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayrollRunLine extends BaseEntity {

    /** Ҳужжат. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_run_id", nullable = false)
    private PayrollRun payrollRun;

    /** Сатр тартиби (1 дан). */
    @Column(name = "line_no", nullable = false)
    private int lineNo;

    /** Ходим - dimension (DB'да FK contact, EMPLOYEE тури - BR-PYR-003). */
    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    /** Ҳисобланган ойлик (prefill oklad'дан, таҳрирланган бўлиши мумкин). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal gross;

    /** Даромад солиғи суммаси - snapshot (ходимдан ушланма). */
    @Column(name = "income_tax", nullable = false, precision = 19, scale = 4)
    private BigDecimal incomeTax;

    /** Пенсия бадали суммаси - snapshot (ходимдан ушланма). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal pension;

    /** Ижтимоий солиқ суммаси - snapshot (иш берувчи устига). */
    @Column(name = "social_tax", nullable = false, precision = 19, scale = 4)
    private BigDecimal socialTax;

    /** Қўлга тегадигани = gross − income_tax − pension. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal net;

    /** Ихтиёрий Class/Йўналиш теги - фақат харажат легига кўчади. */
    @Column(name = "class_id")
    private UUID classId;

    /** Эркин изоҳ. */
    @Column(length = 500)
    private String memo;

    /** Янги сатр - фақат PayrollRun.addLine орқали (композиция). */
    PayrollRunLine(PayrollRun payrollRun, int lineNo, UUID employeeId,
                   BigDecimal gross, BigDecimal incomeTax, BigDecimal pension,
                   BigDecimal socialTax, BigDecimal net, UUID classId, String memo) {
        this.payrollRun = payrollRun;
        this.lineNo = lineNo;
        this.employeeId = employeeId;
        this.gross = gross;
        this.incomeTax = incomeTax;
        this.pension = pension;
        this.socialTax = socialTax;
        this.net = net;
        this.classId = classId;
        this.memo = memo;
    }
}
