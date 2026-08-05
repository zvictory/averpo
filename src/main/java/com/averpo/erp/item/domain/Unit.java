package com.averpo.erp.item.domain;

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
 * Ўлчов бирлиги: дона, кг, литр, соат...
 *
 * <p>QBO'да UoM йўқ - бу бизнинг multi-warehouse inventory кенгайтмамиз
 * талаби (рухсат этилган фарқ, docs/modules/item.md да ҳужжатланган):
 * омбор qty ҳамиша бирликка боғлиқ бўлиши керак.
 *
 * <p>UoM гуруҳи ва конверсия (docs/modules/uom.md): гуруҳли бирликда
 * base'га нисбатан factor бор (1 шу бирлик = factor × base), гуруҳда
 * айнан битта base (factor 1). Гуруҳсиз бирлик конверсиясиз - factor 1
 * бўлиб қолади. Инвариантлар UnitService'да (BR-UOM-002..005).
 */
@Entity
@Table(name = "unit")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Unit extends BaseEntity {

    /** Бирлик номи - unique: «дона», «кг»... */
    @Column(nullable = false, unique = true, length = 50)
    private String name;

    /** Нофаол бирлик янги item'ларда танланмайди. */
    @Column(nullable = false)
    private boolean active = true;

    /**
     * UoM гуруҳи ёки null (гуруҳсиз - конверсиясиз бирлик).
     * EAGER - бирликлар/гуруҳлар экранлари ва ҳужжат формалари гуруҳ
     * номи/id'сини шаблонда ўқийди (каталог кичкина, JOIN арзон).
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "group_id")
    private UnitGroup group;

    /**
     * Конверсия factor'и: 1 шу бирлик = factor × base бирлик.
     * Гуруҳсиз ва base бирликда айнан 1 (BR-UOM-003/005).
     */
    @Column(nullable = false, precision = 24, scale = 12)
    private BigDecimal factor = BigDecimal.ONE;

    /** Гуруҳнинг base бирлигими - гуруҳда айнан биттаси (BR-UOM-004). */
    @Column(name = "is_base", nullable = false)
    private boolean base = false;

    /** Янги гуруҳсиз бирлик (эски оқим - конверсиясиз). */
    public Unit(String name) {
        this.name = name;
    }

    /** Номини ва фаоллигини янгилайди. */
    public void update(String name, boolean active) {
        this.name = name;
        this.active = active;
    }

    /**
     * Гуруҳ/конверсия майдонларини қўяди - фақат UnitService чақиради,
     * инвариантлар (base factor 1, гуруҳсизга factor йўқ...) ўша ерда.
     */
    public void applyGrouping(UnitGroup group, BigDecimal factor, boolean base) {
        this.group = group;
        this.factor = factor;
        this.base = base;
    }
}
