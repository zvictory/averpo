package com.averpo.erp.purchase.domain;

import com.averpo.erp.shared.domain.BaseEntity;
import com.averpo.erp.shared.domain.Currency;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PurchaseOrder - таъминотчига буюртма (docs/modules/estimates-po.md,
 * QBO PurchaseOrder). Estimate'нинг харид томонидаги кўзгуси: GL'сиз,
 * POSTED тушунчаси йўқ, таҳрирланади - ҳаёт цикли
 * {@link PurchaseOrderStatus} (OPEN→CLOSED) билан. Ҳимоя қоидалари
 * (BR-PO-002/003) шу entity'да.
 *
 * @author Zafar
 */
@Entity
@Table(name = "purchase_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseOrder extends BaseEntity {

    /** Ҳужжат рақами - DocumentSequence PO-2026-NNNNN (unique). */
    @Column(name = "po_number", nullable = false, unique = true, length = 20)
    private String poNumber;

    /** Vendor контакт id'си (dimension, VENDOR типи service'да текширилади). */
    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    /** Ҳужжат санаси. */
    @Column(name = "po_date", nullable = false)
    private LocalDate poDate;

    /** Кутилган етказиб бериш санаси (ихтиёрий, QBO ShipDate услуби). */
    @Column(name = "expected_date")
    private LocalDate expectedDate;

    /** Ҳужжат валютаси - каталогга ManyToOne (қоида №11). EAGER: рўйхат/кўриш шаблонларида lazy хатоси бўлмасин. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    /** Ҳужжат курси (bill қолипи); home валютада 1. */
    @Column(name = "exchange_rate", nullable = false, precision = 24, scale = 12)
    private BigDecimal exchangeRate;

    /** Нархлар режими (tax.md): false - ҚҚСсиз, true - ҚҚС ичида. */
    @Column(name = "amounts_inclusive", nullable = false)
    private boolean amountsInclusive = false;

    /** Ҳаёт цикли ҳолати (ўтишлар changeStatus'да). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PurchaseOrderStatus status = PurchaseOrderStatus.OPEN;

    /** Жами GROSS сумма ҳужжат валютасида (сатр gross'лари йиғиндиси). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal total = BigDecimal.ZERO;

    /** Эркин изоҳ. */
    @Column(length = 500)
    private String memo;

    /** Айлантирилган bill id'си (LinkedTxn) - тўлдирилгач ҳужжат қулф. */
    @Column(name = "bill_id")
    private UUID billId;

    /** Сатрлар - буюртма билан бирга сақланади/ўчади (композиция). */
    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    /** Янги OPEN буюртма (валидация service'да). */
    public PurchaseOrder(String poNumber, UUID vendorId, LocalDate poDate,
                         LocalDate expectedDate, Currency currency,
                         BigDecimal exchangeRate, boolean amountsInclusive,
                         String memo) {
        this.poNumber = poNumber;
        this.vendorId = vendorId;
        this.poDate = poDate;
        this.expectedDate = expectedDate;
        this.currency = currency;
        this.exchangeRate = exchangeRate;
        this.amountsInclusive = amountsInclusive;
        this.memo = memo;
    }

    /** BR-PO-002: CLOSED таҳрирланмайди - domain guard. */
    private void requireEditable() {
        if (status == PurchaseOrderStatus.CLOSED) {
            throw new BusinessRuleException(BusinessRule.BR_PO_002,
                    "CLOSED буюртма таҳрирланмайди: " + poNumber);
        }
    }

    /** Сарлавҳани янгилайди (валидация service'да). */
    public void updateHeader(UUID vendorId, LocalDate poDate, LocalDate expectedDate,
                             Currency currency, BigDecimal exchangeRate,
                             boolean amountsInclusive, String memo) {
        requireEditable();
        this.vendorId = vendorId;
        this.poDate = poDate;
        this.expectedDate = expectedDate;
        this.currency = currency;
        this.exchangeRate = exchangeRate;
        this.amountsInclusive = amountsInclusive;
        this.memo = memo;
    }

    /** Сатр қўшади ва жамини қайта ҳисоблайди. */
    public PurchaseOrderLine addLine(UUID itemId, BigDecimal quantity,
                                     BigDecimal unitPrice, UUID unitId,
                                     BigDecimal amount, UUID taxRateId,
                                     BigDecimal taxRateValue, BigDecimal taxAmount,
                                     String memo) {
        requireEditable();
        PurchaseOrderLine line = new PurchaseOrderLine(this, lines.size() + 1, itemId,
                quantity, unitPrice, unitId, amount, taxRateId, taxRateValue,
                taxAmount, memo);
        lines.add(line);
        recalcTotal();
        return line;
    }

    /** Сатрларни тозалайди (форма таҳририда қайта терилади). */
    public void clearLines() {
        requireEditable();
        lines.clear();
        recalcTotal();
    }

    /** Жами = сатр gross'лари йиғиндиси (GL йўқ - base ҳисобланмайди). */
    private void recalcTotal() {
        BigDecimal sum = BigDecimal.ZERO;
        for (PurchaseOrderLine line : lines) {
            sum = sum.add(line.grossAmount());
        }
        this.total = sum;
    }

    /**
     * Status ўтиши (spec оқими): OPEN→CLOSED (қўлда ёпиш/бекор);
     * CLOSED→OPEN қайта очиш - фақат linked bill бўлмаса (BR-PO-002).
     */
    public void changeStatus(PurchaseOrderStatus to) {
        if (to == status) {
            return;
        }
        if (status == PurchaseOrderStatus.CLOSED && billId != null) {
            throw new BusinessRuleException(BusinessRule.BR_PO_002,
                    "Bill'га айлантирилган буюртма қайта очилмайди: " + poNumber);
        }
        this.status = to;
    }

    /** BR-PO-002/003: айлантириш мумкинлигини текширади (ёзмайди). */
    public void requireConvertible() {
        if (billId != null) {
            throw new BusinessRuleException(BusinessRule.BR_PO_003,
                    "Буюртма аллақачон bill'га айлантирилган: " + poNumber);
        }
        if (status == PurchaseOrderStatus.CLOSED) {
            throw new BusinessRuleException(BusinessRule.BR_PO_002,
                    "CLOSED буюртма айлантирилмайди: " + poNumber);
        }
    }

    /** Айлантирилди: linked bill ёзилади, буюртма CLOSED бўлади. */
    public void markConverted(UUID billId) {
        requireConvertible();
        this.billId = billId;
        this.status = PurchaseOrderStatus.CLOSED;
    }

    /** BR-PO-003: linked ҳужжат ўчирилмайди. */
    public void requireDeletable() {
        if (billId != null) {
            throw new BusinessRuleException(BusinessRule.BR_PO_003,
                    "Bill'га айлантирилган буюртма ўчирилмайди: " + poNumber);
        }
    }
}
