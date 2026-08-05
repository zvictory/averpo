package com.averpo.erp.bank.domain;

import com.averpo.erp.shared.domain.BaseEntity;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Банк reconciliation ҳужжати - QBO Reconcile модели (2026-07-06
 * қарори, docs/modules/banking.md): кўчирма сатрлари киритилмайди,
 * давр + якуний қолдиқ киритилиб GL сатрлари белгиланади. Қолдиқлар
 * СЧЁТ ВАЛЮТАСИДА. account_id - dimension паттерни (DB FK, JPA'да
 * UUID - ledger'га entity боғланиш йўқ, қоида №6).
 *
 * @author Zafar
 */
@Entity
@Table(name = "bank_reconciliation",
       uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "statement_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BankReconciliation extends BaseEntity {

    /** Жараён ҳолати. */
    public enum Status {
        /** Белгилаш давом этмоқда - бекор қилиш мумкин. */
        IN_PROGRESS,
        /** Якунланган (фарқ 0 да) - ўзгармас. */
        COMPLETED
    }

    /** Солиштирилаётган банк счёти id'си (BANK тури, BR-RCN-001). */
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    /** Кўчирма якуний санаси - UNIQUE(account, date) (BR-RCN-003). */
    @Column(name = "statement_date", nullable = false)
    private LocalDate statementDate;

    /** Бошланғич қолдиқ (счёт валютасида) - аввалги COMPLETED'нинг closing'идан. */
    @Column(name = "opening_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal openingBalance;

    /** Кўчирмадаги якуний қолдиқ (счёт валютасида, киритилади). */
    @Column(name = "closing_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal closingBalance;

    /** Ҳолат. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Status status = Status.IN_PROGRESS;

    /** Якунланган вақт (UTC). */
    @Column(name = "completed_at")
    private Instant completedAt;

    /** Янги reconciliation - IN_PROGRESS (валидация service'да). */
    public BankReconciliation(UUID accountId, LocalDate statementDate,
                              BigDecimal openingBalance, BigDecimal closingBalance) {
        this.accountId = accountId;
        this.statementDate = statementDate;
        this.openingBalance = openingBalance;
        this.closingBalance = closingBalance;
    }

    /** Белгилаш/якунлаш олдидан ҳолат гарови (BR-RCN-004) - domain guard. */
    public void requireInProgress() {
        if (status != Status.IN_PROGRESS) {
            throw new BusinessRuleException(BusinessRule.BR_RCN_004,
                    "Reconciliation якунланган - ўзгартириб бўлмайди: "
                    + statementDate + " (" + status + ")");
        }
    }

    /** IN_PROGRESS'дан COMPLETED'га ўтказади (фарқ гарови service'да). */
    public void markCompleted(Instant completedAt) {
        requireInProgress();
        this.status = Status.COMPLETED;
        this.completedAt = completedAt;
    }
}
