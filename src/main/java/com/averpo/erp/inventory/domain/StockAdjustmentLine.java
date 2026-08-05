package com.averpo.erp.inventory.domain;

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
 * Инвентаризация акти сатри (Arbitr-093): item, ЯНГИ qty (QBO «New
 * quantity» услуби - фойдаланувчи янги қолдиқни киритади) → delta авто
 * (new − жорий). unit_cost ихтиёрий (фақат кўпайишда, BR-INV-007 сатрга).
 * delta_qty ва line_cost - актни сақлаш пайтидаги snapshot (жорий qty
 * кейин ўзгарса ҳам акт ёзуви ўзгармас, темир қоида 3).
 *
 * <p>item_id - dimension (DB'да FK, JPA'да UUID - модуллараро entity
 * боғланиш йўқ, StockMovement қолипи). (акт, item) UNIQUE - BR-INV-012.
 *
 * @author Zafar
 */
@Entity
@Table(name = "stock_adjustment_line",
       uniqueConstraints = @UniqueConstraint(columnNames = {"stock_adjustment_id", "line_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockAdjustmentLine extends BaseEntity {

    /** Акт. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_adjustment_id", nullable = false)
    private StockAdjustment stockAdjustment;

    /** Сатр тартиби (1 дан). */
    @Column(name = "line_no", nullable = false)
    private int lineNo;

    /** Item - dimension (DB'да FK, фақат INVENTORY тип - BR-INV-001). */
    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    /** Фойдаланувчи киритган ЯНГИ қолдиқ (QBO New quantity). */
    @Column(name = "new_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal newQty;

    /** Ҳисобланган ўзгариш (new − жорий) snapshot: мусбат-кўпайиш, манфий-камайиш. */
    @Column(name = "delta_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal deltaQty;

    /** Кўпайиш нархи (home) ёки null - жорий қиймат ишлатилган (BR-INV-007). */
    @Column(name = "unit_cost", precision = 24, scale = 12)
    private BigDecimal unitCost;

    /** Сатрнинг GL таъсири (home): кўпайишда мусбат, камайишда манфий. */
    @Column(name = "line_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineCost = BigDecimal.ZERO;

    /** Эркин изоҳ. */
    @Column(length = 500)
    private String memo;

    /** Янги сатр - фақат StockAdjustment.addLine орқали (композиция). */
    StockAdjustmentLine(StockAdjustment stockAdjustment, int lineNo, UUID itemId,
                        BigDecimal newQty, BigDecimal deltaQty, BigDecimal unitCost,
                        BigDecimal lineCost, String memo) {
        this.stockAdjustment = stockAdjustment;
        this.lineNo = lineNo;
        this.itemId = itemId;
        this.newQty = newQty;
        this.deltaQty = deltaQty;
        this.unitCost = unitCost;
        this.lineCost = lineCost == null ? BigDecimal.ZERO : lineCost;
        this.memo = memo;
    }
}
