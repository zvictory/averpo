package com.averpo.erp.sales.domain;

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

/**
 * Тушум тақсимоти: тўловнинг қайси invoice'га қанчаси кетгани. Бир
 * (тўлов, invoice) жуфтига биттагина ёзув (DB unique, BR-RCPT-011).
 * Полиморф ҳавола атайлаб йўқ - тушум фақат invoice'ларга (purchase
 * модулидаги BillPaymentAllocation қарорининг кўзгуси). Олиб ташлаш
 * йўқ - тўловни reverse қилиш орқали бекор бўлади (MVP).
 */
@Entity
@Table(name = "invoice_payment_allocation",
       uniqueConstraints = @UniqueConstraint(columnNames = {"payment_id", "invoice_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvoicePaymentAllocation extends BaseEntity {

    /** Тўлов. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private InvoicePayment payment;

    /** Тақсимот кетган invoice. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    /** Тақсимот суммаси (ҳужжат/тўлов валютасида - бир хил, BR-RCPT-006). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Янги тақсимот - фақат InvoicePaymentService орқали (валидация ўша ерда). */
    public InvoicePaymentAllocation(InvoicePayment payment, Invoice invoice,
                                    BigDecimal amount) {
        this.payment = payment;
        this.invoice = invoice;
        this.amount = amount;
    }
}
