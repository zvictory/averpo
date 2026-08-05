package com.averpo.erp.bank.domain;

import com.averpo.erp.shared.domain.BaseEntity;
import com.averpo.erp.shared.domain.Currency;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * Банк транзакцияси (docs/modules/banking.md): DEPOSIT (кўп сатрли
 * кирим) / EXPENSE (чиқим) / TRANSFER (ўтказма, конверсия билан).
 * DRAFT ҳолати ЙЎҚ - яратилди = POSTED (тўлов модели), тузатиш
 * reverse орқали. Ҳужжат валютаси банк счётидан келади (танланмайди).
 *
 * <p>bank_account_id/contact_id - dimension паттерни (DB'да FK,
 * JPA'да UUID - ledger/contact модулларига entity боғланиш йўқ,
 * қоида №6). counterpart_* майдонлари фақат TRANSFER'да: манзил банк
 * ва унинг томонидаги сумма/курс (конверсияда фарқли).
 *
 * @author Zafar
 */
@Entity
@Table(name = "bank_transaction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BankTransaction extends BaseEntity {

    /** Транзакция ҳолати - DRAFT йўқ (тўлов модели). */
    public enum Status {
        /** Ўтказилган - GL'да акс этган, ўзгармас. */
        POSTED,
        /** Сторно қилинган. */
        REVERSED
    }

    /** Ҳужжат рақами - DocumentSequence BT-2026-NNNNN (unique). */
    @Column(name = "txn_number", nullable = false, unique = true, length = 20)
    private String txnNumber;

    /** Транзакция тури - проводка йўналиши шундан. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BankTransactionType type;

    /** Асосий банк: deposit'да қабул қилувчи, expense'да тўловчи, transfer'да МАНБА. */
    @Column(name = "bank_account_id", nullable = false)
    private UUID bankAccountId;

    /** Transfer'да манзил банк; бошқа турда null. */
    @Column(name = "counterpart_account_id")
    private UUID counterpartAccountId;

    /** Транзакция санаси. */
    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    /** Ҳужжат валютаси - банк счётиники (қоида №11). EAGER: рўйхат/кўриш шаблонларида lazy хатоси бўлмасин. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    /** Ҳужжат курси (банк счёти валютасида); home'да 1. */
    @Column(name = "exchange_rate", nullable = false, precision = 24, scale = 12)
    private BigDecimal exchangeRate;

    /** Transfer манзил томон суммаси (манзил банк валютасида); бошқа турда null. */
    @Column(name = "counterpart_amount", precision = 19, scale = 4)
    private BigDecimal counterpartAmount;

    /** Transfer манзил томон курси; бошқа турда null. */
    @Column(name = "counterpart_rate", precision = 24, scale = 12)
    private BigDecimal counterpartRate;

    /** Жами сумма ҳужжат (банк) валютасида. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal total = BigDecimal.ZERO;

    /** Жами сумма home валютада. */
    @Column(name = "total_base", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalBase = BigDecimal.ZERO;

    /** Ихтиёрий контрагент (QBO payee) - dimension. */
    @Column(name = "contact_id")
    private UUID contactId;

    /**
     * Ихтиёрий тўлов усули (Arbitr-033, QBO PaymentMethodRef) -
     * dimension паттерни: DB'да FK, JPA'да UUID (bank модули shared
     * domain'ига entity боғланмайди, қоида №6 услуби).
     */
    @Column(name = "payment_method_id")
    private UUID paymentMethodId;

    /** Ихтиёрий ҳужжат/чек рақами (QBO DocNumber - Ref no). */
    @Column(name = "ref_no", length = 30)
    private String refNo;

    /** Ҳолат. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.POSTED;

    /** Эркин изоҳ. */
    @Column(length = 500)
    private String memo;

    /** Сатрлар (DEPOSIT/EXPENSE) - транзакция билан бирга сақланади. */
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<BankTransactionLine> lines = new ArrayList<>();

    /** Янги транзакция - дарҳол POSTED (валидация service'да). */
    public BankTransaction(String txnNumber, BankTransactionType type,
                           UUID bankAccountId, UUID counterpartAccountId,
                           LocalDate txnDate, Currency currency,
                           BigDecimal exchangeRate, BigDecimal counterpartAmount,
                           BigDecimal counterpartRate, UUID contactId, String memo) {
        this.txnNumber = txnNumber;
        this.type = type;
        this.bankAccountId = bankAccountId;
        this.counterpartAccountId = counterpartAccountId;
        this.txnDate = txnDate;
        this.currency = currency;
        this.exchangeRate = exchangeRate;
        this.counterpartAmount = counterpartAmount;
        this.counterpartRate = counterpartRate;
        this.contactId = contactId;
        this.memo = memo;
    }

    /**
     * Тўлов реквизитлари (усул + ҳужжат рақами) - конструкторни
     * узайтирмасдан алоҳида: transfer оқимида бу майдонлар йўқ,
     * фақат createLinedTransaction тўлдиради (Arbitr-033).
     */
    public void applyPaymentDetails(UUID paymentMethodId, String refNo) {
        this.paymentMethodId = paymentMethodId;
        this.refNo = refNo;
    }

    /** DEPOSIT/EXPENSE'га сатр қўшади ва жамини қайта ҳисоблайди. */
    public BankTransactionLine addLine(UUID accountId, BigDecimal amount,
                                       UUID contactId, String memo) {
        BankTransactionLine line = new BankTransactionLine(this, lines.size() + 1,
                accountId, amount, contactId, memo);
        lines.add(line);
        recalcTotals();
        return line;
    }

    /** TRANSFER жамисини ўрнатади (сатр йўқ - сумма тўғридан-тўғри). */
    public void applyTransferTotal(BigDecimal amount) {
        this.total = amount;
        // Формула totalBase билан бир хил жойда туради (MoneyAllocation)
        this.totalBase = com.averpo.erp.shared.domain.MoneyAllocation
                .targetBase(amount, exchangeRate);
    }

    /**
     * Жами ва base жамини сатрлардан қайта ҳисоблайди. totalBase =
     * {@link com.averpo.erp.shared.domain.MoneyAllocation#targetBase} -
     * битта яхлитлаш: GL'да банк томони айнан шу target, сатр base'лари
     * эса largest-remainder билан шунга тақсимланади (Asrorxoja-002,
     * Bill қолипи). TRANSFER'да сатр йўқ - жами битта сумма
     * ({@link #applyTransferTotal}), формула ўша.
     */
    private void recalcTotals() {
        BigDecimal sum = BigDecimal.ZERO;
        for (BankTransactionLine line : lines) {
            sum = sum.add(line.getAmount());
        }
        this.total = sum;
        this.totalBase = com.averpo.erp.shared.domain.MoneyAllocation
                .targetBase(sum, exchangeRate);
    }

    /** POSTED'дан REVERSED'га ўтказади (фақат BankTransactionService чақиради). */
    public void markReversed() {
        if (status != Status.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_BT_007,
                    "Фақат POSTED транзакция reverse қилинади: " + txnNumber
                    + " - " + status);
        }
        this.status = Status.REVERSED;
    }
}
