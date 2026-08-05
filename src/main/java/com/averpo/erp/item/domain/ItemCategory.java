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
import lombok.Setter;

/**
 * Товар категорияси - QBO Categories услубида иерархик.
 * Цикл ҳимояси service'да (Account иерархияси паттерни).
 */
@Entity
@Table(name = "item_category")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemCategory extends BaseEntity {

    /** Категория номи - unique. */
    @Column(nullable = false, unique = true)
    private String name;

    /** Ота категория ёки null (илдиз). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ItemCategory parent;

    /** Нофаол категория янги item'ларда танланмайди. */
    @Setter
    @Column(nullable = false)
    private boolean active = true;

    /** Янги категория. */
    public ItemCategory(String name, ItemCategory parent) {
        this.name = name;
        this.parent = parent;
    }

    /** Ном ва отани янгилайди (цикл текшируви service'да). */
    public void update(String name, ItemCategory parent) {
        this.name = name;
        this.parent = parent;
    }
}
