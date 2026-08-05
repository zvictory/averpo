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
 * Таъминотчи кредитини bill'га қўллаш (docs/modules/returns.md) -
 * credit_application'нинг AP кўзгуси: GL'сиз subledger ҳаракати
 * (иккала ҳужжат ўз JE'сини аллақачон ёзган), фақат realized FX фарқи
 * алоҳида JE (VENDOR_CREDIT_APPLICATION манба). Бир (кредит, bill)
 * жуфтига биттагина ёзув (DB unique); unapply'да ёзув ЎЧИРИЛАДИ
 * (кредитнинг ўзи очиқ қолдиғи билан туради).
 */
@Entity
@Table(name = "vendor_credit_application",
       uniqueConstraints = @UniqueConstraint(columnNames = {"vendor_credit_id", "bill_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VendorCreditApplication extends BaseEntity {

    /** Қўлланаётган кредит-нота. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_credit_id", nullable = false)
    private VendorCredit vendorCredit;

    /** Кредит кетган bill. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    /** Қўллаш суммаси (ҳужжат валютасида - иккаласи бир хил, BR-RET-004). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Янги қўллаш - фақат VendorCreditService орқали (валидация ўша ерда). */
    public VendorCreditApplication(VendorCredit vendorCredit, Bill bill, BigDecimal amount) {
        this.vendorCredit = vendorCredit;
        this.bill = bill;
        this.amount = amount;
    }
}
