package com.averpo.erp.security.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;

import java.io.IOException;

/**
 * Рухсат рад этилиши (403)ни икки оқимга ажратади (DEC-096):
 *
 * <ul>
 *   <li><b>CSRF хатоси</b> ({@link CsrfException} - Missing/Invalid) -
 *       амалда сессия муддати тугаган: эски сессия токени билан render
 *       қилинган форма POST'да токени мос эмас/йўқ. Фойдаланувчига хом
 *       403 (Whitelabel) ўрнига {@code /login?expired} - login саҳифаси
 *       «муддат тугади» хабарини кўрсатади (мавжуд ?error/?locked/
 *       ?logout нақши). Хавфсизликка зарар йўқ - CSRF ҳимояси ишлаяпти,
 *       фақат UX.</li>
 *   <li><b>Оддий рухсат рад</b> (соҳа-даража, DEC-092 модели) -
 *       default хулқ сақланади: {@link AccessDeniedHandlerImpl} 403
 *       статус қўяди, контейнер {@code /error} диспетчерига ўтади
 *       (ErrorController чиройли саҳифа беради). Роль тизимидаги 403
 *       семантикаси ЎЗГАРМАЙДИ.</li>
 * </ul>
 *
 * <p>CSRF қиймати ҲЕЧ ҚАЧОН log'га ёзилмайди (logging.md тақиғи) - фақат
 * «сессия муддати» факти DEBUG'да қолади.
 */
public class CsrfAwareAccessDeniedHandler implements AccessDeniedHandler {

    /** Триаж учун: муддат тугаши қанчалик тез-тез юз беришини кузатиш. */
    private static final Logger log = LoggerFactory.getLogger(CsrfAwareAccessDeniedHandler.class);

    /** CSRF бўлмаган радларда 092 модели ўзгармасин - default handler'га делегация. */
    private final AccessDeniedHandler delegate = new AccessDeniedHandlerImpl();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        if (accessDeniedException instanceof CsrfException) {
            log.debug("CSRF токени мос эмас/йўқ (сессия муддати) - login?expired га: {}",
                    request.getRequestURI());
            response.sendRedirect(request.getContextPath() + "/login?expired");
            return;
        }
        // Соҳа-даража рад (092): default 403 + /error диспетчери
        delegate.handle(request, response, accessDeniedException);
    }
}
