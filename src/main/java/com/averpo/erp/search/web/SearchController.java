package com.averpo.erp.search.web;

import com.averpo.erp.search.service.GlobalSearchService;
import com.averpo.erp.search.service.SearchResults;
import com.averpo.erp.shared.web.Htmx;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Глобал қидирув web-қатлами (docs/modules/global-search.md). Битта
 * {@code GET /search}: HTMX сўровида фақат dropdown partial'ни, оддий
 * (JS'сиз) сўровда тўлиқ саҳифани render қилади - мажбурий fallback
 * (input Enter'да форма шу манзилга юборилади). Контроллер юпқа:
 * роль аниқлаш + service чақириқ + view танлаш.
 */
@Controller
@RequiredArgsConstructor
public class SearchController {

    /** Қидирув service (барча манба + экран реестри). */
    private final GlobalSearchService searchService;

    /**
     * Қидирув натижаси.
     *
     * @param q       сўров (бўш/қисқа бўлса service бўш натижа қайтаради)
     * @param auth    жорий фойдаланувчи - экран реестри роль филтри учун
     * @param request HTMX header'ини текшириш учун (partial ёки тўлиқ саҳифа)
     */
    @GetMapping("/search")
    public String search(@RequestParam(name = "q", required = false) String q,
                         Authentication auth, HttpServletRequest request, Model model) {
        SearchResults results = searchService.search(q, isAdmin(auth));
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("results", results);
        return Htmx.isHtmx(request) ? "search/results" : "search/page";
    }

    /**
     * Фойдаланувчи SUPER_ADMIN'ми - Созламалар экранлари реестрда фақат
     * унга чиқади (user-roles.md: SETTINGS/USERS соҳаси фақат SUPER_ADMIN,
     * сайдбардаги isAdmin филтри билан бир хил семантика).
     */
    private boolean isAdmin(Authentication auth) {
        if (auth == null) {
            return false;
        }
        for (var authority : auth.getAuthorities()) {
            if ("ROLE_SUPER_ADMIN".equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
