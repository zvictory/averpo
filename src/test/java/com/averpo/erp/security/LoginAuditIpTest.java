package com.averpo.erp.security;

import com.averpo.erp.audit.domain.AuditEvent;
import com.averpo.erp.audit.domain.AuditEventType;
import com.averpo.erp.audit.repo.AuditEventRepository;
import com.averpo.erp.security.domain.UserRole;
import com.averpo.erp.security.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Arbitr-058: аудит журналига кириш IP'си клиент манзилидан ёзилиши.
 *
 * <p>Иккита қатлам гаровланади:
 * <ol>
 *   <li>{@code AuthAuditListener} кириш хатосида IP'ни
 *       {@code WebAuthenticationDetails.getRemoteAddress()} (=
 *       {@code request.getRemoteAddr()})'дан олади - веб оқим.
 *       {@code LoginLockoutTest} login'ни тўғридан-тўғри
 *       {@code AuthenticationManager} орқали синайди (web details йўқ, IP
 *       доим null), шунинг учун бу йўл шу пайтгача синалмай қолган эди.</li>
 *   <li>{@code application.yml} даги {@code server.tomcat.remoteip} конфиги
 *       nginx контрактига мос (X-Real-IP / X-Forwarded-Proto) - аудит IP'си
 *       регресси айнан бу конфиг йўқолиши/нотўғри header'дан келади.</li>
 * </ol>
 *
 * <p><b>Нега RemoteIpValve бу ерда синалмайди:</b> MockMvc Tomcat
 * connector/valve занжирини четлаб ўтади - валве MockMvc'да УМУМАН
 * ишламайди, шунинг учун X-Real-IP header'ни бу ерда бериб бўлмайди
 * (у эътиборсиз қоларди). Валве X-Real-IP header'ни {@code getRemoteAddr()}
 * га ечиши жонли embedded Tomcat'да бўлади ва deploy'дан кейин серверда
 * тасдиқланади. Шу боис бу тест валве ЧИҚИШИНИ ({@code getRemoteAddr})
 * симуляция қилиб (remoteAddr'ни қўлда қўяди) уни аудитга тўғри кўчишини
 * текширади; конфиг ўзи иккинчи тест билан гаровланади.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LoginAuditIpTest {

    /** Тест фойдаланувчисининг тўғри пароли (хато уриниш учун бошқаси юборилади). */
    private static final String PASSWORD = "togri-parol-123";

    /**
     * nginx ортидаги «ҳақиқий» клиент IP симуляцияси - жонли серверда
     * RemoteIpValve айнан шу қийматни X-Real-IP'дан ечиб getRemoteAddr'га
     * қўяди. TEST-NET-3 (RFC 5737) диапазони - 127.0.0.1'дан аниқ фарқли.
     */
    private static final String CLIENT_IP = "203.0.113.7";

    @Autowired WebApplicationContext context;
    @Autowired UserService userService;
    @Autowired AuditEventRepository auditRepository;

    /** application.yml'дан remoteip конфиги (тест профили уни инҳерит қилади). */
    @Value("${server.tomcat.remoteip.remote-ip-header:}") String remoteIpHeader;
    @Value("${server.tomcat.remoteip.protocol-header:}") String protocolHeader;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /**
     * Веб login хатоси: {@code request.getRemoteAddr()} аудит ёзувига IP
     * бўлиб тушади. remoteAddr'ни тўғридан-тўғри қўямиз - жонли серверда
     * бу қийматни RemoteIpValve X-Real-IP'дан ечади (MockMvc валвени
     * юргизмайди, класс изоҳига қаранг).
     */
    @Test
    void webLoginFailure_recordsClientIpFromRemoteAddr() throws Exception {
        userService.create("iptest", "IP тест", UserRole.VIEWER_AUDITOR, PASSWORD);

        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "iptest")
                        .param("password", "notogri")   // хато парол → BadCredentials
                        .with(request -> {
                            request.setRemoteAddr(CLIENT_IP);
                            return request;
                        }))
                .andExpect(status().is3xxRedirection());

        AuditEvent failure = auditRepository.findAll().stream()
                .filter(e -> e.getEventType() == AuditEventType.LOGIN_FAILURE
                        && "iptest".equals(e.getUsername()))
                .findFirst().orElseThrow();
        assertThat(failure.getIpAddress()).isEqualTo(CLIENT_IP);
    }

    /**
     * application.yml {@code server.tomcat.remoteip} nginx контрактига мос:
     * X-Real-IP (nginx X-Real-IP ← CF-Connecting-IP) ва X-Forwarded-Proto.
     * Бу гаров конфиг ўчиб кетса ёки нотўғри header'га алмашса йиқилади
     * (Arbitr-058 регресс тутгичи).
     */
    @Test
    void applicationYml_remoteIpForwardingMatchesNginxContract() {
        assertThat(remoteIpHeader).isEqualToIgnoringCase("x-real-ip");
        assertThat(protocolHeader).isEqualToIgnoringCase("x-forwarded-proto");
    }
}
