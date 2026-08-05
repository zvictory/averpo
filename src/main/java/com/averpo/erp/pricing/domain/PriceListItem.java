package com.averpo.erp.pricing.domain;

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
 * Рўйхатдаги битта поғонали нарх: min_quantity дан бошлаб (BASE
 * бирликда) шу нарх амал қилади. item_id - dimension паттерни (DB'да
 * FK бор, JPA'да UUID - item модулига entity боғланиш йўқ, қоида №6).
 */
@Entity
@Table(name = "price_list_item",
       uniqueConstraints = @UniqueConstraint(
               columnNames = {"price_list_id", "item_id", "min_quantity"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceListItem extends BaseEntity {

    /** Эга рўйхат. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "price_list_id", nullable = false)
    private PriceList priceList;

    /** Товар/хизмат id'си (dimension). */
    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    /** Поғона бошланиши - item BASE бирлигида, мусбат (BR-PL-002). */
    @Column(name = "min_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal minQuantity;

    /** Нарх - рўйхат валютасида, base бирликка, манфий эмас (BR-PL-002). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    /** Янги поғона - фақат PriceListService орқали (валидация ўша ерда). */
    public PriceListItem(PriceList priceList, UUID itemId,
                         BigDecimal minQuantity, BigDecimal price) {
        this.priceList = priceList;
        this.itemId = itemId;
        this.minQuantity = minQuantity;
        this.price = price;
    }
}
