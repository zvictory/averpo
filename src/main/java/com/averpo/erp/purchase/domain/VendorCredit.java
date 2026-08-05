package com.averpo.erp.purchase.domain;

import com.averpo.erp.shared.domain.BaseEntity;
import com.averpo.erp.shared.domain.Currency;
import com.averpo.erp.shared.domain.MoneyAllocation;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Таъминотчи кредит-нотаси (docs/modules/returns.md, QBO VendorCredit):
 * CreditMemo'нинг AP кўзгуси - харажат/ҚҚС қайтади, AP камаяди,
 * inventory сатрлар омбордан ЧИҚАДИ. DRAFT йўқ - яратилди = POSTED
 * (bank txn нақши), тузатиш reverse (темир қоида 3).
 *
 * <p>Очиқ қолдиқ = total − appliedAmount - денормализация (CreditMemo
 * қолипи); application фақат VendorCreditService орқали. billId -
 * ихтиёрий асл ҳужжат ҳаваласи (prefill + BR-RET-006 миқдор чеклови).
 */
@Entity
@Table(name = "vendor_credit")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VendorCredit extends BaseEntity {

    /** Ҳужжат ҳолати - DRAFT йўқ (тўлов модели). */
    public enum Status {
        /** Ўтказилган - GL'да акс этган, ўзгармас. */
        POSTED,
        /** Сторно қилинган. */
        REVERSED
    }

    /** Ҳужжат рақами - DocumentSequence VC-2026-NNNNN (unique). */
    @Column(name = "vc_number", nullable = false, unique = true, length = 20)
    private String vcNumber;

    /** Таъминотчи - dimension (DB'да FK contact). */
    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    /** Ихтиёрий асл bill ҳаваласи (DB'да FK) - prefill ва миқдор чеклови. */
    @Column(name = "bill_id")
    private UUID billId;

    /** Ҳужжат санаси. */
    @Column(name = "vc_date", nullable = false)
    private LocalDate vcDate;

    /** Ҳужжат валютаси (қоида №11). EAGER - рўйхат/кўриш шаблонлари учун. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    /** Ҳужжат курси; home'да 1. */
    @Column(name = "exchange_rate", nullable = false, precision = 24, scale = 12)
    private BigDecimal exchangeRate;

    /** Нархлар режими: true - ҚҚС ичида (docs/modules/tax.md). */
    @Column(name = "amounts_inclusive", nullable = false)
    private boolean amountsInclusive;

    /** Жами GROSS (net + ҚҚС) - ҳужжат валютасида; AP дебети шу. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal total = BigDecimal.ZERO;

    /** Жами home валютада (MoneyAllocation.targetBase - битта яхлитлаш). */
    @Column(name = "total_base", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalBase = BigDecimal.ZERO;

    /** Bill'ларга қўлланган қисми - денормализация. */
    @Column(name = "applied_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal appliedAmount = BigDecimal.ZERO;

    /** Очиқ қолдиқ = total − applied - денормализация (рўйхат/қўллаш экрани). */
    @Column(name = "open_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal openBalance = BigDecimal.ZERO;

    /** Ҳолат. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.POSTED;

    /** GL'га ўтказилган вақт (UTC). */
    @Column(name = "posted_at")
    private Instant postedAt;

    /** Эркин изоҳ. */
    @Column(length = 500)
    private String memo;

    /** Сатрлар - ҳужжат билан бирга сақланади. */
    @OneToMany(mappedBy = "vendorCredit", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<VendorCreditLine> lines = new ArrayList<>();

    /** Янги кредит-нота - валидация VendorCreditService'да. */
    public VendorCredit(String vcNumber, UUID vendorId, UUID billId,
                        LocalDate vcDate, Currency currency, BigDecimal exchangeRate,
                        boolean amountsInclusive, String memo) {
        this.vcNumber = vcNumber;
        this.vendorId = vendorId;
        this.billId = billId;
        this.vcDate = vcDate;
        this.currency = currency;
        this.exchangeRate = exchangeRate;
        this.amountsInclusive = amountsInclusive;
        this.memo = memo;
    }

    /** Сатр қўшади ва жамиларни қайта ҳисоблайди (CreditMemo addLine кўзгуси). */
    public VendorCreditLine addLine(BillLineType type, UUID itemId, UUID warehouseId,
                                    BigDecimal quantity, BigDecimal unitPrice,
                                    UUID unitId, BigDecimal unitFactor,
                                    UUID accountId, BigDecimal amount,
                                    UUID taxRateId, BigDecimal taxRateValue,
                                    BigDecimal taxAmount, String memo) {
        VendorCreditLine line = new VendorCreditLine(this, lines.size() + 1, type,
                itemId, warehouseId, quantity, unitPrice, unitId, unitFactor,
                accountId, amount, taxRateId, taxRateValue, taxAmount, memo);
        lines.add(line);
        recalcTotals();
        return line;
    }

    /**
     * Жамилар: total = gross йиғиндиси, totalBase = битта яхлитлаш
     * (MoneyAllocation.targetBase - LOG-002 қолипи), очиқ қолдиқ
     * қайта ҳисобланади.
     */
    private void recalcTotals() {
        BigDecimal sum = BigDecimal.ZERO;
        for (VendorCreditLine line : lines) {
            sum = sum.add(line.grossAmount());
        }
        this.total = sum;
        this.totalBase = MoneyAllocation.targetBase(sum, exchangeRate);
        this.openBalance = sum.subtract(appliedAmount);
    }

    /** POSTED белгиси - фақат VendorCreditService чақиради. */
    public void markPosted(Instant postedAt) {
        this.postedAt = postedAt;
    }

    /**
     * Application денормализацияси (фақат VendorCreditService):
     * applied/open битта формулада, бир жойда (CreditMemo қолипи).
     */
    public void applyAppliedAmount(BigDecimal appliedAmount) {
        this.appliedAmount = appliedAmount;
        this.openBalance = total.subtract(appliedAmount);
    }

    /** POSTED'дан REVERSED'га (фақат VendorCreditService, BR-RET-007 у ерда). */
    public void markReversed() {
        if (status != Status.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_RET_007,
                    "Фақат POSTED кредит-нота reverse қилинади: " + vcNumber
                    + " - " + status);
        }
        this.status = Status.REVERSED;
    }
}
