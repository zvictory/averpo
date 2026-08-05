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
 * Кўчириш акти сатри (Arbitr-093): item, qty. line_cost - кўчган
 * қиймат snapshot (аудит; манба ўртачасида ёки FIFO партияларида).
 *
 * <p>item_id - dimension (DB'да FK, StockMovement қолипи). (акт, item)
 * UNIQUE - BR-INV-012 (битта актда item такрорланмайди).
 *
 * @author Zafar
 */
@Entity
@Table(name = "stock_transfer_line",
       uniqueConstraints = @UniqueConstraint(columnNames = {"stock_transfer_id", "line_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockTransferLine extends BaseEntity {

    /** Акт. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_transfer_id", nullable = false)
    private StockTransfer stockTransfer;

    /** Сатр тартиби (1 дан). */
    @Column(name = "line_no", nullable = false)
    private int lineNo;

    /** Item - dimension (DB'да FK, фақат INVENTORY тип - BR-INV-001). */
    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    /** Кўчириладиган миқдор - доим мусбат (BR-INV-002). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    /** Кўчган қиймат (home) snapshot - аудит/рўйхат учун. */
    @Column(name = "line_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineCost = BigDecimal.ZERO;

    /** Эркин изоҳ. */
    @Column(length = 500)
    private String memo;

    /** Янги сатр - фақат StockTransfer.addLine орқали (композиция). */
    StockTransferLine(StockTransfer stockTransfer, int lineNo, UUID itemId,
                      BigDecimal quantity, BigDecimal lineCost, String memo) {
        this.stockTransfer = stockTransfer;
        this.lineNo = lineNo;
        this.itemId = itemId;
        this.quantity = quantity;
        this.lineCost = lineCost == null ? BigDecimal.ZERO : lineCost;
        this.memo = memo;
    }
}
