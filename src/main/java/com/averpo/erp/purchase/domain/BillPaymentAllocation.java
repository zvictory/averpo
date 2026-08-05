package com.averpo.erp.purchase.domain;

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
 * Тўлов тақсимоти: тўловнинг қайси bill'га қанчаси кетгани. Бир
 * (тўлов, bill) жуфтига биттагина ёзув (DB unique). Полиморф ҳавола
 * атайлаб йўқ - тўлов фақат bill'ларга (2026-07-06 қарори,
 * 7-босқичда InvoicePayment алоҳида бўлади). Олиб ташлаш йўқ -
 * тўловни reverse қилиш орқали бекор бўлади (MVP).
 *
 * @author Zafar
 */
@Entity
@Table(name = "bill_payment_allocation",
       uniqueConstraints = @UniqueConstraint(columnNames = {"payment_id", "bill_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillPaymentAllocation extends BaseEntity {

    /** Тўлов. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private BillPayment payment;

    /** Тақсимот кетган bill. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    /** Тақсимот суммаси (ҳужжат/тўлов валютасида - бир хил, BR-PAY-006). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Янги тақсимот - фақат BillPaymentService орқали (валидация ўша ерда). */
    public BillPaymentAllocation(BillPayment payment, Bill bill, BigDecimal amount) {
        this.payment = payment;
        this.bill = bill;
        this.amount = amount;
    }
}
