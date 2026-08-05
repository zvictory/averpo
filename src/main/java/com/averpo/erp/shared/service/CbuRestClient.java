package com.averpo.erp.shared.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link CbuRateClient}'нинг ҳақиқий импли - cbu.uz JSON API.
 * Package-private: ташқарига фақат интерфейс кўринади.
 *
 * <p>API: {@code /uz/arkhiv-kursov-valyut/json/all/{sana}/} - шу кун
 * учун барча валюталар. ЦБ Rate'ни string беради («12600.47»),
 * Ccy - ISO код. Ҳар қандай хато (тармоқ, формат) BR-FX-004 га
 * ўралади - чақирувчи учун битта тушунарли хато тури.
 *
 * <p>Rate семантикаси: ЦБ {@code Rate}'ни {@code Nominal} дона валюта
 * учун беради (ҳозир ҳаммасида 1) - client ҳимоя учун Rate/Nominal
 * қилади, бизнинг курс доим «1 валюта = N сўм» бўлиб қолади
 * (docs/modules/multi-currency.md).
 */
@Component
class CbuRestClient implements CbuRateClient {

    /** ЦБ кунлик курслар API'си - sana ISO форматда (2026-07-06). */
    private static final String URL_TEMPLATE =
            "https://cbu.uz/uz/arkhiv-kursov-valyut/json/all/{date}/";

    /** Nominal'га бўлишдаги аниқлик - Money.RATE_SCALE билан бир хил (12). */
    private static final int RATE_SCALE = 12;

    /** HTTP client - тайм-аутлар қатъий белгиланган (пастга қаранг). */
    private final RestClient restClient;

    /**
     * Boot 4 starter-web {@code RestClient.Builder} bean бермайди
     * (алоҳида starter керак бўларди) - биттагина ташқи API учун
     * client шу ерда қурилади. Тайм-аутлар мажбурий: ЦБ жавоб бермай
     * қолса scheduler ipи чексиз осилиб қолмасин.
     */
    @Autowired
    CbuRestClient() {
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();
        org.springframework.http.client.JdkClientHttpRequestFactory factory =
                new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(java.time.Duration.ofSeconds(20));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** Тестлар учун: MockRestServiceServer шу builder'га боғланади. */
    CbuRestClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    /** ЦБ JSON жавобининг бизга керакли уч майдони. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record CbuRateDto(@JsonProperty("Ccy") String ccy,
                      @JsonProperty("Rate") String rate,
                      @JsonProperty("Nominal") String nominal) { }

    /** {@inheritDoc} */
    @Override
    public List<CbuRate> rates(LocalDate date) {
        try {
            CbuRateDto[] dtos = restClient.get()
                    .uri(URL_TEMPLATE, date.toString())
                    .retrieve()
                    .body(CbuRateDto[].class);
            if (dtos == null) {
                throw new BusinessRuleException(BusinessRule.BR_FX_004,
                        "ЦБ бўш жавоб қайтарди: " + date);
            }
            List<CbuRate> rates = new ArrayList<>(dtos.length);
            for (CbuRateDto dto : dtos) {
                if (dto.ccy() == null || dto.rate() == null || dto.rate().isBlank()) {
                    continue; // тўлиқсиз ёзув - бошқа валюталарни тўсмайди
                }
                rates.add(new CbuRate(dto.ccy().strip().toUpperCase(),
                        perUnitRate(dto)));
            }
            return rates;
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            // RestClientException, NumberFormatException, ArithmeticException
            // ва ҳ.к. - ҳаммаси фойдаланувчига битта тушунарли хато бўлиб чиқади
            throw new BusinessRuleException(BusinessRule.BR_FX_004,
                    "ЦБ курс хизматига мурожаат амалга ошмади: " + e.getMessage());
        }
    }

    /**
     * Rate'ни «1 дона валюта» семантикасига келтиради: ЦБ Nominal
     * майдони (ҳозир доим 1) 10/100 бўлиб қолса ҳам курс тўғри қолади.
     * Nominal йўқ/бўш - 1 деб қаралади.
     */
    private BigDecimal perUnitRate(CbuRateDto dto) {
        BigDecimal rate = new BigDecimal(dto.rate().strip());
        if (dto.nominal() == null || dto.nominal().isBlank()) {
            return rate;
        }
        BigDecimal nominal = new BigDecimal(dto.nominal().strip());
        if (nominal.compareTo(BigDecimal.ONE) == 0) {
            return rate;
        }
        return rate.divide(nominal, RATE_SCALE, RoundingMode.HALF_UP);
    }
}
