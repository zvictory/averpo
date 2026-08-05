package com.averpo.erp.security;

import com.averpo.erp.audit.domain.AuditEventType;
import com.averpo.erp.audit.repo.AuditEventRepository;
import com.averpo.erp.security.domain.AppUser;
import com.averpo.erp.security.domain.UserRole;
import com.averpo.erp.security.repo.AppUserRepository;
import com.averpo.erp.security.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Login lockout тестлари (BR-USR-009/010, SEC-002): оқим тўлиқ
 * Spring Security орқали - AuthenticationManager хато уринишларда
 * event чиқаради (LoginAttemptListener санайди),
 * JpaUserDetailsService қулф/нофаолликни accountNonLocked/disabled
 * орқали қайтаради.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LoginLockoutTest {

    /** Тест фойдаланувчисининг тўғри пароли. */
    private static final String PASSWORD = "togri-parol-123";

    @Autowired UserService userService;
    @Autowired AppUserRepository repository;
    @Autowired AuthenticationConfiguration authenticationConfiguration;

    /** Аудит ёзувлари текшируви учун (audit-log.md «Тестлар» 4-банд). */
    @Autowired AuditEventRepository auditRepository;

    /** Global AuthenticationManager - худди form login ишлатадигани. */
    private AuthenticationManager manager() throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /** Login уринишини симуляция қилади. */
    private void attempt(String username, String password) throws Exception {
        manager().authenticate(
                new UsernamePasswordAuthenticationToken(username, password));
    }

    @Test
    void fiveFailures_locks_evenCorrectPasswordRejected_untilExpiry() throws Exception {
        AppUser user = userService.create("lockme", "Қулф тест",
                UserRole.VIEWER_AUDITOR, PASSWORD);

        // 5 кетма-кет хато уриниш - ҳар бири BadCredentials
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> attempt("lockme", "notogri"))
                    .isInstanceOf(BadCredentialsException.class);
        }
        AppUser locked = repository.findByUsername("lockme").orElseThrow();
        assertThat(locked.getFailedAttempts()).isEqualTo(5);
        assertThat(locked.getLockedUntil()).isAfter(Instant.now());

        // Аудит (audit-log.md 4-банд): ҳар хато уриниш LOGIN_FAILURE
        // бўлиб ёзилган, бўсағада битта LOCKOUT ёзуви бор
        assertThat(auditRepository.findAll().stream()
                .filter(e -> e.getEventType() == AuditEventType.LOGIN_FAILURE
                        && "lockme".equals(e.getUsername())).count()).isEqualTo(5);
        assertThat(auditRepository.findAll().stream()
                .filter(e -> e.getEventType() == AuditEventType.LOCKOUT
                        && "lockme".equals(e.getUsername())).count()).isEqualTo(1);

        // 6-уриниш ТЎҒРИ парол билан ҳам LockedException (BR-USR-009)
        assertThatThrownBy(() -> attempt("lockme", PASSWORD))
                .isInstanceOf(LockedException.class);

        // U-007: қулф давридаги уриниш ҳам журналга тушади - LOGIN_FAILURE
        // details'ида қулф изи (аввал бу уринишлар жимликда қоларди)
        assertThat(auditRepository.findAll().stream()
                .filter(e -> e.getEventType() == AuditEventType.LOGIN_FAILURE
                        && "lockme".equals(e.getUsername())
                        && e.getDetails() != null && e.getDetails().contains("Қулф"))
                .count()).isEqualTo(1);

        // Муддат ўтди (симуляция) - кириш OK ва счётчик нолланган
        locked.lockUntil(Instant.now().minusSeconds(1));
        repository.saveAndFlush(locked);
        attempt("lockme", PASSWORD);
        AppUser after = repository.findByUsername("lockme").orElseThrow();
        assertThat(after.getFailedAttempts()).isZero();
        assertThat(after.getLockedUntil()).isNull();

        // Муваффақиятли кириш ҳам аудитда (LOGIN_SUCCESS)
        assertThat(auditRepository.findAll().stream()
                .anyMatch(e -> e.getEventType() == AuditEventType.LOGIN_SUCCESS
                        && "lockme".equals(e.getUsername()))).isTrue();
    }

    @Test
    void successAfterFourFailures_resetsCounter() throws Exception {
        userService.create("qaytadi", "Счётчик тест", UserRole.VIEWER_AUDITOR, PASSWORD);

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> attempt("qaytadi", "notogri"))
                    .isInstanceOf(BadCredentialsException.class);
        }
        assertThat(repository.findByUsername("qaytadi").orElseThrow()
                .getFailedAttempts()).isEqualTo(4);

        // Муваффақиятли кириш счётчикни нолга туширади (SEC-002 кутилмаси)
        attempt("qaytadi", PASSWORD);
        assertThat(repository.findByUsername("qaytadi").orElseThrow()
                .getFailedAttempts()).isZero();
    }

    @Test
    void failureAfterExpiredLock_restartsCounterAtOne() throws Exception {
        // PERF-014: счётчик энди атомар SQL'да - муддати ўтган қулф
        // тозаланиб янги серия 1 дан бошланиши ўша UPDATE ичида
        userService.create("muddati", "Муддат тест", UserRole.VIEWER_AUDITOR, PASSWORD);
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> attempt("muddati", "notogri"))
                    .isInstanceOf(BadCredentialsException.class);
        }
        AppUser locked = repository.findByUsername("muddati").orElseThrow();
        locked.lockUntil(Instant.now().minusSeconds(1)); // муддат ўтди (симуляция)
        repository.saveAndFlush(locked);

        // Хато уриниш: эски қулф ўчади, счётчик 5+1 эмас - айнан 1
        assertThatThrownBy(() -> attempt("muddati", "notogri"))
                .isInstanceOf(BadCredentialsException.class);
        AppUser after = repository.findByUsername("muddati").orElseThrow();
        assertThat(after.getFailedAttempts()).isEqualTo(1);
        assertThat(after.getLockedUntil()).isNull();
    }

    @Test
    void inactiveUser_cannotLogin() throws Exception {
        // BR-USR-010 (мавжуд хулқ regression тести)
        AppUser user = userService.create("nofaol", "Нофаол тест",
                UserRole.VIEWER_AUDITOR, PASSWORD);
        user.setActive(false);
        repository.saveAndFlush(user);

        assertThatThrownBy(() -> attempt("nofaol", PASSWORD))
                .isInstanceOf(DisabledException.class);

        // U-007: нофаол ҳисобга уриниш ҳам аудитга тушади - LOGIN_FAILURE
        // details'ида нофаоллик изи
        assertThat(auditRepository.findAll().stream()
                .filter(e -> e.getEventType() == AuditEventType.LOGIN_FAILURE
                        && "nofaol".equals(e.getUsername())
                        && e.getDetails() != null && e.getDetails().contains("Нофаол"))
                .count()).isEqualTo(1);
    }
}
