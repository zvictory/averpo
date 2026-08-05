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
 * Bill сатри - тури проводка йўналишини белгилайди (BillLineType).
 * Суммалар ҳужжат валютасида; item/warehouse/account - dimension
 * паттернидаги UUID'лар (DB'да FK бор, JPA боғланиш йўқ - қоида №6).
 * POSTED bill'нинг сатрлари ўзгармас - guard Bill'нинг ўзида.
 *
 * @author Zafar
 */
@Entity
@Table(name = "bill_line",
       uniqueConstraints = @UniqueConstraint(columnNames = {"bill_id", "line_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillLine extends BaseEntity {

    /** Эга ҳужжат. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    /** Сатр тартиб рақами (1 дан). */
    @Column(name = "line_no", nullable = false)
    private int lineNo;

    /** Сатр тури - ITEM / EXPENSE / LANDED_COST. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private BillLineType type;

    /** ITEM сатрида: INVENTORY типдаги item id'си (BR-BILL-004). */
    @Column(name = "item_id")
    private UUID itemId;

    /** ITEM сатрида: кирим омбори. */
    @Column(name = "warehouse_id")
    private UUID warehouseId;

    /** ITEM сатрида: миқдор (мусбат) - КИРИТИЛГАН бирликда. */
    @Column(precision = 19, scale = 4)
    private BigDecimal quantity;

    /** ITEM сатрида: бирлик нарх ҳужжат валютасида - КИРИТИЛГАН бирликка. */
    @Column(name = "unit_price", precision = 24, scale = 12)
    private BigDecimal unitPrice;

    /**
     * ITEM сатрида: киритилган бирлик id'си (dimension, UoM) ёки null -
     * item base бирлиги (эски сатрлар ҳам шундай ўқилади).
     */
    @Column(name = "unit_id")
    private UUID unitId;

    /**
     * Бирлик factor'ининг SNAPSHOT'и (сатр ёзилган пайтдаги қиймат,
     * docs/modules/uom.md): base миқдор = quantity × unit_factor.
     * Кейин каталогда factor ўзгарса тарихий ҳужжат бузилмайди.
     * null - factor 1 (base бирликда киритилган).
     */
    @Column(name = "unit_factor", precision = 24, scale = 12)
    private BigDecimal unitFactor;

    /** EXPENSE сатрида: харажат счёти id'си (BR-BILL-005). */
    @Column(name = "account_id")
    private UUID accountId;

    /**
     * Сатр НЕТТО суммаси (солиқсиз) ҳужжат валютасида - GL дебет асоси.
     * ITEM'да exclusive: qty × unit_price; inclusive: gross / (1+r).
     * Gross = amount + tax_amount (docs/modules/tax.md).
     */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Танланган ҚҚС ставкаси id'си (dimension) ёки null - солиқсиз. */
    @Column(name = "tax_rate_id")
    private UUID taxRateId;

    /**
     * Ставка фоизининг SNAPSHOT'и (сатр ёзилган пайтдаги қиймат,
     * docs/modules/tax.md): кейин каталогда ставка ўзгарса тарихий
     * ҳужжат бузилмайди. null - солиқсиз сатр.
     */
    @Column(name = "tax_rate_value", precision = 9, scale = 4)
    private BigDecimal taxRateValue;

    /** Сатр ҚҚСи ҳужжат валютасида (SALES_TAX_PAYABLE'га боради). */
    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    /** Эркин изоҳ. */
    @Column(length = 500)
    private String memo;

    /**
     * Ихтиёрий Class/Йўналиш теги (class-tracking.md) - GL'да шу
     * сатрнинг харажат/inventory легига айнан кўчади. Dimension
     * паттерни: DB'да FK txn_class, JPA'да UUID.
     */
    @Column(name = "class_id")
    private UUID classId;

    /** Class тегини қўяди - конструктор кенгаймасин (applyPaymentDetails нақши). */
    public void applyClass(UUID classId) {
        this.classId = classId;
    }

    /** Янги сатр - фақат Bill.addLine орқали (композиция). */
    BillLine(Bill bill, int lineNo, BillLineType type, UUID itemId,
             UUID warehouseId, BigDecimal quantity, BigDecimal unitPrice,
             UUID unitId, BigDecimal unitFactor, UUID accountId, BigDecimal amount,
             UUID taxRateId, BigDecimal taxRateValue, BigDecimal taxAmount, String memo) {
        this.bill = bill;
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

    /** Gross (ҚҚСли) сумма - AP/тўлов шу устида (amount + tax). */
    public BigDecimal grossAmount() {
        return amount.add(taxAmount);
    }

    /** Base миқдор ҳисоби учун factor - null'да 1 (эски/base сатрлар). */
    public BigDecimal unitFactorOrOne() {
        return unitFactor == null ? BigDecimal.ONE : unitFactor;
    }
}
