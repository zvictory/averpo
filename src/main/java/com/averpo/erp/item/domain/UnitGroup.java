package com.averpo.erp.item.domain;

import com.averpo.erp.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Ўлчов бирликлари гуруҳи («Оғирлик», «Дона ҳисоби»...) - конверсия
 * фақат бир гуруҳ ичида (BR-UOM-006). Гуруҳда айнан битта base бирлик
 * бўлади, қолганлари унга factor орқали боғланади (docs/modules/uom.md).
 *
 * <p>Гуруҳ ўчирилмайди ва нофаолланмайди - бирликларнинг ўзи
 * нофаолланади (тарихдаги ҳужжат сатрлари бузилмасин).
 */
@Entity
@Table(name = "unit_group")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UnitGroup extends BaseEntity {

    /** Гуруҳ номи - unique (BR-UOM-001). */
    @Column(nullable = false, unique = true, length = 50)
    private String name;

    /** Янги гуруҳ (валидация UnitService'да). */
    public UnitGroup(String name) {
        this.name = name;
    }

    /** Номини янгилайди (валидация UnitService'да). */
    public void rename(String name) {
        this.name = name;
    }
}
