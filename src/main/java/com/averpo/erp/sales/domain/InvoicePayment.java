package com.averpo.erp.sales.domain;

import com.averpo.erp.shared.domain.BaseEntity;
import com.averpo.erp.shared.domain.Currency;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Мижоз тўлови/тушум (docs/modules/sales.md) - BillPayment'нинг кўзгу
 * акси. DRAFT ҳолати ЙЎҚ - яратилди = POSTED (QBO услуби), тузатиш
 * reverse орқали. Аванс рухсат: total = allocated + unallocated
 * (DB CHECK ҳам бор) - тақсимланмаган қисм AR'да мижоз аванси
 * (кредит қолдиқ) бўлиб туради, кейин allocate қилинади.
 */
@Entity
@Table(name = "invoice_payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvoicePayment extends BaseEntity {

    /** Тўлов ҳолати - DRAFT йўқ (тўлов модели). */
    public enum Status {
        /** Ўтказилган - GL'да акс этган, ўзгармас. */
        POSTED,
        /** Сторно қилинган - allocation'лари бекор бўлган. */
        REVERSED
    }

    /** Ҳужжат рақами - DocumentSequence RCPT-2026-NNNNN (unique). */
    @Column(name = "receipt_number", nullable = false, unique = true, length = 20)
    private String receiptNumber;

    /** Customer контакт id'си (dimension) - allocation'лар ҳам шу мижозга. */
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    /** Тўлов санаси. */
    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    /** Қабул счёти id'си: банк/касса/UNDEPOSITED_FUNDS (BR-RCPT-002). */
    @Column(name = "deposit_account_id", nullable = false)
    private UUID depositAccountId;

    /** Тўлов валютаси (invoice валютаси билан бир хил - BR-RCPT-006). */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    /** Тўлов курси - invoice курсидан фарқли бўлса realized курс фарқи. */
    @Column(name = "exchange_rate", nullable = false, precision = 24, scale = 12)
    private BigDecimal exchangeRate;

    /** Тўлиқ тўлов суммаси (ҳужжат валютасида). */
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    /** Invoice'ларга тақсимланган қисм - денормализация. */
    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal allocatedAmount = BigDecimal.ZERO;

    /** Тақсимланмаган қисм (аванс) - денормализация. */
    @Column(name = "unallocated_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal unallocatedAmount = BigDecimal.ZERO;

    /** Ҳолат. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.POSTED;

    /** Эркин изоҳ. */
    @Column(length = 500)
    private String memo;

    /** Янги тўлов - дарҳол POSTED, unallocated = total (валидация service'да). */
    public InvoicePayment(String receiptNumber, UUID customerId, LocalDate paymentDate,
                          UUID depositAccountId, Currency currency,
                          BigDecimal exchangeRate, BigDecimal totalAmount, String memo) {
        this.receiptNumber = receiptNumber;
        this.customerId = customerId;
        this.paymentDate = paymentDate;
        this.depositAccountId = depositAccountId;
        this.currency = currency;
        this.exchangeRate = exchangeRate;
        this.totalAmount = totalAmount;
        this.unallocatedAmount = totalAmount;
        this.memo = memo;
    }

    /**
     * Тақсимот денормализациясини янгилайди (фақат InvoicePaymentService
     * чақиради) - total = allocated + unallocated инварианти DB CHECK
     * билан ҳам ҳимояланган.
     */
    public void applyAllocated(BigDecimal allocatedAmount) {
        this.allocatedAmount = allocatedAmount;
        this.unallocatedAmount = totalAmount.subtract(allocatedAmount);
    }

    /** POSTED'дан REVERSED'га ўтказади (фақат InvoicePaymentService чақиради). */
    public void markReversed() {
        if (status != Status.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_RCPT_007,
                    "Фақат POSTED тўлов reverse қилинади: " + receiptNumber
                    + " - " + status);
        }
        this.status = Status.REVERSED;
    }
}
