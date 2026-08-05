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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Иш ҳақи тўлови (docs/modules/payroll.md 23в; posting-rules «Иш ҳақи»):
 * аванс/ойлик тўлови. Ҳаёт цикли invoice қолипи (PayrollRun кўзгуси):
 * DRAFT таҳрирланади → POSTED ўзгармас (фақат reverse - темир қоида 3)
 * → REVERSED. Ҳамма суммалар ФАҚАТ home валютада (BR-PYR-001) -
 * валюта/курс майдонлари атайлаб йўқ.
 *
 * <p>Проводка: Dr PAYROLL_CLEARING (ҳар ходим кесимида) / Cr банк-касса
 * (жами). Тўлов run'га боғланМАЙДИ - clearing қолдиғи (GL контакт
 * кесими) ўзи ҳақиқат манбаи (Lite соддалиги, ведомость шуни жамлайди).
 * account_id - тўлов счёти (dimension; BANK/CASH ва home валютали
 * текшируви PayrollPaymentService'да).
 *
 * @author Zafar
 */
@Entity
@Table(name = "payroll_payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayrollPayment extends BaseEntity {

    /** Ҳаёт цикли - invoice қолипи (DRAFT таҳрирланади, POSTED ўзгармас). */
    public enum Status {
        /** Қоралама - таҳрирланади, GL'га тегмаган. */
        DRAFT,
        /** Ўтказилган - GL'да акс этган, ўзгармас. */
        POSTED,
        /** Сторно қилинган. */
        REVERSED
    }

    /** Ҳужжат рақами - DocumentSequence PAYP-2026-NNNNN (unique). */
    @Column(name = "payp_number", nullable = false, unique = true, length = 20)
    private String paypNumber;

    /** Тури - фақат белги (ADVANCE/SALARY), проводкаси бир хил. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 10)
    private PayrollPaymentType paymentType;

    /** Тўлов санаси (closing date текшируви шу сана бўйича). */
    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    /** Банк/касса счёти id'си (dimension; BANK/CASH ва home валютали текшируви service'да). */
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    /** Жами сумма home валютада (сатрлар йиғиндиси) - денормализация (рўйхат экрани). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal total = BigDecimal.ZERO;

    /** Ҳаёт цикли ҳолати. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.DRAFT;

    /** Эркин изоҳ. */
    @Column(length = 500)
    private String memo;

    /** Post вақти (UTC). */
    @Column(name = "posted_at")
    private Instant postedAt;

    /** Сатрлар - тўлов билан бирга сақланади/ўчади (композиция). */
    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PayrollPaymentLine> lines = new ArrayList<>();

    /** Янги DRAFT тўлов - валидация PayrollPaymentService'да. */
    public PayrollPayment(String paypNumber, PayrollPaymentType paymentType,
                          LocalDate paymentDate, UUID accountId, String memo) {
        this.paypNumber = paypNumber;
        this.paymentType = paymentType;
        this.paymentDate = paymentDate;
        this.accountId = accountId;
        this.memo = memo;
    }

    /**
     * POSTED/REVERSED ҳужжат ўзгармас (темир қоида №3) - domain guard.
     * BR-PYR-005 (фақат DRAFT payroll ҳужжати таҳрирланади/post қилинади) -
     * PayrollRun ҳам шу lifecycle кодини ишлатади (модул изчиллиги; хабар
     * контекстли қолади).
     */
    private void requireDraft() {
        if (status != Status.DRAFT) {
            throw new BusinessRuleException(BusinessRule.BR_PYR_005,
                    "Тўлов ўзгармас ҳолатда: " + paypNumber + " - " + status);
        }
    }

    /** Draft сарлавҳасини янгилайди (валидация service'да). */
    public void updateHeader(PayrollPaymentType paymentType, LocalDate paymentDate,
                             UUID accountId, String memo) {
        requireDraft();
        this.paymentType = paymentType;
        this.paymentDate = paymentDate;
        this.accountId = accountId;
        this.memo = memo;
    }

    /** Draft'га сатр қўшади ва жамини қайта ҳисоблайди (сумма service'да текширилган). */
    public PayrollPaymentLine addLine(UUID employeeId, BigDecimal amount) {
        requireDraft();
        PayrollPaymentLine line = new PayrollPaymentLine(this, employeeId, amount);
        lines.add(line);
        recalcTotal();
        return line;
    }

    /** Draft сатрларини тозалайди (қайта териш - форма таҳрири). */
    public void clearLines() {
        requireDraft();
        lines.clear();
        recalcTotal();
    }

    /** Жами (сатрлар йиғиндиси) - home валютада (base == amount). */
    private void recalcTotal() {
        BigDecimal sum = BigDecimal.ZERO;
        for (PayrollPaymentLine line : lines) {
            sum = sum.add(line.getAmount());
        }
        this.total = sum;
    }

    /** DRAFT'дан POSTED'га (фақат PayrollPaymentService чақиради). */
    public void markPosted(Instant postedAt) {
        requireDraft();
        this.status = Status.POSTED;
        this.postedAt = postedAt;
    }

    /** POSTED'дан REVERSED'га (фақат PayrollPaymentService чақиради). */
    public void markReversed() {
        if (status != Status.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_PYR_006,
                    "Фақат POSTED тўлов reverse қилинади: " + paypNumber + " - " + status);
        }
        this.status = Status.REVERSED;
    }
}
