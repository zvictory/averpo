package com.averpo.erp.payroll.web;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountType;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.ledger.service.LedgerDashboardService;
import com.averpo.erp.payroll.domain.PayrollPayment;
import com.averpo.erp.payroll.domain.PayrollPaymentType;
import com.averpo.erp.payroll.service.PayrollPaymentService;
import com.averpo.erp.payroll.service.PayrollPaymentService.LineData;
import com.averpo.erp.payroll.service.PayrollPaymentService.PaymentData;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Иш ҳақи тўлови экранлари (payroll.md 23в): саҳифаланган рўйхат, форма
 * (тур ADVANCE/SALARY, счёт Balance билан, «Очиқ қолдиқни тўлдириш»
 * prefill - unpaidByEmployee), кўриш (post/reverse/draft-delete). Ҳамма
 * ёзиш PayrollPaymentService орқали - контроллер юпқа. VIEWER ҳимояси
 * SecurityConfig'даги POST /** қоидасида.
 */
@Controller
@RequestMapping("/payroll/payments")
@RequiredArgsConstructor
public class PayrollPaymentController {

    /** Тўловнинг ягона public API'си. */
    private final PayrollPaymentService paymentService;

    /** Ходим select ва номлари учун. */
    private final ContactService contactService;

    /** Тўлов счёти select ва номлари учун. */
    private final AccountService accountService;

    /** Танланган счёт ёнида жонли қолдиқ (QBO Balance). */
    private final LedgerDashboardService dashboardService;

    /** Home currency ва бугунги сана (компания минтақаси). */
    private final CompanySettingsService settingsService;

    /** Flash хабарлар учун i18n. */
    private final com.averpo.erp.i18n.Msg msg;

    /** Рўйхат - саҳифаланган, янгидан эскига. */
    @GetMapping
    public String list(@RequestParam(required = false, defaultValue = "0") int page,
                       jakarta.servlet.http.HttpServletRequest request,
                       jakarta.servlet.http.HttpServletResponse response,
                       Model model) {
        int size = com.averpo.erp.shared.web.PageSizeResolver.resolve(
                request, response, "payroll-payments");
        var paymentPage = paymentService.list(page, size);
        model.addAttribute("payments", paymentPage.getContent());
        model.addAttribute("page", paymentPage);
        model.addAttribute("accountNames", accountNames());
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        return "payroll/payrollPayments";
    }

    /** Янги форма - бўш; сатрлар «Очиқ қолдиқни тўлдириш» билан келади. */
    @GetMapping("/new")
    public String createForm(Model model) {
        PayrollPaymentForm form = PayrollPaymentForm.empty(2);
        // Default сана - компания zoneId'даги «бугун» (JVM tz эмас, қоида 12/Arbitr-044)
        form.setPaymentDate(LocalDate.now(settingsService.zoneId()));
        fillFormModel(model, form);
        return "payroll/payrollPaymentForm";
    }

    /** Мавжуд DRAFT'ни таҳрирлаш формаси. */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model,
                           RedirectAttributes redirect) {
        PayrollPayment payment = paymentService.getWithLines(id);
        if (payment.getStatus() != PayrollPayment.Status.DRAFT) {
            redirect.addFlashAttribute("error", msg.get("payp.onlyDraftEditable"));
            return "redirect:/payroll/payments/" + id;
        }
        fillFormModel(model, PayrollPaymentForm.from(payment));
        return "payroll/payrollPaymentForm";
    }

    /** HTMX partial: формага битта бўш сатр қўшиш. */
    @GetMapping("/line-row")
    public String lineRow(@RequestParam int index, Model model) {
        model.addAttribute("index", index);
        fillLineRefs(model);
        return "payroll/payrollPaymentLineRow";
    }

    /**
     * HTMX partial: «Очиқ қолдиқни тўлдириш» - фаол ходимларнинг очиқ
     * clearing қолдиғи (unpaidByEmployee) сумма билан prefill (tbody
     * тўлиқ алмашади). Фақат owed > 0 ва фаол EMPLOYEE (BR-PYR-003).
     */
    @GetMapping("/prefill-rows")
    public String prefillRows(Model model) {
        LocalDate today = LocalDate.now(settingsService.zoneId());
        Map<UUID, BigDecimal> owed = paymentService.unpaidByEmployee(today);
        Map<UUID, String> active = activeEmployeeNames();
        List<PayrollPaymentForm.LineForm> lines = new ArrayList<>();
        owed.entrySet().stream()
                .filter(e -> e.getValue().signum() > 0 && active.containsKey(e.getKey()))
                .sorted(Comparator.comparing(e -> active.get(e.getKey()),
                        String.CASE_INSENSITIVE_ORDER))
                .forEach(e -> {
                    PayrollPaymentForm.LineForm lf = new PayrollPaymentForm.LineForm();
                    lf.setEmployeeId(e.getKey().toString());
                    lf.setAmount(Fmt.n(e.getValue()));
                    lines.add(lf);
                });
        model.addAttribute("prefillLines", lines);
        fillLineRefs(model);
        return "payroll/payrollPaymentPrefillRows";
    }

    /** Сақлаш: action=draft - фақат сақлаш, action=post - сақлаш + post. */
    @PostMapping
    public String save(@ModelAttribute PayrollPaymentForm form,
                       @RequestParam String action,
                       Model model, RedirectAttributes redirect) {
        try {
            UUID id = FormParsers.uuid(form.getId(), BusinessRule.NOT_FOUND, "Тўлов");
            PayrollPayment payment = paymentService.saveDraft(id, toData(form));
            if ("post".equals(action)) {
                payment = paymentService.post(payment.getId());
            }
            redirect.addFlashAttribute("message", msg.get("payp.saved",
                    payment.getPaypNumber(), msg.get("status." + payment.getStatus().name())));
            return "redirect:/payroll/payments/" + payment.getId();
        } catch (BusinessRuleException e) {
            fillFormModel(model, form);
            model.addAttribute("error", e.displayMessage());
            return "payroll/payrollPaymentForm";
        }
    }

    /** Кўриш: сатрлар + жами + post/reverse/draft-delete. */
    @GetMapping("/{id}")
    public String view(@PathVariable UUID id, Model model) {
        PayrollPayment payment = paymentService.getWithLines(id);
        model.addAttribute("payment", payment);
        model.addAttribute("employeeNames", employeeNames(payment));
        model.addAttribute("accountName",
                accountService.get(payment.getAccountId()).getName());
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        model.addAttribute("today",
                LocalDate.now(settingsService.zoneId()).toString());
        return "payroll/payrollPaymentView";
    }

    /** Draft'ни post қилиш. */
    @PostMapping("/{id}/post")
    public String post(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            PayrollPayment payment = paymentService.post(id);
            redirect.addFlashAttribute("message",
                    msg.get("payp.posted", payment.getPaypNumber()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/payroll/payments/" + id;
    }

    /** POSTED тўловни сторно қилиш. */
    @PostMapping("/{id}/reverse")
    public String reverse(@PathVariable UUID id,
                          @RequestParam LocalDate reversalDate,
                          @RequestParam(required = false) String reason,
                          RedirectAttributes redirect) {
        try {
            PayrollPayment payment = paymentService.reverse(id, reversalDate, reason);
            redirect.addFlashAttribute("message",
                    msg.get("payp.reversed", payment.getPaypNumber()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/payroll/payments/" + id;
    }

    /** Draft'ни ўчириш. */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            PayrollPayment payment = paymentService.get(id);
            String number = payment.getPaypNumber();
            paymentService.deleteDraft(id);
            redirect.addFlashAttribute("message", msg.get("payp.deleted", number));
            return "redirect:/payroll/payments";
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
            return "redirect:/payroll/payments/" + id;
        }
    }

    // ---- ички ёрдамчилар ----

    /** Форма model'и: тўлов счётлари (Balance билан) + ходимлар. */
    private void fillFormModel(Model model, PayrollPaymentForm form) {
        model.addAttribute("form", form);
        String home = settingsService.homeCurrency();
        // Тўлов счёти: BANK туридаги (банк/касса) фаол postable, HOME
        // валютали счётлар (Arbitr-070/Nargiza-044: payroll home валютада
        // юритилади - BR-PYR-001; чет валюта счётини select'да кўрсатиш
        // фойдаланувчини кафолатли радга бошларди)
        model.addAttribute("bankAccounts", accountService.postableAccounts().stream()
                .filter(a -> a.getType() == AccountType.BANK
                        && (a.getCurrency() == null
                                || home.equals(a.getCurrency().getCode())))
                .toList());
        model.addAttribute("homeCurrency", home);
        // Танланган счёт ёнида жонли қолдиқ (QBO Balance) - id → «сумма КОД»
        Map<String, String> balances = new HashMap<>();
        for (LedgerDashboardService.BankBalance balance : dashboardService.bankBalances()) {
            String code = balance.currencyCode() != null ? balance.currencyCode() : home;
            balances.put(balance.accountId().toString(),
                    Fmt.money(balance.amount()) + " " + code);
        }
        model.addAttribute("bankBalances", balances);
        fillLineRefs(model);
    }

    /** Сатр select'и: фаол ходимлар. */
    private void fillLineRefs(Model model) {
        model.addAttribute("employees",
                contactService.byType(ContactType.EMPLOYEE, false));
    }

    /** Формани service маълумотига айлантиради (бўш сатрлар ташланади). */
    private PaymentData toData(PayrollPaymentForm form) {
        List<LineData> lines = new ArrayList<>();
        int no = 0;
        for (PayrollPaymentForm.LineForm lf : form.getLines()) {
            no++;
            if (lf.isEmpty()) {
                continue;
            }
            lines.add(new LineData(
                    FormParsers.uuid(lf.getEmployeeId(), BusinessRule.BR_PYR_003,
                            no + "-сатр: ходим"),
                    FormParsers.decimal(lf.getAmount(), BusinessRule.BR_PYR_003,
                            no + "-сатр: сумма")));
        }
        return new PaymentData(parseType(form.getPaymentType()), form.getPaymentDate(),
                FormParsers.uuid(form.getAccountId(), BusinessRule.BR_PYR_001, "Тўлов счёти"),
                form.getMemo(), lines);
    }

    /** Тур матни - бузуқ қийматга BR-PYR-001 (форма контексти қолипи). */
    private PayrollPaymentType parseType(String type) {
        try {
            return PayrollPaymentType.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessRuleException(BusinessRule.BR_PYR_001,
                    "Тўлов тури нотўғри: " + type);
        }
    }

    /** Фаол ходим id → ном (prefill фильтр/тартиб учун). */
    private Map<UUID, String> activeEmployeeNames() {
        Map<UUID, String> names = new HashMap<>();
        for (Contact contact : contactService.byType(ContactType.EMPLOYEE, false)) {
            names.put(contact.getId(), contact.getDisplayName());
        }
        return names;
    }

    /**
     * Тўлов сатрларидаги ходим номлари - фақат керакли id'лар byIds/IN
     * сўровда (ARBITR-105б, Ulugbek-003 §1); нофаоллар ҳам келади -
     * тарихий тўловда ном кўриниши шарт.
     */
    private Map<UUID, String> employeeNames(PayrollPayment payment) {
        List<UUID> ids = new ArrayList<>();
        for (var line : payment.getLines()) {
            if (line.getEmployeeId() != null) {
                ids.add(line.getEmployeeId());
            }
        }
        return contactService.namesByIds(ids);
    }

    /** Счёт id → ном харитаси (рўйхат учун). */
    private Map<UUID, String> accountNames() {
        Map<UUID, String> names = new HashMap<>();
        for (Account account : accountService.all()) {
            names.put(account.getId(), account.getName());
        }
        return names;
    }
}
