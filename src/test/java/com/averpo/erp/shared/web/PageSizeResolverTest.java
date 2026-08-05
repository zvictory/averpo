package com.averpo.erp.shared.web;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PageSizeResolver} тестлари (ARBITR-105): ?size= / cookie /
 * default устуворлиги, whitelist ҳимояси ва cookie ёзилиши. Static
 * метод - Spring контекст керак эмас, {@code MockHttpServlet*} билан
 * тез unit тест.
 */
class PageSizeResolverTest {

    /** ?size= рухсат этилган - қабул қилинади ва cookie'га ёзилади. */
    @Test
    void explicitSize_isAcceptedAndWrittenToCookie() {
        var request = new MockHttpServletRequest();
        request.setParameter("size", "50");
        var response = new MockHttpServletResponse();

        int size = PageSizeResolver.resolve(request, response, "invoices");

        assertThat(size).isEqualTo(50);
        // Cookie ps_invoices=50 (path=/, Lax, httpOnly) ёзилган
        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie).contains("ps_invoices=50")
                .contains("Path=/").contains("SameSite=Lax").contains("HttpOnly");
    }

    /** Параметрсиз - олдинги танлов cookie'дан ўқилади. */
    @Test
    void noParam_readsFromCookie() {
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie("ps_bills", "100"));
        var response = new MockHttpServletResponse();

        int size = PageSizeResolver.resolve(request, response, "bills");

        assertThat(size).isEqualTo(100);
        // Ўзгармаган - cookie қайта ёзилмайди (фақат ?size= ёзади)
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    /** Whitelist'дан ташқари ?size= - жим default 25 (қўлбола/DoS ҳимояси). */
    @Test
    void unknownSizeParam_fallsBackToDefault() {
        var request = new MockHttpServletRequest();
        request.setParameter("size", "999");
        var response = new MockHttpServletResponse();

        int size = PageSizeResolver.resolve(request, response, "invoices");

        assertThat(size).isEqualTo(PageSizeResolver.DEFAULT_SIZE);
        // Бегона қиймат cookie'га ёзилмайди
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    /** Бузуқ cookie қиймати (сон эмас) - default 25 (cookie бузилишига чидамли). */
    @Test
    void corruptCookie_fallsBackToDefault() {
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie("ps_invoices", "abc"));
        var response = new MockHttpServletResponse();

        int size = PageSizeResolver.resolve(request, response, "invoices");

        assertThat(size).isEqualTo(PageSizeResolver.DEFAULT_SIZE);
    }

    /** Whitelist'дан ташқари cookie қиймати (масалан эски 30) - default 25. */
    @Test
    void nonWhitelistedCookie_fallsBackToDefault() {
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie("ps_invoices", "30"));
        var response = new MockHttpServletResponse();

        int size = PageSizeResolver.resolve(request, response, "invoices");

        assertThat(size).isEqualTo(PageSizeResolver.DEFAULT_SIZE);
    }

    /** Параметр ҳам, cookie ҳам йўқ - default 25. */
    @Test
    void nothing_returnsDefault() {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        int size = PageSizeResolver.resolve(request, response, "invoices");

        assertThat(size).isEqualTo(PageSizeResolver.DEFAULT_SIZE);
    }

    /** ?size= cookie'дан устун (фойдаланувчи ҳозир бошқасини танлади). */
    @Test
    void paramWinsOverCookie() {
        var request = new MockHttpServletRequest();
        request.setParameter("size", "200");
        request.setCookies(new Cookie("ps_invoices", "50"));
        var response = new MockHttpServletResponse();

        int size = PageSizeResolver.resolve(request, response, "invoices");

        assertThat(size).isEqualTo(200);
        assertThat(response.getHeader("Set-Cookie")).contains("ps_invoices=200");
    }

    /** Ҳар рўйхат ўз cookie калити (ps_<key>) - бошқасиникини ўқимайди. */
    @Test
    void cookieKeyIsPerList() {
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie("ps_bills", "100"));
        var response = new MockHttpServletResponse();

        // invoices сўралди, лекин cookie bills'ники - invoices default олади
        int size = PageSizeResolver.resolve(request, response, "invoices");

        assertThat(size).isEqualTo(PageSizeResolver.DEFAULT_SIZE);
    }
}
