package com.averpo.erp.shared.web;

import com.averpo.erp.i18n.Msg;
import com.averpo.erp.shared.service.PaymentMethodService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Тўлов усуллари каталог экрани (Arbitr-033, TaxRate экрани нақшида):
 * рўйхат + inline қўшиш/таҳрир. Йўл /settings остида - SETTINGS (SUPER_ADMIN) чекловига
 * автоматик тушади (SecurityConfig).
 *
 * <p>BR кодисиз валидация (арбитр кўлами): бўш ном шу ерда flash билан
 * қайтарилади (формада required бор - бу tampered POST ҳимояси), ном
 * дубли DB unique'дан DataIntegrityViolation бўлиб келади ва тушунарли
 * хабарга айлантирилади.
 *
 * @author Zafar
 */
@Controller
@RequestMapping("/settings/payment-methods")
@RequiredArgsConstructor
public class PaymentMethodController {

    /** Усуллар service. */
    private final PaymentMethodService paymentMethodService;

    /** Flash хабарлар учун i18n. */
    private final Msg msg;

    /** Рўйхат + қўшиш формаси. */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("methods", paymentMethodService.all());
        return "shared/paymentMethods";
    }

    /** Янги усул. */
    @PostMapping
    public String create(@RequestParam String name, RedirectAttributes redirect) {
        if (name == null || name.isBlank()) {
            redirect.addFlashAttribute("error", msg.get("pm.nameRequired"));
            return "redirect:/settings/payment-methods";
        }
        try {
            paymentMethodService.create(name);
            redirect.addFlashAttribute("message", msg.get("pm.created", name.strip()));
        } catch (DataIntegrityViolationException e) {
            redirect.addFlashAttribute("error", msg.get("pm.nameTaken", name.strip()));
        }
        return "redirect:/settings/payment-methods";
    }

    /** Усулни таҳрирлаш (ном/фаоллик). */
    @PostMapping("/{id}")
    public String update(@PathVariable UUID id, @RequestParam String name,
                         @RequestParam(defaultValue = "false") boolean active,
                         RedirectAttributes redirect) {
        if (name == null || name.isBlank()) {
            redirect.addFlashAttribute("error", msg.get("pm.nameRequired"));
            return "redirect:/settings/payment-methods";
        }
        try {
            paymentMethodService.update(id, name, active);
            redirect.addFlashAttribute("message", msg.get("pm.updated", name.strip()));
        } catch (DataIntegrityViolationException e) {
            redirect.addFlashAttribute("error", msg.get("pm.nameTaken", name.strip()));
        }
        return "redirect:/settings/payment-methods";
    }
}
