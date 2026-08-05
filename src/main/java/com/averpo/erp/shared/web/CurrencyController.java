package com.averpo.erp.shared.web;

import com.averpo.erp.shared.domain.Currency;
import com.averpo.erp.shared.domain.ExchangeRate;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.exception.NotFoundException;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.service.CurrencyService;
import com.averpo.erp.shared.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Валюталар ва курслар экранлари (QBO Currencies услуби,
 * docs/modules/multi-currency.md). /settings/** остида - фақат SUPER_ADMIN
 * (SecurityConfig); фақат lookup endpoint'и алоҳида йўлда, чунки уни
 * проводка формаси (ACCOUNTANT ҳам) ишлатади.
 *
 * @author Zafar
 */
@Controller
@RequiredArgsConstructor
public class CurrencyController {

    /**
     * Currencies экранидаги битта қатор.
     *
     * @param currency валютанинг ўзи
     * @param latest   энг охирги курс ёзуви ёки null (ҳали киритилмаган)
     * @param home     home валютами - тогл ва курс киритиш ёпилади
     */
    public record CurrencyRow(Currency currency, ExchangeRate latest, boolean home) { }

    /** Валюта каталоги service. */
    private final CurrencyService currencyService;

    /** Курслар service. */
    private final ExchangeRateService exchangeRateService;

    /** Home currency ва timezone учун. */
    private final CompanySettingsService settingsService;

    /** Flash хабарлар учун i18n. */
    private final com.averpo.erp.i18n.Msg msg;

    /** Валюталар рўйхати: фаол тогл, охирги курс, ЦБ импорт тугмаси. */
    @GetMapping("/settings/currencies")
    public String list(Model model) {
        String home = settingsService.homeCurrency();
        // Beruniy-023: ҳар валютага алоҳида latest() N+1 эди - энди
        // ҳамма валютанинг амалдаги курси битта сўровда
        var latest = exchangeRateService.latestForEachCurrency();
        List<CurrencyRow> rows = currencyService.all().stream()
                .map(currency -> new CurrencyRow(currency,
                        latest.get(currency.getCode()),
                        currency.getCode().equals(home)))
                .toList();
        model.addAttribute("rows", rows);
        model.addAttribute("homeCurrency", home);
        model.addAttribute("today", LocalDate.now(settingsService.zoneId()).toString());
        return "shared/currencies";
    }

    /** Валютани фаоллаштириш/нофаол қилиш. */
    @PostMapping("/settings/currencies/{code}/active")
    public String setActive(@PathVariable String code,
                            @RequestParam(defaultValue = "false") boolean active,
                            RedirectAttributes redirect) {
        try {
            Currency currency = currencyService.setActive(code, active);
            redirect.addFlashAttribute("message", msg.get(
                    active ? "currency.activated" : "currency.deactivated",
                    currency.getCode()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/settings/currencies";
    }

    /** ЦБ'дан қўлда импорт - scheduler'ни кутмасдан (сана танланади). */
    @PostMapping("/settings/currencies/import")
    public String importFromCbu(@RequestParam(required = false) LocalDate date,
                                RedirectAttributes redirect) {
        try {
            LocalDate importDate = date != null
                    ? date : LocalDate.now(settingsService.zoneId());
            ExchangeRateService.ImportResult result =
                    exchangeRateService.importFromCbu(importDate);
            // «Янгиланди» ФАҚАТ курс ўзгарганда (Arbitr-168): дам олишда ЦБ
            // жума курсини қайтаради - «текширилди, ўзгармади» деб ҳалол
            // хабар, фойдаланувчи «курс олинмаяпти» деб адашмасин
            String message = result.changed() > 0
                    ? msg.get("currency.imported", result.checked(), result.changed(),
                            result.skipped(), importDate.toString())
                    : msg.get("currency.importedNoChange", result.checked(),
                            result.skipped(), importDate.toString());
            redirect.addFlashAttribute("message", message);
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/settings/currencies";
    }

    /** Битта валютанинг курс тарихи + қўлда киритиш формаси. */
    @GetMapping("/settings/currencies/{code}/rates")
    public String rates(@PathVariable String code, Model model) {
        Currency currency = currencyService.byCode(code)
                .orElseThrow(() -> new NotFoundException("Валюта топилмади: " + code));
        model.addAttribute("currency", currency);
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        model.addAttribute("rates", exchangeRateService.history(currency.getCode()));
        model.addAttribute("today", LocalDate.now(settingsService.zoneId()).toString());
        return "shared/currencyRates";
    }

    /**
     * Курс киритиш - тарихга ЯНГИ MANUAL ёзув қўшилади (append-only,
     * Arbitr-022): эски сақланади, амалда энг охиргиси; айнан бир хил
     * курс такрор келса дубль ёзилмайди. upsert номи тарихий - амалда
     * record(MANUAL) (ExchangeRateService изоҳи).
     */
    @PostMapping("/settings/currencies/{code}/rates")
    public String saveRate(@PathVariable String code,
                           @RequestParam(required = false) LocalDate date,
                           @RequestParam(required = false) String rate,
                           RedirectAttributes redirect) {
        String normalized = code.strip().toUpperCase();
        try {
            exchangeRateService.upsert(normalized, date, parseRate(rate));
            redirect.addFlashAttribute("message",
                    msg.get("currency.rateSaved", normalized, String.valueOf(date)));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/settings/currencies/" + normalized + "/rates";
    }

    /**
     * Форма prefill'лари учун (проводка сатри, opening balance): шу
     * санага амал қиладиган курс ёки бўш сатр. /settings остида ЭМАС -
     * ACCOUNTANT ҳам ишлатади (фақат ўқийди).
     */
    @GetMapping("/exchange-rates/lookup")
    @ResponseBody
    public String lookup(@RequestParam String currency,
                         @RequestParam(required = false) LocalDate date) {
        LocalDate at = date != null ? date : LocalDate.now(settingsService.zoneId());
        return exchangeRateService.rateFor(currency, at)
                .map(rate -> rate.stripTrailingZeros().toPlainString())
                .orElse("");
    }

    /** Курс матни - бўш → null (service рад этади); FormParsers қоидаси. */
    private BigDecimal parseRate(String rate) {
        return FormParsers.decimal(rate, BusinessRule.BR_FX_001, "Курс");
    }
}
