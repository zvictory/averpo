package com.averpo.erp.inventory.domain;

import com.averpo.erp.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Омбор ҳаракати - ЎЗГАРМАС журнал ёзуви (ledger entry'лар каби):
 * яратилгандан кейин таҳрир йўқ, хато тескари adjustment билан
 * тузатилади. Барча қийматлар home валютада (spec, «Қатъий қарорлар»).
 *
 * <p>item_id - dimension паттерни (JournalEntryLine каби): DB'да FK
 * бор, JPA'да оддий UUID - модуллараро entity боғланиш йўқ (қоида №6),
 * item маълумоти ItemService орқали олинади.
 *
 * @author Zafar
 */
@Entity
@Table(name = "stock_movement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockMovement extends BaseEntity {

    /** Ҳаракат тури - йўналиш шундан (MovementType.inbound). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType type;

    /** Товар id (dimension - фақат INVENTORY типдаги item, BR-INV-001). */
    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    /** Таъсирланган омбор - ҳар ёзув айнан биттасига таъсир қилади. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    /** Transfer'да иккинчи томон омбори (аудит из учун), бошқа турда null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterpart_warehouse_id")
    private Warehouse counterpartWarehouse;

    /** Миқдор - доим мусбат (BR-INV-002), йўналиш type'дан. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    /** Бирлик қиймати home валютада; чиқимда valuation натижаси. */
    @Column(name = "unit_cost", nullable = false, precision = 24, scale = 12)
    private BigDecimal unitCost;

    /** Жами қиймат home валютада - GL проводкага айнан шу киради. */
    @Column(name = "total_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCost;

    /** Ҳаракат санаси. */
    @Column(name = "movement_date", nullable = false)
    private LocalDate movementDate;

    /** Манба ҳужжат тури: BILL, INVOICE, ADJUSTMENT, TRANSFER... (полиморф ҳавола). */
    @Column(name = "reference_type", length = 30)
    private String referenceType;

    /** Манба ҳужжат id'си - 6-7-босқичда Bill/Invoice уланади. */
    @Column(name = "reference_id")
    private UUID referenceId;

    /** Эркин изоҳ. */
    @Column(length = 500)
    private String memo;

    /** Янги ҳаракат ёзуви - барча майдонлар билан (валидация service'да). */
    public StockMovement(MovementType type, UUID itemId, Warehouse warehouse,
                         Warehouse counterpartWarehouse, BigDecimal quantity,
                         BigDecimal unitCost, BigDecimal totalCost,
                         LocalDate movementDate, String referenceType,
                         UUID referenceId, String memo) {
        this.type = type;
        this.itemId = itemId;
        this.warehouse = warehouse;
        this.counterpartWarehouse = counterpartWarehouse;
        this.quantity = quantity;
        this.unitCost = unitCost;
        this.totalCost = totalCost;
        this.movementDate = movementDate;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.memo = memo;
    }
}
