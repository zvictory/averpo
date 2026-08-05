package com.averpo.erp.sales.domain;

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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Сотув ҳужжати (docs/modules/sales.md) - Bill'нинг кўзгу акси.
 * Ҳаёт цикли ledger модели билан бир хил: DRAFT таҳрирланади, POSTED
 * ўзгармас (фақат reverse). Суммалар ҳужжат валютасида,
 * base = amount × exchange_rate.
 *
 * <p>customer_id - dimension паттерни (DB'да FK, JPA'да UUID - контакт
 * модулига entity боғланиш йўқ, қоида №6). Денормализация
 * (paid/balance/status) фақат InvoicePaymentService орқали янгиланади.
 */
@Entity
@Table(name = "invoice")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invoice extends BaseEntity {

    /** Ҳужжат рақами - DocumentSequence INV-2026-NNNNN (unique). */
    @Column(name = "invoice_number", nullable = false, unique = true, length = 20)
    private String invoiceNumber;

    /** Customer контакт id'си (dimension, CUSTOMER типи service'да текширилади). */
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    /** Ҳужжат санаси. */
    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    /** Тўлов муддати - мижоз тўлов шартидан автоматик, ўзгартириш мумкин. */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /** Ҳужжат валютаси - каталогга ManyToOne (қоида №11). EAGER: рўйхат/кўриш шаблонларида lazy хатоси бўлмасин. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    /** Ҳужжат курси (QBO услуби - ҳужжат даражасида); home валютада 1. */
    @Column(name = "exchange_rate", nullable = false, precision = 24, scale = 12)
    private BigDecimal exchangeRate;

    /**
     * Нархлар режими (docs/modules/tax.md): false - ҚҚСсиз (exclusive,
     * default), true - ҚҚС ичида (inclusive).
     */
    @Column(name = "amounts_inclusive", nullable = false)
    private boolean amountsInclusive = false;

    /** Ҳаёт цикли ҳолати. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    /** Жами сумма ҳужжат валютасида (сатрлар йиғиндиси). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal total = BigDecimal.ZERO;

    /** Жами сумма home валютада - AR дебети айнан шу. */
    @Column(name = "total_base", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalBase = BigDecimal.ZERO;

    /** Тўланган қисм (ҳужжат валютасида) - денормализация. */
    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    /** Қолган қарз (ҳужжат валютасида) - денормализация. */
    @Column(name = "balance_due", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceDue = BigDecimal.ZERO;

    /** Тўланганлик ҳолати - денормализация (рўйхат экрани учун). */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 10)
    private InvoicePaymentStatus paymentStatus = InvoicePaymentStatus.UNPAID;

    /** Эркин изоҳ. */
    @Column(length = 500)
    private String memo;

    /** Post вақти (UTC). */
    @Column(name = "posted_at")
    private Instant postedAt;

    /** Сатрлар - invoice билан бирга сақланади/ўчади (композиция). */
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<InvoiceLine> lines = new ArrayList<>();

    /** Янги DRAFT invoice (валидация service'да). */
    public Invoice(String invoiceNumber, UUID customerId, LocalDate invoiceDate,
                   LocalDate dueDate, Currency currency, BigDecimal exchangeRate,
                   boolean amountsInclusive, String memo) {
        this.invoiceNumber = invoiceNumber;
        this.customerId = customerId;
        this.invoiceDate = invoiceDate;
        this.dueDate = dueDate;
        this.currency = currency;
        this.exchangeRate = exchangeRate;
        this.amountsInclusive = amountsInclusive;
        this.memo = memo;
    }

    /** POSTED/REVERSED ҳужжат ўзгармас (темир қоида №3) - domain guard. */
    private void requireDraft() {
        if (status != InvoiceStatus.DRAFT) {
            throw new BusinessRuleException(BusinessRule.BR_SINV_006,
                    "Invoice ўзгармас ҳолатда: " + invoiceNumber + " - " + status);
        }
    }

    /** Draft сарлавҳасини янгилайди (валидация service'да). */
    public void updateHeader(UUID customerId, LocalDate invoiceDate, LocalDate dueDate,
                             Currency currency, BigDecimal exchangeRate,
                             boolean amountsInclusive, String memo) {
        requireDraft();
        this.customerId = customerId;
        this.invoiceDate = invoiceDate;
        this.dueDate = dueDate;
        this.currency = currency;
        this.exchangeRate = exchangeRate;
        this.amountsInclusive = amountsInclusive;
        this.memo = memo;
    }

    /** Draft'га сатр қўшади ва жамини қайта ҳисоблайди. */
    public InvoiceLine addLine(InvoiceLineType type, UUID itemId, UUID warehouseId,
                               BigDecimal quantity, BigDecimal unitPrice,
                               UUID unitId, BigDecimal unitFactor, UUID incomeAccountId,
                               BigDecimal amount, UUID taxRateId, BigDecimal taxRateValue,
                               BigDecimal taxAmount, String memo) {
        requireDraft();
        InvoiceLine line = new InvoiceLine(this, lines.size() + 1, type, itemId,
                warehouseId, quantity, unitPrice, unitId, unitFactor, incomeAccountId,
                amount, taxRateId, taxRateValue, taxAmount, memo);
        lines.add(line);
        recalcTotals();
        return line;
    }

    /** Draft сатрларини тозалайди (қайта туриш учун - форма таҳрири). */
    public void clearLines() {
        requireDraft();
        lines.clear();
        recalcTotals();
    }

    /**
     * Жами ва base жамини сатрлардан қайта ҳисоблайди. totalBase =
     * {@link com.averpo.erp.shared.domain.MoneyAllocation#targetBase} -
     * битта яхлитлаш: GL'да AR дебети айнан шу target, даромад сатрлари
     * base'лари эса largest-remainder билан шунга тақсимланади
     * (LOG-002, Bill қолипи).
     */
    private void recalcTotals() {
        BigDecimal sum = BigDecimal.ZERO;
        for (InvoiceLine line : lines) {
            sum = sum.add(line.grossAmount());
        }
        this.total = sum;
        this.totalBase = com.averpo.erp.shared.domain.MoneyAllocation
                .targetBase(sum, exchangeRate);
        this.balanceDue = sum.subtract(paidAmount);
    }

    /** DRAFT'дан POSTED'га ўтказади (фақат InvoiceService чақиради). */
    public void markPosted(Instant postedAt) {
        requireDraft();
        this.status = InvoiceStatus.POSTED;
        this.postedAt = postedAt;
    }

    /** POSTED'дан REVERSED'га ўтказади (фақат InvoiceService чақиради). */
    public void markReversed() {
        if (status != InvoiceStatus.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_SINV_007,
                    "Фақат POSTED invoice reverse қилинади: " + invoiceNumber
                    + " - " + status);
        }
        this.status = InvoiceStatus.REVERSED;
    }

    /**
     * Тўлов денормализациясини янгилайди (фақат InvoicePaymentService
     * чақиради) - paid/balance/status битта формулада, бир жойда.
     */
    public void applyPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
        this.balanceDue = total.subtract(paidAmount);
        if (paidAmount.signum() == 0) {
            this.paymentStatus = InvoicePaymentStatus.UNPAID;
        } else if (balanceDue.signum() > 0) {
            this.paymentStatus = InvoicePaymentStatus.PARTIAL;
        } else {
            this.paymentStatus = InvoicePaymentStatus.PAID;
        }
    }
}
