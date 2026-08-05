package com.averpo.erp.security.web;

import com.averpo.erp.security.config.AdminUserInitializer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Login саҳифаси. POST /login'ни Spring Security'нинг ўзи қабул
 * қилади - бу контроллер фақат форманы кўрсатади.
 *
 * @author Zafar
 */
@Controller
@RequiredArgsConstructor
public class LoginController {

    /** Профиль текшируви - autofill фақат dev профилида ёқилади. */
    private final Environment environment;

    /** Admin пароли env'дан берилган бўлса default'га мос келмайди -
     * autofill ўчади (нотўғри парол тўлдириб қўймаслик учун). */
    @Value("${AVERPO_ADMIN_PASSWORD:}")
    private String adminPassword;

    /**
     * Login формаси; error/locked/logout/expired query параметрлари ҳолат
     * хабарини белгилайди. {@code ?expired} - Arbitr-096: сессия муддати
     * тугаб CSRF токени эскирганда accessDeniedHandler шу манзилга буради.
     */
    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error,
                        @RequestParam(required = false) String locked,
                        @RequestParam(required = false) String logout,
                        @RequestParam(required = false) String expired,
                        Model model) {
        model.addAttribute("loginError", error != null);
        model.addAttribute("loginLocked", locked != null);
        model.addAttribute("loggedOut", logout != null);
        model.addAttribute("sessionExpired", expired != null);
        if (devAutofill()) {
            model.addAttribute("devUsername", AdminUserInitializer.ADMIN_USERNAME);
            model.addAttribute("devPassword", AdminUserInitializer.DEV_DEFAULT_PASSWORD);
        }
        return "security/login";
    }

    /**
     * Локал қулайлик: dev профилида (ва фақат default парол ишлаётганда)
     * логин/парол формада тайёр туради - фойдаланувчи фақат «Кириш»ни
     * босади. Бошқа ҳар қандай муҳитда парол HTML'га ҳеч қачон чиқмайди.
     */
    private boolean devAutofill() {
        return environment.acceptsProfiles(Profiles.of("dev"))
                && (adminPassword == null || adminPassword.isBlank());
    }
}
