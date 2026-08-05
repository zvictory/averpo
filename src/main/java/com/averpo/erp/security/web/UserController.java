package com.averpo.erp.security.web;

import com.averpo.erp.i18n.Msg;
import com.averpo.erp.security.domain.AppUser;
import com.averpo.erp.security.domain.UserRole;
import com.averpo.erp.security.service.UserService;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.web.Fmt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Фойдаланувчилар бошқаруви экранлари (user-management.md): рўйхат,
 * яратиш/таҳрир, admin томонидан парол алмаштириш. Йўллар SecurityConfig
 * билан USERS соҳасига (SUPER_ADMIN) чекланган; мантиқ тўлиқ UserService'да - контроллер
 * юпқа (validation + service + view).
 */
@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    /** Фойдаланувчилар public API'си. */
    private final UserService userService;

    /** locked_until'ни компания минтақасида кўрсатиш учун (қоида №12). */
    private final CompanySettingsService settingsService;

    /** Flash хабарлар учун i18n. */
    private final Msg msg;

    /** Рўйхат - username тартибида, қулф ҳолати билан. */
    @GetMapping
    public String list(Model model) {
        var users = userService.all();
        // Қулфланганлар учун «HH:mm гача» матни олдиндан тайёрланади -
        // шаблонда Instant таққослаш/формат мантиқи бўлмасин
        Map<UUID, String> lockedTexts = new HashMap<>();
        Instant now = Instant.now();
        for (AppUser user : users) {
            if (user.lockedAt(now)) {
                lockedTexts.put(user.getId(),
                        Fmt.dt(user.getLockedUntil(), settingsService.zoneId()));
            }
        }
        model.addAttribute("users", users);
        model.addAttribute("lockedTexts", lockedTexts);
        return "security/users";
    }

    /** Янги фойдаланувчи формаси. */
    @GetMapping("/new")
    public String createForm(Model model) {
        UserForm form = new UserForm();
        // Default роль - энг кам ҳуқуқли (DEC-092): select'нинг биринчи
        // банди SUPER_ADMIN, танлов унутилса тасодифан тўлиқ ҳуқуқ
        // берилмасин (хавфсиз томонга default)
        form.setRole(UserRole.VIEWER_AUDITOR.name());
        model.addAttribute("form", form);
        model.addAttribute("isNew", true);
        return "security/userForm";
    }

    /** Яратиш - парол такрори server'да ҳам текширилади (spec). */
    @PostMapping
    public String create(@ModelAttribute UserForm form,
                         Model model, RedirectAttributes redirect) {
        if (!Objects.equals(form.getPassword(), form.getPasswordConfirm())) {
            model.addAttribute("form", form);
            model.addAttribute("isNew", true);
            model.addAttribute("error", msg.get("user.form.passwordMismatch"));
            return "security/userForm";
        }
        try {
            AppUser user = userService.create(form.getUsername(), form.getDisplayName(),
                    parseRoleSafe(form.getRole()), form.getPassword());
            redirect.addFlashAttribute("message",
                    msg.get("user.created", user.getUsername()));
            return "redirect:/users";
        } catch (BusinessRuleException e) {
            model.addAttribute("form", form);
            model.addAttribute("isNew", true);
            model.addAttribute("error", e.displayMessage());
            return "security/userForm";
        }
    }

    /**
     * Таҳрир формаси - username read-only, парол алоҳида картада. Ходим
     * танлагичи учун фаол EMPLOYEE контактлар рўйхати (DEC-101
     * 4-бўлим) - фақат таҳрирда (super-admin боғлайди).
     */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        model.addAttribute("form", UserForm.from(userService.get(id)));
        model.addAttribute("isNew", false);
        model.addAttribute("employees", userService.employeeContactRefs());
        return "security/userForm";
    }

    /** Таҳрирни сақлаш - username service'да ўзгармаганлигини текширади. */
    @PostMapping("/{id}")
    public String update(@PathVariable UUID id, @ModelAttribute UserForm form,
                         Model model, RedirectAttributes redirect) {
        try {
            AppUser user = userService.update(id, form.getUsername(),
                    form.getDisplayName(), parseRoleSafe(form.getRole()),
                    form.isActive(), parseUuidSafe(form.getEmployeeContactId()),
                    form.getEmail());
            redirect.addFlashAttribute("message",
                    msg.get("user.updated", user.getUsername()));
            return "redirect:/users";
        } catch (BusinessRuleException e) {
            form.setId(id.toString());
            model.addAttribute("form", form);
            model.addAttribute("isNew", false);
            // Хатода ҳам ходим танлагичи тўлсин (форма қайта рендер бўлади)
            model.addAttribute("employees", userService.employeeContactRefs());
            model.addAttribute("error", e.displayMessage());
            return "security/userForm";
        }
    }

    /** ADMIN томонидан парол алмаштириш (алоҳида карта, эски парол сўралмайди). */
    @PostMapping("/{id}/password")
    public String changePassword(@PathVariable UUID id,
                                 @RequestParam String password,
                                 @RequestParam String passwordConfirm,
                                 RedirectAttributes redirect) {
        if (!Objects.equals(password, passwordConfirm)) {
            redirect.addFlashAttribute("error", msg.get("user.form.passwordMismatch"));
            return "redirect:/users/" + id + "/edit";
        }
        try {
            userService.changePassword(id, password);
            redirect.addFlashAttribute("message", msg.get("user.passwordChanged"));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/users/" + id + "/edit";
    }

    /**
     * Query/форма қийматидан рольни хавфсиз парслайди - бузуқ қиймат
     * null бўлиб service'даги аниқ BR хатосига учрайди (500 эмас).
     */
    private static UserRole parseRoleSafe(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        try {
            return UserRole.valueOf(role);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Форма қийматидан ходим контакт UUID'сини хавфсиз парслайди: бўш -
     * null (уланмаган/узилди), бузуқ UUID (форма tampering) ҳам null -
     * хом 400 бермайди (service EMPLOYEE ва бандликни текширади).
     */
    private static UUID parseUuidSafe(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
