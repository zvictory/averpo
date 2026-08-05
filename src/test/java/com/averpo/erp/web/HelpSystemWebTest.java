package com.averpo.erp.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import com.averpo.erp.testsupport.WithMockRole;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Контекстли ёрдам (?) тизими тестлари (DEC-079, help-system.md
 * «Тестлар» рўйхати): render (helpKey берилган саҳифада (?) бор,
 * берилмаганда йўқ) ва help.* калитлари уч тил паритети.
 *
 * <p>ДИҚҚАТ: оддий «helpbtn» substring майдон (?) тугмаларида ҳам
 * учрайди - саҳифа тугмаси фақат унга хос барқарор префикс
 * («helpbtn float-right», DEC-122 утилита канони) бўйича assert
 * қилинади.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockRole(username = "admin")
class HelpSystemWebTest {

    @Autowired WebApplicationContext context;

    /** Security filter chain уланган MockMvc (ScreenSmokeTest қолипи). */
    private MockMvc mockMvc;

    /** springSecurity() уланмаса ҳар GET 302 login'га кетади. */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /**
     * helpKey берилган саҳифада саҳифа (?) тугмаси, майдон (?) матни
     * (closing date - spec триггер саволи) ва глобал диалог markup бор.
     */
    @Test
    void settingsPage_rendersPageAndFieldHelp() throws Exception {
        mockMvc.perform(get("/settings"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"helpbtn float-right")))
                .andExpect(content().string(containsString("ўтган даврлар ҳисоботлари ўзгармай туради")))
                .andExpect(content().string(containsString("id=\"helpDlgTitle\"")));
    }

    /**
     * helpKey берилмаган саҳифада саҳифа (?) йўқ, глобал диалог эса бор.
     * {@code /users/new} (userForm) - helpKey'сиз саҳифа. (Эски
     * {@code /profile/password} DEC-101'да helpKey'ли {@code /profile}'га
     * redirect бўлди - энди helpKey'сиз саҳифа сифатида ишлатиб бўлмайди.)
     */
    @Test
    void pageWithoutHelpKey_hasNoPageHelpButton() throws Exception {
        mockMvc.perform(get("/users/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("class=\"helpbtn float-right"))))
                .andExpect(content().string(containsString("id=\"helpDlgTitle\"")));
    }

    /**
     * layout.form ишлатадиган ҳужжат формасида ҳам диалог + майдон (?)
     * бор (2026-07-11 фойдаланувчи топилмаси: диалог фақат main.jte'да
     * эди - формалардаги (?) жавобсиз қоларди; энди умумий partial
     * иккала layout'да).
     */
    @Test
    void formLayoutPage_hasFieldHelpAndDialog() throws Exception {
        mockMvc.perform(get("/estimates/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"helpDlgTitle\"")))
                .andExpect(content().string(containsString("Таклиф амал муддати")));
    }

    /**
     * help.* калитлари учала тилда айнан бир тўплам ва ҳар калитда
     * title+body жуфти тўлиқ (паритет - spec 2-банди).
     */
    @Test
    void helpKeys_parityAcrossThreeLanguages() throws Exception {
        Set<String> uz = helpKeys("messages.properties");
        Set<String> en = helpKeys("messages_en.properties");
        Set<String> ru = helpKeys("messages_ru.properties");

        assertThat(uz).isNotEmpty().isEqualTo(en).isEqualTo(ru);

        Set<String> titles = uz.stream()
                .filter(k -> k.endsWith(".title")).collect(Collectors.toSet());
        Set<String> bodies = uz.stream()
                .filter(k -> k.endsWith(".body")).collect(Collectors.toSet());
        assertThat(titles).isNotEmpty();
        assertThat(titles.stream()
                .map(t -> t.substring(0, t.length() - ".title".length()) + ".body")
                .collect(Collectors.toSet())).isEqualTo(bodies);
    }

    /**
     * Битта тилнинг help.* калитлари (.properties UTF-8 - Properties
     * default ISO-8859-1 ўқийди, шунинг учун Reader шарт).
     */
    private Set<String> helpKeys(String file) throws Exception {
        Properties props = new Properties();
        try (var reader = new InputStreamReader(
                new ClassPathResource(file).getInputStream(), StandardCharsets.UTF_8)) {
            props.load(reader);
        }
        return props.stringPropertyNames().stream()
                .filter(k -> k.startsWith("help."))
                .collect(Collectors.toSet());
    }
}
