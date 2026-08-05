package com.averpo.erp.purchase.domain;

import com.averpo.erp.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Таъминотчи кредит-нотаси сатри - bill_line кўзгуси (returns.md):
 * ITEM (омбордан қайтадиган товар - item/омбор/миқдор/нарх) ёки
 * EXPENSE (қайтадиган харажат - счёт/сумма); LANDED_COST қайтарилмайди
 * (у bill'нинг ўз механизми). ҚҚС ставка snapshot tax.md механизми
 * айнан - amount НЕТТО, gross = net + tax; class теги class-tracking.md.
 *
 * @author Zafar
 */
@Entity
@Table(name = "vendor_credit_line",
       uniqueConstraints = @UniqueConstraint(columnNames = {"vendor_credit_id", "line_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VendorCreditLine extends BaseEntity {

    /** Ҳужжат. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_credit_id", nullable = false)
    private VendorCredit vendorCredit;

    /** Сатр тартиби (1 дан). */
    @Column(name = "line_no", nullable = false)
    private int lineNo;

    /** Тур - ITEM'да омбор чиқими бор, EXPENSE'да харажат счёти (bill кўзгуси). */
    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, length = 10)
    private BillLineType type;

    /** ITEM сатрида: қайтарилаётган item (dimension, DB'да FK). */
    @Column(name = "item_id")
    private UUID itemId;

    /** ITEM сатрида: қайтим омбори (BR-RET-002). */
    @Column(name = "warehouse_id")
    private UUID warehouseId;

    /** ITEM сатрида: миқдор (киритилган бирликда). */
    @Column(precision = 19, scale = 4)
    private BigDecimal quantity;

    /** ITEM сатрида: бирлик нархи (ҳужжат валютасида). */
    @Column(name = "unit_price", precision = 19, scale = 4)
    private BigDecimal unitPrice;

    /** ITEM сатрида: киритилган бирлик (UoM) ёки null - base. */
    @Column(name = "unit_id")
    private UUID unitId;

    /** Бирлик factor snapshot'и (uom.md) - null = 1 (base). */
    @Column(name = "unit_factor", precision = 19, scale = 6)
    private BigDecimal unitFactor;

    /** EXPENSE сатрида: қайтадиган харажат счёти - GL'да Cr томонга. */
    @Column(name = "account_id")
    private UUID accountId;

    /** НЕТТО сумма (ҳужжат валютасида) - gross = amount + taxAmount. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Танланган ҚҚС ставкаси ёки null - солиқсиз. */
    @Column(name = "tax_rate_id")
    private UUID taxRateId;

    /** Ставка ҚИЙМАТИ snapshot'и (tax.md) - каталог ўзгарса ҳужжат бузилмайди. */
    @Column(name = "tax_rate_value", precision = 9, scale = 4)
    private BigDecimal taxRateValue;

    /** Сатр ҚҚСи (ҳужжат валютасида) - input қайтиши, Cr SALES_TAX_PAYABLE. */
    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    /** Ихтиёрий Class/Йўналиш теги - харажат/қайтим-фарқи легига кўчади. */
    @Column(name = "class_id")
    private UUID classId;

    /** Эркин изоҳ. */
    @Column(length = 500)
    private String memo;

    /** Class тегини қўяди - конструктор кенгаймасин (bill қолипи). */
    public void applyClass(UUID classId) {
        this.classId = classId;
    }

    /** Base миқдор ҳисоби учун factor - null'да 1 (base сатрлар). */
    public BigDecimal unitFactorOrOne() {
        return unitFactor == null ? BigDecimal.ONE : unitFactor;
    }

    /** Gross (ҚҚСли) сумма - AP дебети шу устида (amount + tax). */
    public BigDecimal grossAmount() {
        return amount.add(taxAmount);
    }

    /** Янги сатр - фақат VendorCredit.addLine орқали (композиция). */
    VendorCreditLine(VendorCredit vendorCredit, int lineNo, BillLineType type,
                     UUID itemId, UUID warehouseId, BigDecimal quantity,
                     BigDecimal unitPrice, UUID unitId, BigDecimal unitFactor,
                     UUID accountId, BigDecimal amount, UUID taxRateId,
                     BigDecimal taxRateValue, BigDecimal taxAmount, String memo) {
        this.vendorCredit = vendorCredit;
        this.lineNo = lineNo;
        this.type = type;
        this.itemId = itemId;
        this.warehouseId = warehouseId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.unitId = unitId;
        this.unitFactor = unitFactor;
        this.accountId = accountId;
        this.amount = amount;
        this.taxRateId = taxRateId;
        this.taxRateValue = taxRateValue;
        this.taxAmount = taxAmount == null ? BigDecimal.ZERO : taxAmount;
        this.memo = memo;
    }
}
