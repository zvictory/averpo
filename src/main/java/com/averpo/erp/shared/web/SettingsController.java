package com.averpo.erp.shared.web;

import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.service.CurrencyService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

/**
 * Компания созламалари экрани: ном, home currency, timezone.
 * Контроллер юпқа - валидация ва қулф мантиқи CompanySettingsService'да.
 */
@Controller
@RequestMapping("/settings")
@lombok.RequiredArgsConstructor
public class SettingsController {

    /**
     * Timezone select учун қисқа рўйхат - тўлиқ IANA рўйхати (~600 та)
     * фойдаланувчини чалғитади. Жорий қиймат рўйхатда бўлмаса қўшилади.
     */
    private static final List<String> COMMON_TIMEZONES = List.of(
            "Asia/Tashkent", "Asia/Samarkand", "Asia/Almaty", "Asia/Bishkek",
            "Asia/Dushanbe", "Asia/Ashgabat", "Europe/Moscow", "Europe/Istanbul",
            "Asia/Dubai", "Europe/Berlin", "Europe/London", "America/New_York");

    /** Созламалар service - ягона ёзиш нуқтаси. */
    private final CompanySettingsService settingsService;

    /** Home currency select учун валюта каталоги. */
    private final CurrencyService currencyService;

    /** Flash хабарлар учун i18n. */
    private final com.averpo.erp.i18n.Msg msg;

    /**
     * Build версия маълумоти - settings саҳифаси тагидаги диагностика
     * қатори учун (DEC-104). {@link ObjectProvider} чунки {@link
     * BuildProperties} bean тест ва оддий classpath'да БЎЛМАЙДИ
     * (bootBuildInfo фақат bootJar/bootRun'да build-info.properties
     * яратади) - bean йўқлигида саҳифа синмаслиги шарт.
     */
    private final ObjectProvider<BuildProperties> buildProperties;

    /**
     * Созламалар формасини кўрсатади. {@code setup=1} - онбординг ҳолати
     * (DEC-056): login success handler янги ўрнатишда ADMIN'ни шу манзилга
     * олиб келади, экранда хуш келибсиз banner'и чиқади.
     */
    @GetMapping
    public String show(@RequestParam(name = "setup", required = false) boolean setup, Model model) {
        fillModel(model);
        model.addAttribute("setup", setup);
        return "shared/settings";
    }

    /**
     * Форма сақлаш - хато бўлса ўша экранда кўрсатилади.
     *
     * <p>Давр ёпилиш санаси ва payroll ставкалари String сифатида олиниб
     * {@link FormParsers} орқали парсланади (QA-021/DEC-045 банд 5):
     * хом {@code @RequestParam LocalDate/BigDecimal} байндинги бузуқ форматда
     * (пробел, вергул, бузуқ сана) фойдаланувчига хом 400 саҳифа берарди -
     * лойиҳа сиёсати эса кириллча BR хабари + форма қиймати сақланиши.
     * Барча инпут service чақиришдан ОЛДИН парсланади: биттаси бузуқ бўлса
     * ҳеч нарса қисман сақланмайди (update() ва updatePayrollRates() алоҳида tx).
     */
    @PostMapping
    public String save(@RequestParam String name,
                       @RequestParam String homeCurrency,
                       @RequestParam String timezone,
                       @RequestParam(required = false)
                       com.averpo.erp.shared.domain.InventoryValuationMethod inventoryValuation,
                       @RequestParam(required = false) String closingDate,
                       @RequestParam(required = false) Integer fiscalYearStartMonth,
                       @RequestParam(required = false) String incomeTaxRate,
                       @RequestParam(required = false) String pensionRate,
                       @RequestParam(required = false) String socialTaxRate,
                       Model model,
                       RedirectAttributes redirect) {
        try {
            // Ҳамма форма қиймати аввал парсланади - service чақируви фақат
            // барчаси тўғри форматда бўлса бошланади (қисман сақланиш йўқ)
            java.time.LocalDate closing = FormParsers.localDate(closingDate,
                    BusinessRule.BR_SET_006, "Давр ёпилиш санаси");
            java.math.BigDecimal itRate = FormParsers.decimal(incomeTaxRate,
                    BusinessRule.BR_SET_005, "Даромад солиғи ставкаси");
            java.math.BigDecimal pRate = FormParsers.decimal(pensionRate,
                    BusinessRule.BR_SET_005, "Пенсия бадали ставкаси");
            java.math.BigDecimal sRate = FormParsers.decimal(socialTaxRate,
                    BusinessRule.BR_SET_005, "Ижтимоий солиқ ставкаси");
            settingsService.update(name.strip(), homeCurrency, timezone,
                    inventoryValuation, closing, fiscalYearStartMonth);
            // Payroll ставкалари (payroll.md) - шу форманинг қисми, ADMIN
            settingsService.updatePayrollRates(itRate, pRate, sRate);
        } catch (BusinessRuleException e) {
            fillModel(model);
            model.addAttribute("error", e.displayMessage());
            return "shared/settings";
        }
        redirect.addFlashAttribute("message", msg.get("settings.saved"));
        return "redirect:/settings";
    }

    /**
     * Давр ёпилиш санасининг АЛОҲИДА саҳифаси (user-roles.md
     * PERIOD_CLOSE): CHIEF_ACCOUNTANT'да SETTINGS рухсати йўқ - тўлиқ
     * /settings формасига кира олмайди, лекин даврни очиб-ёпиш унинг
     * иши. URL қоидаси SecurityConfig'да /settings/** дан ОЛДИН туради.
     */
    @GetMapping("/closing-date")
    public String closingDateForm(Model model) {
        model.addAttribute("closingDate", settingsService.closingDate());
        return "shared/closingDate";
    }

    /**
     * Давр ёпилиш санасини сақлайди - бўш қиймат қулфни олади (null).
     * {@code @PreAuthorize} - иккинчи қатлам гарови (user-roles.md:
     * URL матчеридан ташқари метод даражасида ҳам PERIOD_CLOSE).
     */
    @PostMapping("/closing-date")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('PERIOD_CLOSE')")
    public String saveClosingDate(@RequestParam(required = false) String closingDate,
                                  Model model, RedirectAttributes redirect) {
        try {
            java.time.LocalDate closing = FormParsers.localDate(closingDate,
                    BusinessRule.BR_SET_006, "Давр ёпилиш санаси");
            settingsService.changeClosingDate(closing);
        } catch (BusinessRuleException e) {
            model.addAttribute("closingDate", settingsService.closingDate());
            model.addAttribute("error", e.displayMessage());
            return "shared/closingDate";
        }
        redirect.addFlashAttribute("message", msg.get("closing.saved"));
        return "redirect:/settings/closing-date";
    }

    /** Форма учун умумий model атрибутлари. */
    private void fillModel(Model model) {
        var settings = settingsService.get();
        List<String> timezones = new ArrayList<>(COMMON_TIMEZONES);
        if (!timezones.contains(settings.getTimezone())) {
            timezones.add(0, settings.getTimezone());
        }
        model.addAttribute("settings", settings);
        // DEC-056 банд 6: home currency ТЎЛИҚ каталогдан танланади
        // (нофаоллар ҳам) - танланса CompanySettingsService.update() уни
        // автоматик активлаштиради. Ҳужжат формалари эса active()'дагина қолади.
        model.addAttribute("currencies", currencyService.all());
        model.addAttribute("timezones", timezones);
        model.addAttribute("currencyLocked", settingsService.homeCurrencyLocked());
        model.addAttribute("valuationLocked", settingsService.valuationLocked());
        // DEC-104: build диагностикаси - bean мавжуд бўлса (jar/bootRun)
        // версия/вақт/hash саҳифа тагидаги хира қаторга чиқади; тест ва
        // build-info'сиз classpath'да bean йўқ - атрибутлар қўшилмайди,
        // шаблон @if(buildVersion != null) билан ўтказиб юборади. Вақт
        // компания timezone'ида (темир қоида №12, Fmt.dt).
        BuildProperties build = buildProperties.getIfAvailable();
        if (build != null) {
            model.addAttribute("buildVersion", build.getVersion());
            model.addAttribute("buildTime", Fmt.dt(build.getTime(), settingsService.zoneId()));
            model.addAttribute("buildCommit", build.get("commit"));
        }
    }
}
