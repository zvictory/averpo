package com.averpo.erp.purchase.domain;

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
 * Харид ҳужжати (docs/modules/purchases.md). Ҳаёт цикли ledger модели
 * билан бир хил: DRAFT таҳрирланади, POSTED ўзгармас (фақат reverse).
 * Суммалар ҳужжат валютасида, base = amount × exchange_rate.
 *
 * <p>vendor_id - dimension паттерни (DB'да FK, JPA'да UUID - контакт
 * модулига entity боғланиш йўқ, қоида №6). Денормализация
 * (paid/balance/status) фақат BillPaymentService орқали янгиланади.
 *
 * @author Zafar
 */
@Entity
@Table(name = "bill")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bill extends BaseEntity {

    /** Ҳужжат рақами - DocumentSequence BILL-2026-NNNNN (unique). */
    @Column(name = "bill_number", nullable = false, unique = true, length = 20)
    private String billNumber;

    /** Vendor контакт id'си (dimension, VENDOR типи service'да текширилади). */
    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    /** Vendor'нинг ўз ҳисобварақ рақами - киритилса дубликат guard (BR-BILL-006). */
    @Column(name = "vendor_invoice_number", length = 100)
    private String vendorInvoiceNumber;

    /** Ҳужжат санаси. */
    @Column(name = "bill_date", nullable = false)
    private LocalDate billDate;

    /** Тўлов муддати - vendor тўлов шартидан автоматик, ўзгартириш мумкин. */
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
     * default), true - ҚҚС ичида (inclusive). Ҳисоблашда net/tax
     * бўлиниши шунга қараб (TaxAmounts).
     */
    @Column(name = "amounts_inclusive", nullable = false)
    private boolean amountsInclusive = false;

    /** Ҳаёт цикли ҳолати. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BillStatus status = BillStatus.DRAFT;

    /** Жами сумма ҳужжат валютасида (сатрлар йиғиндиси). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal total = BigDecimal.ZERO;

    /** Жами сумма home валютада - AP кредити айнан шу. */
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
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    /** Эркин изоҳ. */
    @Column(length = 500)
    private String memo;

    /** Post вақти (UTC). */
    @Column(name = "posted_at")
    private Instant postedAt;

    /** Сатрлар - bill билан бирга сақланади/ўчади (композиция). */
    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<BillLine> lines = new ArrayList<>();

    /** Янги DRAFT bill (валидация service'да). */
    public Bill(String billNumber, UUID vendorId, String vendorInvoiceNumber,
                LocalDate billDate, LocalDate dueDate, Currency currency,
                BigDecimal exchangeRate, boolean amountsInclusive, String memo) {
        this.billNumber = billNumber;
        this.vendorId = vendorId;
        this.vendorInvoiceNumber = vendorInvoiceNumber;
        this.billDate = billDate;
        this.dueDate = dueDate;
        this.currency = currency;
        this.exchangeRate = exchangeRate;
        this.amountsInclusive = amountsInclusive;
        this.memo = memo;
    }

    /** POSTED/REVERSED ҳужжат ўзгармас (темир қоида №3) - domain guard. */
    private void requireDraft() {
        if (status != BillStatus.DRAFT) {
            throw new BusinessRuleException(BusinessRule.BR_BILL_007,
                    "Bill ўзгармас ҳолатда: " + billNumber + " - " + status);
        }
    }

    /** Draft сарлавҳасини янгилайди (валидация service'да). */
    public void updateHeader(UUID vendorId, String vendorInvoiceNumber,
                             LocalDate billDate, LocalDate dueDate, Currency currency,
                             BigDecimal exchangeRate, boolean amountsInclusive, String memo) {
        requireDraft();
        this.vendorId = vendorId;
        this.vendorInvoiceNumber = vendorInvoiceNumber;
        this.billDate = billDate;
        this.dueDate = dueDate;
        this.currency = currency;
        this.exchangeRate = exchangeRate;
        this.amountsInclusive = amountsInclusive;
        this.memo = memo;
    }

    /** Draft'га сатр қўшади ва жамини қайта ҳисоблайди. */
    public BillLine addLine(BillLineType type, UUID itemId, UUID warehouseId,
                            BigDecimal quantity, BigDecimal unitPrice,
                            UUID unitId, BigDecimal unitFactor, UUID accountId,
                            BigDecimal amount, UUID taxRateId, BigDecimal taxRateValue,
                            BigDecimal taxAmount, String memo) {
        requireDraft();
        BillLine line = new BillLine(this, lines.size() + 1, type, itemId,
                warehouseId, quantity, unitPrice, unitId, unitFactor, accountId,
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
     * Жами ва base жамини сатрлардан қайта ҳисоблайди. total энди
     * GROSS (ҚҚСли): Σ(net + tax) - AP, balance due, тўловлар шунга
     * ишлайди (docs/modules/tax.md). totalBase =
     * {@link com.averpo.erp.shared.domain.MoneyAllocation#targetBase} -
     * битта яхлитлаш: GL'да AP кредити айнан шу target, дебет сатрлар
     * (net'лар + ҚҚС'лар) base'лари largest-remainder билан шунга
     * тақсимланади (Asrorxoja-002).
     */
    private void recalcTotals() {
        BigDecimal sum = BigDecimal.ZERO;
        for (BillLine line : lines) {
            sum = sum.add(line.grossAmount());
        }
        this.total = sum;
        this.totalBase = com.averpo.erp.shared.domain.MoneyAllocation
                .targetBase(sum, exchangeRate);
        this.balanceDue = sum.subtract(paidAmount);
    }

    /** DRAFT'дан POSTED'га ўтказади (фақат BillService чақиради). */
    public void markPosted(Instant postedAt) {
        requireDraft();
        this.status = BillStatus.POSTED;
        this.postedAt = postedAt;
    }

    /** POSTED'дан REVERSED'га ўтказади (фақат BillService чақиради). */
    public void markReversed() {
        if (status != BillStatus.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_BILL_008,
                    "Фақат POSTED bill reverse қилинади: " + billNumber + " - " + status);
        }
        this.status = BillStatus.REVERSED;
    }

    /**
     * Тўлов денормализациясини янгилайди (фақат BillPaymentService
     * чақиради) - paid/balance/status битта формулада, бир жойда.
     */
    public void applyPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
        this.balanceDue = total.subtract(paidAmount);
        if (paidAmount.signum() == 0) {
            this.paymentStatus = PaymentStatus.UNPAID;
        } else if (balanceDue.signum() > 0) {
            this.paymentStatus = PaymentStatus.PARTIAL;
        } else {
            this.paymentStatus = PaymentStatus.PAID;
        }
    }
}
