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
import java.util.UUID;

/**
 * PurchaseOrder сатри - EstimateLine'нинг харид томонидаги кўзгуси:
 * фақат item буюртмаси (EXPENSE/LANDED_COST турлари bill'да,
 * айлантиришда қўшилиши мумкин), омбор йўқ - bill формасида
 * танланади. ҚҚС фақат кўрсатиш учун (amount - НЕТТО, snapshot'ли).
 */
@Entity
@Table(name = "purchase_order_line",
       uniqueConstraints = @UniqueConstraint(columnNames = {"po_id", "line_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseOrderLine extends BaseEntity {

    /** Эга ҳужжат. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "po_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    /** Сатр тартиб рақами (1 дан). */
    @Column(name = "line_no", nullable = false)
    private int lineNo;

    /** Буюртма қилинаётган item id'си (dimension). */
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

    /** Янги сатр - фақат PurchaseOrder.addLine орқали (композиция). */
    PurchaseOrderLine(PurchaseOrder purchaseOrder, int lineNo, UUID itemId,
                      BigDecimal quantity, BigDecimal unitPrice, UUID unitId,
                      BigDecimal amount, UUID taxRateId, BigDecimal taxRateValue,
                      BigDecimal taxAmount, String memo) {
        this.purchaseOrder = purchaseOrder;
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

    /** Gross (ҚҚСли) сумма - таъминотчига тўланадиган тўлиқ нарх. */
    public BigDecimal grossAmount() {
        return amount.add(taxAmount);
    }
}
