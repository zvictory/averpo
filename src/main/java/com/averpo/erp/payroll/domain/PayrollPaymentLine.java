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
 * Иш ҳақи тўлови сатри (docs/modules/payroll.md 23в): битта ходимга
 * тегадиган сумма (home валютада - BR-PYR-001, base == amount). GL'да
 * шу сатр PAYROLL_CLEARING'нинг ходим кесимидаги дебет легига айнан
 * кўчади (posting-rules «Иш ҳақи»).
 *
 * <p>employee_id - dimension (DB'да FK contact, JPA'да оддий UUID -
 * контакт модулига entity боғланиш йўқ, қоида №6; EMPLOYEE тури ва
 * фаоллик service'да - BR-PYR-003). Бир тўловда ходим бир марта
 * (uq_payroll_payment_line_employee).
 */
@Entity
@Table(name = "payroll_payment_line",
       uniqueConstraints = @UniqueConstraint(columnNames = {"payment_id", "employee_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayrollPaymentLine extends BaseEntity {

    /** Эгаси - тўлов билан бирга сақланади/ўчади (композиция). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private PayrollPayment payment;

    /** Ходим - dimension (EMPLOYEE тури ва фаоллик service'да - BR-PYR-003). */
    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    /** Тўлов суммаси home валютада (мусбат - BR-PYR-003, DB CHECK ҳам). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Янги сатр - фақат PayrollPayment.addLine орқали (композиция). */
    PayrollPaymentLine(PayrollPayment payment, UUID employeeId, BigDecimal amount) {
        this.payment = payment;
        this.employeeId = employeeId;
        this.amount = amount;
    }
}
