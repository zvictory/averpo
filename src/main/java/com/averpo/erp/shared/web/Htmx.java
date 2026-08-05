package com.averpo.erp.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.FlashMapManager;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * HTMX сўровлари учун web-қатлам ёрдамчилари (Arbitr-024 drawer оқими).
 * Соф кўрсатиш/навигация воситаси - бизнес мантиқ эмас.
 *
 * <p>Қолип: controller HX-Request header'ига қараб drawer partial ёки
 * тўлиқ саҳифани render қилади - JS'сиз (HTMX'сиз) ҳам ҳамма оқим
 * тўлиқ саҳифа сифатида ишлашда қолади (мажбурий fallback).
 */
public final class Htmx {

    /** Utility класс - instance яратилмайди. */
    private Htmx() { }

    /** Сўров HTMX'данми - drawer partial'и render қилинадими. */
    public static boolean isHtmx(HttpServletRequest request) {
        return "true".equals(request.getHeader("HX-Request"));
    }

    /**
     * HTMX жавобида client-side redirect: HX-Redirect header + flash
     * хабар кейинги GET учун ҚЎЛДА сақланади - HX-Redirect Spring
     * {@code redirect:} оқимидан ўтмагани учун RedirectAttributes
     * ишламас эди. Қайтарилган бўш partial'ни htmx эътиборга олмайди.
     *
     * @return бўш partial view номи - controller шуни return қилади
     */
    public static String redirect(HttpServletRequest request, HttpServletResponse response,
                                  String location, String flashKey, String flashValue) {
        FlashMapManager manager = RequestContextUtils.getFlashMapManager(request);
        if (manager != null) {
            FlashMap flashMap = new FlashMap();
            flashMap.put(flashKey, flashValue);
            flashMap.setTargetRequestPath(location);
            manager.saveOutputFlashMap(flashMap, request, response);
        }
        response.setHeader("HX-Redirect", location);
        return "shared/empty";
    }
}
