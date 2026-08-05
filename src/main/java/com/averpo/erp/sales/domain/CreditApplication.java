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
 * Кредитни invoice'га қўллаш (docs/modules/returns.md) - payment
 * allocation қолипи: GL'сиз subledger ҳаракати (иккала ҳужжат ўз
 * JE'сини аллақачон ёзган), фақат realized FX фарқи алоҳида JE
 * (CREDIT_APPLICATION манба). Бир (кредит, invoice) жуфтига биттагина
 * ёзув (DB unique). Payment allocation'дан фарқи - unapply бор:
 * қўллаш бекор қилинса ёзув ЎЧИРИЛАДИ (кредитнинг ўзи туради).
 *
 * @author Zafar
 */
@Entity
@Table(name = "credit_application",
       uniqueConstraints = @UniqueConstraint(columnNames = {"credit_memo_id", "invoice_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditApplication extends BaseEntity {

    /** Қўлланаётган кредит-нота. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_memo_id", nullable = false)
    private CreditMemo creditMemo;

    /** Кредит кетган invoice. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    /** Қўллаш суммаси (ҳужжат валютасида - иккаласи бир хил, BR-RET-004). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Янги қўллаш - фақат CreditMemoService орқали (валидация ўша ерда). */
    public CreditApplication(CreditMemo creditMemo, Invoice invoice, BigDecimal amount) {
        this.creditMemo = creditMemo;
        this.invoice = invoice;
        this.amount = amount;
    }
}
