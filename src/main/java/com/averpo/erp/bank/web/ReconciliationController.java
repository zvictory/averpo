package com.averpo.erp.bank.web;

import com.averpo.erp.bank.domain.BankReconciliation;
import com.averpo.erp.bank.service.ReconciliationService;
import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountType;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.web.FormParsers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Reconciliation экранлари - QBO Reconcile флоуси: рўйхат + бошлаш
 * формаси, ишчи экран (сатрларни белгилаш checkbox'лари, жонли фарқ,
 * якунлаш/бекор қилиш). Белгилаш оддий form POST билан - CSRF табиий
 * ҳимояланган, ҳар босишда фарқ серверда қайта ҳисобланади.
 */
@Controller
@RequestMapping("/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    /** Reconciliation'нинг ягона public API'си. */
    private final ReconciliationService reconciliationService;

    /** Банк select'и ва счёт номлари учун. */
    private final AccountService accountService;

    /** Home currency - валютасиз счёт қолдиқлари шу валютада кўрсатилади. */
    private final com.averpo.erp.shared.service.CompanySettingsService settingsService;

    /** Flash хабарлар учун i18n. */
    private final com.averpo.erp.i18n.Msg msg;

    /** Рўйхат + янги reconciliation бошлаш формаси. */
    @GetMapping
    public String list(@org.springframework.web.bind.annotation.RequestParam(
                               required = false, defaultValue = "0") int page,
                       jakarta.servlet.http.HttpServletRequest request,
                       jakarta.servlet.http.HttpServletResponse response,
                       Model model) {
        // DEC-105 3-босқич: саҳифаланган + ҳажм ?size=/cookie'дан
        int size = com.averpo.erp.shared.web.PageSizeResolver.resolve(
                request, response, "reconciliation");
        var reconPage = reconciliationService.list(page, size);
        model.addAttribute("reconciliations", reconPage.getContent());
        model.addAttribute("page", reconPage);
        // OPT-009: счёт каталоги БИР марта олиниб ном/валюта хариталари
        // ва банк select'и шундан ясалади (TransferController нақши) -
        // аввал all() икки марта + postableAccounts() алоҳида кетарди
        java.util.List<Account> accounts = accountService.all();
        String home = settingsService.homeCurrency();
        TransferController.AccountViewMaps maps =
                TransferController.accountViewMaps(accounts, home);
        model.addAttribute("accountNames", maps.names());
        // Қолдиқлар счёт валютасида сақланади (banking.md) - экранда ҳам
        // шу валюта коди билан чиқади (UI-002), акс ҳолда UZS/USD
        // счётлар аралаш рўйхатда рақамлар адаштиради
        model.addAttribute("accountCurrencies", maps.currencies());
        model.addAttribute("homeCurrency", home);
        // postableAccounts() кўзгуси: фаол+postable, CHART_ORDER сақланади
        model.addAttribute("bankAccounts", accounts.stream()
                .filter(a -> a.isActive() && a.isPostable()
                        && a.getType() == AccountType.BANK).toList());
        return "bank/reconciliations";
    }

    /** Янги reconciliation бошлаш. */
    @PostMapping("/start")
    public String start(@RequestParam(required = false) String accountId,
                        @RequestParam(required = false) LocalDate statementDate,
                        @RequestParam(required = false) String closingBalance,
                        @RequestParam(required = false) String openingBalance,
                        RedirectAttributes redirect) {
        try {
            BankReconciliation reconciliation = reconciliationService.start(
                    FormParsers.uuid(accountId, BusinessRule.BR_RCN_001, "Банк счёти"),
                    statementDate,
                    FormParsers.decimal(closingBalance, BusinessRule.BR_RCN_002,
                            "Якуний қолдиқ"),
                    FormParsers.decimal(openingBalance, BusinessRule.BR_RCN_002,
                            "Бошланғич қолдиқ"));
            return "redirect:/reconciliation/" + reconciliation.getId();
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
            return "redirect:/reconciliation";
        }
    }

    /** Ишчи экран: номзод сатрлар + жонли фарқ. */
    @GetMapping("/{id}")
    public String view(@PathVariable UUID id, Model model) {
        // OPT-006: бутун экран битта read-only транзакцияда йиғилади -
        // аввал get/candidates/difference алоҳида чақирилиб, reconciliation
        // уч, счёт икки, match рўйхати икки марта қайта ўқиларди
        ReconciliationService.ReconciliationView view = reconciliationService.view(id);
        model.addAttribute("recon", view.reconciliation());
        model.addAttribute("accountName", view.accountName());
        // Қолдиқ/фарқ/кирим-чиқим суммалари счёт валютасида (UI-002);
        // валютасиз счёт home валютада юритилади
        model.addAttribute("accountCurrency", view.accountCurrency() != null
                ? view.accountCurrency() : settingsService.homeCurrency());
        model.addAttribute("candidates", view.candidates());
        model.addAttribute("difference", view.difference());
        return "bank/reconciliationView";
    }

    /** Сатрни белгилаш/ечиш (checkbox auto-submit form'и). */
    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable UUID id,
                         @RequestParam UUID lineId,
                         RedirectAttributes redirect) {
        try {
            reconciliationService.toggle(id, lineId);
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/reconciliation/" + id;
    }

    /** Якунлаш - фарқ айнан 0 бўлса COMPLETED. */
    @PostMapping("/{id}/complete")
    public String complete(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            reconciliationService.complete(id);
            redirect.addFlashAttribute("message", msg.get("rcn.completed"));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/reconciliation/" + id;
    }

    /** IN_PROGRESS'ни бекор қилиш - рўйхатга қайтади. */
    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            reconciliationService.cancel(id);
            redirect.addFlashAttribute("message", msg.get("rcn.cancelled"));
            return "redirect:/reconciliation";
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
            return "redirect:/reconciliation/" + id;
        }
    }

}
