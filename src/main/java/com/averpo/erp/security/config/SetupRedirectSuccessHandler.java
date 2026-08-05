package com.averpo.erp.security.config;

import com.averpo.erp.security.service.UserService;
import com.averpo.erp.shared.service.CompanySettingsService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import java.io.IOException;

/**
 * Кириш муваффақиятли бўлгач онбординг йўналтириши (DEC-056).
 *
 * <p>Янги (бўш) ўрнатишда компания созламалари ҳали тўлдирилмаган -
 * ADMIN'ни бир марта {@code /settings?setup=1} га олиб борамиз (QBO
 * биринчи кириш setup оқими эталони). Фақат ADMIN: home currency /
 * valuation каби қулфланадиган қарорларни фақат у қила олади;
 * ACCOUNTANT/VIEWER созлай олмагани учун йўналтирилмайди - улар одатий
 * оқимга тушади.
 *
 * <p>Онбординг тугаган (setupDone=true) ёки ADMIN бўлмаган ҳолда
 * {@link SavedRequestAwareAuthenticationSuccessHandler} хулқи тўлиқ
 * сақланади: deep-link'дан login'га тушган фойдаланувчи ўша саҳифасига
 * қайтади. Фақат онбординг ҳолатидагина уни устун қиламиз.
 */
public class SetupRedirectSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    /** Онбординг ҳолатини (setupDone) ўқийди - shared модул хизмати. */
    private final CompanySettingsService settingsService;

    /** must_change_password флагини ўқийди (рефайнмент банд 5) - public service. */
    private final UserService userService;

    /** Saved request'ни онбординг ҳолатида тозалаш учун сессия кэши. */
    private final HttpSessionRequestCache requestCache = new HttpSessionRequestCache();

    public SetupRedirectSuccessHandler(CompanySettingsService settingsService,
                                       UserService userService) {
        this.settingsService = settingsService;
        this.userService = userService;
    }

    /**
     * Созламаларга кира оладиган фойдаланувчи (SETTINGS EDIT - амалда
     * SUPER_ADMIN) ва онбординг тугамаган бўлса setup саҳифасига, акс
     * ҳолда одатий (saved request'ни ҳисобга олувчи) оқимга йўналтиради.
     * Роль номи эмас, authority текширилади (user-roles.md) - бошқа
     * роллар /settings'га 403 оларди, уларни йўналтирмаймиз.
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String required = com.averpo.erp.security.domain.RolePermissions
                .editAuthority(com.averpo.erp.security.domain.Permission.SETTINGS);
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> required.equals(a.getAuthority()));
        if (isAdmin && !settingsService.isSetupDone()) {
            // Онбординг устун: saved request'ни тозалаймиз - setup тугагач
            // фойдаланувчи эски deep-link'га сакрамай дашбордда қолсин.
            requestCache.removeRequest(request, response);
            getRedirectStrategy().sendRedirect(request, response, "/settings?setup=1");
            return;
        }
        // Паролни алмаштириш зарур (рефайнмент банд 5, 066): admin қўйган ёки
        // reset'лаган паролдан кейин фойдаланувчини /profile'га олиб борамиз
        // (banner + парол блоки). Онбординг устун (юқорида) - admin аввал
        // созлайди. user ЎЗ паролини алмаштиргач флаг тушади.
        var user = userService.findByUsername(authentication.getName()).orElse(null);
        if (user != null && user.isMustChangePassword()) {
            getRedirectStrategy().sendRedirect(request, response, "/profile");
            return;
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
