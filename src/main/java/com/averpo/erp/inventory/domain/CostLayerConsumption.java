package com.averpo.erp.inventory.domain;

import com.averpo.erp.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * FIFO ейилиш изи: қайси партия (layer) қайси чиқим ҳаракатига қанча
 * ейилгани - таннарх ҳисобининг тўлиқ audit изи (old-erp-ideas §6).
 * Ёзувлар ўзгармас.
 *
 * @author Zafar
 */
@Entity
@Table(name = "cost_layer_consumption")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CostLayerConsumption extends BaseEntity {

    /** Ейилган партия. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "layer_id", nullable = false)
    private CostLayer layer;

    /** Ейган чиқим ҳаракати. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movement_id", nullable = false)
    private StockMovement movement;

    /** Шу партиядан ейилган миқдор. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    /** Янги ейилиш ёзуви - FIFO чиқимида яратилади. */
    public CostLayerConsumption(CostLayer layer, StockMovement movement,
                                BigDecimal quantity) {
        this.layer = layer;
        this.movement = movement;
        this.quantity = quantity;
    }
}
