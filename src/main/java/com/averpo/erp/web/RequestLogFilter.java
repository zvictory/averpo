package com.averpo.erp.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Ҳар сўровга MDC контексти (rid + user) + ёзувчи сўровлар изи
 * (docs/modules/logging.md, Arbitr-099). Марказий «ҳар амал кўринсин»
 * нуқтаси - ҳар service'га қўлда log ёзилмайди.
 *
 * <p><b>Тартиб ТУЗОҒИ (карта 1-тузоқ):</b> бу filter Spring Security
 * занжиридан КЕЙИН (ичкарида) рўйхатга олинади ({@link
 * com.averpo.erp.config.LoggingFilterConfig}) - шунда filter
 * ишлаганда {@code SecurityContext} аллақачон юкланган, {@code user}
 * MDC'га бўш эмас тушади. Занжирдан ОЛДИН турса user доим бўш бўларди.
 *
 * <p>MDC leak олдини олиш: {@code rid}/{@code user} доим {@code finally}'да
 * тозаланади - thread pool қайта ишлатилганда эски қиймат сизиб чиқмайди.
 *
 * <p>ЁЗУВ ТАҚИҚИ (logging.md): URL query (getRequestURI query'сиз),
 * парол/токен/CSRF ҲЕЧ ҚАЧОН логланмайди - фақат метод, йўл, статус,
 * давомийлик.
 */
public class RequestLogFilter extends OncePerRequestFilter {

    /** Сўров изи логгери (айнан шу класс номи билан кўринади). */
    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    /** MDC калити: қисқа сўров идентификатори (бир сессия қаторларини боғлайди). */
    static final String MDC_RID = "rid";

    /** MDC калити: аутентификацияланган фойдаланувчи. */
    static final String MDC_USER = "user";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        MDC.put(MDC_RID, newRequestId());
        String user = currentUser();
        if (user != null) {
            MDC.put(MDC_USER, user);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            logRequest(request, response, System.currentTimeMillis() - startedAt);
            // Тартиб муҳим эмас, лекин иккови ҳам тозаланиши ШАРТ (MDC leak)
            MDC.remove(MDC_RID);
            MDC.remove(MDC_USER);
        }
    }

    /** 6 белгили hex идентификатор - қатор боши учун ихчам (UUID'дан кесиб). */
    private String newRequestId() {
        return UUID.randomUUID().toString().substring(0, 6);
    }

    /**
     * Жорий аутентификацияланган фойдаланувчи номи ёки null (аноним/
     * login олди). Anonymous token'ни атайлаб чиқариб ташлаймиз - у
     * «anonymousUser» деб шовқин қиларди.
     */
    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return auth.getName();
    }

    /**
     * Ёзувчи сўров (POST/PUT/DELETE/PATCH) INFO, кузатувчи (GET/HEAD)
     * DEBUG - шовқинни камайтириб «ҳар ўзгартириш изли» талабини сақлайди
     * (logging.md). Статус жавобдан (sendError/redirect'дан кейин ҳам аниқ).
     */
    private void logRequest(HttpServletRequest request, HttpServletResponse response, long ms) {
        String method = request.getMethod();
        boolean writeRequest = !("GET".equals(method) || "HEAD".equals(method));
        if (writeRequest) {
            log.info("{} {} -> {} ({}ms)", method, request.getRequestURI(),
                    response.getStatus(), ms);
        } else if (log.isDebugEnabled()) {
            log.debug("{} {} -> {} ({}ms)", method, request.getRequestURI(),
                    response.getStatus(), ms);
        }
    }
}
