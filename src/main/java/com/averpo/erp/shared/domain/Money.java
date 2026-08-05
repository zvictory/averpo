package com.averpo.erp.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Пул қиймати - ТЕМИР ҚОИДА №1.
 * amount  - ҳужжат валютасида, currency билан
 * baseAmount - home валютада (CompanySettings'дан), ledger шу бўйича
 * балансланади
 * exchangeRate - ҳужжат санасидаги курс (1 currency = rate home)
 *
 * Инвариант: baseAmount == amount * exchangeRate (0.0001 tolerance)
 *
 * <p>Home currency атайлаб бу классга ёзилмаган - Money соф қиймат
 * объекти, валюта контексти чақирувчи service орқали
 * CompanySettings'дан келади.
 *
 * @author Zafar
 */
@Embeddable
@Getter
public class Money {

    /** Сумма аниқлиги: вергулдан кейин 4 хона. */
    public static final int AMOUNT_SCALE = 4;

    /**
     * Курс аниқлиги: 12 хона. 8 хона етмасди - home currency кучли
     * валюта бўлганда (масалан USD) тескари курс жуда кичик сон бўлади:
     * 1 UZS = 0.000081967213 USD. 12 хонада 8 та маъноли рақам сақланади,
     * 1 млрд UZS'да хато 0.001 USD дан кам.
     */
    public static final int RATE_SCALE = 12;

    /** Ҳужжат валютасидаги сумма. */
    @Column(precision = 19, scale = AMOUNT_SCALE)
    private BigDecimal amount;

    /** Сумма валютасининг ISO коди. */
    @Column(length = 3)
    private String currency;

    /** Home валютадаги эквивалент - ledger шу бўйича балансланади. */
    @Column(precision = 19, scale = AMOUNT_SCALE)
    private BigDecimal baseAmount;

    /** Ҳужжат санасидаги курс: 1 currency = rate home. */
    @Column(precision = 24, scale = RATE_SCALE)
    private BigDecimal exchangeRate;

    protected Money() { /* JPA */ }

    /**
     * Ягона ёзиш нуқтаси - factory'лар нима узатишидан қатъи назар
     * тўртта майдоннинг бирортаси null бўла олмайди: инвариант
     * constructor'нинг ўзида ҳимояланади, factory текширувига ишониб
     * қолинмайди.
     */
    private Money(BigDecimal amount, String currency,
                  BigDecimal baseAmount, BigDecimal exchangeRate) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(baseAmount, "baseAmount");
        Objects.requireNonNull(exchangeRate, "exchangeRate");
        this.amount = amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        this.currency = currency;
        this.baseAmount = baseAmount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        this.exchangeRate = exchangeRate.setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    /** Home валютадаги сумма (курс = 1) */
    public static Money ofBase(BigDecimal amount, String homeCurrency) {
        return new Money(amount, homeCurrency, amount, BigDecimal.ONE);
    }

    /** Чет валютадаги сумма - baseAmount автоматик ҳисобланади */
    public static Money of(BigDecimal amount, String currency, BigDecimal exchangeRate) {
        // multiply constructor'дан олдин ишлайди - null'ни шу ерда тутамиз,
        // акс ҳолда хабарсиз NPE чиқарди.
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(exchangeRate, "exchangeRate");
        BigDecimal base = amount.multiply(exchangeRate);
        return new Money(amount, currency, base, exchangeRate);
    }

    /**
     * Чет валютадаги сумма АНИҚ келтирилган baseAmount билан - penny
     * rounding тақсимоти учун: чет валюта ҳужжатида base'лар
     * {@link MoneyAllocation} орқали ҳисобланади (назорат сатри -
     * targetBase, сатрлар - lineBases largest-remainder билан) ва шу
     * тайёр қийматлар билан Money ясалади; {@link #of} эса base'ни
     * ҳар сафар ўзи яхлитлаб ҳисоблар эди - тақсимот кафолатлари
     * (Asrorxoja-002) йўқолар эди. BR-LED-003 инварианти (±0.0001)
     * кучда қолади - узоқлашган base бари бир рад этилади.
     */
    public static Money withBase(BigDecimal amount, String currency,
                                 BigDecimal baseAmount, BigDecimal exchangeRate) {
        return new Money(amount, currency, baseAmount, exchangeRate);
    }

    /** Нол қиймат - бошланғич қолдиқ ва йиғиндиларда қулай. */
    public static Money zero(String currency) { return ofBase(BigDecimal.ZERO, currency); }

    /** Сумма мусбатми - XOR валидацияда ишлатилади. */
    public boolean isPositive() { return amount.signum() > 0; }

    /**
     * Value object семантикаси: иккита Money барча майдонлари тенг
     * бўлсагина тенг. BigDecimal'да equals scale'ни ҳам солиштиради -
     * конструктор scale'ни normalize қилгани учун бу тўғри ишлайди.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money other)) return false;
        return Objects.equals(amount, other.amount)
                && Objects.equals(currency, other.currency)
                && Objects.equals(baseAmount, other.baseAmount)
                && Objects.equals(exchangeRate, other.exchangeRate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, currency, baseAmount, exchangeRate);
    }

    @Override
    public String toString() {
        return amount + " " + currency + " (base " + baseAmount + ")";
    }
}
