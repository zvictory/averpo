package com.averpo.erp.shared;

import com.averpo.erp.shared.service.CompanySettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.averpo.erp.testsupport.WithMockRole;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /settings форма парси хулқи (Xorazmiy-021/Arbitr-045 банд 5): ставка ва
 * давр ёпилиш санаси String сифатида олиниб FormParsers орқали парсланади -
 * бузуқ форматда фойдаланувчи хом 400 эмас, ўша экранда кириллча BR хабарини
 * («[BR-SET-005]» / «[BR-SET-006]») кўради; тўғри форматда сақланиб redirect
 * бўлади. Service даражасидаги чегара (0..100) PayrollRatesTest'да - бу ерда
 * фақат web парс қатлами.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockRole(username = "admin")
class SettingsWebTest {

    @Autowired WebApplicationContext context;
    @Autowired CompanySettingsService settingsService;

    private MockMvc mockMvc;
    /** Жорий home currency/timezone - формада ўзгармас юборилади (қулф/валидация тегмасин). */
    private String currency;
    private String timezone;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        // get() қатор йўқ бўлса default (UZS) билан яратади
        currency = settingsService.homeCurrency();
        timezone = settingsService.get().getTimezone();
    }

    /** Бузуқ ставка форматида: хом 400 эмас, ўша экранда BR-SET-005 хабари. */
    @Test
    void malformedRate_showsCyrillicError_notRaw400() throws Exception {
        mockMvc.perform(post("/settings").with(csrf())
                        .param("name", "Компания")
                        .param("homeCurrency", currency)
                        .param("timezone", timezone)
                        .param("closingDate", "2026-07-09")
                        .param("incomeTaxRate", "abc")   // сон эмас
                        .param("pensionRate", "8")
                        .param("socialTaxRate", "12"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("[BR-SET-005]")));
    }

    /** Бузуқ сана форматида: хом 400/DateTimeParseException эмас, BR-SET-006 хабари. */
    @Test
    void malformedClosingDate_showsCyrillicError_notRaw400() throws Exception {
        mockMvc.perform(post("/settings").with(csrf())
                        .param("name", "Компания")
                        .param("homeCurrency", currency)
                        .param("timezone", timezone)
                        .param("closingDate", "09.07.2026")   // ISO эмас
                        .param("incomeTaxRate", "12")
                        .param("pensionRate", "8")
                        .param("socialTaxRate", "12"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("[BR-SET-006]")));
    }

    /** Тўғри форматда: сақланади (ставка + сана) ва redirect бўлади. */
    @Test
    void validForm_parsesSavesAndRedirects() throws Exception {
        mockMvc.perform(post("/settings").with(csrf())
                        .param("name", "Компания")
                        .param("homeCurrency", currency)
                        .param("timezone", timezone)
                        .param("closingDate", "2026-06-30")
                        .param("incomeTaxRate", "12,5")   // вергул ўнлик - FormParsers қабул қилади
                        .param("pensionRate", "8")
                        .param("socialTaxRate", "25"))
                .andExpect(status().is3xxRedirection());

        var settings = settingsService.get();
        assertThat(settings.getClosingDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(settings.getIncomeTaxRate()).isEqualByComparingTo("12.5");
        assertThat(settings.getPensionRate()).isEqualByComparingTo("8");
        assertThat(settings.getSocialTaxRate()).isEqualByComparingTo("25");
    }

    /**
     * Arbitr-104: settings тагидаги build версия қатори рендери. Бу
     * репо'да {@code ./gradlew test} bootBuildInfo'ни (classes task
     * орқали) ишга туширади - build-info.properties main classpath'да
     * бўлади, шунинг учун {@code BuildProperties} bean МАВЖУД ва «Averpo
     * ERP v...» қатори чиқади. bean бўлмаган ҳолда ҳам (controller
     * ObjectProvider null-чидамли) саҳифа синмас эди - фақат қатор
     * чиқмасди (карта тузоқ 3, ScreenSmoke яшиллигича).
     */
    @Test
    void settingsPageRenders_buildVersionRow() throws Exception {
        mockMvc.perform(get("/settings"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AVERPO v")));
    }
}
