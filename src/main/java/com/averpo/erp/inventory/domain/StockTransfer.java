package com.averpo.erp.inventory.domain;

import com.averpo.erp.shared.domain.BaseEntity;
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
 * Ҳужжатли омборлараро кўчириш акти (Arbitr-093, docs/modules/inventory.md):
 * кўп сатрли, манба ва манзил омбор, дарҳол POSTED, GL'СИЗ (posting-rules
 * «Омбор»: transfer GL проводкасиз). Ҳар сатр TRANSFER_OUT+TRANSFER_IN
 * жуфти билан амалга ошади (reference=акт id). Тузатиш reverse (қарши-акт).
 *
 * <p>Барча қийматлар home валютада (омбор ҳисоби доим home, spec).
 *
 * @author Zafar
 */
@Entity
@Table(name = "stock_transfer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockTransfer extends BaseEntity {

    /** Акт ҳолати - DRAFT йўқ (дарҳол POSTED модели). */
    public enum Status {
        /** Ўтказилган - омбор балансларида акс этган, ўзгармас. */
        POSTED,
        /** Сторно қилинган (қарши-акт билан). */
        REVERSED
    }

    /** Акт рақами - DocumentSequence WTR-2026-NNNNN (unique). */
    @Column(name = "wtr_number", nullable = false, unique = true, length = 20)
    private String wtrNumber;

    /** Манба омбор (қаердан). EAGER - рўйхат/кўриш шаблонлари учун. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "from_warehouse_id", nullable = false)
    private Warehouse fromWarehouse;

    /** Манзил омбор (қаерга). EAGER - рўйхат/кўриш шаблонлари учун. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "to_warehouse_id", nullable = false)
    private Warehouse toWarehouse;

    /** Акт санаси. */
    @Column(name = "wtr_date", nullable = false)
    private LocalDate wtrDate;

    /** Кўчган умумий қиймат (home) - аудит/рўйхат учун денормализация. */
    @Column(name = "total_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCost = BigDecimal.ZERO;

    /** Ҳолат. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.POSTED;

    /** GL'сиз, лекин POSTED вақти аудит учун (UTC). */
    @Column(name = "posted_at")
    private Instant postedAt;

    /** Эркин изоҳ. */
    @Column(length = 500)
    private String memo;

    /**
     * Ташқи ҳужжат рақами (Arbitr-109, QBO «Reference no.»): қоғоз акт/
     * дафтар рақами - ихтиёрий, аудит учун. GL/movement'га тегмайди.
     */
    @Column(name = "external_ref", length = 50)
    private String externalRef;

    /** Сатрлар - акт билан бирга сақланади (композиция). */
    @OneToMany(mappedBy = "stockTransfer", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<StockTransferLine> lines = new ArrayList<>();

    /** Янги акт - валидация InventoryService'да. */
    public StockTransfer(String wtrNumber, Warehouse fromWarehouse, Warehouse toWarehouse,
                         LocalDate wtrDate, String memo, String externalRef) {
        this.wtrNumber = wtrNumber;
        this.fromWarehouse = fromWarehouse;
        this.toWarehouse = toWarehouse;
        this.wtrDate = wtrDate;
        this.memo = memo;
        this.externalRef = externalRef;
    }

    /** Сатр қўшади ва кўчган қийматни жамлайди (lineCost - кўчиш таннархи, home). */
    public StockTransferLine addLine(UUID itemId, BigDecimal quantity, BigDecimal lineCost,
                                     String memo) {
        StockTransferLine line = new StockTransferLine(this, lines.size() + 1,
                itemId, quantity, lineCost, memo);
        lines.add(line);
        this.totalCost = this.totalCost.add(lineCost);
        return line;
    }

    /** POSTED вақти белгиси - фақат InventoryService чақиради. */
    public void markPosted(Instant postedAt) {
        this.postedAt = postedAt;
    }

    /** POSTED'дан REVERSED'га (фақат InventoryService). */
    public void markReversed() {
        if (status != Status.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_INV_002,
                    "Фақат POSTED акт reverse қилинади: " + wtrNumber + " - " + status);
        }
        this.status = Status.REVERSED;
    }
}
