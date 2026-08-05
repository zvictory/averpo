package com.averpo.erp.inventory.domain;

import com.averpo.erp.shared.domain.BaseEntity;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
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
import java.time.LocalDate;
import java.util.UUID;

/**
 * FIFO партияси (cost layer): ҳар кирим ўз нархи билан алоҳида қатлам,
 * чиқимлар received_date (кейин id) тартибида ейди
 * (docs/modules/inventory.md, old-erp-ideas §6). Тўлиқ ейилган layer
 * is_exhausted=true - «кейинги ейилмаган» қидируви partial index
 * билан тез.
 *
 * @author Zafar
 */
@Entity
@Table(name = "cost_layer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CostLayer extends BaseEntity {

    /** Омбор - layer'лар (омбор, item) кесимида юритилади. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    /** Товар id (dimension паттерни). */
    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    /** Партия кирган сана - FIFO тартибининг асосий калити. */
    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    /** Партия бирлик нархи (home валютада). */
    @Column(name = "unit_cost", nullable = false, precision = 24, scale = 12)
    private BigDecimal unitCost;

    /** Кирган миқдор - ўзгармайди. */
    @Column(name = "original_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal originalQty;

    /** Ҳали ейилмаган қисм - фақат камаяди. */
    @Column(name = "remaining_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal remainingQty;

    /** Тўлиқ ейилган - partial index'дан чиқади, қидирувлар тезлашади. */
    @Column(name = "is_exhausted", nullable = false)
    private boolean exhausted;

    /** Партияни яратган кирим ҳаракати - аудит из. */
    @Column(name = "source_movement_id", nullable = false)
    private UUID sourceMovementId;

    /** Янги партия - кирим ҳаракатидан (валидация service'да). */
    public CostLayer(Warehouse warehouse, UUID itemId, LocalDate receivedDate,
                     BigDecimal unitCost, BigDecimal qty, UUID sourceMovementId) {
        this.warehouse = warehouse;
        this.itemId = itemId;
        this.receivedDate = receivedDate;
        this.unitCost = unitCost;
        this.originalQty = qty;
        this.remainingQty = qty;
        this.sourceMovementId = sourceMovementId;
    }

    /**
     * Партия бирлик нархини ўзгартиради (landed cost тақсимоти/reverse).
     * Манфий натижа - инвариант бузилиши: юкланганидан кўпроқ айириш
     * дастур хатоси, дарҳол тўхтатилади.
     */
    public void addUnitCost(BigDecimal delta) {
        BigDecimal updated = this.unitCost.add(delta);
        if (updated.signum() < 0) {
            throw new BusinessRuleException(BusinessRule.BR_INV_004,
                    "Партия нархи манфийга тушади: " + unitCost + " + " + delta);
        }
        this.unitCost = updated;
    }

    /**
     * Партиядан qty ейди - қолдиқ нолга тушса exhausted белгиланади.
     * Ошиқча ейиш дастур хатоси эмас, бизнес инвариант бузилиши
     * сифатида дарҳол тўхтатилади (service тўғри тақсимлаши шарт).
     */
    public void consume(BigDecimal qty) {
        if (qty.compareTo(remainingQty) > 0) {
            throw new BusinessRuleException(BusinessRule.BR_INV_003,
                    "Партиядан ошиқча ейиш: қолган " + remainingQty + ", сўралди " + qty);
        }
        this.remainingQty = this.remainingQty.subtract(qty);
        if (this.remainingQty.signum() == 0) {
            this.exhausted = true;
        }
    }

    /**
     * Ейилган миқдорни партияга қайтаради (Invoice reverse -
     * consumption изи бўйича). original'дан ошиш - инвариант бузилиши:
     * қайтарилаётган миқдор consumption ёзувларидан келади, улар
     * йиғиндиси ҳеч қачон original'дан ошмайди.
     */
    public void restore(BigDecimal qty) {
        BigDecimal updated = this.remainingQty.add(qty);
        if (updated.compareTo(originalQty) > 0) {
            throw new BusinessRuleException(BusinessRule.BR_INV_003,
                    "Партияга ошиқча қайтариш: original " + originalQty
                    + ", бўлади " + updated);
        }
        this.remainingQty = updated;
        this.exhausted = updated.signum() == 0;
    }
}
