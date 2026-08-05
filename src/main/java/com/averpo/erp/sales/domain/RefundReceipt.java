package com.averpo.erp.sales.domain;

import com.averpo.erp.shared.domain.BaseEntity;
import com.averpo.erp.shared.domain.Currency;
import com.averpo.erp.shared.domain.MoneyAllocation;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Мижозга пул қайтариш чеки (docs/modules/returns.md, QBO
 * RefundReceipt): CreditMemo'нинг айнан кўзгуси, фарқи - AR ўрнига
 * пул счёти кредитланади (ҳужжатда танланади) ва application ЙЎҚ:
 * пул дарҳол қайтгани учун очиқ қолдиқ тушунчаси йўқ - тугал ҳужжат.
 * DRAFT йўқ - яратилди = POSTED, тузатиш reverse (темир қоида 3).
 *
 * @author Zafar
 */
@Entity
@Table(name = "refund_receipt")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundReceipt extends BaseEntity {

    /** Ҳужжат ҳолати - DRAFT йўқ (тўлов модели). */
    public enum Status {
        /** Ўтказилган - GL'да акс этган, ўзгармас. */
        POSTED,
        /** Сторно қилинган. */
        REVERSED
    }

    /** Ҳужжат рақами - DocumentSequence RR-2026-NNNNN (unique). */
    @Column(name = "rr_number", nullable = false, unique = true, length = 20)
    private String rrNumber;

    /** Мижоз - dimension (DB'да FK contact). */
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    /** Ихтиёрий асл invoice ҳаваласи (DB'да FK) - prefill ва қайтим таннархи. */
    @Column(name = "invoice_id")
    private UUID invoiceId;

    /** Пул қайтадиган банк/касса счёти - dimension (DB'да FK account). */
    @Column(name = "bank_account_id", nullable = false)
    private UUID bankAccountId;

    /** Ҳужжат санаси. */
    @Column(name = "rr_date", nullable = false)
    private LocalDate rrDate;

    /** Ҳужжат валютаси (қоида №11). EAGER - рўйхат/кўриш шаблонлари учун. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    /** Ҳужжат курси; home'да 1. */
    @Column(name = "exchange_rate", nullable = false, precision = 24, scale = 12)
    private BigDecimal exchangeRate;

    /** Нархлар режими: true - ҚҚС ичида (docs/modules/tax.md). */
    @Column(name = "amounts_inclusive", nullable = false)
    private boolean amountsInclusive;

    /** Жами GROSS (net + ҚҚС) - ҳужжат валютасида; пул счёти кредити шу. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal total = BigDecimal.ZERO;

    /** Жами home валютада (MoneyAllocation.targetBase - битта яхлитлаш). */
    @Column(name = "total_base", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalBase = BigDecimal.ZERO;

    /** Ҳолат. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.POSTED;

    /** GL'га ўтказилган вақт (UTC). */
    @Column(name = "posted_at")
    private Instant postedAt;

    /** Эркин изоҳ. */
    @Column(length = 500)
    private String memo;

    /** Сатрлар - ҳужжат билан бирга сақланади. */
    @OneToMany(mappedBy = "refundReceipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<RefundReceiptLine> lines = new ArrayList<>();

    /** Янги чек - валидация RefundReceiptService'да. */
    public RefundReceipt(String rrNumber, UUID customerId, UUID invoiceId,
                         UUID bankAccountId, LocalDate rrDate, Currency currency,
                         BigDecimal exchangeRate, boolean amountsInclusive, String memo) {
        this.rrNumber = rrNumber;
        this.customerId = customerId;
        this.invoiceId = invoiceId;
        this.bankAccountId = bankAccountId;
        this.rrDate = rrDate;
        this.currency = currency;
        this.exchangeRate = exchangeRate;
        this.amountsInclusive = amountsInclusive;
        this.memo = memo;
    }

    /** Сатр қўшади ва жамиларни қайта ҳисоблайди (CreditMemo кўзгуси). */
    public RefundReceiptLine addLine(InvoiceLineType type, UUID itemId, UUID warehouseId,
                                     BigDecimal quantity, BigDecimal unitPrice,
                                     UUID unitId, BigDecimal unitFactor,
                                     UUID incomeAccountId, BigDecimal amount,
                                     UUID taxRateId, BigDecimal taxRateValue,
                                     BigDecimal taxAmount, String memo) {
        RefundReceiptLine line = new RefundReceiptLine(this, lines.size() + 1, type,
                itemId, warehouseId, quantity, unitPrice, unitId, unitFactor,
                incomeAccountId, amount, taxRateId, taxRateValue, taxAmount, memo);
        lines.add(line);
        recalcTotals();
        return line;
    }

    /**
     * Жамилар: total = gross йиғиндиси, totalBase = битта яхлитлаш
     * (MoneyAllocation.targetBase - Asrorxoja-002 қолипи).
     */
    private void recalcTotals() {
        BigDecimal sum = BigDecimal.ZERO;
        for (RefundReceiptLine line : lines) {
            sum = sum.add(line.grossAmount());
        }
        this.total = sum;
        this.totalBase = MoneyAllocation.targetBase(sum, exchangeRate);
    }

    /** POSTED белгиси - фақат RefundReceiptService чақиради. */
    public void markPosted(Instant postedAt) {
        this.postedAt = postedAt;
    }

    /** POSTED'дан REVERSED'га (фақат RefundReceiptService). */
    public void markReversed() {
        if (status != Status.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_RET_007,
                    "Фақат POSTED чек reverse қилинади: " + rrNumber
                    + " - " + status);
        }
        this.status = Status.REVERSED;
    }
}
