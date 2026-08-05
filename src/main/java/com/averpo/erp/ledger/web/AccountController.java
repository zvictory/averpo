package com.averpo.erp.ledger.web;

import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountClassification;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.shared.exception.BusinessRule;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Счётлар режаси экранлари: tree view (QBO услуби), яратиш/таҳрирлаш,
 * CSV импорт. Контроллер юпқа - мантиқ AccountService'да.
 */
@Controller
@RequestMapping("/accounts")
@lombok.RequiredArgsConstructor
public class AccountController {

    /**
     * Tree view'га тайёрланган қатор.
     *
     * @param account     счёт
     * @param depth       indent даражаси
     * @param hasChildren chevron кўрсатиладими
     * @param hiddenExpr  Alpine ифодаси: ота занжиридан бирортаси
     *                    йиғилган бўлса қатор яширинади
     * @param balance     QBO услубидаги Balance устуни: balance-sheet
     *                    счётда бугунгача қолдиқ (болалари билан,
     *                    табиий ишорада), P&amp;L счётда null (QBO ҳам
     *                    кўрсатмайди)
     */
    public record TreeRow(com.averpo.erp.ledger.domain.Account account,
                          int depth, boolean hasChildren, String hiddenExpr,
                          java.math.BigDecimal balance) { }

    /** Счётлар service. */
    private final AccountService accountService;

    /** Валюта select'и учун каталог. */
    private final com.averpo.erp.shared.service.CurrencyService currencyService;

    /** Balance устуни учун қолдиқлар. */
    private final com.averpo.erp.ledger.service.TrialBalanceService trialBalanceService;

    /** Счёт яратиш + opening balance битта транзакцияда. */
    private final com.averpo.erp.ledger.service.OpeningBalanceService openingBalanceService;

    /** «Бугун» компания вақт минтақасида бўлиши учун (темир қоида №12). */
    private final com.averpo.erp.shared.service.CompanySettingsService settingsService;

    /** Flash хабарлар учун i18n. */
    private final com.averpo.erp.i18n.Msg msg;

    /**
     * Счётлар рўйхати - QBO услубидаги йиғма дарахт; стандарт каталог
     * филтри (DEC-068): матн (ном/код), фаоллик, classification.
     * Филтрсиз default - ТЎЛИҚ дарахт (мавжуд хатти-ҳаракат, иерархия
     * бузилмасин); филтр танланса натижа ТЕКИС рўйхат бўлиб чиқади
     * (мос келган счётнинг ота-жилди мос келмаса дарахт узилиб қоларди).
     * Матн Java toLowerCase билан - кирилни ILIKE каби тўғри folds
     * қилади; дарахт барибир тўлиқ юкланган (balance ҳисоби учун) -
     * қўшимча SQL сўров керак эмас.
     */
    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String activity,
                       @RequestParam(required = false) String classification,
                       Model model) {
        List<AccountService.AccountNode> nodes = accountService.tree();
        var subtotals = subtreeBalances(nodes);
        String act = activity == null || activity.isBlank() ? "ALL" : activity;
        AccountClassification cls = parseClassificationSafe(classification);
        boolean filtered = (q != null && !q.isBlank()) || !"ALL".equals(act) || cls != null;
        List<TreeRow> rows;
        if (filtered) {
            String needle = q == null || q.isBlank() ? null : q.strip().toLowerCase();
            rows = nodes.stream()
                    .filter(node -> matches(node.account(), needle, act, cls))
                    .map(node -> new TreeRow(node.account(), 0, false, "false",
                            displayBalance(node.account(), subtotals)))
                    .toList();
        } else {
            rows = nodes.stream()
                    .map(node -> new TreeRow(node.account(), node.depth(), node.hasChildren(),
                            node.ancestors().isEmpty()
                                    ? "false"
                                    : node.ancestors().stream()
                                            .map(id -> "c['" + id + "']")
                                            .collect(java.util.stream.Collectors.joining(" || ")),
                            displayBalance(node.account(), subtotals)))
                    .toList();
        }
        model.addAttribute("rows", rows);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("activity", act);
        model.addAttribute("classification", cls == null ? "" : cls.name());
        // DEC-023: Balance қиймати home'да (GL base) - код ҳам home'ники
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        return "ledger/accounts";
    }

    /** Битта счёт филтрга мос келадими (DEC-068 текис режими). */
    private static boolean matches(Account account, String needle,
                                   String activity, AccountClassification cls) {
        if ("ACTIVE".equals(activity) && !account.isActive()) {
            return false;
        }
        if ("INACTIVE".equals(activity) && account.isActive()) {
            return false;
        }
        if (cls != null && account.getClassification() != cls) {
            return false;
        }
        if (needle == null) {
            return true;
        }
        return (account.getName() != null
                        && account.getName().toLowerCase().contains(needle))
                || (account.getCode() != null
                        && account.getCode().toLowerCase().contains(needle));
    }

    /** Query қийматидан classification'ни хавфсиз парслайди (бузуқ - филтрсиз). */
    private static AccountClassification parseClassificationSafe(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AccountClassification.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Ҳар счёт учун ўзи + болалари қолдиғи (хом, дебет-мусбат). QBO
     * ҳам parent'да cumulative кўрсатади - гуруҳни йиғиб қўйганда
     * жами йўқолиб қолмаслиги учун.
     */
    private java.util.Map<UUID, java.math.BigDecimal> subtreeBalances(
            List<AccountService.AccountNode> nodes) {
        var raw = trialBalanceService.balancesByAccountId(
                java.time.LocalDate.now(settingsService.zoneId()));
        var subtotals = new java.util.HashMap<UUID, java.math.BigDecimal>();
        for (AccountService.AccountNode node : nodes) {
            java.math.BigDecimal own = raw.get(node.account().getId());
            if (own == null || own.signum() == 0) {
                continue;
            }
            subtotals.merge(node.account().getId(), own, java.math.BigDecimal::add);
            for (UUID ancestor : node.ancestors()) {
                subtotals.merge(ancestor, own, java.math.BigDecimal::add);
            }
        }
        return subtotals;
    }

    /**
     * Хом қолдиқни экран ишорасига келтиради: пассив/капитал табиий
     * кредит қолдиқ мусбат кўринади. P&amp;L (даромад/харажат) счётига
     * null - QBO CoA'да ҳам Balance фақат balance-sheet счётларда.
     */
    private java.math.BigDecimal displayBalance(
            com.averpo.erp.ledger.domain.Account account,
            java.util.Map<UUID, java.math.BigDecimal> subtotals) {
        var classification = account.getClassification();
        boolean balanceSheet = switch (classification) {
            case ASSET, LIABILITY, EQUITY -> true;
            default -> false;
        };
        if (!balanceSheet) {
            return null;
        }
        java.math.BigDecimal total = subtotals.getOrDefault(
                account.getId(), java.math.BigDecimal.ZERO);
        return classification == com.averpo.erp.ledger.domain.AccountClassification.ASSET
                ? total : total.negate();
    }

    /** Янги счёт формаси - HTMX'да drawer partial, оддийда тўлиқ саҳифа (fallback). */
    @GetMapping("/new")
    public String createForm(Model model, jakarta.servlet.http.HttpServletRequest request) {
        fillFormModel(model, new AccountForm(), null);
        return com.averpo.erp.shared.web.Htmx.isHtmx(request)
                ? "ledger/accountFormDrawer" : "ledger/accountForm";
    }

    /** Янги счёт сақлаш - opening balance киритилган бўлса проводкаси билан. */
    @PostMapping
    public String create(@ModelAttribute AccountForm form,
                         Model model, RedirectAttributes redirect,
                         jakarta.servlet.http.HttpServletRequest request,
                         jakarta.servlet.http.HttpServletResponse response) {
        try {
            // Ном/detail type валидацияси service ичида (BR-COA-008/009) -
            // controller'да strip/null текширув йўқ, tampered request 500 бермайди
            if (form.getOpeningBalance() != null && !form.getOpeningBalance().isBlank()) {
                openingBalanceService.createAccountWithOpeningBalance(
                        form.getName(),
                        parseDetailType(form.getDetailType()),
                        form.getCode(), form.getDescription(),
                        parseUuid(form.getParentId()), form.isPostable(),
                        form.getCurrency(),
                        parseDecimal(form.getOpeningBalance()),
                        parseDate(form.getOpeningBalanceDate()),
                        form.getOpeningBalanceRate() == null || form.getOpeningBalanceRate().isBlank()
                                ? null : parseDecimal(form.getOpeningBalanceRate()));
            } else {
                accountService.create(form.getName(),
                        parseDetailType(form.getDetailType()),
                        form.getCode(), form.getDescription(),
                        parseUuid(form.getParentId()), form.isPostable(),
                        form.getCurrency());
            }
        } catch (com.averpo.erp.shared.exception.BusinessRuleException e) {
            fillFormModel(model, form, null);
            model.addAttribute("error", e.displayMessage());
            // DEC-024: хато drawer ичида қайта render бўлади
            return com.averpo.erp.shared.web.Htmx.isHtmx(request)
                    ? "ledger/accountFormDrawer" : "ledger/accountForm";
        }
        if (com.averpo.erp.shared.web.Htmx.isHtmx(request)) {
            // Drawer ёпилиб рўйхат HX-Redirect билан янгиланади (flash сақланади)
            return com.averpo.erp.shared.web.Htmx.redirect(request, response,
                    "/accounts", "message", msg.get("accounts.created", form.getName()));
        }
        redirect.addFlashAttribute("message", msg.get("accounts.created", form.getName()));
        return "redirect:/accounts";
    }

    /** Таҳрир формаси - HTMX'да drawer partial, оддийда тўлиқ саҳифа (fallback). */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model,
                           jakarta.servlet.http.HttpServletRequest request) {
        Account account = accountService.get(id);
        fillFormModel(model, AccountForm.from(account), id);
        return com.averpo.erp.shared.web.Htmx.isHtmx(request)
                ? "ledger/accountFormDrawer" : "ledger/accountForm";
    }

    /** Таҳрирни сақлаш. */
    @PostMapping("/{id}")
    public String update(@PathVariable UUID id, @ModelAttribute AccountForm form,
                         Model model, RedirectAttributes redirect,
                         jakarta.servlet.http.HttpServletRequest request,
                         jakarta.servlet.http.HttpServletResponse response) {
        try {
            accountService.update(id, form.getName(),
                    parseDetailType(form.getDetailType()),
                    form.getCode(), form.getDescription(),
                    parseUuid(form.getParentId()), form.isPostable(),
                    form.getCurrency(), form.isActive());
        } catch (com.averpo.erp.shared.exception.BusinessRuleException e) {
            fillFormModel(model, form, id);
            model.addAttribute("error", e.displayMessage());
            // DEC-024: хато drawer ичида қайта render бўлади
            return com.averpo.erp.shared.web.Htmx.isHtmx(request)
                    ? "ledger/accountFormDrawer" : "ledger/accountForm";
        }
        if (com.averpo.erp.shared.web.Htmx.isHtmx(request)) {
            return com.averpo.erp.shared.web.Htmx.redirect(request, response,
                    "/accounts", "message", msg.get("accounts.updated", form.getName()));
        }
        redirect.addFlashAttribute("message", msg.get("accounts.updated", form.getName()));
        return "redirect:/accounts";
    }

    /** Bundled QBO услуб default chart'ни импорт қилади. */
    @PostMapping("/import-default")
    public String importDefault(RedirectAttributes redirect) {
        AccountService.ImportResult result = accountService.importDefaultChart();
        redirect.addFlashAttribute("message", importMessage(result));
        return "redirect:/accounts";
    }

    /** Фойдаланувчи CSV файлидан импорт. */
    @PostMapping("/import")
    public String importCsv(@RequestParam MultipartFile file,
                            RedirectAttributes redirect) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                file.getInputStream(), StandardCharsets.UTF_8))) {
            AccountService.ImportResult result = accountService.importCsv(reader);
            redirect.addFlashAttribute("message", importMessage(result));
        } catch (com.averpo.erp.shared.exception.BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        } catch (IOException e) {
            redirect.addFlashAttribute("error",
                    msg.get("accounts.fileError", e.getMessage()));
        }
        return "redirect:/accounts";
    }

    /**
     * Импорт натижаси flash хабари. «Дубликат тур» огоҳлантиришлари
     * (DEC-060, BR-COA-010 импорт кўриниши) бўлса охирига қўшилади -
     * фойдаланувчи қайси сатр нега яратилмаганини шу ерда кўради.
     */
    private String importMessage(AccountService.ImportResult result) {
        String message = msg.get("accounts.imported", result.created(), result.skipped());
        return result.warnings().isEmpty()
                ? message
                : message + " - " + String.join("; ", result.warnings());
    }

    /** Форма model'ини тўлдиради: form, parents, валюталар, edit id. */
    private void fillFormModel(Model model, AccountForm form, UUID editId) {
        model.addAttribute("form", form);
        model.addAttribute("parents", accountService.all());
        model.addAttribute("currencies", currencyService.active());
        model.addAttribute("editId", editId == null ? null : editId.toString());
        // Opening balance бўлими қайси detail type'ларда кўринади -
        // қоида service'да битта жойда (BR-COA-005)
        model.addAttribute("obDetailTypes",
                java.util.Arrays.stream(AccountDetailType.values())
                        .filter(com.averpo.erp.ledger.service.OpeningBalanceService::supports)
                        .map(Enum::name)
                        .toList());
    }

    /** Detail type enum'ини парслайди - бўш/бузуқ қийматга BR-COA-008. */
    private AccountDetailType parseDetailType(String value) {
        if (value == null || value.isBlank()) {
            return null; // AccountService BR-COA-008 билан рад этади
        }
        try {
            return AccountDetailType.valueOf(value);
        } catch (IllegalArgumentException e) {
            // valueOf'нинг «No enum constant» хабари фойдаланувчига ярамайди
            throw new com.averpo.erp.shared.exception.BusinessRuleException(
                    BusinessRule.BR_COA_008, "Нотўғри detail type: " + value);
        }
    }

    /** Ота счёт танлови - parse қоидаси FormParsers'да (бўш → null). */
    private UUID parseUuid(String value) {
        return com.averpo.erp.shared.web.FormParsers.uuid(value,
                BusinessRule.NOT_FOUND, "Ота счёт");
    }

    /** Opening balance сумма/курси - бузуқ сонга BR-COA-007 (FormParsers). */
    private java.math.BigDecimal parseDecimal(String value) {
        return com.averpo.erp.shared.web.FormParsers.decimal(value,
                BusinessRule.BR_COA_007, "Opening balance");
    }

    /** Opening balance санасини парслайди - бузуқ санага BR-COA-007. */
    private java.time.LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null; // OpeningBalanceService BR-COA-007 билан рад этади
        }
        try {
            return java.time.LocalDate.parse(value.strip());
        } catch (java.time.format.DateTimeParseException e) {
            throw new com.averpo.erp.shared.exception.BusinessRuleException(
                    BusinessRule.BR_COA_007, "Opening balance: сана формати нотўғри: " + value);
        }
    }
}
