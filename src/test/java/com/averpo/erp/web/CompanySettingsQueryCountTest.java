package com.averpo.erp.web;

import com.averpo.erp.shared.domain.ClassTrackingMode;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.testsupport.SqlCaptureInspector;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sanjar-005 query-count регрессияси: ҳужжат формаси GET'ида
 * company_settings бир request ичида фақат икки марта ўқилади -
 * биттаси {@link GlobalModelAttributes} layout'и (топбар бренди),
 * биттаси controller оқимининг snapshot'и. Тузатишдан олдин 5 та эди
 * (layout 1 + controller accessor'лари 4: zoneId, homeCurrency ×2,
 * trackClasses).
 *
 * <p>Хулқ регрессиялари (карта талаби): home=UZS/USD render, class mode
 * OFF/PER_TXN ва компания timezone'идаги default сана - snapshot
 * қийматлари accessor'лар қайтарадигани билан айнан бир хил бўлиши шарт.
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockRole(username = "admin")
class CompanySettingsQueryCountTest {

    @Autowired WebApplicationContext context;

    /** Созлама қийматларини тестда ўзгартириш/солиштириш учун. */
    @Autowired CompanySettingsService settingsService;

    /** Security filter chain уланган MockMvc (ScreenSmokeTest қолипи). */
    private MockMvc mockMvc;

    /** MockMvc'га springSecurity() уланмаса ҳар GET 302 login'га кетади. */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /** Берилган route GET'ида company_settings SELECT'лар сонини ўлчайди. */
    private long settingsSelectCount(String route) throws Exception {
        // Қатор олдиндан яратилсин - биринчи мурожаат INSERT'и ўлчовга кирмасин
        settingsService.get();
        SqlCaptureInspector.start();
        List<String> captured;
        try {
            mockMvc.perform(get(route)).andExpect(status().isOk());
        } finally {
            captured = SqlCaptureInspector.stop();
        }
        return SqlCaptureInspector.selectCount(captured, "company_settings");
    }

    @Test
    void bankTransactionForm_readsCompanySettingsTwiceOnly() throws Exception {
        assertEquals(2, settingsSelectCount("/bank-transactions/new"),
                "layout 1 + controller snapshot 1 бўлиши керак (аввал 5 эди)");
    }

    @Test
    void salesReceiptForm_readsCompanySettingsTwiceOnly() throws Exception {
        assertEquals(2, settingsSelectCount("/sales-receipts/new"),
                "layout 1 + controller snapshot 1 бўлиши керак (аввал 5 эди)");
    }

    @Test
    void bankTransactionForm_defaultsUzsOffAndCompanyZoneToday() throws Exception {
        // Default ҳолат аниқ ўрнатилади - seed'га боғланиб қолмаслик учун
        settingsService.update("Компания", "UZS", "Asia/Tashkent", null, null);
        settingsService.changeTrackClasses(ClassTrackingMode.OFF);
        mockMvc.perform(get("/bank-transactions/new"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("homeCurrency", "UZS"))
                .andExpect(model().attribute("classMode", "OFF"))
                .andExpect(model().attribute("classes", List.of()))
                .andExpect(model().attribute("form", hasProperty("txnDate",
                        equalTo(LocalDate.now(ZoneId.of("Asia/Tashkent"))))));
    }

    @Test
    void bankTransactionForm_homeUsdPerTxn_snapshotMatchesAccessors() throws Exception {
        settingsService.update("Компания", "USD", "Asia/Tashkent", null, null);
        settingsService.changeTrackClasses(ClassTrackingMode.PER_TXN);
        mockMvc.perform(get("/bank-transactions/new"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("homeCurrency", "USD"))
                .andExpect(model().attribute("classMode", "PER_TXN"));
    }

    @Test
    void bankTransactionForm_companyTimezone_drivesDefaultDate() throws Exception {
        // UTC+14 зона: JVM/UTC зонасидан деярли доим фарқ қиладиган «бугун» -
        // default сана айнан КОМПАНИЯ зонасидан олинаётганини исботлайди
        settingsService.update("Компания", "UZS", "Pacific/Kiritimati", null, null);
        mockMvc.perform(get("/bank-transactions/new"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("form", hasProperty("txnDate",
                        equalTo(LocalDate.now(ZoneId.of("Pacific/Kiritimati"))))));
    }
}
