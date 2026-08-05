package com.averpo.erp.sales.domain;

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
 * Estimate - мижозга таклиф/смета (docs/modules/estimates-po.md, QBO
 * Estimate). GL'сиз ҳужжат: проводка/омбор ҳаракати умуман йўқ, шунинг
 * учун POSTED тушунчаси ҳам йўқ (темир қоида №3 тегишли эмас) -
 * ТАҲРИРЛАНАДИ, ҳаёт цикли {@link EstimateStatus} билан бошқарилади.
 * Барча ўтиш/ҳимоя қоидалари (BR-EST-002/003) шу entity'да - service
 * қатлами четлаб ўтолмайди.
 */
@Entity
@Table(name = "estimate")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Estimate extends BaseEntity {

    /** Ҳужжат рақами - DocumentSequence EST-2026-NNNNN (unique). */
    @Column(name = "estimate_number", nullable = false, unique = true, length = 20)
    private String estimateNumber;

    /** Customer контакт id'си (dimension, CUSTOMER типи service'да текширилади). */
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    /** Ҳужжат санаси. */
    @Column(name = "estimate_date", nullable = false)
    private LocalDate estimateDate;

    /** Таклифнинг амал қилиш муддати (ихтиёрий, QBO ExpirationDate). */
    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    /** Ҳужжат валютаси - каталогга ManyToOne (қоида №11). EAGER: рўйхат/кўриш шаблонларида lazy хатоси бўлмасин. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    /** Ҳужжат курси (invoice қолипи); home валютада 1. */
    @Column(name = "exchange_rate", nullable = false, precision = 24, scale = 12)
    private BigDecimal exchangeRate;

    /** Нархлар режими (tax.md): false - ҚҚСсиз, true - ҚҚС ичида. */
    @Column(name = "amounts_inclusive", nullable = false)
    private boolean amountsInclusive = false;

    /** Ҳаёт цикли ҳолати (ўтишлар changeStatus'да). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstimateStatus status = EstimateStatus.PENDING;

    /** Жами GROSS сумма ҳужжат валютасида (сатр gross'лари йиғиндиси). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal total = BigDecimal.ZERO;

    /** Эркин изоҳ. */
    @Column(length = 500)
    private String memo;

    /** Айлантирилган invoice id'си (LinkedTxn) - тўлдирилгач ҳужжат қулф. */
    @Column(name = "invoice_id")
    private UUID invoiceId;

    /** Сатрлар - estimate билан бирга сақланади/ўчади (композиция). */
    @OneToMany(mappedBy = "estimate", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<EstimateLine> lines = new ArrayList<>();

    /** Янги PENDING estimate (валидация service'да). */
    public Estimate(String estimateNumber, UUID customerId, LocalDate estimateDate,
                    LocalDate expirationDate, Currency currency, BigDecimal exchangeRate,
                    boolean amountsInclusive, String memo) {
        this.estimateNumber = estimateNumber;
        this.customerId = customerId;
        this.estimateDate = estimateDate;
        this.expirationDate = expirationDate;
        this.currency = currency;
        this.exchangeRate = exchangeRate;
        this.amountsInclusive = amountsInclusive;
        this.memo = memo;
    }

    /** BR-EST-002: CLOSED/REJECTED таҳрирланмайди - domain guard. */
    private void requireEditable() {
        if (status == EstimateStatus.CLOSED || status == EstimateStatus.REJECTED) {
            throw new BusinessRuleException(BusinessRule.BR_EST_002,
                    "Estimate таҳрирланмас ҳолатда: " + estimateNumber + " - " + status);
        }
    }

    /** Сарлавҳани янгилайди (валидация service'да). */
    public void updateHeader(UUID customerId, LocalDate estimateDate,
                             LocalDate expirationDate, Currency currency,
                             BigDecimal exchangeRate, boolean amountsInclusive,
                             String memo) {
        requireEditable();
        this.customerId = customerId;
        this.estimateDate = estimateDate;
        this.expirationDate = expirationDate;
        this.currency = currency;
        this.exchangeRate = exchangeRate;
        this.amountsInclusive = amountsInclusive;
        this.memo = memo;
    }

    /** Сатр қўшади ва жамини қайта ҳисоблайди. */
    public EstimateLine addLine(UUID itemId, BigDecimal quantity, BigDecimal unitPrice,
                                UUID unitId, BigDecimal amount, UUID taxRateId,
                                BigDecimal taxRateValue, BigDecimal taxAmount,
                                String memo) {
        requireEditable();
        EstimateLine line = new EstimateLine(this, lines.size() + 1, itemId, quantity,
                unitPrice, unitId, amount, taxRateId, taxRateValue, taxAmount, memo);
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
        for (EstimateLine line : lines) {
            sum = sum.add(line.grossAmount());
        }
        this.total = sum;
    }

    /**
     * Status ўтиши (spec оқими): PENDING ↔ ACCEPTED/REJECTED, ҳар
     * очиқ ҳолатдан CLOSED (қўлда ёпиш); REJECTED'дан аввал PENDING'га
     * қайта очилади (тўғри ACCEPTED тақиқ); CLOSED'дан фақат PENDING'га
     * ва фақат linked invoice бўлмаса (BR-EST-002).
     */
    public void changeStatus(EstimateStatus to) {
        if (to == status) {
            return;
        }
        if (status == EstimateStatus.CLOSED
                && (to != EstimateStatus.PENDING || invoiceId != null)) {
            throw new BusinessRuleException(BusinessRule.BR_EST_002,
                    "CLOSED estimate'ни фақат linked ҳужжатсиз PENDING'га қайта очиш мумкин: "
                    + estimateNumber);
        }
        if (status == EstimateStatus.REJECTED && to == EstimateStatus.ACCEPTED) {
            throw new BusinessRuleException(BusinessRule.BR_EST_002,
                    "REJECTED estimate аввал PENDING'га қайта очилади: " + estimateNumber);
        }
        this.status = to;
    }

    /** BR-EST-002/003: айлантириш мумкинлигини текширади (ёзмайди). */
    public void requireConvertible() {
        if (invoiceId != null) {
            throw new BusinessRuleException(BusinessRule.BR_EST_003,
                    "Estimate аллақачон invoice'га айлантирилган: " + estimateNumber);
        }
        if (status == EstimateStatus.REJECTED || status == EstimateStatus.CLOSED) {
            throw new BusinessRuleException(BusinessRule.BR_EST_002,
                    "REJECTED/CLOSED estimate айлантирилмайди: " + estimateNumber
                    + " - " + status);
        }
    }

    /** Айлантирилди: linked invoice ёзилади, ҳужжат CLOSED бўлади. */
    public void markConverted(UUID invoiceId) {
        requireConvertible();
        this.invoiceId = invoiceId;
        this.status = EstimateStatus.CLOSED;
    }

    /** BR-EST-003: linked ҳужжат ўчирилмайди. */
    public void requireDeletable() {
        if (invoiceId != null) {
            throw new BusinessRuleException(BusinessRule.BR_EST_003,
                    "Invoice'га айлантирилган estimate ўчирилмайди: " + estimateNumber);
        }
    }
}
