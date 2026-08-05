package com.averpo.erp.bank.web;

import com.averpo.erp.bank.domain.BankTransaction;
import com.averpo.erp.bank.domain.BankTransactionType;
import com.averpo.erp.bank.service.BankTransactionService;
import com.averpo.erp.bank.service.BankTransactionService.LineData;
import com.averpo.erp.bank.service.BankTransactionService.TxnData;
import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.domain.AccountType;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.ledger.service.LedgerDashboardService;
import com.averpo.erp.shared.domain.CompanySettings;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.service.PaymentMethodService;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Чиқим (Expense) экрани - QBO /app/expense паритети (Arbitr-033):
 * рўйхат + QBO тартибидаги алоҳида форма + кўриш/сторно, transfers
 * нақшида. Умумий «Банк транзакцияси» формасида фақат Кирим қолди.
 *
 * <p>Ёзувчи мантиқ ўзгармаган - ягона
 * {@link BankTransactionService#expense}; проводка ўша (Dt сатр
 * счётлари / Cr банк, posting-rules «Банк» бўлими ЎЗГАРМАЙДИ).
 *
 * @author Zafar
 */
@Controller
@RequestMapping("/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    /** Чиқимнинг ягона public API'си (BankTransaction EXPENSE тури). */
    private final BankTransactionService bankService;

    /** Банк/сатр счёт select'лари ва номлари учун. */
    private final AccountService accountService;

    /** Танланган тўлов счёти ёнида жонли қолдиқ (QBO Balance). */
    private final LedgerDashboardService dashboardService;

    /** Payee select'и ва номлари учун. */
    private final ContactService contactService;

    /** Тўлов усули select'и (Arbitr-033 каталоги). */
    private final PaymentMethodService paymentMethodService;

    /** Home currency - курс майдонлари ва Total (home) учун. */
    private final CompanySettingsService settingsService;

    /** Йўналиш select'и (class-tracking.md) - shared каталог. */
    private final com.averpo.erp.shared.service.TxnClassService txnClassService;

    /** Flash хабарлар учун i18n. */
    private final com.averpo.erp.i18n.Msg msg;

    /**
     * Рўйхат - фақат EXPENSE, саҳифаланган (Beruniy-perf1); Arbitr-068
     * стандарт филтр қатори: давр/статус/payee/матн (GET форма - bookmark,
     * list-filters.md). Кесиш service Specification'ида (SQL LIMIT/OFFSET).
     */
    @GetMapping
    public String list(@RequestParam(required = false) LocalDate from,
                       @RequestParam(required = false) LocalDate to,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) UUID contactId,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false, defaultValue = "0") int page,
                       jakarta.servlet.http.HttpServletRequest request,
                       jakarta.servlet.http.HttpServletResponse response,
                       Model model) {
        int size = com.averpo.erp.shared.web.PageSizeResolver.resolve(
                request, response, "expenses");
        var expensePage = bankService.expenses(new BankTransactionService.ListFilter(
                from, to, parseStatusSafe(status), contactId, q), page, size);
        model.addAttribute("expenses", expensePage.getContent());
        model.addAttribute("page", expensePage);
        // Саҳифа линклари жорий филтрларни сақлайди (audit қолипи)
        model.addAttribute("filterQuery", new com.averpo.erp.shared.web.FilterQuery()
                .add("from", from).add("to", to).add("status", status)
                .add("contactId", contactId).add("q", q).toString());
        // Счётлар ҳам, контактлар ҳам биттадан сўров (Beruniy-020 сабоғи)
        Map<UUID, String> accountNames = new HashMap<>();
        for (Account account : accountService.all()) {
            accountNames.put(account.getId(), account.getName());
        }
        model.addAttribute("accountNames", accountNames);
        model.addAttribute("contactNames", contactNames(expensePage.getContent()));
        // Payee филтр combobox'и: мижоз+етказувчи енгил рефлари, танланган қиймат стринг
        model.addAttribute("payees", payeeRefs());
        model.addAttribute("from", from == null ? "" : from.toString());
        model.addAttribute("to", to == null ? "" : to.toString());
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("contactId", contactId == null ? "" : contactId.toString());
        return "bank/expenses";
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

    /** Payee филтри рефлари: мижоз + етказувчи, ном тартибида ({@link #allContacts} енгил кўзгуси). */
    private List<ContactService.ContactRef> payeeRefs() {
        List<ContactService.ContactRef> refs = new ArrayList<>();
        refs.addAll(contactService.activeRefsByType(ContactType.CUSTOMER));
        refs.addAll(contactService.activeRefsByType(ContactType.VENDOR));
        refs.sort(Comparator.comparing(ContactService.ContactRef::displayName));
        return refs;
    }

    /** Янги чиқим формаси - 2 та бўш сатр билан. */
    @GetMapping("/new")
    public String createForm(Model model) {
        BankTransactionForm form = BankTransactionForm.empty(2);
        form.setType(BankTransactionType.EXPENSE.name());
        // Sanjar-005: созламалар оқим бошида бир марта ўқилади - аввал ҳар
        // accessor (zoneId/homeCurrency/trackClasses) алоҳида SELECT берарди
        CompanySettings settings = settingsService.get();
        // Default сана - компания zoneId'даги «бугун» (JVM tz эмас, қоида 12/Arbitr-044)
        form.setTxnDate(LocalDate.now(settings.zoneId()));
        fillFormModel(model, form, settings);
        return "bank/expenseForm";
    }

    /** Яратиш - {@link BankTransactionService#expense} (дарҳол POSTED). */
    @PostMapping
    public String create(@ModelAttribute BankTransactionForm form,
                         Model model, RedirectAttributes redirect) {
        // Sanjar-005: битта snapshot toTxnData'га ҳам, хато қайтишига ҳам
        CompanySettings settings = settingsService.get();
        try {
            BankTransaction txn = bankService.expense(toTxnData(form, settings));
            redirect.addFlashAttribute("message", msg.get("bt.saved", txn.getTxnNumber()));
            return "redirect:/expenses/" + txn.getId();
        } catch (BusinessRuleException e) {
            fillFormModel(model, form, settings);
            model.addAttribute("error", e.displayMessage());
            return "bank/expenseForm";
        }
    }

    /**
     * Чиқимни кўриш - transfers нақши (Otabek-003): EXPENSE бўлмаган id
     * умумий банк view'ига йўналтирилади, маълумот йўқолмайди.
     */
    @GetMapping("/{id}")
    public String view(@PathVariable UUID id, Model model) {
        BankTransaction txn = bankService.getWithLines(id);
        if (txn.getType() != BankTransactionType.EXPENSE) {
            return "redirect:/bank-transactions/" + id;
        }
        model.addAttribute("txn", txn);
        Map<UUID, String> accountNames = new HashMap<>();
        for (Account account : accountService.all()) {
            accountNames.put(account.getId(), account.getName());
        }
        model.addAttribute("accountNames", accountNames);
        model.addAttribute("contactNames", contactNames(txn));
        model.addAttribute("paymentMethodName", txn.getPaymentMethodId() == null
                ? null : paymentMethodService.get(txn.getPaymentMethodId()).getName());
        // Sanjar-005: созламалар snapshot'и - оқимда битта SELECT
        CompanySettings settings = settingsService.get();
        model.addAttribute("homeCurrency", settings.homeCurrencyCode());
        model.addAttribute("today", LocalDate.now(settings.zoneId()).toString());
        return "bank/expenseView";
    }

    /** Сторно - мавжуд service reverse'и, redirect чиқим view'ида қолади. */
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
        return "redirect:/expenses/" + id;
    }

    // ---- ички ёрдамчилар ----

    /** Форма model'и: тўлов счётлари + жонли қолдиқ + сатр счётлари -
     * settings оқим бошидаги snapshot (Sanjar-005, қайта SELECT қилинмайди). */
    private void fillFormModel(Model model, BankTransactionForm form,
                               CompanySettings settings) {
        model.addAttribute("form", form);
        // Sanjar-009: счёт каталоги БИР марта олинади - аввал
        // postableAccounts() + қуйидаги all() иккита SELECT эди
        List<Account> accounts = accountService.all();
        // Тўлов счёти: BANK туридаги фаол postable счётлар (QBO Payment
        // account) - data-currency/data-balance атрибутлари билан
        // (postableAccounts() кўзгуси: фаол+postable, CHART_ORDER сақланади)
        model.addAttribute("bankAccounts", accounts.stream()
                .filter(a -> a.isActive() && a.isPostable()
                        && a.getType() == AccountType.BANK).toList());
        model.addAttribute("contacts", allContacts());
        model.addAttribute("paymentMethods", paymentMethodService.activeForSelect());
        model.addAttribute("homeCurrency", settings.homeCurrencyCode());
        Map<String, String> balances = new HashMap<>();
        String home = settings.homeCurrencyCode();
        for (LedgerDashboardService.BankBalance balance : dashboardService.bankBalances()) {
            String code = balance.currencyCode() != null ? balance.currencyCode() : home;
            balances.put(balance.accountId().toString(),
                    Fmt.money(balance.amount()) + " " + code);
        }
        model.addAttribute("bankBalances", balances);
        // Сатр счётлари: банкдан бошқа, тизим назорат счётларисиз
        // (BR-BT-010, Xorazmiy-012) - UNDEPOSITED_FUNDS истисноси билан
        model.addAttribute("lineAccounts", accounts.stream()
                .filter(a -> a.getType() != AccountType.BANK
                        && (!a.getDetailType().systemManaged()
                                || a.getDetailType() == AccountDetailType.UNDEPOSITED_FUNDS))
                .toList());
        // Class tracking (class-tracking.md): режим UI'ни бошқаради -
        // OFF'да рўйхат сўралмайди ҳам (майдонлар render бўлмайди)
        var mode = settings.getTrackClasses();
        model.addAttribute("classMode", mode.name());
        model.addAttribute("classes",
                mode == com.averpo.erp.shared.domain.ClassTrackingMode.OFF
                        ? List.<com.averpo.erp.shared.service.TxnClassService.ClassOption>of()
                        : txnClassService.activeForSelect());
    }

    /** EXPENSE формасини service маълумотига айлантиради (generic нақш). */
    private TxnData toTxnData(BankTransactionForm form, CompanySettings settings) {
        // PER_TXN (class-tracking.md): сарлавҳадаги битта Йўналиш ҳамма
        // сатрга тарқатилади - схема ягона, class доим сатрда туради
        boolean perTxn = settings.getTrackClasses()
                == com.averpo.erp.shared.domain.ClassTrackingMode.PER_TXN;
        UUID headerClass = FormParsers.uuid(form.getClassId(),
                BusinessRule.BR_CLS_001, "Йўналиш");
        List<LineData> lines = new ArrayList<>();
        int no = 0;
        for (BankTransactionForm.LineForm lf : form.getLines()) {
            no++;
            if (lf.isEmpty()) {
                continue;
            }
            lines.add(new LineData(
                    FormParsers.uuid(lf.getAccountId(), BusinessRule.BR_BT_004,
                            no + "-сатр счёти"),
                    FormParsers.decimal(lf.getAmount(), BusinessRule.BR_BT_001,
                            no + "-сатр суммаси"),
                    FormParsers.uuid(lf.getContactId(), BusinessRule.NOT_FOUND, "Контакт"),
                    lf.getMemo(),
                    perTxn ? headerClass
                            : FormParsers.uuid(lf.getClassId(), BusinessRule.BR_CLS_001,
                                    no + "-сатр: Йўналиш")));
        }
        return new TxnData(
                FormParsers.uuid(form.getBankAccountId(), BusinessRule.BR_BT_002, "Тўлов счёти"),
                form.getTxnDate(),
                FormParsers.decimal(form.getExchangeRate(), BusinessRule.BR_BT_008, "Курс"),
                FormParsers.uuid(form.getContactId(), BusinessRule.NOT_FOUND, "Олувчи"),
                form.getMemo(), lines,
                FormParsers.uuid(form.getPaymentMethodId(), BusinessRule.NOT_FOUND, "Тўлов усули"),
                form.getRefNo());
    }

    /** Payee select'и: мижоз + етказувчи бирлашган рўйхати, ном тартибида. */
    private List<Contact> allContacts() {
        List<Contact> contacts = new ArrayList<>();
        contacts.addAll(contactService.byType(ContactType.CUSTOMER, false));
        contacts.addAll(contactService.byType(ContactType.VENDOR, false));
        contacts.sort(Comparator.comparing(Contact::getDisplayName));
        return contacts;
    }

    /**
     * Саҳифа қаторларидаги контакт номлари - фақат керакли id'лар
     * byIds/IN сўровда (ARBITR-105б, Ulugbek-003 §1); нофаоллар ҳам
     * келади - тарихий ҳужжатда ном кўриниши шарт.
     */
    private Map<UUID, String> contactNames(List<BankTransaction> rows) {
        List<UUID> ids = new ArrayList<>();
        for (BankTransaction txn : rows) {
            if (txn.getContactId() != null) {
                ids.add(txn.getContactId());
            }
        }
        return contactService.namesByIds(ids);
    }

    /**
     * Ҳужжатдаги (сарлавҳа + сатрлар) контакт номлари - фақат керакли
     * id'лар byIds/IN сўровда (ARBITR-105б, Ulugbek-003 §1).
     */
    private Map<UUID, String> contactNames(BankTransaction txn) {
        List<UUID> ids = new ArrayList<>();
        if (txn.getContactId() != null) {
            ids.add(txn.getContactId());
        }
        for (var line : txn.getLines()) {
            if (line.getContactId() != null) {
                ids.add(line.getContactId());
            }
        }
        return contactService.namesByIds(ids);
    }
}
