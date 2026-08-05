package com.averpo.erp.bank.web;

import com.averpo.erp.bank.domain.BankTransaction;
import com.averpo.erp.bank.domain.BankTransactionType;
import com.averpo.erp.bank.service.BankTransactionService;
import com.averpo.erp.bank.service.BankTransactionService.TransferData;
import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountClassification;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.ledger.service.LedgerDashboardService;
import com.averpo.erp.ledger.service.TrialBalanceService;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.web.Fmt;
import com.averpo.erp.shared.web.FormParsers;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ўтказма (Transfer) экрани - QBO «+ Янги → Ўтказма» паритети: иккита
 * Balance Sheet счёти орасида пул кўчириш, алоҳида битта мақсадли форма
 * (docs/modules/transfer.md, Arbitr-022). Умумий Bank Transactions
 * формасидан ажратилди - у ерда тур танлагич ва сатрлар жадвали бор,
 * бу эса тоза, QBO'дек.
 *
 * <p>Проводка/сақлаш ягона {@link BankTransactionService#transfer}
 * орқали - контроллер юпқа. Кўриш ва сторно мавжуд
 * {@code /bank-transactions/{id}} орқали ишлайди (бир хил entity).
 *
 * @author Zafar
 */
@Controller
@RequestMapping("/transfers")
@RequiredArgsConstructor
public class TransferController {

    /** Ўтказма ягона public API'си (BankTransaction TRANSFER тури). */
    private final BankTransactionService bankService;

    /** Balance Sheet счёт select'лари ва номлари учун. */
    private final AccountService accountService;

    /** Танланган счёт ёнида жонли қолдиқ (QBO Balance) учун. */
    private final LedgerDashboardService dashboardService;

    /**
     * Bank бўлмаган BS счётларнинг жонли қолдиғи учун (Asrorxoja-005) -
     * ledger'нинг мавжуд read-only методи (balancesByAccountId), ёзувчи
     * мантиққа тегилмайди.
     */
    private final TrialBalanceService trialBalanceService;

    /** Home currency - курс майдонлари учун. */
    private final CompanySettingsService settingsService;

    /** Flash хабарлар учун i18n. */
    private final com.averpo.erp.i18n.Msg msg;

    /**
     * Рўйхат - фақат TRANSFER турдаги транзакциялар, янгидан эскига;
     * тўлиқ филтр қатори (Arbitr-068): давр/статус/матн (контактсиз
     * ҳужжат тури).
     */
    @GetMapping
    public String list(@RequestParam(required = false) LocalDate from,
                       @RequestParam(required = false) LocalDate to,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false, defaultValue = "0") int page,
                       jakarta.servlet.http.HttpServletRequest request,
                       jakarta.servlet.http.HttpServletResponse response,
                       Model model) {
        // Beruniy-020: бутун банк журнали эмас - фақат ўтказмалар, валюта
        // JOIN FETCH билан (in-memory филтр ҳам, N+1 ҳам йўқ).
        // ARBITR-105: саҳифаланган (3-босқич) + ҳажм ?size=/cookie'дан
        int size = com.averpo.erp.shared.web.PageSizeResolver.resolve(
                request, response, "transfers");
        var transferPage = bankService.transfers(new BankTransactionService.ListFilter(
                from, to, parseStatusSafe(status), null, q), page, size);
        model.addAttribute("transfers", transferPage.getContent());
        model.addAttribute("page", transferPage);
        // Саҳифа линклари жорий филтрларни сақлайди (audit қолипи)
        model.addAttribute("filterQuery", new com.averpo.erp.shared.web.FilterQuery()
                .add("from", from).add("to", to).add("status", status).add("q", q).toString());
        // Счётлар базадан БИР марта олиниб иккала харита битта циклда
        AccountViewMaps maps = accountViewMaps(accountService.all(),
                settingsService.homeCurrency());
        model.addAttribute("accountNames", maps.names());
        // Alisa-005: кросс-валютада манзил сумма ўз валюта коди билан чиқади
        model.addAttribute("accountCurrencies", maps.currencies());
        model.addAttribute("from", from == null ? "" : from.toString());
        model.addAttribute("to", to == null ? "" : to.toString());
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("q", q == null ? "" : q);
        return "bank/transfers";
    }

    /** Query қийматидан статусни хавфсиз парслайди (бузуқ қиймат - филтрсиз). */
    private static BankTransaction.Status parseStatusSafe(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return BankTransaction.Status.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Ўтказмани кўриш - dedicated view (Otabek-003): транзфер label'лари
     * (Манбадан/Манзилга), орқага /transfers, сторно шу ерда. «bank» номи
     * фақат ички қолади деган spec қарори (transfer.md) шу билан тўлиқ
     * бажарилади. TRANSFER бўлмаган id келса умумий банк view'ига
     * йўналтирилади - маълумот йўқолмайди.
     */
    @GetMapping("/{id}")
    public String view(@PathVariable UUID id, Model model) {
        BankTransaction txn = bankService.get(id);
        if (txn.getType() != BankTransactionType.TRANSFER) {
            return "redirect:/bank-transactions/" + id;
        }
        model.addAttribute("txn", txn);
        model.addAttribute("accountNames", accountNames());
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        // Манзил сумма манзил счёти валютасида кўрсатилади (Alisa-003 паттерни)
        String counterpartCurrency = null;
        if (txn.getCounterpartAccountId() != null) {
            Account counterpart = accountService.get(txn.getCounterpartAccountId());
            counterpartCurrency = counterpart.getCurrency() != null
                    ? counterpart.getCurrency().getCode()
                    : settingsService.homeCurrency();
        }
        model.addAttribute("counterpartCurrency", counterpartCurrency);
        model.addAttribute("today", LocalDate.now(settingsService.zoneId()).toString());
        return "bank/transferView";
    }

    /**
     * Сторно - мавжуд {@link BankTransactionService#reverse} қайта
     * ишлатилади (ёзувчи мантиқ ўзгармайди), фақат redirect транзфер
     * view'ида қолади (Otabek-003).
     */
    @PostMapping("/{id}/reverse")
    public String reverse(@PathVariable UUID id,
                          @RequestParam LocalDate reversalDate,
                          @RequestParam(required = false) String reason,
                          RedirectAttributes redirect) {
        try {
            BankTransaction storno = bankService.reverse(id, reversalDate, reason);
            redirect.addFlashAttribute("message",
                    msg.get("bt.reversed", storno.getTxnNumber()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/transfers/" + id;
    }

    /** Янги ўтказма формаси (сатрсиз - транзфер сатрлар жадвалисиз). */
    @GetMapping("/new")
    public String createForm(Model model) {
        BankTransactionForm form = BankTransactionForm.empty(0);
        form.setType(BankTransactionType.TRANSFER.name());
        // Default сана - компания zoneId'даги «бугун» (JVM tz эмас, қоида 12/Arbitr-044)
        form.setTxnDate(LocalDate.now(settingsService.zoneId()));
        fillFormModel(model, form);
        return "bank/transferForm";
    }

    /** Яратиш - {@link BankTransactionService#transfer} (дарҳол POSTED). */
    @PostMapping
    public String create(@ModelAttribute BankTransactionForm form,
                         Model model, RedirectAttributes redirect) {
        try {
            BankTransaction txn = bankService.transfer(toTransferData(form));
            redirect.addFlashAttribute("message", msg.get("bt.saved", txn.getTxnNumber()));
            // Otabek-003: сақлагач транзфернинг ўз view'ига (банкникига эмас)
            return "redirect:/transfers/" + txn.getId();
        } catch (BusinessRuleException e) {
            fillFormModel(model, form);
            model.addAttribute("error", e.displayMessage());
            return "bank/transferForm";
        }
    }

    // ---- ички ёрдамчилар ----

    /** Форма model'и: Balance Sheet счётлар + жонли қолдиқлар. */
    private void fillFormModel(Model model, BankTransactionForm form) {
        model.addAttribute("form", form);
        // BR-TXF-001: транзфер счётлари Balance Sheet (Актив/Мажбурият/
        // Капитал), фаол ва postable - банк оқимидан фарқли (QBO Transfer).
        // BR-TXF-002 (Komil-008): тизим назорат счётлари (AR/AP/INVENTORY...)
        // dropdown'да умуман кўринмайди - service гарови устига UI ҳимояси
        List<Account> bsAccounts = accountService.all().stream()
                .filter(a -> a.getClassification().isBalanceSheet()
                        && a.isActive() && a.isPostable()
                        && !a.getDetailType().systemManaged())
                .toList();
        model.addAttribute("bsAccounts", bsAccounts);
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        // Жонли қолдиқ (QBO Balance). Банк/касса счётларида ўз валютасидаги
        // аниқ қолдиқ (dashboardService); қолган BS счётларда (заём, аванс,
        // капитал...) умумий GL қолдиғи home'да (Asrorxoja-005) - ишора
        // счёт табиатига нормаланган (актив дебет-мусбат, мажбурият/капитал
        // кредит-мусбат - CoA Balance устуни услуби).
        Map<String, String> balances = new HashMap<>();
        String home = settingsService.homeCurrency();
        for (LedgerDashboardService.BankBalance balance : dashboardService.bankBalances()) {
            String code = balance.currencyCode() != null ? balance.currencyCode() : home;
            balances.put(balance.accountId().toString(),
                    Fmt.money(balance.amount()) + " " + code);
        }
        Map<UUID, BigDecimal> glBalances = trialBalanceService.balancesByAccountId(
                LocalDate.now(settingsService.zoneId()));
        for (Account account : bsAccounts) {
            String key = account.getId().toString();
            if (balances.containsKey(key)) {
                continue; // банк қолдиғи ўз валютасида аниқроқ - устидан ёзилмайди
            }
            BigDecimal raw = glBalances.get(account.getId());
            if (raw == null) {
                // Alisa-009: ҳаракати йўқ счёт «номаълум» эмас - 0.00 home
                // кўрсатилади, акс ҳолда нол қолдиқ билан аралашарди
                balances.put(key, Fmt.money(BigDecimal.ZERO) + " " + home);
                continue;
            }
            BigDecimal normalized = account.getClassification() == AccountClassification.ASSET
                    ? raw : raw.negate();
            balances.put(key, Fmt.money(normalized) + " " + home);
        }
        model.addAttribute("bankBalances", balances);
    }

    /**
     * Форма → {@link TransferData}. Манба/манзил счётга BR-TXF-001 (Balance
     * Sheet гарови service'да), сумма/курсга мавжуд BR-BT кодлари.
     */
    private TransferData toTransferData(BankTransactionForm form) {
        return new TransferData(
                FormParsers.uuid(form.getBankAccountId(), BusinessRule.BR_TXF_001, "Манба счёт"),
                FormParsers.uuid(form.getToBankAccountId(), BusinessRule.BR_TXF_001, "Манзил счёт"),
                form.getTxnDate(),
                FormParsers.decimal(form.getFromAmount(), BusinessRule.BR_BT_001, "Сумма"),
                FormParsers.decimal(form.getExchangeRate(), BusinessRule.BR_BT_008, "Курс"),
                FormParsers.decimal(form.getToAmount(), BusinessRule.BR_BT_001, "Манзил сумма"),
                FormParsers.decimal(form.getToRate(), BusinessRule.BR_BT_008, "Манзил курс"),
                form.getMemo());
    }

    /** Счёт id → ном харитаси (рўйхат учун). */
    private Map<UUID, String> accountNames() {
        Map<UUID, String> names = new HashMap<>();
        for (Account account : accountService.all()) {
            names.put(account.getId(), account.getName());
        }
        return names;
    }

    /**
     * Рўйхат саҳифасининг счёт маълумотномалари: id → ном ва id → валюта
     * коди БИТТА циклда (Beruniy-020 - счётлар базадан бир марта
     * олинади). Валютасиз счётга home коди қайтади (Alisa-005).
     * Static - Spring контекстисиз unit тестланади.
     */
    static AccountViewMaps accountViewMaps(List<Account> accounts, String home) {
        Map<UUID, String> names = new HashMap<>();
        Map<UUID, String> currencies = new HashMap<>();
        for (Account account : accounts) {
            names.put(account.getId(), account.getName());
            currencies.put(account.getId(),
                    account.getCurrency() != null ? account.getCurrency().getCode() : home);
        }
        return new AccountViewMaps(names, currencies);
    }

    /** accountViewMaps натижаси - ном ва валюта хариталари жуфти. */
    record AccountViewMaps(Map<UUID, String> names, Map<UUID, String> currencies) {
    }
}
