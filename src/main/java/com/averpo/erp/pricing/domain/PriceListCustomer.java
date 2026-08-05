package com.averpo.erp.pricing.domain;

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

import java.util.UUID;

/**
 * Мижоз бириктируви - РЎЙХАТ томонида (QBO Price rules услуби,
 * docs/modules/price-list.md): contact модулига тегилмайди, доиравий
 * боғлиқлик йўқ. customer_id глобал UNIQUE - мижозга биттагина рўйхат
 * (BR-PL-006); бошқа рўйхатга бириктирилса ёзув кўчади (service).
 *
 * @author Zafar
 */
@Entity
@Table(name = "price_list_customer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceListCustomer extends BaseEntity {

    /** Эга рўйхат. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "price_list_id", nullable = false)
    private PriceList priceList;

    /** Мижоз id'си (dimension, CUSTOMER типдаги фаол контакт - BR-PL-008). */
    @Column(name = "customer_id", nullable = false, unique = true)
    private UUID customerId;

    /** Янги бириктирув - фақат PriceListService орқали. */
    public PriceListCustomer(PriceList priceList, UUID customerId) {
        this.priceList = priceList;
        this.customerId = customerId;
    }

    /** Мижозни бошқа рўйхатга кўчиради (service - BR-PL-006 семантикаси). */
    public void moveTo(PriceList priceList) {
        this.priceList = priceList;
    }
}
