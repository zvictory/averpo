package com.averpo.erp.shared.service;

import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.CbuRateClient.CbuRate;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * CbuRestClient импл тести - ҳақиқий URL формати ва JSON парсинг
 * MockRestServiceServer билан текширилади (тармоққа чиқилмайди).
 * ExchangeRateServiceTest портни mock қилади - бу тест импл'нинг ўзи
 * учун (алоҳида unit қамров). Импл package-private бўлгани
 * учун тест шу пакетда туради; Spring контексти йўқ - соф unit тест.
 */
class CbuRestClientTest {

    /** Тест санаси - URL'да ISO форматда кутилади. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 4);

    /** Кутиладиган тўлиқ URL - формат ўзгарса тест дарҳол кўрсатади. */
    private static final String EXPECTED_URL =
            "https://cbu.uz/uz/arkhiv-kursov-valyut/json/all/2026-07-04/";

    /** ЦБ жавобининг қисқартирилган реал намунаси (ортиқча майдонлар билан). */
    private static final String CBU_JSON = """
            [
              {"id":69,"Code":"840","Ccy":"USD","CcyNm_UZ":"AQSH dollari",
               "Nominal":"1","Rate":"11909.66","Diff":"-12.92","Date":"04.07.2026"},
              {"id":21,"Code":"398","Ccy":"KZT","Nominal":"100","Rate":"2280.50",
               "Date":"04.07.2026"},
              {"id":9,"Code":"051","Ccy":"","Nominal":"1","Rate":"","Date":"04.07.2026"}
            ]""";

    /** Builder'га боғланган client + mock server жуфти. */
    private record Fixture(CbuRestClient client, MockRestServiceServer server) { }

    /** Тест client'и - HTTP қатлами mock server'га уланган. */
    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new CbuRestClient(builder), server);
    }

    @Test
    void rates_parsesRealJsonShape_andNormalizesNominal() {
        Fixture fx = fixture();
        fx.server().expect(requestTo(EXPECTED_URL))
                .andRespond(withSuccess(CBU_JSON, MediaType.APPLICATION_JSON));

        List<CbuRate> rates = fx.client().rates(DATE);

        // Бўш Ccy/Rate ёзуви ташлаб юборилади - иккита валюта қолади
        assertThat(rates).hasSize(2);
        assertThat(rates.get(0).currencyCode()).isEqualTo("USD");
        assertThat(rates.get(0).rate()).isEqualByComparingTo("11909.66");
        // Nominal=100 - Rate 1 донага келтирилади (ҳимоя, spec'да ҳужжатланган)
        assertThat(rates.get(1).currencyCode()).isEqualTo("KZT");
        assertThat(rates.get(1).rate()).isEqualByComparingTo("22.805");
        fx.server().verify();
    }

    @Test
    void rates_serverError_wrappedAsBrFx004() {
        Fixture fx = fixture();
        fx.server().expect(requestTo(EXPECTED_URL))
                .andRespond(withServerError());

        assertThatThrownBy(() -> fx.client().rates(DATE))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-FX-004"));
    }

    @Test
    void rates_nonJsonBody_wrappedAsBrFx004() {
        Fixture fx = fixture();
        fx.server().expect(requestTo(EXPECTED_URL))
                .andRespond(withSuccess("<html>maintenance</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> fx.client().rates(DATE))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-FX-004"));
    }

    @Test
    void rates_badNumberInRate_wrappedAsBrFx004() {
        Fixture fx = fixture();
        fx.server().expect(requestTo(EXPECTED_URL))
                .andRespond(withSuccess(
                        "[{\"Ccy\":\"USD\",\"Nominal\":\"1\",\"Rate\":\"N/A\"}]",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fx.client().rates(DATE))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-FX-004"));
    }
}
