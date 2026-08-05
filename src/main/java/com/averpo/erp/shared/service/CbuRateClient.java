package com.averpo.erp.shared.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * ЎзР Марказий банки курс манбаининг порти - ExchangeRateService тармоқ
 * тафсилотидан ажратилган, тестларда сохта импл билан алмаштирилади
 * (тестлар тармоққа чиқмайди).
 */
public interface CbuRateClient {

    /** Битта валютанинг битта кундаги ЦБ курси: 1 валюта = rate сўм. */
    record CbuRate(String currencyCode, BigDecimal rate) { }

    /**
     * Кўрсатилган кун учун ЦБ эълон қилган барча курслар.
     *
     * @throws com.averpo.erp.shared.exception.BusinessRuleException
     *         BR-FX-004 - тармоқ/формат хатоси (қўлда киритиш доим очиқ)
     */
    List<CbuRate> rates(LocalDate date);
}
