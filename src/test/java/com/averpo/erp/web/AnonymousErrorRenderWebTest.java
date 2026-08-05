package com.averpo.erp.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Arbitr-127 жонли smoke топилмаси учун регресс тест: cookie'СИЗ аноним
 * сўров /error'га тушганда саҳифа ТЎЛИҚ render бўлиши шарт. Аввал
 * deferred CSRF токен sidebar'даги logout формасида (csrf.jte) илк бор
 * ҳал бўлар эди - у пайтда жавоб аллақачон commit бўлган (chunked оқим
 * бошланган), сессия яратиш IllegalStateException билан оқимни ярмида
 * узарди (браузерда ERR_INCOMPLETE_CHUNKED_ENCODING). MockMvc бу синфни
 * КЎРМАЙДИ (mock response ҳеч қачон commit бўлмайди) - шунга бу тест
 * реал embedded server ({@code RANDOM_PORT}) билан юради.
 *
 * @author Zafar
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AnonymousErrorRenderWebTest {

    /** Реал server порти - URL қўлда йиғилади. */
    @LocalServerPort
    int port;

    @Test
    void anonymousCookielessErrorPage_rendersFully_notTruncated() {
        // Янги RestTemplate = cookie'сиз «биринчи ташриф» ҳолати
        RestTemplate rest = new RestTemplate();
        ResponseEntity<String> resp = rest.getForEntity(
                "http://localhost:" + port + "/error", String.class);

        // /error тўғридан-тўғри очилса status атрибути йўқ - 500 деб render
        // бўлади, жавобнинг ЎЗИ 200 (диспетчер эмас, оддий GET)
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = resp.getBody();
        assertThat(body).isNotNull();
        // Оқим узилмаган - ҳужжат охиригача етиб борган
        assertThat(body).contains(">500</p>");
        assertThat(body).contains("Хатолик юз берди");
        assertThat(body.trim()).endsWith("</html>");
    }
}
