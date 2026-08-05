package com.averpo.erp.web;

import com.averpo.erp.i18n.Msg;
import com.averpo.erp.shared.web.Htmx;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Контейнер даражасидаги хато диспетчери (Arbitr-096): Spring Boot
 * default Whitelabel саҳифасини АЛМАШТИРАДИ. {@code sendError(...)} ёки
 * фильтр қатламидаги хатолар (масалан соҳа-даража 403 -
 * {@link com.averpo.erp.security.config.CsrfAwareAccessDeniedHandler}
 * делегацияси) контейнер орқали {@code /error} га тушади - бу ерда улар
 * {@code shared/error.jte} билан render бўлади (уч тил, статусга мос
 * сарлавҳа). Whitelabel ҲЕЧ ҚАЧОН кўринмайди.
 *
 * <p>Чегара: {@link GlobalExceptionHandler} DispatcherServlet ичидаги
 * exception'ларни (BusinessRule, NotFound ва ҳ.к.) тутади ва ЎЗИ
 * error.jte render қилади. Бу контроллер эса ундан ЎТИБ КЕТГАН/фильтр
 * даражасидаги хатоларни (403 access denied, статик 404) қоплайди -
 * иккови бир-бирини тўлдиради.
 *
 * <p>{@code @ExceptionHandler} model'ига @ControllerAdvice атрибутлари
 * қўшилмагани каби (GlobalExceptionHandler изоҳи), бу ерда ҳам
 * msg/lang/csrf/perms қўлда берилади - акс ҳолда error.jte'даги msg
 * null бўлиб NPE берарди.
 */
@Controller
@RequiredArgsConstructor
public class ErrorController implements org.springframework.boot.webmvc.error.ErrorController {

    /** Layout ва хабар атрибутларини қайта ишлатиш учун (GlobalExceptionHandler нақши). */
    private final GlobalModelAttributes modelAttributes;

    /**
     * Ҳар турдаги контейнер хатосини чиройли саҳифага айлантиради.
     * Status атрибути {@code jakarta.servlet.error.status_code}'дан
     * олинади (диспетчер қўяди); йўқ бўлса 500 деб қаралади.
     *
     * <p>Arbitr-127: HTMX partial сўровида (масалан drawer hx-get'и
     * фильтр даражасида 403 еса) тўлиқ саҳифа ўрнига ихчам alert
     * фрагмент қайтади - механика GlobalExceptionHandler.errorView
     * изоҳида (X-Averpo-Error + HX-Reswap жуфти, статус ўзгармайди).
     */
    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, HttpServletResponse response,
                              Model model) {
        int status = resolveStatus(request);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Msg msg = modelAttributes.msg();
        var csrfToken = modelAttributes.csrf(request);
        if (csrfToken != null) {
            // Deferred токен жавоб commit бўлмасидан ҳал қилинади - акс
            // ҳолда cookie'сиз аноним сўровда render ўртасидаги сессия
            // яратиш саҳифани узарди (GlobalExceptionHandler'даги изоҳ).
            csrfToken.getToken();
        }

        model.addAttribute("msg", msg);
        model.addAttribute("lang", modelAttributes.lang());
        model.addAttribute("csrf", csrfToken);
        // canEdit энди соҳага-сезгир (Arbitr-092) - request узатилади
        model.addAttribute("canEdit", modelAttributes.canEdit(auth, request));
        model.addAttribute("isAdmin", modelAttributes.isAdmin(auth));
        model.addAttribute("status", status);
        model.addAttribute("errorTitle", titleFor(status, msg));
        model.addAttribute("errorMessage", messageFor(status, msg));
        if (Htmx.isHtmx(request)) {
            response.setHeader("X-Averpo-Error", "1");
            response.setHeader("HX-Reswap", "afterbegin");
            return "shared/errorAlert";
        }
        return "shared/error";
    }

    /** Диспетчер қўйган HTTP статус коди (йўқ бўлса 500). */
    private int resolveStatus(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (code instanceof Integer statusCode) {
            return statusCode;
        }
        return 500;
    }

    /**
     * Статусга мос сарлавҳа (404/403/400/405 - махсус; қолгани умумий).
     * Static ва package-private: GlobalExceptionHandler ҳам шу
     * мосликдан фойдаланади - икки жойда ёзилмайди (Arbitr-127).
     */
    static String titleFor(int status, Msg msg) {
        return switch (status) {
            case 404 -> msg.lookup("error.notFoundTitle");
            case 403 -> msg.lookup("error.forbiddenTitle");
            case 400 -> msg.lookup("error.badRequestTitle");
            case 405 -> msg.lookup("error.methodNotAllowedTitle");
            default -> msg.lookup("error.title");
        };
    }

    /** Статусга мос тушунтириш матни. */
    private String messageFor(int status, Msg msg) {
        return switch (status) {
            case 404 -> msg.lookup("error.notFound");
            case 403 -> msg.lookup("error.forbidden");
            case 400 -> msg.lookup("error.badRequest");
            case 405 -> msg.lookup("error.methodNotAllowed");
            default -> msg.lookup("error.serverError");
        };
    }
}
