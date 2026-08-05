package com.averpo.erp.audit.service;

import com.averpo.erp.audit.domain.AuditEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureDisabledEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

/**
 * Кириш уринишларини аудитга ёзади (docs/modules/audit-log.md):
 * Spring Security'нинг ўз event'лари тингланади - LoginAttemptListener
 * нақши, security модулига ТЕГИЛМАЙДИ (audit фақат framework
 * класларини import қилади, цикл йўқ).
 *
 * <p>Учала хато уриниш ҳам битта LOGIN_FAILURE тури билан ёзилади
 * (enum кенгаймайди, U-007) - сабаб details'да фарқланади: нотўғри
 * парол / қулф даврида уриниш / нофаол ҳисобга уриниш. Акс ҳолда
 * LOCKOUT'дан кейинги уринишлар журналда жимликда қоларди.
 *
 * <p>LOGIN_FAILURE'да authenticated principal йўқ - username устунига
 * уринилган ном ёзилади (AuditEvent.username изоҳи). IP манзил event
 * ичидаги WebAuthenticationDetails'дан; web бўлмаган оқимда (тестдаги
 * тўғридан-тўғри AuthenticationManager) null қолади.
 */
@Component
@RequiredArgsConstructor
public class AuthAuditListener {

    /** Ёзишнинг ягона йўли (ўз транзакциясини ўзи очади). */
    private final AuditLogService auditLogService;

    /** Муваффақиятли кириш. */
    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        auditLogService.record(AuditEventType.LOGIN_SUCCESS,
                event.getAuthentication().getName(), null, null, null,
                clientIp(event.getAuthentication()));
    }

    /** Хато парол билан уриниш. */
    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        auditLogService.record(AuditEventType.LOGIN_FAILURE,
                event.getAuthentication().getName(), null, null,
                "Нотўғри парол билан уриниш",
                clientIp(event.getAuthentication()));
    }

    /** Қулф даврида (BR-USR-009) уриниш - парол текширилмасдан рад. */
    @EventListener
    public void onLocked(AuthenticationFailureLockedEvent event) {
        auditLogService.record(AuditEventType.LOGIN_FAILURE,
                event.getAuthentication().getName(), null, null,
                "Қулф даврида уриниш",
                clientIp(event.getAuthentication()));
    }

    /** Нофаол (active=false, BR-USR-010) ҳисобга уриниш. */
    @EventListener
    public void onDisabled(AuthenticationFailureDisabledEvent event) {
        auditLogService.record(AuditEventType.LOGIN_FAILURE,
                event.getAuthentication().getName(), null, null,
                "Нофаол ҳисобга уриниш",
                clientIp(event.getAuthentication()));
    }

    /**
     * Клиент IP'си auth details'дан - form login оқимида Spring Security
     * WebAuthenticationDetails қўяди ({@code request.getRemoteAddr()});
     * бошқа оқимларда null (сохта манзил ёзилмайди). Public: LOCKOUT
     * ёзувида LoginAttemptListener ҳам худди шу усулда IP олади - икки
     * жойда ёзилмасин.
     *
     * <p>Жонли серверда илова Cloudflare + nginx ортида: {@code getRemoteAddr()}
     * хом socket манзили (nginx = 127.0.0.1) эмас, RemoteIpValve X-Real-IP
     * header'дан ечган ҳақиқий клиент IP'сини қайтаради (nginx X-Real-IP ←
     * CF-Connecting-IP; конфиг application.yml server.tomcat.remoteip).
     * Спуфинг ҳимояси - валве header'га ФАҚАТ ички proxy'дан келганда
     * ишонади (internal-proxies рўйхати); локал dev'да proxy йўқ, 127.0.0.1
     * қолаверади (DEC-058).
     */
    public static String clientIp(Authentication authentication) {
        return authentication.getDetails() instanceof WebAuthenticationDetails web
                ? web.getRemoteAddress() : null;
    }
}
