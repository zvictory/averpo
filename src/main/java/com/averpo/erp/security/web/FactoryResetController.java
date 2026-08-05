package com.averpo.erp.security.web;

import com.averpo.erp.i18n.Msg;
import com.averpo.erp.security.repo.AppUserRepository;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.exception.NotFoundException;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.service.FactoryResetService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Заводга қайтариш оқими (factory-reset.md): /settings «Хавфли ҳудуд»дан
 * бошланадиган икки босқичли danger оқими. Фақат SUPER_ADMIN
 * (/settings/** = SETTINGS соҳаси, фақат SUPER_ADMIN - қолганларга 403), ҳар босқич
 * POST + CSRF.
 *
 * <p>Нима учун security модулида: (1) жорий admin паролини
 * {@link AuthenticationManager} орқали қайта текширади (BR-RST-001);
 * (2) reset қоладиган ягона фойдаланувчи id'сини ўз
 * {@link AppUserRepository}'сидан олади. Тозалашнинг ўзи shared'даги
 * {@link FactoryResetService}'да (модул чегараси сақланади).
 *
 * <p><b>Пароль HTML'да сақланмайди:</b> 1-босқичда текширилган пароль
 * 2-босқичга hidden майдон орқали ЎТКАЗИЛМАЙДИ - session маркери қўйилади
 * ({@link #VERIFIED_ATTR}). Якуний confirm шу маркерни ва компания номи
 * тасдиғини (BR-RST-002) текшириб reset'ни бажаради.
 */
@Controller
@RequiredArgsConstructor
public class FactoryResetController {

    /** Reset бажарилганини серверда ким/қачон билан ёзиш учун (spec). */
    private static final Logger log = LoggerFactory.getLogger(FactoryResetController.class);

    /**
     * Session маркери: пароль 1-босқичда текширилдими. Пароль қийматининг
     * ўзи эмас - фақат «текширилди» байроғи (пароль HTML/сессияда матн
     * сифатида ётмайди).
     */
    private static final String VERIFIED_ATTR = "factoryReset.passwordVerified";

    /** Жорий admin паролини қайта текшириш (BR-RST-001). */
    private final AuthenticationManager authenticationManager;

    /** Reset қоладиган ягона фойдаланувчи id'сини олиш - ўз модулимиз. */
    private final AppUserRepository userRepository;

    /** Тозалашнинг ўзи (shared) - модул чегараси. */
    private final FactoryResetService resetService;

    /** Компания номи (тасдиқ матни) ва reset'дан кейинги lazy default учун. */
    private final CompanySettingsService settingsService;

    /** Flash хабар i18n'и. */
    private final Msg msg;

    /**
     * 1-босқич (GET /settings/reset): нима ўчиши огоҳлантирилади + admin
     * пароли сўралади. Ҳар кириш - тоза бошланиш: аввалги session маркери
     * олиб ташланади (эски «текширилди» ҳолати confirm'га сирғалиб
     * ўтмасин).
     */
    @GetMapping("/settings/reset")
    public String step1(HttpSession session, Model model) {
        session.removeAttribute(VERIFIED_ATTR);
        model.addAttribute("companyName", settingsService.get().getName());
        return "security/factory-reset-1";
    }

    /**
     * 1→2 босқич (POST /settings/reset): паролни AuthenticationManager
     * орқали текширади. Нотўғри бўлса BR-RST-001 билан 1-босқичда қолади
     * (ҳеч нарса ўчмайди); тўғри бўлса session маркери қўйилиб 2-босқич
     * кўрсатилади.
     */
    @PostMapping("/settings/reset")
    public String verifyPassword(@RequestParam(required = false) String password,
                                 Authentication authentication,
                                 HttpSession session, Model model) {
        String username = authentication.getName();
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
        } catch (AuthenticationException e) {
            // BR-RST-001: нотўғри парол - 1-босқичда қоламиз, reset бошланмайди
            model.addAttribute("companyName", settingsService.get().getName());
            model.addAttribute("error", new BusinessRuleException(
                    BusinessRule.BR_RST_001, "Жорий admin пароли нотўғри").displayMessage());
            return "security/factory-reset-1";
        }
        session.setAttribute(VERIFIED_ATTR, Boolean.TRUE);
        model.addAttribute("companyName", settingsService.get().getName());
        return "security/factory-reset-2";
    }

    /**
     * Якуний тасдиқ (POST /settings/reset/confirm): session маркери
     * (пароль текширилган) + компания номи тасдиғи (BR-RST-002) тўғри
     * бўлса reset бажарилади ва /settings?setup=1 га йўналтирилади
     * (онбординг 056 табиий қайта бошланади).
     */
    @PostMapping("/settings/reset/confirm")
    // 2-қатлам гарови (user-roles.md): URL қоидасидан ташқари метод
    // даражасида ҳам SETTINGS EDIT - reset энг хавфли амал
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('SETTINGS_EDIT')")
    public String confirm(@RequestParam(required = false) String confirmName,
                          Authentication authentication, HttpSession session,
                          Model model, RedirectAttributes redirect) {
        if (!Boolean.TRUE.equals(session.getAttribute(VERIFIED_ATTR))) {
            // Пароль босқичи ўтмаган (тўғридан-тўғри POST ёки сессия эскирган) -
            // 1-босқичдан қайта бошлаш
            return "redirect:/settings/reset";
        }
        String companyName = settingsService.get().getName();
        if (confirmName == null || !companyName.equals(confirmName.strip())) {
            // BR-RST-002: тасдиқ матни мос эмас - 2-босқичда қоламиз
            model.addAttribute("companyName", companyName);
            model.addAttribute("error", new BusinessRuleException(BusinessRule.BR_RST_002,
                    "Тасдиқ матни компания номига мос эмас").displayMessage());
            return "security/factory-reset-2";
        }
        UUID adminId = userRepository.findByUsername(authentication.getName())
                .map(u -> u.getId())
                .orElseThrow(() -> new NotFoundException(
                        "Жорий admin топилмади: " + authentication.getName()));
        resetService.reset(adminId);
        session.removeAttribute(VERIFIED_ATTR);
        // Ким/қачон - server WARN (spec); вақт лог timestamp'идан
        log.warn("Заводга қайтариш БАЖАРИЛДИ: admin '{}'", authentication.getName());
        redirect.addFlashAttribute("message", msg.get("reset.done"));
        return "redirect:/settings?setup=1";
    }
}
