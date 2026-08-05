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
 * Банк транзакцияси сатри (DEPOSIT/EXPENSE): манба/харажат счёти +
 * сумма (ҳужжат валютасида) + ихтиёрий контакт (QBO deposit
 * "received from"). account_id/contact_id - dimension паттернидаги
 * UUID'лар (DB'да FK, JPA боғланиш йўқ - қоида №6). POSTED
 * транзакциянинг сатрлари ўзгармас.
 */
@Entity
@Table(name = "bank_transaction_line",
       uniqueConstraints = @UniqueConstraint(columnNames = {"txn_id", "line_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BankTransactionLine extends BaseEntity {

    /** Эга транзакция. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "txn_id", nullable = false)
    private BankTransaction transaction;

    /** Сатр тартиб рақами (1 дан). */
    @Column(name = "line_no", nullable = false)
    private int lineNo;

    /** Манба (deposit) ёки харажат (expense) счёти id'си (BR-BT-004). */
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    /** Сатр суммаси ҳужжат валютасида (мусбат). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Ихтиёрий контрагент - dimension (QBO received from/payee). */
    @Column(name = "contact_id")
    private UUID contactId;

    /** Эркин изоҳ. */
    @Column(length = 500)
    private String memo;

    /**
     * Ихтиёрий Class/Йўналиш теги (class-tracking.md) - GL'да шу
     * сатрнинг легига айнан кўчади (банк жами сатри class'сиз).
     */
    @Column(name = "class_id")
    private UUID classId;

    /** Class тегини қўяди - конструктор кенгаймасин (applyPaymentDetails нақши). */
    public void applyClass(UUID classId) {
        this.classId = classId;
    }

    /** Янги сатр - фақат BankTransaction.addLine орқали (композиция). */
    BankTransactionLine(BankTransaction transaction, int lineNo, UUID accountId,
                        BigDecimal amount, UUID contactId, String memo) {
        this.transaction = transaction;
        this.lineNo = lineNo;
        this.accountId = accountId;
        this.amount = amount;
        this.contactId = contactId;
        this.memo = memo;
    }
}
