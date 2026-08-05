package com.averpo.erp.web;

import com.averpo.erp.i18n.Msg;
import com.averpo.erp.security.domain.AppUser;
import com.averpo.erp.security.service.UserService;
import com.averpo.erp.shared.domain.CompanySettings;
import com.averpo.erp.shared.service.CompanySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Ҳар бир view model'ига автоматик қўшиладиган атрибутлар -
 * контроллерларда такрор-такрор қўшиб юрмаслик учун.
 *
 * @author Zafar
 */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAttributes {

    /** i18n кўприги - шаблонлар msg номи билан олади. */
    private final Msg msg;

    /** Топбар бренди (company name + brand logo) учун - shared public service. */
    private final CompanySettingsService settingsService;

    /** Sidebar аватар/ном ва mustChange banner учун жорий user (public service). */
    private final UserService userService;

    /** Плагин гейти (Arbitr-113) - request бошида ёқиқ тўплам бир марта ўқилади. */
    private final com.averpo.erp.plugins.core.service.PluginService pluginService;

    /** Ҳамма шаблонга i18n ёрдамчиси. */
    @ModelAttribute("msg")
    public Msg msg() {
        return msg;
    }

    /** Тил алмаштиргичда жорий тилни белгилаш учун (uz/ru/en). */
    @ModelAttribute("lang")
    public String lang() {
        return LocaleContextHolder.getLocale().getLanguage();
    }

    /**
     * CSRF токени - ҳар POST формага hidden input сифатида қўйилади.
     * Spring Security deferred token'ни request атрибутида беради;
     * шаблон getToken() чақирганда ҳал бўлади.
     */
    @ModelAttribute("csrf")
    public org.springframework.security.web.csrf.CsrfToken csrf(
            jakarta.servlet.http.HttpServletRequest request) {
        return (org.springframework.security.web.csrf.CsrfToken)
                request.getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());
    }

    /**
     * Фойдаланувчи ЖОРИЙ САҲИФА соҳасида ёза оладими (Arbitr-092:
     * соҳага-сезгир) - request path {@link UrlPermissionMap} орқали
     * соҳага айланади (SecurityConfig билан ЯГОНА харита - UI тугмаси
     * билан server ҳақиқати ажралмайди), кейин фойдаланувчининг ўша
     * соҳадаги EDIT authority'си текширилади. 102 JTE'даги canEdit
     * параметри шу туфайли ЎЗГАРМАЙ қолди - ҳар экран ўз соҳасига
     * қараб тўғри тугма кўрсатади. Соҳасиз саҳифада (дашборд, қидирув,
     * профиль, error) - камида битта соҳада EDIT бўлса true (эски
     * «VIEWER'дан бошқа ҳамма» хулқининг умумлашмаси). Ҳақиқий ҳимоя
     * SecurityConfig'да, бу фақат кўриниш.
     */
    @ModelAttribute("canEdit")
    public Boolean canEdit(org.springframework.security.core.Authentication auth,
                           jakarta.servlet.http.HttpServletRequest request) {
        if (auth == null) {
            return false;
        }
        return com.averpo.erp.security.config.UrlPermissionMap
                .areaOf(request.getRequestURI())
                .map(area -> hasAnyAuthority(auth,
                        com.averpo.erp.security.domain.RolePermissions.editAuthority(area)))
                .orElseGet(() -> hasAnyAuthority(auth,
                        com.averpo.erp.security.domain.RolePermissions.allEditAuthorities()));
    }

    /**
     * SUPER_ADMIN'ми - sidebar'даги Созламалар бўлими (settings/users/
     * audit-log) фақат унга кўринади (матрицада SETTINGS/USERS фақат
     * SUPER_ADMIN'да). Эски isAdmin=ROLE_ADMIN текшируви айнан шу
     * семантикага кўчди - 83 JTE'даги isAdmin параметри ўзгармаган.
     */
    @ModelAttribute("isAdmin")
    public Boolean isAdmin(org.springframework.security.core.Authentication auth) {
        return hasAnyAuthority(auth, "ROLE_SUPER_ADMIN");
    }

    /**
     * Layout контексти (Arbitr-101/112 рефайнмент) - {@link LayoutInfo}
     * request attribute'га: main.jte топбар бренди (company name + brand
     * logo), sidebar аватар/ном, mustChange banner. main.jte/sidebar.jte
     * СТАТИК {@code LayoutInfo.current()} орқали ўқийди (Perms нақши - JTE
     * layout параметрини кўпайтирмасдан, зона тақиғи). Void метод: model'га
     * эмас, request attribute'га ёзади (layout шаблони param эмас, static
     * олади). Auth йўқ (login) ёки user топилмаса - брендли, лекин
     * user'сиз LayoutInfo.
     */
    @ModelAttribute
    public void layout(jakarta.servlet.http.HttpServletRequest request,
                       org.springframework.security.core.Authentication auth) {
        CompanySettings settings = settingsService.get();
        String companyName = settings.getName();
        java.util.UUID brandLogoId = settings.getBrandLogoAttachmentId();
        // Онбординг тугаганми (Arbitr-142): sidebar тепа company name'ни
        // фақат setup'дан кейин кўрсатади - тугамаган ҳолда default
        // «Компания» placeholder ўрнига AVERPO fallback чиқади
        boolean setupDone = settings.isSetupDone();
        if (auth != null && auth.getName() != null) {
            AppUser user = userService.findByUsername(auth.getName()).orElse(null);
            if (user != null) {
                request.setAttribute(LayoutInfo.ATTR, new LayoutInfo(
                        companyName, brandLogoId, setupDone, user.isMustChangePassword(),
                        user.getDisplayName(), user.getUsername(), user.getProfileImageId()));
                return;
            }
        }
        request.setAttribute(LayoutInfo.ATTR, new LayoutInfo(
                companyName, brandLogoId, setupDone, false, null, null, null));
    }

    /**
     * Плагин гейти (Arbitr-113) - {@link Plugins} request attribute'га:
     * JTE меню/созлама бандлари СТАТИК {@code Plugins.current()} орқали
     * ўқийди (LayoutInfo нақши - layout параметрини кўпайтирмасдан).
     * Битта request = битта {@code enabledKeys()} сўрови; server ҳимояси
     * бу эмас - route/фича гарови PluginService.isEnabled'да.
     */
    @ModelAttribute
    public void plugins(jakarta.servlet.http.HttpServletRequest request) {
        request.setAttribute(Plugins.ATTR, new Plugins(pluginService.enabledKeys()));
    }

    /** Authority текшируви ёрдамчиси (берилганлардан камида биттаси). */
    private boolean hasAnyAuthority(org.springframework.security.core.Authentication auth,
                                    String... authorities) {
        if (auth == null) {
            return false;
        }
        for (var granted : auth.getAuthorities()) {
            for (String authority : authorities) {
                if (authority.equals(granted.getAuthority())) {
                    return true;
                }
            }
        }
        return false;
    }
}
