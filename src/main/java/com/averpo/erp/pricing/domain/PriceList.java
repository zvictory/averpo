package com.averpo.erp.pricing.domain;

import com.averpo.erp.shared.domain.BaseEntity;
import com.averpo.erp.shared.domain.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Нарх рўйхати (docs/modules/price-list.md): валютали ва даврли
 * каталог, поғонали нархлар {@link PriceListItem}'да. Ҳужжатга ҳавола
 * сақланмайди - фақат invoice формасида prefill манбаси, шунинг учун
 * рўйхат/поғона ўзгариши тарихий ҳужжатларга таъсир қилмайди.
 *
 * @author Zafar
 */
@Entity
@Table(name = "price_list")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceList extends BaseEntity {

    /** Рўйхат номи - unique (BR-PL-001). */
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /**
     * Рўйхат валютаси - нархлар шу валютада; ҳужжат валютасига мос
     * бўлгандагина қўлланади. EAGER - каталог кичкина, шаблонда
     * lazy хатоси бўлмасин (Account.currency прецеденти).
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    /** Амал даври боши ёки null (чексиз). */
    @Column(name = "valid_from")
    private LocalDate validFrom;

    /** Амал даври охири ёки null (чексиз). */
    @Column(name = "valid_to")
    private LocalDate validTo;

    /**
     * Default рўйхатми - мижозга рўйхат бириктирилмаганда шу ишлатилади.
     * Биттагина бўлади (BR-PL-003, DB partial unique) - алмашувни
     * PriceListService қилади.
     */
    @Column(name = "is_default", nullable = false)
    private boolean defaultList;

    /** Нофаол рўйхат нарх ечишда қатнашмайди, тарихда қолади. */
    @Column(nullable = false)
    private boolean active = true;

    /** Янги рўйхат (валидация PriceListService'да). */
    public PriceList(String name, Currency currency, LocalDate validFrom,
                     LocalDate validTo, boolean defaultList) {
        this.name = name;
        this.currency = currency;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.defaultList = defaultList;
    }

    /** Сарлавҳа майдонларини янгилайди (валидация service'да). */
    public void update(String name, Currency currency, LocalDate validFrom,
                       LocalDate validTo, boolean defaultList, boolean active) {
        this.name = name;
        this.currency = currency;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.defaultList = defaultList;
        this.active = active;
    }

    /** Default мақомини бўшатади - янги default белгиланганда (service). */
    public void clearDefault() {
        this.defaultList = false;
    }

    /** Рўйхат шу санада ва шу валютада қўлланадими (ечиш тартиби 2-қадами). */
    public boolean appliesTo(String currencyCode, LocalDate date) {
        return active
                && currency.getCode().equals(currencyCode)
                && (validFrom == null || !date.isBefore(validFrom))
                && (validTo == null || !date.isAfter(validTo));
    }
}
