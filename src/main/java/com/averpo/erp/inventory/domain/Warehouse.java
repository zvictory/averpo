package com.averpo.erp.inventory.domain;

import com.averpo.erp.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Омбор каталоги (docs/modules/inventory.md) - multi-warehouse
 * Averpo'нинг QBO'дан атайлаб фарқи. Ўчириш йўқ - фақат active=false:
 * ҳаракатлар тарихи бузилмайди, нофаол омборга янги ҳаракат тақиқ
 * (BR-INV-006).
 */
@Entity
@Table(name = "warehouse")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Warehouse extends BaseEntity {

    /** Кўрсатиладиган ном - unique (BR-WH-001). */
    @Column(nullable = false, unique = true)
    private String name;

    /** Ихтиёрий қисқа код (MAIN, FIL1...) - киритилса unique (BR-WH-002). */
    @Column(length = 20)
    private String code;

    /** Нофаол омбор янги ҳаракатларда танланмайди. */
    @Column(nullable = false)
    private boolean active = true;

    /** Янги омбор (валидация service'да). */
    public Warehouse(String name, String code) {
        this.name = name;
        this.code = code;
    }

    /** Ном/код/фаолликни янгилаш (валидация service'да). */
    public void update(String name, String code, boolean active) {
        this.name = name;
        this.code = code;
        this.active = active;
    }
}
