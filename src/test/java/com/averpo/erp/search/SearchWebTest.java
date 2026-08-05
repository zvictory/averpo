package com.averpo.erp.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.averpo.erp.security.domain.UserRole;
import com.averpo.erp.testsupport.WithMockRole;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Глобал қидирув web қатлами (docs/modules/global-search.md «Тестлар» 6):
 * /search тўлиқ саҳифа смоки + HTMX dropdown partial смоки. Партиал
 * тўлиқ саҳифа emas (layout йўқ) - шу билан ажратилади.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SearchWebTest {

    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /** Тўлиқ саҳифа (JS'сиз fallback): layout билан render (VIEWER ҳам кўради). */
    @Test
    @WithMockRole(value = UserRole.VIEWER_AUDITOR, username = "viewer")
    void fullPage_rendersForAnyRole() throws Exception {
        mockMvc.perform(get("/search").param("q", "zzqqx"))
                .andExpect(status().isOk())
                // layout.main белгиси - тўлиқ HTML ҳужжат
                .andExpect(content().string(containsString("<!DOCTYPE html>")))
                // натижа йўқ - «Ҳеч нарса топилмади»
                .andExpect(content().string(containsString("Ҳеч нарса топилмади")));
    }

    /** HTMX сўрови: фақат dropdown partial (layout йўқ) + реал натижа линки. */
    @Test
    @WithMockRole(value = UserRole.ACCOUNTANT, username = "acc")
    void hxRequest_returnsPartialWithHit() throws Exception {
        // Транзакция ичида битта счёт - партиалда реал натижа кўринсин
        jdbc.sql("""
                        INSERT INTO account (id, name, classification, type, detail_type,
                            code, active, postable)
                        VALUES (:id, 'Zephyr Hisob', 'ASSET', 'BANK', 'CHECKING', 'ZPH1', true, true)
                        """)
                .param("id", UUID.randomUUID()).update();

        mockMvc.perform(get("/search").param("q", "zephyr").header("HX-Request", "true"))
                .andExpect(status().isOk())
                // партиал - тўлиқ саҳифа эмас
                .andExpect(content().string(not(containsString("<!DOCTYPE html>"))))
                // реал натижа: счёт номи + Account history route
                .andExpect(content().string(containsString("Zephyr Hisob")))
                .andExpect(content().string(containsString("/transactions")));
    }
}
