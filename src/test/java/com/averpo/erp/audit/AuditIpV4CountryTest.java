package com.averpo.erp.audit;

import com.averpo.erp.audit.domain.AuditEvent;
import com.averpo.erp.audit.domain.AuditEventType;
import com.averpo.erp.audit.repo.AuditEventRepository;
import com.averpo.erp.audit.service.AuditLogService;
import com.averpo.erp.security.domain.UserRole;
import com.averpo.erp.security.service.UserService;
import com.averpo.erp.testsupport.TestRoles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DEC-091: CF header'ларидан Pseudo IPv4 ва давлат коди аудитга
 * ёзилиши + экранда IPv4-биринчи формат.
 *
 * <p>Айнан LOGIN йўли синалади (карта 4-тузоғи): AuthAuditListener IP'ни
 * WebAuthenticationDetails'дан олади, аммо CF header'лари
 * RequestContextHolder'дан ўқилади - иккала йўл бир ёзувга бирлашиши
 * гаровланади. MockMvc жонли CF занжирини юргизмайди (header'лар қўлда
 * сохталанади) - жонли занжир deploy'дан кейин серверда текширилади.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditIpV4CountryTest {

    /** Тест фойдаланувчиси пароли (хато уриниш учун бошқаси юборилади). */
    private static final String PASSWORD = "cf-sinov-parol-123";

    /** IPv6 уланиш манзили симуляцияси (жонли серверда RemoteIpValve ечади). */
    private static final String CLIENT_V6 = "2a05:45c2:1010:3400:c0ff:eeba:d015:40c1";

    /** CF Pseudo IPv4 - синтетик диапазон 240.0.0.0/4 намунаси. */
    private static final String PSEUDO_V4 = "240.11.22.33";

    /** Windows десктоп Chrome UA - render форматини тўлиқ текшириш учун. */
    private static final String WIN_CHROME =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    @Autowired WebApplicationContext context;
    @Autowired UserService userService;
    @Autowired AuditEventRepository auditRepository;
    @Autowired AuditLogService auditLogService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /** Хато login уриниши - LOGIN_FAILURE ёзувини қайтаради. */
    private AuditEvent failedLogin(String username, boolean withCfHeaders) throws Exception {
        userService.create(username, "CF синов", UserRole.VIEWER_AUDITOR, PASSWORD);
        var request = post("/login").with(csrf())
                .param("username", username)
                .param("password", "notogri")
                .header("User-Agent", WIN_CHROME)
                .with(r -> {
                    r.setRemoteAddr(CLIENT_V6);
                    return r;
                });
        if (withCfHeaders) {
            request = request.header(AuditLogService.CF_PSEUDO_IPV4, PSEUDO_V4)
                    .header(AuditLogService.CF_IP_COUNTRY, "UZ");
        }
        mockMvc.perform(request).andExpect(status().is3xxRedirection());
        return auditRepository.findAll().stream()
                .filter(e -> e.getEventType() == AuditEventType.LOGIN_FAILURE
                        && username.equals(e.getUsername()))
                .findFirst().orElseThrow();
    }

    /**
     * CF header'лари бор login: ip_address уланиш манзили (IPv6)
     * ҳозиргидек, ip_v4 ва country header'лардан тушади - login ҳодисаси
     * IP'ни AuthAuditListener йўлидан олса ҳам header'лар ушланади.
     */
    @Test
    void loginWithCfHeaders_writesIpV4AndCountry() throws Exception {
        AuditEvent failure = failedLogin("cfsinov01", true);
        assertThat(failure.getIpAddress()).isEqualTo(CLIENT_V6);
        assertThat(failure.getIpV4()).isEqualTo(PSEUDO_V4);
        assertThat(failure.getCountry()).isEqualTo("UZ");
    }

    /** Header'сиз (dev муҳити) login: ip_v4/country null - ёзув йиқилмайди. */
    @Test
    void loginWithoutCfHeaders_leavesNulls() throws Exception {
        AuditEvent failure = failedLogin("cfsinov02", false);
        assertThat(failure.getIpAddress()).isEqualTo(CLIENT_V6);
        assertThat(failure.getIpV4()).isNull();
        assertThat(failure.getCountry()).isNull();
    }

    /**
     * Экран формати (карта кўрсатиш кутилмаси): IPv4 БИРИНЧИ, ёнида
     * қисқартирилган v6, кейин браузер + major версия, OS, қурилма тури
     * ва давлат коди - «·» ажратгичи билан.
     */
    @Test
    void auditPage_rendersIpv4FirstFormat() throws Exception {
        failedLogin("cfsinov03", true);
        mockMvc.perform(get("/audit-log")
                        .with(TestRoles.as("cfadmin01", UserRole.SUPER_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "IP: 240.11.22.33 · v6: 2a05:…40c1 · Chrome 126 "
                                + "· Windows · десктоп · UZ")));
    }

    /**
     * Тарихий ёзув (ip_v4/country бўш, фақат IPv4 ip_address + raw UA):
     * янги OS формати raw user_agent'дан парсланиб чиқади, v6 бўлаги
     * ва давлат коди эса кўринмайди - null-safe кўрсатиш.
     */
    @Test
    void auditPage_oldRecordRendersOsFromRawUa() throws Exception {
        auditLogService.record(AuditEventType.LOGIN_SUCCESS, "eskisinov01",
                null, null, null, "84.54.70.11", WIN_CHROME, null, null);
        mockMvc.perform(get("/audit-log")
                        .with(TestRoles.as("cfadmin02", UserRole.SUPER_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "IP: 84.54.70.11 · Chrome 126 · Windows · десктоп")));
    }
}
