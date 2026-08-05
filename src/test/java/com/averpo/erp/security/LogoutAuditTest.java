package com.averpo.erp.security;

import com.averpo.erp.audit.domain.AuditEvent;
import com.averpo.erp.audit.domain.AuditEventType;
import com.averpo.erp.audit.repo.AuditEventRepository;
import com.averpo.erp.security.domain.UserRole;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LOGOUT аудит ёзуви + user_agent сақланиши (Arbitr-062): logout success
 * handler ҳодисани username/IP/User-Agent билан ёзади ва одатий
 * {@code /login?logout} манзилига йўналтиради (аввалги logoutSuccessUrl
 * хулқи ўзгармаган). MockMvc web оқими - LoginAuditIpTest нақши
 * (remoteAddr қўлда қўйилади, жонли серверда уни RemoteIpValve ечади).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LogoutAuditTest {

    /** Клиент IP симуляцияси (TEST-NET-3 - LoginAuditIpTest нақши). */
    private static final String CLIENT_IP = "203.0.113.9";

    /** Синов браузер қатори - user_agent устунида айнан сақланиши текширилади. */
    private static final String USER_AGENT = "Mozilla/5.0 (Test) Firefox/128.0";

    @Autowired WebApplicationContext context;
    @Autowired AuditEventRepository auditRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void logout_writesLogoutEvent_withIpAndUserAgent() throws Exception {
        mockMvc.perform(post("/logout").with(csrf())
                        .with(TestRoles.as("chiquvchi", UserRole.VIEWER_AUDITOR))
                        .header("User-Agent", USER_AGENT)
                        .with(request -> {
                            request.setRemoteAddr(CLIENT_IP);
                            return request;
                        }))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));

        AuditEvent logout = auditRepository.findAll().stream()
                .filter(e -> e.getEventType() == AuditEventType.LOGOUT)
                .findFirst().orElseThrow();
        assertThat(logout.getUsername()).isEqualTo("chiquvchi");
        assertThat(logout.getIpAddress()).isEqualTo(CLIENT_IP);
        // user_agent сақланиши (changeset 050) - айнан юборилган қатор
        assertThat(logout.getUserAgent()).isEqualTo(USER_AGENT);
    }
}
