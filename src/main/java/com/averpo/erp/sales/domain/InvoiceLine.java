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
 * Invoice сатри - тури item'нинг ItemType'идан келиб чиқади: ITEM
 * (INVENTORY item - омбордан чиқим + COGS) ёки SERVICE (омборсиз,
 * фақат даромад). Суммалар ҳужжат валютасида; item/warehouse/account -
 * dimension паттернидаги UUID'лар (DB'да FK, JPA боғланиш йўқ -
 * қоида №6). POSTED invoice'нинг сатрлари ўзгармас - guard
 * Invoice'нинг ўзида.
 */
@Entity
@Table(name = "invoice_line",
       uniqueConstraints = @UniqueConstraint(columnNames = {"invoice_id", "line_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvoiceLine extends BaseEntity {

    /** Эга ҳужжат. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    /** Сатр тартиб рақами (1 дан). */
    @Column(name = "line_no", nullable = false)
    private int lineNo;

    /** Сатр тури - ITEM / SERVICE. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InvoiceLineType type;

    /** Сотилаётган item id'си - ҳар икки турда ҳам шарт (BR-SINV-004/010). */
    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    /** ITEM сатрида: чиқим омбори (SERVICE'да null). */
    @Column(name = "warehouse_id")
    private UUID warehouseId;

    /** Миқдор (мусбат) - КИРИТИЛГАН бирликда. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    /** Бирлик сотув нархи ҳужжат валютасида - КИРИТИЛГАН бирликка. */
    @Column(name = "unit_price", nullable = false, precision = 24, scale = 12)
    private BigDecimal unitPrice;

    /**
     * Киритилган бирлик id'си (dimension, UoM) ёки null - item base
     * бирлиги (эски сатрлар ҳам шундай ўқилади).
     */
    @Column(name = "unit_id")
    private UUID unitId;

    /**
     * Бирлик factor'ининг SNAPSHOT'и (сатр ёзилган пайтдаги қиймат,
     * docs/modules/uom.md): ITEM сатрида омбордан чиқим base миқдорда =
     * quantity × unit_factor. Кейин каталогда factor ўзгарса тарихий
     * ҳужжат бузилмайди. null - factor 1 (base бирликда киритилган).
     */
    @Column(name = "unit_factor", precision = 24, scale = 12)
    private BigDecimal unitFactor;

    /** Даромад счёти id'си - item'дан default, сатрда ўзгартирса бўлади (BR-SINV-005). */
    @Column(name = "income_account_id", nullable = false)
    private UUID incomeAccountId;

    /**
     * Сатр НЕТТО суммаси (солиқсиз) ҳужжат валютасида - GL даромад
     * асоси. Gross = amount + tax_amount (docs/modules/tax.md).
     */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Танланган ҚҚС ставкаси id'си (dimension) ёки null - солиқсиз. */
    @Column(name = "tax_rate_id")
    private UUID taxRateId;

    /** Ставка фоизининг SNAPSHOT'и (сатр ёзилган пайтдаги қиймат, tax.md). */
    @Column(name = "tax_rate_value", precision = 9, scale = 4)
    private BigDecimal taxRateValue;

    /** Сатр ҚҚСи ҳужжат валютасида (SALES_TAX_PAYABLE'га боради). */
    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    /** Эркин изоҳ (item'нинг sales description'идан default). */
    @Column(length = 500)
    private String memo;

    /**
     * Ихтиёрий Class/Йўналиш теги (class-tracking.md) - GL'да даромад
     * ва шу сатрдан келиб чиққан COGS легига айнан кўчади. Dimension
     * паттерни: DB'да FK txn_class, JPA'да UUID.
     */
    @Column(name = "class_id")
    private UUID classId;

    /** Class тегини қўяди - конструктор кенгаймасин (applyPaymentDetails нақши). */
    public void applyClass(UUID classId) {
        this.classId = classId;
    }

    /** Янги сатр - фақат Invoice.addLine орқали (композиция). */
    InvoiceLine(Invoice invoice, int lineNo, InvoiceLineType type, UUID itemId,
                UUID warehouseId, BigDecimal quantity, BigDecimal unitPrice,
                UUID unitId, BigDecimal unitFactor, UUID incomeAccountId,
                BigDecimal amount, UUID taxRateId, BigDecimal taxRateValue,
                BigDecimal taxAmount, String memo) {
        this.invoice = invoice;
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

    /** Base миқдор ҳисоби учун factor - null'да 1 (эски/base сатрлар). */
    public BigDecimal unitFactorOrOne() {
        return unitFactor == null ? BigDecimal.ONE : unitFactor;
    }

    /** Gross (ҚҚСли) сумма - AR/тўлов шу устида (amount + tax). */
    public BigDecimal grossAmount() {
        return amount.add(taxAmount);
    }
}
