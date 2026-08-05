package com.averpo.erp.purchase.domain;

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
 * Vendor тўлови (docs/modules/purchases.md). DRAFT ҳолати ЙЎҚ -
 * яратилди = POSTED (QBO услуби), тузатиш reverse орқали. Аванс
 * рухсат: total = allocated + unallocated (DB CHECK ҳам бор) -
 * тақсимланмаган қисм AP'да vendor аванси бўлиб туради, кейин
 * allocate қилинади.
 */
@Entity
@Table(name = "bill_payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillPayment extends BaseEntity {

    /** Тўлов ҳолати - DRAFT йўқ, шунинг учун BillStatus ишлатилмайди. */
    public enum Status {
        /** Ўтказилган - GL'да акс этган, ўзгармас. */
        POSTED,
        /** Сторно қилинган - allocation'лари бекор бўлган. */
        REVERSED
    }

    /** Ҳужжат рақами - DocumentSequence PAY-2026-NNNNN (unique). */
    @Column(name = "payment_number", nullable = false, unique = true, length = 20)
    private String paymentNumber;

    /** Vendor контакт id'си (dimension) - allocation'лар ҳам шу vendor'га. */
    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    /** Тўлов санаси. */
    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    /** Тўлов банк счёти id'си (BANK тури, BR-PAY-002). */
    @Column(name = "bank_account_id", nullable = false)
    private UUID bankAccountId;

    /** Тўлов валютаси (bill валютаси билан бир хил - BR-PAY-006). */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    /** Тўлов курси - bill курсидан фарқли бўлса realized курс фарқи. */
    @Column(name = "exchange_rate", nullable = false, precision = 24, scale = 12)
    private BigDecimal exchangeRate;

    /** Тўлиқ тўлов суммаси (ҳужжат валютасида). */
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    /** Bill'ларга тақсимланган қисм - денормализация. */
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
    public BillPayment(String paymentNumber, UUID vendorId, LocalDate paymentDate,
                       UUID bankAccountId, Currency currency, BigDecimal exchangeRate,
                       BigDecimal totalAmount, String memo) {
        this.paymentNumber = paymentNumber;
        this.vendorId = vendorId;
        this.paymentDate = paymentDate;
        this.bankAccountId = bankAccountId;
        this.currency = currency;
        this.exchangeRate = exchangeRate;
        this.totalAmount = totalAmount;
        this.unallocatedAmount = totalAmount;
        this.memo = memo;
    }

    /**
     * Тақсимот денормализациясини янгилайди (фақат BillPaymentService
     * чақиради) - total = allocated + unallocated инварианти DB CHECK
     * билан ҳам ҳимояланган.
     */
    public void applyAllocated(BigDecimal allocatedAmount) {
        this.allocatedAmount = allocatedAmount;
        this.unallocatedAmount = totalAmount.subtract(allocatedAmount);
    }

    /** POSTED'дан REVERSED'га ўтказади (фақат BillPaymentService чақиради). */
    public void markReversed() {
        if (status != Status.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_PAY_007,
                    "Фақат POSTED тўлов reverse қилинади: " + paymentNumber + " - " + status);
        }
        this.status = Status.REVERSED;
    }
}
