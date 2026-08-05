package com.averpo.erp.bank.domain;

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
 * Reconciliation'да белгиланган GL сатри. journal_entry_line_id -
 * dimension паттерни (DB'да FK, JPA'да UUID - ledger'га entity
 * боғланиш йўқ, қоида №6). ГЛОБАЛ unique (BR-RCN-006): сатр фақат
 * бир марта reconcile қилинади - бошқа statement'да ҳам эмас.
 *
 * <p>amount - сатр суммаси СЧЁТ ВАЛЮТАСИДА, ишорали (дебет мусбат,
 * кредит манфий): фарқ ҳисоби ledger'га қайта мурожаатсиз чиқади.
 * POSTED GL сатри ўзгармас (темир қоида №3) - snapshot хавфсиз.
 */
@Entity
@Table(name = "bank_reconciliation_match",
       uniqueConstraints = @UniqueConstraint(columnNames = {"journal_entry_line_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BankReconciliationMatch extends BaseEntity {

    /** Эга reconciliation. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reconciliation_id", nullable = false)
    private BankReconciliation reconciliation;

    /** Белгиланган GL сатри id'си (dimension). */
    @Column(name = "journal_entry_line_id", nullable = false)
    private UUID journalEntryLineId;

    /** Сатр суммаси счёт валютасида, ишорали (Dt +, Cr -). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Янги белги - фақат ReconciliationService орқали (валидация ўша ерда). */
    public BankReconciliationMatch(BankReconciliation reconciliation,
                                   UUID journalEntryLineId, BigDecimal amount) {
        this.reconciliation = reconciliation;
        this.journalEntryLineId = journalEntryLineId;
        this.amount = amount;
    }
}
