package com.averpo.erp.web;

import com.averpo.erp.i18n.Msg;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.exception.NotFoundException;
import com.averpo.erp.shared.web.Htmx;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Умумий хато қайта ишлагич: бизнес қоида бузилишлари ва бузуқ
 * параметрлар фойдаланувчига 500 эмас, тушунарли хато саҳифаси
 * сифатида қайтади. Қоида кодлари (BR-*) хабар олдида кўрсатилади -
 * қўллаб-қувватлашга мурожаатда айнан шу код айтилади
 * (ягона каталог: shared.exception.BusinessRule enum).
 *
 * <p>Arbitr-127: ҳар handler статусни ҳам узатади - error.jte катта
 * код ва статусга мос иконка кўрсатади; HTMX partial сўровида эса
 * тўлиқ саҳифа ўрнига ихчам alert қайтади (errorView изоҳи).
 */
@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    /** Error шаблонига мажбурий атрибутларни бериш учун (пастга қаранг). */
    private final GlobalModelAttributes modelAttributes;

    /** Ёзув топилмади - 404 (хабар код префиксисиз, ўзи тушунарли). */
    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(NotFoundException e, Model model, HttpServletRequest request,
                           HttpServletResponse response) {
        log.debug("Топилмади: {}", e.getMessage());
        return errorView(model, request, response, 404, e.getMessage());
    }

    /**
     * Йўқ статик ресурс/йўл - 404, stacktrace'сиз (Arbitr-021). Браузер
     * ҳар ташрифда /favicon.ico сўрайди, эски bookmark ёки хато URL ҳам
     * шу ерга тушади: махсус handler бўлмаса catch-all уни 500 + ERROR
     * log қиларди - ҳар ташрифда сохта хато log'да ҳақиқий хатоларни
     * кўмиб юборарди. Хабар i18n калит орқали (уч тил).
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String noResource(NoResourceFoundException e, Model model,
                             HttpServletRequest request, HttpServletResponse response) {
        log.debug("Ресурс топилмади: {}", e.getMessage());
        return errorView(model, request, response, 404,
                modelAttributes.msg().lookup("error.notFound"));
    }

    /**
     * Бизнес қоида бузилиши - HTTP status қоиданинг ўзидан
     * (400/404/409, BusinessRule enum'да белгиланган), қоида коди билан.
     */
    @ExceptionHandler(BusinessRuleException.class)
    public String businessRule(BusinessRuleException e, Model model,
                               HttpServletRequest request,
                               HttpServletResponse response) {
        // Arbitr-099: БР ради айнан «warn» синфи (logging.md) - код + йўл
        // билан WARN (аввал DEBUG эди, default INFO'да умуман кўринмасди).
        // Стектрейс эмас - бу кутилган бизнес рад, кутилмаган хато эмас.
        log.warn("Бизнес қоида ради [{}] {}: {}", e.getCode(),
                request.getRequestURI(), e.getMessage());
        response.setStatus(e.getHttpStatus());
        // Формат exception'нинг ўзида (displayMessage) - controller
        // catch'лари билан бир хил кўриниш, икки жойда ёзилмайди
        return errorView(model, request, response, e.getHttpStatus(), e.displayMessage());
    }

    /** Кутилмаган валидация хатолари (эҳтиёт fallback). */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String badRequest(IllegalArgumentException e, Model model,
                             HttpServletRequest request, HttpServletResponse response) {
        log.debug("Валидация хатоси: {}", e.getMessage());
        // Хом e.getMessage() экранга чиқарилмайди: IAE хабарлари кўпинча
        // фойдаланувчи юборган қийматни такрорлайди (масалан UUID.fromString
        // «Invalid UUID string: ...») - reflected гигиена, typeMismatch қолипи.
        // Тўлиқ хабар юқоридаги log'да қолади. Матн i18n'да (Arbitr-127).
        return errorView(model, request, response, 400,
                modelAttributes.msg().lookup("error.badRequest"));
    }

    /** Типи мос келмаган параметр: /journal-entries/abc каби. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String typeMismatch(MethodArgumentTypeMismatchException e, Model model,
                               HttpServletRequest request, HttpServletResponse response) {
        log.debug("Параметр типи хатоси: {} = {}", e.getName(), e.getValue());
        // Фойдаланувчи киритган хом қиймат экранга қайтарилмайди (reflected
        // маълумот гигиенаси) - у фақат log'да қолади. Параметр номи коддан
        // келади, шунинг учун хабарда қолиши хавфсиз.
        return errorView(model, request, response, 400,
                modelAttributes.msg().get("error.badRequestParam", e.getName()));
    }

    /**
     * Сўров усули мос эмас (масалан POST'га мўлжалланган манзилга GET) -
     * 405. Arbitr-127: аввал catch-all'га тушиб 500 + ERROR log + умумий
     * матн берарди - бу кутилган client хатоси учун сохта тревога эди;
     * энди ўз статуси ва уч тилли матни билан қайтади.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public String methodNotAllowed(HttpRequestMethodNotSupportedException e, Model model,
                                   HttpServletRequest request, HttpServletResponse response) {
        log.debug("Усул қўллаб-қувватланмайди: {} {}", e.getMethod(), request.getRequestURI());
        return errorView(model, request, response, 405,
                modelAttributes.msg().lookup("error.methodNotAllowed"));
    }

    /**
     * Catch-all: кутилмаган хато фойдаланувчига stacktrace'сиз, умумий
     * хабар билан 500 қайтади; тўлиқ stacktrace фақат log'да - молия
     * тизимида ички тафсилот экранга чиқмайди. Хабар i18n'да (Arbitr-127).
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String unexpected(Exception e, Model model, HttpServletRequest request,
                             HttpServletResponse response) {
        log.error("Кутилмаган хато", e);
        return errorView(model, request, response, 500,
                modelAttributes.msg().lookup("error.serverError"));
    }

    /**
     * Error шаблонини render'га тайёрлайди. @ExceptionHandler model'ига
     * @ControllerAdvice'даги @ModelAttribute'лар ҚЎШИЛМАЙДИ (Spring MVC
     * қоидаси) - msg/lang/csrf/роллар шу ерда қўлда берилади. Акс ҳолда
     * error.jte'даги msg null бўлиб NPE билан йиқилар ва фойдаланувчи
     * тушунарли хато ўрнига хом 500 кўрар эди.
     *
     * <p>Arbitr-127: HTMX partial сўровида (HX-Request) тўлиқ саҳифа
     * ўрнига ихчам alert фрагмент қайтади - htmx 2 default'да 4xx/5xx
     * жавобни swap қилмагани учун X-Averpo-Error белгиси қўйилади:
     * client тингловчиси (money-input.js) фақат шу белгили жавобга swap
     * очади, HX-Reswap: afterbegin эса alert'ни target ичидаги мавжуд
     * контентни (форма қийматлари, киритилган сатрлар) бузмасдан тепага
     * қўяди. Статус кодлари ўзгармайди - тутиш мантиқи 096 ҳолича.
     */
    private String errorView(Model model, HttpServletRequest request,
                             HttpServletResponse response, int status, String message) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Msg msg = modelAttributes.msg();
        var csrfToken = modelAttributes.csrf(request);
        if (csrfToken != null) {
            // Deferred токен ЖАВОБ COMMIT БЎЛМАСИДАН ҳал қилинади (Arbitr-127
            // жонли smoke топилмаси): cookie'сиз аноним сўровда saveToken
            // сессия яратади - шаблон оқими бошлангандан кейин бу
            // IllegalStateException бўлиб саҳифани ярмида узарди (sidebar
            // logout формасидаги csrf.jte'да). Эрта чақириқда сессия
            // Set-Cookie ҳали мумкин пайтда яратилади - render хавфсиз.
            csrfToken.getToken();
        }
        model.addAttribute("msg", msg);
        model.addAttribute("lang", modelAttributes.lang());
        model.addAttribute("csrf", csrfToken);
        // canEdit энди соҳага-сезгир (Arbitr-092) - хато берган URL'нинг
        // соҳаси бўйича ҳисобланади, request шунга узатилади
        model.addAttribute("canEdit", modelAttributes.canEdit(auth, request));
        model.addAttribute("isAdmin", modelAttributes.isAdmin(auth));
        model.addAttribute("status", status);
        model.addAttribute("errorTitle", ErrorController.titleFor(status, msg));
        model.addAttribute("errorMessage", message);
        if (Htmx.isHtmx(request)) {
            response.setHeader("X-Averpo-Error", "1");
            response.setHeader("HX-Reswap", "afterbegin");
            return "shared/errorAlert";
        }
        return "shared/error";
    }
}
