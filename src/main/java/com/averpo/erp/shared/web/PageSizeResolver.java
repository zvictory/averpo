package com.averpo.erp.shared.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.util.Set;

/**
 * Саҳифа ҳажми ({@code ?size=}) ечувчи - QBO услубидаги мукаммал
 * пагинациянинг маркази (DEC-105, PERF-perf1 3-босқич). Ҳар
 * рўйхат ўз танловини (25/50/100/200) эслаб қолсин: фойдаланувчи
 * бир марта танлаганда кейинги киришларда ҳам ўша ҳажм кўринади.
 *
 * <p><b>Устуворлик</b>: {@code ?size=} параметри → cookie
 * ({@code ps_<listKey>}) → default {@link #DEFAULT_SIZE}. Параметр
 * келганда cookie ёзилади (шу рўйхат учун эсда қолади). Барча ҳолда
 * қиймат {@link #ALLOWED_SIZES} whitelist'дан бўлиши шарт - акс ҳолда
 * жим 25 га тушади (бузуқ/қўлбола cookie ёки параметр саҳифани
 * синдирмайди; парс try-безиён).
 *
 * <p>Cookie {@link ResponseCookie} орқали ёзилади (Servlet {@code Cookie}
 * SameSite'ни қўлламайди): {@code path=/} (барча рўйхатга), 1 йил,
 * {@code SameSite=Lax} (оддий GET навигацияда юборилади, CSRF юзаси
 * тор), {@code httpOnly} (JS ўқимайди - шунчаки UI ҳолати, XSS сирқиши
 * керак эмас). Stateless static: controller'да битта қатор чақириқ,
 * DI шарт эмас.
 */
public final class PageSizeResolver {

    /** Танлов бўлмаса/бузуқ бўлса саҳифа ҳажми (2-босқич қиймати билан бир хил). */
    public static final int DEFAULT_SIZE = 25;

    /**
     * Рухсат этилган ҳажмлар (QBO услуби). Ташқаридаги ҳар қандай сон
     * рад этилади - фойдаланувчи ихтиёрий OFFSET/LIMIT'ни URL орқали
     * бера олмайди (DoS/скан юзаси йўқ), cookie бузилса ҳам зарарсиз.
     */
    public static final Set<Integer> ALLOWED_SIZES = Set.of(25, 50, 100, 200);

    /** Cookie ном олди: {@code ps_} + рўйхат калити (ps_invoices, ps_bills...). */
    private static final String COOKIE_PREFIX = "ps_";

    private PageSizeResolver() {
    }

    /**
     * Шу рўйхат учун амалдаги саҳифа ҳажмини аниқлайди ва керак бўлса
     * cookie'ни янгилайди.
     *
     * @param listKey рўйхат калити (масалан {@code "invoices"}) - cookie
     *                номи ва бошқа рўйхатлардан ажратиш учун
     * @return whitelist'даги ҳажм ({@link #ALLOWED_SIZES}) ёки
     *         {@link #DEFAULT_SIZE}
     */
    public static int resolve(HttpServletRequest request, HttpServletResponse response,
                              String listKey) {
        // 1) Аниқ ?size= - фойдаланувчи ҳозир танлади: қабул қилиб cookie'га ёзамиз
        String param = request.getParameter("size");
        if (param != null) {
            Integer parsed = tryParse(param);
            if (parsed != null && ALLOWED_SIZES.contains(parsed)) {
                writeCookie(response, listKey, parsed);
                return parsed;
            }
            // Бегона ?size= (бузуқ/қўлбола) - default, cookie'га ёзилмайди
            return DEFAULT_SIZE;
        }
        // 2) Параметрсиз - олдинги танлов cookie'дан (бор ва whitelist'да бўлса)
        Integer fromCookie = readCookie(request, listKey);
        if (fromCookie != null && ALLOWED_SIZES.contains(fromCookie)) {
            return fromCookie;
        }
        // 3) Ҳеч нарса - default
        return DEFAULT_SIZE;
    }

    /** «123» → 123, нотўғри бўлса null (парс ҳеч қачон истисно отмайди). */
    private static Integer tryParse(String raw) {
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** {@code ps_<listKey>} cookie қийматини сон сифатида (йўқ/бузуқ - null). */
    private static Integer readCookie(HttpServletRequest request, String listKey) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String name = COOKIE_PREFIX + listKey;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return tryParse(cookie.getValue());
            }
        }
        return null;
    }

    /** Танланган ҳажмни {@code ps_<listKey>} cookie'га ёзади (1 йил, Lax, httpOnly). */
    private static void writeCookie(HttpServletResponse response, String listKey, int size) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_PREFIX + listKey, String.valueOf(size))
                .path("/")
                .maxAge(Duration.ofDays(365))
                .sameSite("Lax")
                .httpOnly(true)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
