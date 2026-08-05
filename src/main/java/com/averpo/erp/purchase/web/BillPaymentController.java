package com.averpo.erp.purchase.web;

import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.ledger.domain.AccountType;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.purchase.domain.Bill;
import com.averpo.erp.purchase.domain.BillPayment;
import com.averpo.erp.purchase.domain.BillPaymentAllocation;
import com.averpo.erp.purchase.service.BillPaymentService;
import com.averpo.erp.purchase.service.BillPaymentService.AllocationData;
import com.averpo.erp.purchase.service.BillPaymentService.PaymentData;
import com.averpo.erp.purchase.service.BillService;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.service.CurrencyService;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Vendor тўлови экранлари: рўйхат, форма (vendor танланганда очиқ
 * bill'лар HTMX билан юкланади), кўриш (тақсимотлар + аванс ишлатиш +
 * reverse). Тўлов DRAFT'сиз - яратилди = POSTED (QBO услуби).
 */
@Controller
@RequestMapping("/payments")
@RequiredArgsConstructor
public class BillPaymentController {

    /** Тўловнинг ягона public API'си. */
    private final BillPaymentService paymentService;

    /** Очиқ bill'лар рўйхати учун. */
    private final BillService billService;

    /** Vendor select ва номлари учун. */
    private final ContactService contactService;

    /** Банк счёти select'и учун. */
    private final AccountService accountService;

    /** Валюта select'и учун. */
    private final CurrencyService currencyService;

    /** Home currency - курс майдонининг default'и учун. */
    private final CompanySettingsService settingsService;

    /** Flash хабарлар учун i18n. */
    private final com.averpo.erp.i18n.Msg msg;

    /**
     * Рўйхат - саҳифаланган (Beruniy-perf1); тўлиқ филтр қатори
     * (Arbitr-068): давр/статус/vendor/матн, саҳифа линклари филтрни
     * сақлайди (audit қолипи).
     */
    @GetMapping
    public String list(@RequestParam(required = false) LocalDate from,
                       @RequestParam(required = false) LocalDate to,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) UUID vendorId,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false, defaultValue = "0") int page,
                       jakarta.servlet.http.HttpServletRequest request,
                       jakarta.servlet.http.HttpServletResponse response,
                       Model model) {
        int size = com.averpo.erp.shared.web.PageSizeResolver.resolve(
                request, response, "payments");
        var paymentPage = paymentService.list(new BillPaymentService.ListFilter(
                from, to, parseStatusSafe(status), vendorId, q), page, size);
        model.addAttribute("payments", paymentPage.getContent());
        model.addAttribute("page", paymentPage);
        // Vendor номлари - фақат саҳифадаги сатрлар (+ филтр id'си)
        // byIds/IN сўровда (ARBITR-105б, Ulugbek-003 §1)
        Set<UUID> vendorIds = new HashSet<>();
        for (BillPayment payment : paymentPage.getContent()) {
            vendorIds.add(payment.getVendorId());
        }
        if (vendorId != null) {
            vendorIds.add(vendorId);
        }
        model.addAttribute("vendorNames", contactService.namesByIds(vendorIds));
        // Филтр ҳолати + select учун фаол таъминотчиларнинг енгил рўйхати
        model.addAttribute("vendors", contactService.activeRefsByType(ContactType.VENDOR));
        model.addAttribute("from", from == null ? "" : from.toString());
        model.addAttribute("to", to == null ? "" : to.toString());
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("vendorId", vendorId == null ? "" : vendorId.toString());
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("filterQuery", new com.averpo.erp.shared.web.FilterQuery()
                .add("from", from).add("to", to).add("status", status)
                .add("vendorId", vendorId).add("q", q).toString());
        return "purchase/payments";
    }

    /** Query қийматидан статусни хавфсиз парслайди (бузуқ қиймат - филтрсиз). */
    private static BillPayment.Status parseStatusSafe(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return BillPayment.Status.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Янги тўлов формаси. */
    @GetMapping("/new")
    public String createForm(@RequestParam(required = false) UUID vendorId, Model model) {
        BillPaymentForm form = new BillPaymentForm();
        // Default сана - компания zoneId'даги «бугун» (JVM tz эмас, қоида 12/Arbitr-044)
        form.setPaymentDate(LocalDate.now(settingsService.zoneId()));
        if (vendorId != null) {
            form.setVendorId(vendorId.toString());
        }
        fillFormModel(model, form);
        return "purchase/paymentForm";
    }

    /** HTMX partial: vendor танланганда очиқ bill қаторлари. */
    @GetMapping("/open-bills")
    public String openBills(@RequestParam(required = false) UUID vendorId, Model model) {
        model.addAttribute("openBills", vendorId == null
                ? List.<Bill>of() : billService.openBills(vendorId));
        return "purchase/paymentOpenBills";
    }

    /** Тўлов яратиш - дарҳол POSTED (GL + тақсимотлар + курс фарқи). */
    @PostMapping
    public String create(@ModelAttribute BillPaymentForm form,
                         Model model, RedirectAttributes redirect) {
        try {
            BillPayment payment = paymentService.create(toData(form));
            redirect.addFlashAttribute("message",
                    msg.get("pay.saved", payment.getPaymentNumber()));
            return "redirect:/payments/" + payment.getId();
        } catch (BusinessRuleException e) {
            fillFormModel(model, form);
            model.addAttribute("error", e.displayMessage());
            return "purchase/paymentForm";
        }
    }

    /** Битта тўловни кўриш - тақсимотлар ва аванс ишлатиш формаси билан. */
    @GetMapping("/{id}")
    public String view(@PathVariable UUID id, Model model) {
        BillPayment payment = paymentService.get(id);
        List<BillPaymentAllocation> allocations = paymentService.allocationsOf(id);
        model.addAttribute("payment", payment);
        model.addAttribute("allocations", allocations);
        model.addAttribute("vendorName",
                contactService.get(payment.getVendorId()).getDisplayName());
        model.addAttribute("bankAccountName",
                accountService.get(payment.getBankAccountId()).getName());
        // Аванс ишлатиш формаси: шу vendor'нинг очиқ bill'лари, аллақачон
        // шу тўловдан тақсимот олганлари чиқарилади (BR-PAY-011)
        List<Bill> allocatable = List.of();
        if (payment.getStatus() == BillPayment.Status.POSTED
                && payment.getUnallocatedAmount().signum() > 0) {
            Set<UUID> taken = new HashSet<>();
            for (BillPaymentAllocation allocation : allocations) {
                taken.add(allocation.getBill().getId());
            }
            allocatable = billService.openBills(payment.getVendorId()).stream()
                    .filter(bill -> !taken.contains(bill.getId()))
                    .toList();
        }
        model.addAttribute("allocatable", allocatable);
        model.addAttribute("today", LocalDate.now(settingsService.zoneId()).toString());
        // Чет валютали тўловда валюта+курс қаторини кўрсатиш учун (U2)
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        return "purchase/paymentView";
    }

    /** Аванс ишлатиш: мавжуд тўловдан кейинги тақсимот. */
    @PostMapping("/{id}/allocate")
    public String allocate(@PathVariable UUID id,
                           @ModelAttribute BillPaymentForm form,
                           RedirectAttributes redirect) {
        try {
            List<AllocationData> allocations = toAllocations(form);
            if (allocations.isEmpty()) {
                throw new BusinessRuleException(BusinessRule.BR_PAY_001,
                        "Камида битта тақсимот суммаси киритилиши шарт");
            }
            paymentService.allocate(id, allocations);
            redirect.addFlashAttribute("message", msg.get("pay.allocated"));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/payments/" + id;
    }

    /** POSTED тўловни сторно қилиш. */
    @PostMapping("/{id}/reverse")
    public String reverse(@PathVariable UUID id,
                          @RequestParam LocalDate reversalDate,
                          @RequestParam(required = false) String reason,
                          RedirectAttributes redirect) {
        try {
            BillPayment payment = paymentService.reverse(id, reversalDate, reason);
            redirect.addFlashAttribute("message",
                    msg.get("pay.reversed", payment.getPaymentNumber()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/payments/" + id;
    }

    // ---- ички ёрдамчилар ----

    /** Форма model'ини тўлдиради (select маълумотлари билан). */
    private void fillFormModel(Model model, BillPaymentForm form) {
        model.addAttribute("form", form);
        model.addAttribute("vendors", contactService.byType(ContactType.VENDOR, false));
        // Xorazmiy-007: all() - ота счётлар ҳам киради, accountOptions
        // уларни disabled жилд қилади; нофаолларни partial ўзи ташлайди
        model.addAttribute("bankAccounts", accountService.all().stream()
                .filter(a -> a.getType() == AccountType.BANK).toList());
        model.addAttribute("currencies", currencyService.active());
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        // Форма қайта кўрсатилганда (хато) танланган vendor'нинг очиқ
        // bill'лари ҳам қайта юкланади - киритилган суммалар сақланади
        model.addAttribute("openBills", form.getVendorId() == null || form.getVendorId().isBlank()
                ? List.<Bill>of()
                : billService.openBills(FormParsers.requireUuid(form.getVendorId(),
                        BusinessRule.BR_PAY_010, "Vendor")));
    }

    /** Формани BillPaymentService маълумотига айлантиради (parse - FormParsers). */
    private PaymentData toData(BillPaymentForm form) {
        return new PaymentData(
                FormParsers.uuid(form.getVendorId(), BusinessRule.BR_PAY_010, "Vendor"),
                form.getPaymentDate(),
                FormParsers.uuid(form.getBankAccountId(), BusinessRule.BR_PAY_002,
                        "Банк счёти"),
                form.getCurrency(),
                parseRate(form.getExchangeRate()),
                parseNumber(form.getTotalAmount(), "тўлов суммаси"),
                form.getMemo(), toAllocations(form));
    }

    /** Тақсимот қаторлари - суммаси бўшлари ташланади. */
    private List<AllocationData> toAllocations(BillPaymentForm form) {
        List<AllocationData> allocations = new ArrayList<>();
        for (BillPaymentForm.AllocationForm af : form.getAllocations()) {
            if (af.getAmount() == null || af.getAmount().isBlank()) {
                continue;
            }
            allocations.add(new AllocationData(
                    FormParsers.uuid(af.getBillId(), BusinessRule.BR_PAY_003, "Bill"),
                    parseNumber(af.getAmount(), "тақсимот суммаси")));
        }
        return allocations;
    }

    /** Курс матни - бўш → null (home'да сервер 1 қилади); FormParsers қоидаси. */
    private BigDecimal parseRate(String text) {
        return FormParsers.decimal(text, BusinessRule.BR_PAY_012, "Курс");
    }

    /** Сон парси - normalize (пробел/NBSP/вергул) FormParsers'да. */
    private BigDecimal parseNumber(String text, String field) {
        return FormParsers.decimal(text, BusinessRule.BR_PAY_001, field);
    }
}
