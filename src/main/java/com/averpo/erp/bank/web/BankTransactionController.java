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
import com.averpo.erp.shared.web.FormParsers;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Банк транзакциялари экранлари: рўйхат, тур бўйича динамик форма
 * (Alpine), кўриш, reverse. Ҳамма ёзиш BankTransactionService орқали -
 * контроллер юпқа.
 *
 * @author Zafar
 */
@Controller
@RequestMapping("/bank-transactions")
@RequiredArgsConstructor
public class BankTransactionController {

    /** Банк транзакцияларининг ягона public API'си. */
    private final BankTransactionService bankService;

    /** Банк/сатр счёт select'лари ва номлари учун. */
    private final AccountService accountService;

    /** Банк қолдиқлари - формада танланган счёт ёнида кўрсатиш учун
     * (Arbitr-012, QBO Transfer паритети). */
    private final LedgerDashboardService dashboardService;

    /** Контакт select'и ва номлари учун. */
    private final ContactService contactService;

    /** Home currency - курс майдонлари учун. */
    private final CompanySettingsService settingsService;

    /** Йўналиш select'и (class-tracking.md) - shared каталог. */
    private final com.averpo.erp.shared.service.TxnClassService txnClassService;

    /** Flash хабарлар учун i18n. */
    private final com.averpo.erp.i18n.Msg msg;

    /**
     * Рўйхат - саҳифаланган (Beruniy-perf1); тўлиқ филтр қатори
     * (Arbitr-068): давр/статус/контакт/матн (ref_no ҳам қидирилади),
     * саҳифа линклари филтрни сақлайди (audit қолипи).
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
        // ARBITR-105: саҳифа ҳажми ?size=/cookie'дан (PageSizeResolver)
        int size = com.averpo.erp.shared.web.PageSizeResolver.resolve(
                request, response, "bank-transactions");
        var txnPage = bankService.list(new BankTransactionService.ListFilter(
                from, to, parseStatusSafe(status), contactId, q), page, size);
        model.addAttribute("transactions", txnPage.getContent());
        model.addAttribute("page", txnPage);
        model.addAttribute("accountNames", accountNames());
        // Филтр ҳолати + контакт select'и (мижоз+етказувчи, форма нақши)
        model.addAttribute("contacts", allContacts());
        model.addAttribute("from", from == null ? "" : from.toString());
        model.addAttribute("to", to == null ? "" : to.toString());
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("contactId", contactId == null ? "" : contactId.toString());
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("filterQuery", new com.averpo.erp.shared.web.FilterQuery()
                .add("from", from).add("to", to).add("status", status)
                .add("contactId", contactId).add("q", q).toString());
        return "bank/bankTransactions";
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

    /** Янги транзакция формаси - 2 та бўш сатр билан (фақат Кирим). */
    @GetMapping("/new")
    public String createForm(@RequestParam(required = false) String type, Model model) {
        // Arbitr-033: Чиқим энди алоҳида /expenses экранида - эски
        // ?type=EXPENSE линклар синмасин (transfer'дагидек нақш)
        if (BankTransactionType.EXPENSE.name().equals(type)) {
            return "redirect:/expenses/new";
        }
        BankTransactionForm form = BankTransactionForm.empty(2);
        // Sanjar-005: созламалар оқим бошида бир марта ўқилади - аввал ҳар
        // accessor (zoneId/homeCurrency/trackClasses) алоҳида SELECT берарди
        CompanySettings settings = settingsService.get();
        // Default сана - компания zoneId'даги «бугун» (JVM tz эмас, қоида 12/Arbitr-044)
        form.setTxnDate(LocalDate.now(settings.zoneId()));
        if (type != null && !type.isBlank()) {
            form.setType(type);
        }
        fillFormModel(model, form, settings);
        return "bank/bankTransactionForm";
    }

    /** HTMX partial: формага янги сатр қўшиш. */
    @GetMapping("/line-row")
    public String lineRow(@RequestParam int index, Model model) {
        model.addAttribute("index", index);
        fillLineRefs(model, settingsService.get(), accountService.all(), allContacts());
        return "bank/bankTxnLineRow";
    }

    /** Яратиш - тур бўйича тегишли service методи (дарҳол POSTED). */
    @PostMapping
    public String create(@ModelAttribute BankTransactionForm form,
                         Model model, RedirectAttributes redirect) {
        // Sanjar-005: битта snapshot toTxnData'га ҳам, хато қайтишига ҳам
        CompanySettings settings = settingsService.get();
        try {
            BankTransactionType type = parseType(form.getType());
            BankTransaction txn = switch (type) {
                case DEPOSIT -> bankService.deposit(toTxnData(form, settings));
                // Чиқим алоҳида /expenses экранида (Arbitr-033) - transfer
                // нақшидек tampered POST рад этилади
                case EXPENSE -> throw new BusinessRuleException(BusinessRule.BR_BT_009,
                        "Чиқим алоҳида «Чиқим» экранида яратилади");
                // Ўтказма алоҳида /transfers экранида (Arbitr-022) - бу эски
                // йўлга tampered POST type=TRANSFER келса рад этилади
                case TRANSFER -> throw new BusinessRuleException(BusinessRule.BR_BT_009,
                        "Ўтказма алоҳида «Ўтказма» экранида яратилади");
            };
            redirect.addFlashAttribute("message",
                    msg.get("bt.saved", txn.getTxnNumber()));
            return "redirect:/bank-transactions/" + txn.getId();
        } catch (BusinessRuleException e) {
            fillFormModel(model, form, settings);
            model.addAttribute("error", e.displayMessage());
            return "bank/bankTransactionForm";
        }
    }

    /** Битта транзакцияни кўриш. */
    @GetMapping("/{id}")
    public String view(@PathVariable UUID id, Model model) {
        BankTransaction txn = bankService.getWithLines(id);
        // Sanjar-005: созламалар snapshot'и - оқимда битта SELECT
        CompanySettings settings = settingsService.get();
        model.addAttribute("txn", txn);
        model.addAttribute("accountNames", accountNames());
        model.addAttribute("contactNames", contactNames(txn));
        model.addAttribute("homeCurrency", settings.homeCurrencyCode());
        model.addAttribute("today", LocalDate.now(settings.zoneId()).toString());
        // Transfer конверсиясида манзил сумма манзил банк валютасида -
        // экранда кодсиз чиқса кўчирма билан солиштиришда адаштиради
        // (Alisa-003); валютасиз счёт = home валюта
        if (txn.getType() == BankTransactionType.TRANSFER
                && txn.getCounterpartAccountId() != null) {
            Account counterpart = accountService.get(txn.getCounterpartAccountId());
            model.addAttribute("counterpartCurrency",
                    counterpart.getCurrency() != null
                            ? counterpart.getCurrency().getCode()
                            : settings.homeCurrencyCode());
        }
        return "bank/bankTransactionView";
    }

    /** POSTED транзакцияни сторно қилиш. */
    @PostMapping("/{id}/reverse")
    public String reverse(@PathVariable UUID id,
                          @RequestParam LocalDate reversalDate,
                          @RequestParam(required = false) String reason,
                          RedirectAttributes redirect) {
        try {
            BankTransaction txn = bankService.reverse(id, reversalDate, reason);
            redirect.addFlashAttribute("message",
                    msg.get("bt.reversed", txn.getTxnNumber()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/bank-transactions/" + id;
    }

    // ---- ички ёрдамчилар ----

    /** Форма model'ини тўлдиради (select маълумотлари билан) - settings
     * оқим бошидаги snapshot (Sanjar-005, қайта SELECT қилинмайди). */
    private void fillFormModel(Model model, BankTransactionForm form,
                               CompanySettings settings) {
        model.addAttribute("form", form);
        // Sanjar-009: счёт каталоги БИР марта олинади - аввал
        // postableAccounts() + fillLineRefs'даги all() иккита SELECT эди
        List<Account> accounts = accountService.all();
        // Банк select'и: BANK туридаги фаол postable счётлар,
        // data-currency атрибути учун валюта коди билан
        // (postableAccounts() кўзгуси: фаол+postable, CHART_ORDER сақланади)
        model.addAttribute("bankAccounts", accounts.stream()
                .filter(a -> a.isActive() && a.isPostable()
                        && a.getType() == AccountType.BANK).toList());
        model.addAttribute("homeCurrency", settings.homeCurrencyCode());
        // Танланган банк ёнида жонли қолдиқ (QBO Transfer'даги Balance):
        // id → «сумма КОД» харитаси option'ларнинг data-balance'ига боради
        Map<String, String> balances = new HashMap<>();
        String home = settings.homeCurrencyCode();
        for (LedgerDashboardService.BankBalance balance : dashboardService.bankBalances()) {
            String code = balance.currencyCode() != null ? balance.currencyCode() : home;
            balances.put(balance.accountId().toString(),
                    Fmt.money(balance.amount()) + " " + code);
        }
        model.addAttribute("bankBalances", balances);
        // Sanjar-009: контактлар ҳам бир марта - аввал fillFormModel ва
        // fillLineRefs алоҳида allContacts() чақириб тўрт SELECT берарди
        fillLineRefs(model, settings, accounts, allContacts());
    }

    /** Сатр select'лари: банкдан бошқа барча счётлар (иерархия билан) -
     * accounts/contacts чақирувчида БИР марта олинган каталоглар (Sanjar-009). */
    private void fillLineRefs(Model model, CompanySettings settings,
                              List<Account> accounts, List<Contact> contacts) {
        // Xorazmiy-007: all() - ота (postable=false) счётлар ҳам киради,
        // accountOptions уларни disabled жилд қилади (Bill қолипи);
        // нофаолларни partial ўзи ташлайди.
        // BR-BT-010 (Xorazmiy-012): тизим назорат счётлари select'да
        // умуман кўринмайди (TransferController.bsAccounts нақши) -
        // UNDEPOSITED_FUNDS истисно, кирим/чиқим унинг ўз оқими.
        model.addAttribute("lineAccounts", accounts.stream()
                .filter(a -> a.getType() != AccountType.BANK
                        && (!a.getDetailType().systemManaged()
                                || a.getDetailType() == AccountDetailType.UNDEPOSITED_FUNDS))
                .toList());
        model.addAttribute("contacts", contacts);
        // Class tracking (class-tracking.md): режим UI'ни бошқаради -
        // OFF'да рўйхат сўралмайди ҳам (майдонлар render бўлмайди)
        var mode = settings.getTrackClasses();
        model.addAttribute("classMode", mode.name());
        model.addAttribute("classes",
                mode == com.averpo.erp.shared.domain.ClassTrackingMode.OFF
                        ? List.<com.averpo.erp.shared.service.TxnClassService.ClassOption>of()
                        : txnClassService.activeForSelect());
    }

    /** DEPOSIT/EXPENSE формасини service маълумотига айлантиради. */
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
                FormParsers.uuid(form.getBankAccountId(), BusinessRule.BR_BT_002, "Банк"),
                form.getTxnDate(),
                FormParsers.decimal(form.getExchangeRate(), BusinessRule.BR_BT_008, "Курс"),
                FormParsers.uuid(form.getContactId(), BusinessRule.NOT_FOUND, "Контакт"),
                form.getMemo(), lines);
    }

    /** Тур матни - бузуқ қийматга BR-BT-009. */
    private BankTransactionType parseType(String type) {
        try {
            return BankTransactionType.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessRuleException(BusinessRule.BR_BT_009,
                    "Нотўғри тур «" + type + "»");
        }
    }

    /** Контакт select'и: мижоз + етказувчи бирлашган рўйхати, ном тартибида. */
    private List<Contact> allContacts() {
        List<Contact> contacts = new ArrayList<>();
        contacts.addAll(contactService.byType(ContactType.CUSTOMER, false));
        contacts.addAll(contactService.byType(ContactType.VENDOR, false));
        contacts.sort(Comparator.comparing(Contact::getDisplayName));
        return contacts;
    }

    /** Счёт id → ном харитаси (рўйхат/кўриш учун). */
    private Map<UUID, String> accountNames() {
        Map<UUID, String> names = new HashMap<>();
        for (Account account : accountService.all()) {
            names.put(account.getId(), account.getName());
        }
        return names;
    }

    /**
     * Ҳужжатдаги (сарлавҳа + сатрлар) контакт номлари - фақат керакли
     * id'лар byIds/IN сўровда (ARBITR-105б, Ulugbek-003 §1); нофаоллар
     * ҳам келади - тарихий ҳужжатда ном кўриниши шарт.
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
