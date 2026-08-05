package com.averpo.erp.payroll.web;

import com.averpo.erp.payroll.service.PayrollRegisterService;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.CompanySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Ойлик ведомость экрани (payroll.md 23в): /reports/payroll-register?period=
 * - ходим кесимида давр ҳаракати (боши/охири clearing қолдиқ, ҳисобланган,
 * тўланган). Ҳисобот - фақат ўқиш; мантиқ PayrollRegisterService'да.
 * Ҳамма суммалар home валютада (сарлавҳада «Барча суммалар UZS да»).
 */
@Controller
@RequiredArgsConstructor
public class PayrollRegisterController {

    /** Ведомостнинг ягона public API'си. */
    private final PayrollRegisterService registerService;

    /** Home currency - сарлавҳа изоҳи учун. */
    private final CompanySettingsService settingsService;

    /** Flash/хато хабарлар учун i18n. */
    private final com.averpo.erp.i18n.Msg msg;

    /**
     * Ведомость - берилган давр (period «YYYY-MM», бўш - жорий ой).
     * Нотўғри форматли period (BR-PYR-004) - хато кўрсатилиб жорий ойга
     * тушади (ҳисобот tampered param'да 500 бермайди).
     */
    @GetMapping("/reports/payroll-register")
    public String register(@RequestParam(required = false) String period, Model model) {
        try {
            model.addAttribute("register", registerService.build(period));
        } catch (BusinessRuleException e) {
            model.addAttribute("error", e.displayMessage());
            model.addAttribute("register", registerService.build(null));
        }
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        // Print сарлавҳаси учун (Nargiza-047, statement нақши)
        model.addAttribute("companyName", settingsService.get().getName());
        return "payroll/payrollRegister";
    }
}
