package com.averpo.erp.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RequestLogFilter} MDC хулқи тести (logging.md «Тестлар»
 * 1-банд, Arbitr-099): rid ва user сўров ДАВОМИДА MDC'да туради,
 * сўров ОХИРИДА тозаланади (thread pool'да leak бўлмасин). Web контекст
 * шарт эмас - filter'ни тўғридан-тўғри юритамиз.
 *
 * @author Zafar
 */
class RequestLogFilterTest {

    private final RequestLogFilter filter = new RequestLogFilter();

    /** Ҳар тестдан кейин контекст/MDC излари кетсин. */
    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void setsRidAndUser_duringRequest_clearsAfter() throws Exception {
        // Кирган фойдаланувчи симуляцияси
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("logtest", "n/a",
                        AuthorityUtils.NO_AUTHORITIES));

        String[] duringRid = new String[1];
        String[] duringUser = new String[1];
        FilterChain chain = (req, res) -> {
            // Сўров ичида MDC тўлдирилган бўлиши шарт
            duringRid[0] = MDC.get(RequestLogFilter.MDC_RID);
            duringUser[0] = MDC.get(RequestLogFilter.MDC_USER);
        };

        filter.doFilter(new MockHttpServletRequest("POST", "/invoices"),
                new MockHttpServletResponse(), chain);

        assertThat(duringRid[0]).as("rid сўров ичида қўйилган").isNotBlank();
        assertThat(duringRid[0]).as("rid 6 белги").hasSize(6);
        assertThat(duringUser[0]).as("user сўров ичида").isEqualTo("logtest");

        // Сўров тугагач иккови ҲАМ тозаланган (MDC leak тақиқи)
        assertThat(MDC.get(RequestLogFilter.MDC_RID)).as("rid тозаланди").isNull();
        assertThat(MDC.get(RequestLogFilter.MDC_USER)).as("user тозаланди").isNull();
    }

    @Test
    void anonymous_leavesUserEmpty() throws Exception {
        // Аноним токен - user MDC'га тушмайди (шовқин бўлмасин)
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        String[] duringUser = new String[1];
        boolean[] hadRid = new boolean[1];
        FilterChain chain = (req, res) -> {
            duringUser[0] = MDC.get(RequestLogFilter.MDC_USER);
            hadRid[0] = MDC.get(RequestLogFilter.MDC_RID) != null;
        };

        filter.doFilter(new MockHttpServletRequest("GET", "/login"),
                new MockHttpServletResponse(), chain);

        assertThat(hadRid[0]).as("rid ҳар доим қўйилади").isTrue();
        assertThat(duringUser[0]).as("аноним - user бўш").isNull();
    }

    @Test
    void mdcCleared_evenWhenChainThrows() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("logtest", "n/a",
                        AuthorityUtils.NO_AUTHORITIES));
        FilterChain boom = (req, res) -> {
            throw new java.io.IOException("тест хатоси");
        };

        try {
            filter.doFilter(new MockHttpServletRequest("POST", "/bills"),
                    new MockHttpServletResponse(), boom);
        } catch (java.io.IOException expected) {
            // finally блоки барибир MDC'ни тозалаши шарт
        }

        assertThat(MDC.get(RequestLogFilter.MDC_RID)).isNull();
        assertThat(MDC.get(RequestLogFilter.MDC_USER)).isNull();
    }
}
