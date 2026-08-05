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
 * Ҳужжатли инвентаризация акти (Arbitr-093, docs/modules/inventory.md):
 * кўп сатрли, БИТТА омбор бўйича, дарҳол POSTED (DRAFT йўқ - SalesReceipt
 * қолипи), тузатиш reverse (қарши-акт). Актнинг ҳамма сатри учун БИТТА
 * JE ёзилади (posting-rules «Ҳужжатли Adjustment»); ҳар сатр ўз
 * StockMovement (ADJUST_IN/OUT, reference=акт id) билан боғланади.
 *
 * <p>Барча қийматлар home валютада (омбор ҳисоби доим home, spec).
 *
 * @author Zafar
 */
@Entity
@Table(name = "stock_adjustment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockAdjustment extends BaseEntity {

    /** Акт ҳолати - DRAFT йўқ (дарҳол POSTED модели, SalesReceipt қолипи). */
    public enum Status {
        /** Ўтказилган - омбор ва GL'да акс этган, ўзгармас. */
        POSTED,
        /** Сторно қилинган (қарши-акт билан). */
        REVERSED
    }

    /** Акт рақами - DocumentSequence ADJ-2026-NNNNN (unique). */
    @Column(name = "adj_number", nullable = false, unique = true, length = 20)
    private String adjNumber;

    /**
     * Инвентаризация омбори - акт битта омборга тегишли. EAGER: рўйхат/
     * кўриш шаблонлари омбор номини lazy'сиз ўқийди (SalesReceipt қолипи).
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    /** Акт санаси. */
    @Column(name = "adj_date", nullable = false)
    private LocalDate adjDate;

    /**
     * Актнинг нетто GL таъсири (home): кўпайиш сатрлари − камайишлар.
     * Аудит/рўйхат учун денормализация; JE леглари сатрлардан йиғилади.
     */
    @Column(name = "total_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCost = BigDecimal.ZERO;

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

    /**
     * Ташқи ҳужжат рақами (Arbitr-109, QBO «Reference no.»): қоғоз акт/
     * дафтар рақами - ихтиёрий, аудит учун. GL/movement'га тегмайди.
     */
    @Column(name = "external_ref", length = 50)
    private String externalRef;

    /** Сатрлар - акт билан бирга сақланади (композиция). */
    @OneToMany(mappedBy = "stockAdjustment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<StockAdjustmentLine> lines = new ArrayList<>();

    /** Янги акт - валидация InventoryService'да. */
    public StockAdjustment(String adjNumber, Warehouse warehouse, LocalDate adjDate,
                           String memo, String externalRef) {
        this.adjNumber = adjNumber;
        this.warehouse = warehouse;
        this.adjDate = adjDate;
        this.memo = memo;
        this.externalRef = externalRef;
    }

    /**
     * Сатр қўшади ва нетто total_cost'ни қайта ҳисоблайди. delta - янги
     * qty ва жорийдан келиб чиққан фарқ (service ҳисоблайди); lineCost -
     * шу сатрнинг GL таъсири (кўпайишда мусбат, камайишда манфий, home).
     */
    public StockAdjustmentLine addLine(UUID itemId, BigDecimal newQty, BigDecimal deltaQty,
                                       BigDecimal unitCost, BigDecimal lineCost, String memo) {
        StockAdjustmentLine line = new StockAdjustmentLine(this, lines.size() + 1,
                itemId, newQty, deltaQty, unitCost, lineCost, memo);
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
                    "Фақат POSTED акт reverse қилинади: " + adjNumber + " - " + status);
        }
        this.status = Status.REVERSED;
    }
}
