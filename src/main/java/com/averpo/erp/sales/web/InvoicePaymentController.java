package com.averpo.erp.sales.web;

import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.domain.AccountType;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.sales.domain.Invoice;
import com.averpo.erp.sales.domain.InvoicePayment;
import com.averpo.erp.sales.domain.InvoicePaymentAllocation;
import com.averpo.erp.sales.service.InvoicePaymentService;
import com.averpo.erp.sales.service.InvoicePaymentService.AllocationData;
import com.averpo.erp.sales.service.InvoicePaymentService.PaymentData;
import com.averpo.erp.sales.service.InvoiceService;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.web.FormParsers;
import com.averpo.erp.shared.service.CurrencyService;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Мижоз тўлови (тушум) экранлари: рўйхат, форма (мижоз танланганда
 * очиқ invoice'лар HTMX билан юкланади), кўриш (тақсимотлар + аванс
 * ишлатиш + reverse). BillPaymentController'нинг кўзгу акси.
 */
@Controller
@RequestMapping("/invoice-payments")
@RequiredArgsConstructor
public class InvoicePaymentController {

    /** Тушумнинг ягона public API'си. */
    private final InvoicePaymentService paymentService;

    /** Очиқ invoice'лар рўйхати учун. */
    private final InvoiceService invoiceService;

    /** Customer select ва номлари учун. */
    private final ContactService contactService;

    /** Қабул счёти select'и учун. */
    private final AccountService accountService;

    /** Валюта select'и учун. */
    private final CurrencyService currencyService;

    /** Home currency - курс майдонининг default'и учун. */
    private final CompanySettingsService settingsService;

    /** Flash хабарлар учун i18n. */
    private final com.averpo.erp.i18n.Msg msg;

    /**
     * Рўйхат - саҳифаланган (Beruniy-perf1); тўлиқ филтр қатори
     * (Arbitr-068): давр/статус/мижоз/матн, саҳифа линклари филтрни
     * сақлайди (audit қолипи).
     */
    @GetMapping
    public String list(@RequestParam(required = false) LocalDate from,
                       @RequestParam(required = false) LocalDate to,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) UUID customerId,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false, defaultValue = "0") int page,
                       jakarta.servlet.http.HttpServletRequest request,
                       jakarta.servlet.http.HttpServletResponse response,
                       Model model) {
        int size = com.averpo.erp.shared.web.PageSizeResolver.resolve(
                request, response, "invoice-payments");
        var paymentPage = paymentService.list(new InvoicePaymentService.ListFilter(
                from, to, parseStatusSafe(status), customerId, q), page, size);
        model.addAttribute("payments", paymentPage.getContent());
        model.addAttribute("page", paymentPage);
        // Мижоз номлари - фақат саҳифа қаторларидаги id'лар
        // byIds/IN сўровда (ARBITR-105б, Ulugbek-003 §1)
        model.addAttribute("customerNames", contactService.namesByIds(
                paymentPage.getContent().stream().map(p -> p.getCustomerId())
                        .filter(java.util.Objects::nonNull).distinct().toList()));
        // Филтр ҳолати + select учун фаол мижозларнинг енгил рўйхати
        model.addAttribute("customers", contactService.activeRefsByType(ContactType.CUSTOMER));
        model.addAttribute("from", from == null ? "" : from.toString());
        model.addAttribute("to", to == null ? "" : to.toString());
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("customerId", customerId == null ? "" : customerId.toString());
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("filterQuery", new com.averpo.erp.shared.web.FilterQuery()
                .add("from", from).add("to", to).add("status", status)
                .add("customerId", customerId).add("q", q).toString());
        return "sales/invoicePayments";
    }

    /** Query қийматидан статусни хавфсиз парслайди (бузуқ қиймат - филтрсиз). */
    private static InvoicePayment.Status parseStatusSafe(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return InvoicePayment.Status.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Янги тушум формаси. */
    @GetMapping("/new")
    public String createForm(@RequestParam(required = false) UUID customerId, Model model) {
        InvoicePaymentForm form = new InvoicePaymentForm();
        // Default сана - компания zoneId'даги «бугун» (JVM tz эмас, қоида 12/Arbitr-044)
        form.setPaymentDate(LocalDate.now(settingsService.zoneId()));
        if (customerId != null) {
            form.setCustomerId(customerId.toString());
        }
        fillFormModel(model, form);
        return "sales/invoicePaymentForm";
    }

    /** HTMX partial: мижоз танланганда очиқ invoice қаторлари. */
    @GetMapping("/open-invoices")
    public String openInvoices(@RequestParam(required = false) UUID customerId, Model model) {
        model.addAttribute("openInvoices", customerId == null
                ? List.<Invoice>of() : invoiceService.openInvoices(customerId));
        return "sales/invoicePaymentOpenInvoices";
    }

    /** Тушум яратиш - дарҳол POSTED (GL + тақсимотлар + курс фарқи). */
    @PostMapping
    public String create(@ModelAttribute InvoicePaymentForm form,
                         Model model, RedirectAttributes redirect) {
        try {
            InvoicePayment payment = paymentService.create(toData(form));
            redirect.addFlashAttribute("message",
                    msg.get("rcpt.saved", payment.getReceiptNumber()));
            return "redirect:/invoice-payments/" + payment.getId();
        } catch (BusinessRuleException e) {
            fillFormModel(model, form);
            model.addAttribute("error", e.displayMessage());
            return "sales/invoicePaymentForm";
        }
    }

    /** Битта тушумни кўриш - тақсимотлар ва аванс ишлатиш формаси билан. */
    @GetMapping("/{id}")
    public String view(@PathVariable UUID id, Model model) {
        InvoicePayment payment = paymentService.get(id);
        List<InvoicePaymentAllocation> allocations = paymentService.allocationsOf(id);
        model.addAttribute("payment", payment);
        model.addAttribute("allocations", allocations);
        model.addAttribute("customerName",
                contactService.get(payment.getCustomerId()).getDisplayName());
        model.addAttribute("depositAccountName",
                accountService.get(payment.getDepositAccountId()).getName());
        // Аванс ишлатиш формаси: шу мижознинг очиқ invoice'лари,
        // аллақачон шу тушумдан тақсимот олганлари чиқарилади (BR-RCPT-011)
        List<Invoice> allocatable = List.of();
        if (payment.getStatus() == InvoicePayment.Status.POSTED
                && payment.getUnallocatedAmount().signum() > 0) {
            Set<UUID> taken = new HashSet<>();
            for (InvoicePaymentAllocation allocation : allocations) {
                taken.add(allocation.getInvoice().getId());
            }
            allocatable = invoiceService.openInvoices(payment.getCustomerId()).stream()
                    .filter(invoice -> !taken.contains(invoice.getId()))
                    .toList();
        }
        model.addAttribute("allocatable", allocatable);
        model.addAttribute("today", LocalDate.now(settingsService.zoneId()).toString());
        return "sales/invoicePaymentView";
    }

    /** Аванс ишлатиш: мавжуд тушумдан кейинги тақсимот. */
    @PostMapping("/{id}/allocate")
    public String allocate(@PathVariable UUID id,
                           @ModelAttribute InvoicePaymentForm form,
                           RedirectAttributes redirect) {
        try {
            List<AllocationData> allocations = toAllocations(form);
            if (allocations.isEmpty()) {
                throw new BusinessRuleException(BusinessRule.BR_RCPT_001,
                        "Камида битта тақсимот суммаси киритилиши шарт");
            }
            paymentService.allocate(id, allocations);
            redirect.addFlashAttribute("message", msg.get("rcpt.allocated"));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/invoice-payments/" + id;
    }

    /** POSTED тушумни сторно қилиш. */
    @PostMapping("/{id}/reverse")
    public String reverse(@PathVariable UUID id,
                          @RequestParam LocalDate reversalDate,
                          @RequestParam(required = false) String reason,
                          RedirectAttributes redirect) {
        try {
            InvoicePayment payment = paymentService.reverse(id, reversalDate, reason);
            redirect.addFlashAttribute("message",
                    msg.get("rcpt.reversed", payment.getReceiptNumber()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/invoice-payments/" + id;
    }

    // ---- ички ёрдамчилар ----

    /** Форма model'ини тўлдиради (select маълумотлари билан). */
    private void fillFormModel(Model model, InvoicePaymentForm form) {
        model.addAttribute("form", form);
        model.addAttribute("customers", contactService.byType(ContactType.CUSTOMER, false));
        // Қабул счётлари: BANK тури (банк/касса) + UNDEPOSITED_FUNDS
        // Xorazmiy-007: all() - ота счётлар ҳам киради, accountOptions
        // уларни disabled жилд қилади; нофаолларни partial ўзи ташлайди
        model.addAttribute("depositAccounts", accountService.all().stream()
                .filter(a -> a.getType() == AccountType.BANK
                        || a.getDetailType() == AccountDetailType.UNDEPOSITED_FUNDS)
                .toList());
        model.addAttribute("currencies", currencyService.active());
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        // Форма қайта кўрсатилганда (хато) танланган мижознинг очиқ
        // invoice'лари ҳам қайта юкланади - киритилган суммалар сақланади
        model.addAttribute("openInvoices",
                form.getCustomerId() == null || form.getCustomerId().isBlank()
                        ? List.<Invoice>of()
                        : invoiceService.openInvoices(FormParsers.requireUuid(
                                form.getCustomerId(), BusinessRule.BR_RCPT_010,
                                "Customer")));
    }

    /** Формани InvoicePaymentService маълумотига айлантиради (parse - FormParsers). */
    private PaymentData toData(InvoicePaymentForm form) {
        return new PaymentData(
                FormParsers.uuid(form.getCustomerId(), BusinessRule.BR_RCPT_010,
                        "Customer"),
                form.getPaymentDate(),
                FormParsers.uuid(form.getDepositAccountId(), BusinessRule.BR_RCPT_002,
                        "Қабул счёти"),
                form.getCurrency(),
                parseRate(form.getExchangeRate()),
                parseNumber(form.getTotalAmount(), "тўлов суммаси"),
                form.getMemo(), toAllocations(form));
    }

    /** Тақсимот қаторлари - суммаси бўшлари ташланади. */
    private List<AllocationData> toAllocations(InvoicePaymentForm form) {
        List<AllocationData> allocations = new ArrayList<>();
        for (InvoicePaymentForm.AllocationForm af : form.getAllocations()) {
            if (af.getAmount() == null || af.getAmount().isBlank()) {
                continue;
            }
            allocations.add(new AllocationData(
                    FormParsers.uuid(af.getInvoiceId(), BusinessRule.BR_RCPT_003,
                            "Invoice"),
                    parseNumber(af.getAmount(), "тақсимот суммаси")));
        }
        return allocations;
    }

    /** Курс матни - бўш → null (home'да сервер 1 қилади); FormParsers қоидаси. */
    private BigDecimal parseRate(String text) {
        return FormParsers.decimal(text, BusinessRule.BR_RCPT_012, "Курс");
    }

    /** Сон парси - normalize (пробел/NBSP/вергул) FormParsers'да. */
    private BigDecimal parseNumber(String text, String field) {
        return FormParsers.decimal(text, BusinessRule.BR_RCPT_001, field);
    }

}
