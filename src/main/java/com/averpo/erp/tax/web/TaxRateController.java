package com.averpo.erp.tax.web;

import com.averpo.erp.i18n.Msg;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.web.FormParsers;
import com.averpo.erp.tax.service.TaxRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * ҚҚС ставкалари каталог экрани (docs/modules/tax.md, units паттерни):
 * рўйхат + inline қўшиш/таҳрир. Йўл /settings остида - SETTINGS (SUPER_ADMIN) чекловига
 * автоматик тушади (SecurityConfig); мантиқ TaxRateService'да,
 * контроллер юпқа.
 */
@Controller
@RequestMapping("/settings/tax-rates")
@RequiredArgsConstructor
public class TaxRateController {

    /** Ставкалар service. */
    private final TaxRateService taxRateService;

    /** Flash хабарлар учун i18n. */
    private final Msg msg;

    /** Рўйхат + қўшиш формаси. */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("rates", taxRateService.all());
        return "tax/taxRates";
    }

    /** Янги ставка. */
    @PostMapping
    public String create(@RequestParam String code, @RequestParam String name,
                         @RequestParam String rate, RedirectAttributes redirect) {
        try {
            taxRateService.create(code, name, parseRate(rate));
            redirect.addFlashAttribute("message", msg.get("tax.created", code.strip()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/settings/tax-rates";
    }

    /** Ставкани таҳрирлаш (код/ном/фоиз/фаоллик). */
    @PostMapping("/{id}")
    public String update(@PathVariable UUID id, @RequestParam String code,
                         @RequestParam String name, @RequestParam String rate,
                         @RequestParam(defaultValue = "false") boolean active,
                         RedirectAttributes redirect) {
        try {
            taxRateService.update(id, code, name, parseRate(rate), active);
            redirect.addFlashAttribute("message", msg.get("tax.updated", code.strip()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/settings/tax-rates";
    }

    /** Фоиз парси - бузуқ қиймат 500 эмас, BR-TAX-002 (FormParsers сиёсати). */
    private BigDecimal parseRate(String value) {
        return FormParsers.decimal(value, BusinessRule.BR_TAX_002, "Ставка");
    }
}
