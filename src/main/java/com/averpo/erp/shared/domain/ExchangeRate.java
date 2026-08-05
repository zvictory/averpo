package com.averpo.erp.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * Валюта курси ёзуви (docs/modules/multi-currency.md, transfer.md Т3).
 * 1 currency = rate home.
 *
 * <p>Append-only ТАРИХ (Arbitr-022): бир (валюта, сана)га бир нечта
 * ёзув бўлиши мумкин - ЦБ импорти ва қўлда/ўтказма ўзгартиришлар устига
 * ёзилмайди, ҳар бири сақланади. Амалдаги курс = энг охирги ёзув
 * ({@code rate_date <= сана}, кейин UUIDv7 {@code id} тартиби - бир
 * кунда 3-4 марта ўзгарса ҳам охиргиси амалда). Манба {@link RateSource}.
 *
 * @author Zafar
 */
@Entity
@Table(name = "exchange_rate")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeRate extends BaseEntity {

    /** Валюта - каталогга ManyToOne (String эмас). */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    /** Курс амал қиладиган сана. */
    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    /** 1 currency = rate home. Аниқлик Money.RATE_SCALE билан бир хил (12). */
    @Column(nullable = false, precision = 24, scale = 12)
    private BigDecimal rate;

    /** Ёзув манбаи - тарихда ЦБ ва қўлда киритилганни фарқлаш учун. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RateSource source;

    /** Янги курс ёзуви - тарихга ҚЎШИЛАДИ (мавжудини алмаштирмайди). */
    public ExchangeRate(Currency currency, LocalDate rateDate,
                        BigDecimal rate, RateSource source) {
        this.currency = currency;
        this.rateDate = rateDate;
        this.rate = rate;
        this.source = source;
    }
}
