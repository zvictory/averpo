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
 * Жорий қолдиқ (омбор, item) кесимида. qty ҳеч қачон манфий бўлмайди
 * (BR-INV-003, DB CHECK ҳам бор). avg_cost - AVCO ўртачаси; FIFO
 * режимида ҳам маълумот учун юритилади (қолдиқлар экранида қиймат
 * кўрсатиш осон бўлади).
 *
 * <p>Ҳисоблаш формулалари InventoryService'да (2-туртки) - entity
 * фақат янги ҳолатни қабул қилади.
 */
@Entity
@Table(name = "stock_balance",
       uniqueConstraints = @UniqueConstraint(columnNames = {"warehouse_id", "item_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockBalance extends BaseEntity {

    /** Омбор. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    /** Товар id (dimension паттерни - StockMovement изоҳига қаранг). */
    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    /** Жорий миқдор - манфий бўлмайди. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal qty = BigDecimal.ZERO;

    /** AVCO ўртача қиймати (home валютада, scale 12 - бўлиниш аниқлиги). */
    @Column(name = "avg_cost", nullable = false, precision = 24, scale = 12)
    private BigDecimal avgCost = BigDecimal.ZERO;

    /** Янги (нол) қолдиқ ёзуви - биринчи ҳаракатда яратилади. */
    public StockBalance(Warehouse warehouse, UUID itemId) {
        this.warehouse = warehouse;
        this.itemId = itemId;
    }

    /** Янги ҳолатни қабул қилади - формулалар InventoryService'да. */
    public void apply(BigDecimal qty, BigDecimal avgCost) {
        this.qty = qty;
        this.avgCost = avgCost;
    }
}
