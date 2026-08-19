package com.averpo.erp.security.service;

import com.averpo.erp.audit.domain.AuditEventType;
import com.averpo.erp.audit.service.AuditLogService;
import com.averpo.erp.security.repo.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Login lockout (BR-USR-009, SEC-002): Spring Security auth
 * event'ларини тинглаб app_user'даги счётчикни юритади - мантиқ
 * controller'да ЭМАС, security қатламида (лойиҳа талаби).
 *
 * <p>Оқим: хато паролда {@code failed_attempts++}; бўсағага етганда
 * {@code locked_until = now + 15 дақиқа} - кейинги уринишлар
 * JpaUserDetailsService'нинг accountNonLocked'и орқали
 * LockedException билан қайтади (парол текширилмасдан ҳам). Қулф
 * муддати ўтгандан кейинги биринчи хато уриниш счётчикни 1 дан қайта
 * бошлайди («муддат ўтгач счётчик нолланади»); муваффақиятли кириш
 * иккала майдонни нолга туширади.
 *
 * <p>Онгли қабул қилинган хавф (spec): қулф хабари username
 * мавжудлигини билвосита ошкор қилади (enumeration) - internal MVP
 * учун қабул қилинган, ташқи deployment'да IP-даражали чекловлар
 * билан қайта кўрилади. Мавжуд бўлмаган username'да ҳеч нарса
 * ёзилмайди - фарқли хулқ ҳам чиқмайди.
 */
@Component
@RequiredArgsConstructor
public class LoginAttemptListener {

    /**
     * Хавфсизлик изи логгери (docs/modules/logging.md, DEC-099):
     * lockout - developer/admin кўриши керак бўлган ҳодиса (WARN), шунинг
     * учун error.log триажида ҳам чиқади. Парол ҲЕЧ ҚАЧОН логланмайди.
     */
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(LoginAttemptListener.class);

    /** BR-USR-009 бўсағаси: шунча кетма-кет хато уриниш - қулф. */
    public static final int MAX_FAILED_ATTEMPTS = 5;

    /** BR-USR-009 қулф муддати. */
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    /** Фойдаланувчилар репозиторийси - ўз модулимиз ичида. */
    private final AppUserRepository repository;

    /**
     * LOCKOUT аудит ёзуви учун (audit-log.md): security → audit
     * боғлиқлик рухсатли, цикл йўқ - audit security модулини import
     * қилмайди (фақат framework event'ларини тинглайди).
     */
    private final AuditLogService auditLogService;

    /** Хато парол: счётчик ошади, бўсағада қулф қўйилади. */
    @EventListener
    @Transactional
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        String username = event.getAuthentication().getName();
        Instant now = Instant.now();
        // Счётчик АТОМАР SQL билан (PERF-014): параллел хато
        // уринишлар row lock'да навбатлашади - optimistic lock 500'и
        // йўқ, иккала уриниш ҳам саналади. Муддати ўтган қулфни ҳам
        // шу UPDATE тозалайди (янги серия 1 дан).
        if (repository.incrementFailedAttempts(username, now) == 0) {
            return; // username мавжуд эмас - аввалгидек ҳеч нарса ёзилмайди
        }
        // Row lock ҳали бизда - янги қийматни ўқиб бўсаға текшируви
        // хавфсиз (иккинчи параллел уриниш commit'имизни кутади)
        repository.findByUsername(username).ifPresent(user -> {
            if (user.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
                user.lockUntil(now.plus(LOCK_DURATION));
                // Техник log (DEC-099): lockout - WARN (error.log триажи).
                // Username кузатув учун, парол ЁЗИЛМАЙДИ.
                log.warn("Login lockout: '{}' {} хато уринишдан кейин {} дақиқага қулфланди",
                        username, MAX_FAILED_ATTEMPTS, LOCK_DURATION.toMinutes());
                // Қулф айнан шу транзакцияда қўйилди - аудитга шу ерда
                // ёзилади (LOGIN_FAILURE'ни AuthAuditListener ёзади)
                auditLogService.record(AuditEventType.LOCKOUT, username, null, null,
                        MAX_FAILED_ATTEMPTS + " хато уринишдан кейин "
                        + LOCK_DURATION.toMinutes() + " дақиқага қулфланди",
                        com.averpo.erp.audit.service.AuthAuditListener
                                .clientIp(event.getAuthentication()));
            }
        });
    }

    /** Муваффақиятли кириш: счётчик ва қулф нолланади (BR-USR-009). */
    @EventListener
    @Transactional
    public void onSuccess(AuthenticationSuccessEvent event) {
        repository.findByUsername(event.getAuthentication().getName())
                .ifPresent(user -> {
                    if (user.getFailedAttempts() > 0 || user.getLockedUntil() != null) {
                        user.resetLock();
                    }
                });
    }
}
