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
import java.util.UUID;

/**
 * Estimate сатри - invoice_line'нинг GL'сиз кўзгуси: омбор ва даромад
 * счёти ЙЎҚ (айлантиришда invoice формасида танланади/аниқланади),
 * unit factor snapshot ҳам керак эмас (омбор ҳаракати йўқ). ҚҚС фақат
 * кўрсатиш учун: amount - НЕТТО, gross = amount + tax_amount,
 * tax_rate_value - snapshot (docs/modules/tax.md нақши).
 */
@Entity
@Table(name = "estimate_line",
       uniqueConstraints = @UniqueConstraint(columnNames = {"estimate_id", "line_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EstimateLine extends BaseEntity {

    /** Эга ҳужжат. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estimate_id", nullable = false)
    private Estimate estimate;

    /** Сатр тартиб рақами (1 дан). */
    @Column(name = "line_no", nullable = false)
    private int lineNo;

    /** Таклиф қилинаётган item id'си (dimension - JPA боғланиш йўқ). */
    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    /** Миқдор (мусбат) - киритилган бирликда. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    /** Бирлик нархи ҳужжат валютасида - киритилган бирликка. */
    @Column(name = "unit_price", nullable = false, precision = 24, scale = 12)
    private BigDecimal unitPrice;

    /** Киритилган бирлик id'си (UoM) ёки null - item base бирлиги. */
    @Column(name = "unit_id")
    private UUID unitId;

    /** Сатр НЕТТО суммаси (солиқсиз) ҳужжат валютасида. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Танланган ҚҚС ставкаси id'си (dimension) ёки null - солиқсиз. */
    @Column(name = "tax_rate_id")
    private UUID taxRateId;

    /** Ставка фоизининг snapshot'и (сатр ёзилган пайтдаги қиймат). */
    @Column(name = "tax_rate_value", precision = 9, scale = 4)
    private BigDecimal taxRateValue;

    /** Сатр ҚҚСи - фақат кўрсатиш (GL'га ҳеч нарса ёзилмайди). */
    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    /** Эркин изоҳ. */
    @Column(length = 500)
    private String memo;

    /** Янги сатр - фақат Estimate.addLine орқали (композиция). */
    EstimateLine(Estimate estimate, int lineNo, UUID itemId, BigDecimal quantity,
                 BigDecimal unitPrice, UUID unitId, BigDecimal amount,
                 UUID taxRateId, BigDecimal taxRateValue, BigDecimal taxAmount,
                 String memo) {
        this.estimate = estimate;
        this.lineNo = lineNo;
        this.itemId = itemId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.unitId = unitId;
        this.amount = amount;
        this.taxRateId = taxRateId;
        this.taxRateValue = taxRateValue;
        this.taxAmount = taxAmount == null ? BigDecimal.ZERO : taxAmount;
        this.memo = memo;
    }

    /** Gross (ҚҚСли) сумма - мижоз кўрадиган тўлиқ нарх. */
    public BigDecimal grossAmount() {
        return amount.add(taxAmount);
    }
}
