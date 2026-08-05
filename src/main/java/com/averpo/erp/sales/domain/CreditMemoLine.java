package com.averpo.erp.sales.domain;

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
 * Кредит-нота сатри - invoice_line кўзгуси (docs/modules/returns.md):
 * item/хизмат, миқдор, бирлик (UoM snapshot), нарх, ҚҚС ставка
 * snapshot (tax.md механизми айнан - amount НЕТТО, gross = net + tax),
 * class теги (class-tracking.md).
 */
@Entity
@Table(name = "credit_memo_line",
       uniqueConstraints = @UniqueConstraint(columnNames = {"credit_memo_id", "line_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditMemoLine extends BaseEntity {

    /** Ҳужжат. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_memo_id", nullable = false)
    private CreditMemo creditMemo;

    /** Сатр тартиби (1 дан). */
    @Column(name = "line_no", nullable = false)
    private int lineNo;

    /** Тур item ItemType'идан - ITEM'да омбор кирими бор (invoice кўзгуси). */
    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, length = 10)
    private InvoiceLineType type;

    /** Қайтарилаётган item - dimension (DB'да FK). */
    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    /** ITEM сатрида қайтим омбори (BR-RET-002). */
    @Column(name = "warehouse_id")
    private UUID warehouseId;

    /** Миқдор (киритилган бирликда). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    /** Бирлик нархи (ҳужжат валютасида). */
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    /** Киритилган бирлик (UoM) ёки null - base. */
    @Column(name = "unit_id")
    private UUID unitId;

    /** Бирлик factor snapshot'и (uom.md) - null = 1 (base). */
    @Column(name = "unit_factor", precision = 19, scale = 6)
    private BigDecimal unitFactor;

    /** Даромад счёти - GL'да Dr томонга қайтади (invoice кўзгуси). */
    @Column(name = "income_account_id", nullable = false)
    private UUID incomeAccountId;

    /** НЕТТО сумма (ҳужжат валютасида) - gross = amount + taxAmount. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Танланган ҚҚС ставкаси ёки null - солиқсиз. */
    @Column(name = "tax_rate_id")
    private UUID taxRateId;

    /** Ставка ҚИЙМАТИ snapshot'и (tax.md) - каталог ўзгарса ҳужжат бузилмайди. */
    @Column(name = "tax_rate_value", precision = 9, scale = 4)
    private BigDecimal taxRateValue;

    /** Сатр ҚҚСи (ҳужжат валютасида) - output қайтиши, Dr SALES_TAX_PAYABLE. */
    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    /** Ихтиёрий Class/Йўналиш теги - даромад/COGS легига кўчади. */
    @Column(name = "class_id")
    private UUID classId;

    /** Эркин изоҳ. */
    @Column(length = 500)
    private String memo;

    /** Class тегини қўяди - конструктор кенгаймасин (invoice қолипи). */
    public void applyClass(UUID classId) {
        this.classId = classId;
    }

    /** Base миқдор ҳисоби учун factor - null'да 1 (base сатрлар). */
    public BigDecimal unitFactorOrOne() {
        return unitFactor == null ? BigDecimal.ONE : unitFactor;
    }

    /** Gross (ҚҚСли) сумма - AR кредити шу устида (amount + tax). */
    public BigDecimal grossAmount() {
        return amount.add(taxAmount);
    }

    /** Янги сатр - фақат CreditMemo.addLine орқали (композиция). */
    CreditMemoLine(CreditMemo creditMemo, int lineNo, InvoiceLineType type,
                   UUID itemId, UUID warehouseId, BigDecimal quantity,
                   BigDecimal unitPrice, UUID unitId, BigDecimal unitFactor,
                   UUID incomeAccountId, BigDecimal amount, UUID taxRateId,
                   BigDecimal taxRateValue, BigDecimal taxAmount, String memo) {
        this.creditMemo = creditMemo;
        this.lineNo = lineNo;
        this.type = type;
        this.itemId = itemId;
        this.warehouseId = warehouseId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.unitId = unitId;
        this.unitFactor = unitFactor;
        this.incomeAccountId = incomeAccountId;
        this.amount = amount;
        this.taxRateId = taxRateId;
        this.taxRateValue = taxRateValue;
        this.taxAmount = taxAmount == null ? BigDecimal.ZERO : taxAmount;
        this.memo = memo;
    }
}
