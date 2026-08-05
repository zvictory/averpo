package com.averpo.erp.purchase.domain;

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
 * Тақсимот қатори: битта receipt'га тушган улуш. movement_id -
 * dimension паттерни (DB'да FK, JPA'да UUID - inventory модулига
 * entity боғланиш йўқ, қоида №6). inventory/cogs бўлиниши ва тақсимот
 * пайтидаги қолдиқ reverse'нинг аниқ гарови учун сақланади.
 */
@Entity
@Table(name = "landed_cost_allocation_line",
       uniqueConstraints = @UniqueConstraint(columnNames = {"allocation_id", "movement_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LandedCostAllocationLine extends BaseEntity {

    /** Тақсимот ҳужжати. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "allocation_id", nullable = false)
    private LandedCostAllocation allocation;

    /** Receipt - BILL манбали кирим ҳаракати id'си (dimension). */
    @Column(name = "movement_id", nullable = false)
    private UUID movementId;

    /** Шу receipt'га тушган улуш (home) - қиймат нисбатида. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Омбор қийматига қўшилган қисм (қолган миқдор улуши). */
    @Column(name = "inventory_share", nullable = false, precision = 19, scale = 4)
    private BigDecimal inventoryShare;

    /** Сотилган улушга тўғри келган қисм - COGS'га кетган. */
    @Column(name = "cogs_share", nullable = false, precision = 19, scale = 4)
    private BigDecimal cogsShare;

    /** Тақсимот пайтидаги қолдиқ - reverse гарови (спец «Reverse»). */
    @Column(name = "remaining_qty_at_alloc", nullable = false, precision = 19, scale = 4)
    private BigDecimal remainingQtyAtAlloc;

    /** Янги қатор - фақат LandedCostService орқали (валидация ўша ерда). */
    public LandedCostAllocationLine(LandedCostAllocation allocation, UUID movementId,
                                    BigDecimal amount, BigDecimal inventoryShare,
                                    BigDecimal cogsShare, BigDecimal remainingQtyAtAlloc) {
        this.allocation = allocation;
        this.movementId = movementId;
        this.amount = amount;
        this.inventoryShare = inventoryShare;
        this.cogsShare = cogsShare;
        this.remainingQtyAtAlloc = remainingQtyAtAlloc;
    }
}
