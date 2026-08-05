package com.averpo.erp.bank;

import com.averpo.erp.testsupport.WithMockRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Transfer формасидаги курс ориентацияси render тести (Arbitr-097б).
 *
 * <p>Transfer формаси client-side Alpine - актуал ориентация қиймати
 * браузерда ҳисобланади (JS orient(), Fmt.orient кўзгуси; math'нинг ўзи
 * {@code RateOrientInputTest}'да server-томон тасдиқланган). Бу тест
 * ШАБЛОН УЛАНИШИНИ гаровлайди: (1) ёрлиқлар кучли-валюта базисига
 * динамик (x-text="rateBase/rateQuote/from2Base/..."), (2) кўринадиган
 * input ориентацияли (x-model="rateVisible/from2Visible/to2Visible"),
 * (3) POST курс майдонлари ҲАР ДОИМ КАНОНИК (hidden name="exchangeRate"/
 * "toRate" каноник модел билан) - server/servis ТЕГИЛМАЙДИ.
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockRole(username = "admin")
class TransferRateOrientRenderTest {

    @Autowired WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void transferForm_rateLabels_orientedBindings() throws Exception {
        String body = mockMvc.perform(get("/transfers/new"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Битта чет валюта: ёрлиқ base/quote динамик, input кўринадиган БУФЕР
        // (Arbitr-108: computed x-model'дан оддий буфер - курсор сакрамасин)
        org.assertj.core.api.Assertions.assertThat(body)
                .as("курс ёрлиғи кучли-валюта базисига динамик")
                .contains("x-text=\"rateBase\"")
                .contains("x-text=\"rateQuote\"")
                .contains("x-model=\"rateBuf\"");
        // Икки чет валюта: ҳар курс ўз жуфти бўйича ориентацияли буфер
        org.assertj.core.api.Assertions.assertThat(body)
                .contains("x-text=\"from2Base\"").contains("x-text=\"from2Quote\"")
                .contains("x-model=\"from2Buf\"")
                .contains("x-text=\"to2Base\"").contains("x-text=\"to2Quote\"")
                .contains("x-model=\"to2Buf\"");

        // Эски computed visible боғланишлари ЙЎҚ (Arbitr-108 - getter қайта
        // ёзиб курсорни сакратар эди) ва каноник модел визуал input'да ЭМАС
        org.assertj.core.api.Assertions.assertThat(body)
                .as("эски computed visible ва каноник боғланишлар визуал input'да йўқ")
                .doesNotContain("x-model=\"rateVisible\"")
                .doesNotContain("x-model=\"from2Visible\"")
                .doesNotContain("x-model=\"to2Visible\"")
                .doesNotContain("x-model=\"rate\"")
                .doesNotContain("x-model=\"fromRate2\"")
                .doesNotContain("x-model=\"toRate2\"");
    }

    /**
     * Arbitr-108: учала кўринадиган курс input'и мини-калькулятор
     * (class="money" - money-input.js фақат шунга уланади) ва edit-buffer
     * нақшига (x-on:input буфер→каноник, x-ref фокус текшируви учун) уланган;
     * буфер курс формати Arbitr-135 қоидасида (fmtRate → averpoRateFmt:
     * &gt;= 1 → 2 хона, &lt; 1 → макс 8 хона), калькулятор натижаси учун
     * data-rate-field маркер. Курс ҳақиқати битта манбада - ориентацияли
     * input (Arbitr-136: алоҳида fxLine матни йўқ). Курсор сакраши ва хом
     * float илдизлари шу боғланишларда ёпилади (жонли smoke тасдиқлайди).
     */
    @Test
    void transferForm_rateInputs_moneyCalcAndEditBuffer() throws Exception {
        String body = mockMvc.perform(get("/transfers/new"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Мини-калькулятор: учала курс input'ида money класси (fromAmount/
        // toAmount қаторида 5 та .money - 3 курс + 2 сумма). Arbitr-121:
        // money энди Penguin канон класслари билан ЁНМА-ЁН туради
        // (class="money mt-1 ...") - маркер префикс бўйича саналади.
        org.assertj.core.api.Assertions.assertThat(
                        body.split("class=\"money", -1).length - 1)
                .as("курс + сумма input'лари money классида")
                .isGreaterThanOrEqualTo(5);
        // Edit-buffer: ҳар input'да буфер→каноник синхрон + фокус учун ref
        org.assertj.core.api.Assertions.assertThat(body)
                .as("буфер→каноник x-on:input + x-ref")
                .contains("x-on:input=\"onRateInput()\"").contains("x-ref=\"rateInput\"")
                .contains("x-on:input=\"onFrom2Input()\"").contains("x-ref=\"from2Input\"")
                .contains("x-on:input=\"onTo2Input()\"").contains("x-ref=\"to2Input\"");
        // Edit-buffer методлари + 135 курс формати шаблонда
        org.assertj.core.api.Assertions.assertThat(body)
                .as("буфер методлари ва fmtRate шаблонда")
                .contains("syncRateBuffers()")
                .contains("canonicalFromBuf(")
                .contains("rateFlipped(")
                .contains("fmtRate(")
                .contains("window.averpoRateFmt(") // 135: ягона JS кўзгу
                .contains("window.averpoMoneyFmt("); // авто-сумма пул форматида
        // Arbitr-136: курс ҳақиқати фақат input'да - дубликат fxLine йўқ,
        // conversionNote ҳинти туради (uz локализация матни бўйича)
        org.assertj.core.api.Assertions.assertThat(body)
                .as("fxLine дубликат ўчган, ҳинт қолган")
                .doesNotContain("fxLine")
                .contains("Валюталар фарқли");
        // Arbitr-135: учала курс input'ида data-rate-field маркер -
        // калькулятор натижаси курс кўрсатиш қоидасида ёзилади
        org.assertj.core.api.Assertions.assertThat(
                        body.split("data-rate-field", -1).length - 1)
                .as("курс input'ларида data-rate-field маркер")
                .isGreaterThanOrEqualTo(3);
    }

    @Test
    void transferForm_hiddenRateFields_stayCanonical() throws Exception {
        String body = mockMvc.perform(get("/transfers/new"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // POST майдонлари КАНОНИК модел билан (fromRate2/rate) - visible
        // ориентацияли бўлса ҳам server айнан «1 чет = ? home» олади
        org.assertj.core.api.Assertions.assertThat(body)
                .as("hidden exchangeRate каноник модел билан")
                .contains("name=\"exchangeRate\"")
                .contains("distinctForeign === 2 ? fromRate2 : (fromForeign ? rate : '')");
        org.assertj.core.api.Assertions.assertThat(body)
                .as("hidden toRate каноник модел билан")
                .contains("name=\"toRate\"")
                .contains("distinctForeign === 2 ? toRate2 : (toForeign ? rate : '')");
        // Alpine ориентация ҳелпери шаблонда (Fmt.orient кўзгуси)
        org.assertj.core.api.Assertions.assertThat(body)
                .contains("RATE_PRIORITY")
                .contains("orient(codeA, codeB, rateStr)");
    }
}
