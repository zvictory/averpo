package com.averpo.erp.shared.repo;

import com.averpo.erp.shared.domain.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Валюта курслари репозиторийси - ташқарига фақат ExchangeRateService
 * орқали. Append-only тарих: тартиб доим (rate_date, кейин UUIDv7 id)
 * бўйича - бир кунда кўп ёзув бўлса энг охиргиси олинади (Arbitr-022).
 *
 * @author Zafar
 */
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {

    /**
     * Санага тенг ёки ундан ОЛДИНГИ энг охирги курс ёзуви: rate_date
     * бўйича, кейин id бўйича (бир кунда кўп ёзув бўлса охиргиси). Дам
     * олиш/байрам кунида олдингиси амал қилади.
     */
    Optional<ExchangeRate> findFirstByCurrencyCodeAndRateDateLessThanEqualOrderByRateDateDescIdDesc(
            String currencyCode, LocalDate rateDate);

    /** Айнан шу (валюта, сана)даги энг охирги ёзув - append skip-if-same учун. */
    Optional<ExchangeRate> findFirstByCurrencyCodeAndRateDateOrderByIdDesc(
            String currencyCode, LocalDate rateDate);

    /** Курс тарихи экрани учун - янгидан эскига (сана, кейин id). */
    List<ExchangeRate> findByCurrencyCodeOrderByRateDateDescIdDesc(String currencyCode);

    /** Валютанинг энг охирги курс ёзуви (Currencies экрани устуни). */
    Optional<ExchangeRate> findFirstByCurrencyCodeOrderByRateDateDescIdDesc(String currencyCode);

    /**
     * ҲАР валютанинг амалдаги (энг охирги) ёзуви битта сўровда -
     * Currencies экранидаги ҳар валютага алоҳида latest() N+1'и ўрнига
     * (Beruniy-023). Window function: ҳар currency_id ичида (rate_date,
     * кейин UUIDv7 id) бўйича энг янгиси - append-only тарихда бир кунда
     * бир нечта ёзув бўлади, id тартиби ҳал қилади.
     */
    @Query(value = """
            SELECT er.* FROM exchange_rate er
            JOIN (SELECT id, ROW_NUMBER() OVER (
                      PARTITION BY currency_id
                      ORDER BY rate_date DESC, id DESC) AS rn
                  FROM exchange_rate) ranked
              ON ranked.id = er.id AND ranked.rn = 1
            """, nativeQuery = true)
    List<ExchangeRate> findLatestForEachCurrency();

    /**
     * ҲАР валютанинг импорт кунигача (шу кунни ҲАМ қўшиб) АМАЛДАГИ
     * (effective) курси битта сўровда - ЦБ импортининг ИККИ мақсади учун
     * (Arbitr-168, аввал Sanjar-011 skip-if-same): (1) «ўзгарди»ни
     * аниқлаш - fetched курс шу effective'дан фарқлими; (2) skip-if-same
     * дубль текшируви - effective айнан шу санадан (rate_date = импорт
     * куни) бўлсагина append current'и. Ҳар currency_id ичида (rate_date
     * DESC, кейин UUIDv7 id DESC) - дам олишда олдинги иш куни ёзуви
     * effective бўлади ({@link #findFirstByCurrencyCodeAndRateDateLessThanEqualOrderByRateDateDescIdDesc}
     * якка методининг batch кўзгуси, тартиб қоидаси бир хил).
     */
    @Query(value = """
            SELECT er.* FROM exchange_rate er
            JOIN (SELECT id, ROW_NUMBER() OVER (
                      PARTITION BY currency_id
                      ORDER BY rate_date DESC, id DESC) AS rn
                  FROM exchange_rate
                  WHERE rate_date <= :rateDate) ranked
              ON ranked.id = er.id AND ranked.rn = 1
            """, nativeQuery = true)
    List<ExchangeRate> findLatestEffectivePerCurrencyOn(
            @org.springframework.data.repository.query.Param("rateDate") LocalDate rateDate);
}
