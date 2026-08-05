package com.averpo.erp.sales.web;

import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.inventory.domain.Warehouse;
import com.averpo.erp.inventory.service.WarehouseService;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.ledger.domain.AccountType;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.sales.domain.RefundReceipt;
import com.averpo.erp.sales.service.InvoiceService;
import com.averpo.erp.sales.service.RefundReceiptService;
import com.averpo.erp.sales.service.RefundReceiptService.LineData;
import com.averpo.erp.sales.service.RefundReceiptService.RefundReceiptData;
import com.averpo.erp.shared.domain.CompanySettings;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Пул қайтариш чеки экранлари (returns.md): саҳифаланган рўйхат,
 * FULL форма (CreditMemo қолипи + пул счёти), кўриш (reverse шу
 * ерда - application йўқ). Ҳамма ёзиш RefundReceiptService орқали.
 *
 * @author Zafar
 */
@Controller
@RequestMapping("/refund-receipts")
@RequiredArgsConstructor
public class RefundReceiptController {

    /** Чекнинг ягона public API'си. */
    private final RefundReceiptService refundReceiptService;

    /** Ҳаволали prefill учун invoice public API'си. */
    private final InvoiceService invoiceService;

    /** Customer select ва номлари учун. */
    private final ContactService contactService;

    /** Сатр item select'и учун. */
    private final ItemService itemService;

    /** UoM: сатрдаги бирлик select'и учун. */
    private final com.averpo.erp.item.service.UnitService unitService;

    /** Омбор select'лари учун. */
    private final WarehouseService warehouseService;

    /** Пул счёти select'и учун. */
    private final AccountService accountService;

    /** Валюта select'и учун. */
    private final CurrencyService currencyService;

    /** Home currency ва бугунги сана (компания минтақаси). */
    private final CompanySettingsService settingsService;

    /** ҚҚС ставкаси select'и учун - tax модули public API'си. */
    private final com.averpo.erp.tax.service.TaxRateService taxRateService;

    /** Йўналиш select'и (class-tracking.md) - shared каталог. */
    private final com.averpo.erp.shared.service.TxnClassService txnClassService;

    /** Flash хабарлар учун i18n. */
    private final com.averpo.erp.i18n.Msg msg;

    /**
     * Рўйхат - саҳифаланган, янгидан эскига; тўлиқ филтр қатори
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
                request, response, "refund-receipts");
        var receiptPage = refundReceiptService.list(new RefundReceiptService.ListFilter(
                from, to, parseStatusSafe(status), customerId, q), page, size);
        model.addAttribute("receipts", receiptPage.getContent());
        model.addAttribute("page", receiptPage);
        // Beruniy-032: бутун каталог эмас - саҳифадаги мижоз id'лари бўйича IN
        Map<UUID, String> customerNames = new HashMap<>();
        for (var ref : contactService.refsByIds(receiptPage.getContent().stream()
                .map(r -> r.getCustomerId()).distinct().toList())) {
            customerNames.put(ref.id(), ref.displayName());
        }
        model.addAttribute("customerNames", customerNames);
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
        return "sales/refundReceipts";
    }

    /** Query қийматидан статусни хавфсиз парслайди (бузуқ қиймат - филтрсиз). */
    private static RefundReceipt.Status parseStatusSafe(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return RefundReceipt.Status.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Янги форма: invoiceId берилса асл ҳужжатдан prefill (QBO оқими). */
    @GetMapping("/new")
    public String createForm(@RequestParam(required = false) UUID invoiceId, Model model) {
        RefundReceiptForm form = invoiceId == null
                ? RefundReceiptForm.empty(3)
                : RefundReceiptForm.from(invoiceService.getWithLines(invoiceId));
        // Sanjar-005: созламалар оқим бошида бир марта ўқилади - аввал ҳар
        // accessor (zoneId/homeCurrency/trackClasses) алоҳида SELECT берарди
        CompanySettings settings = settingsService.get();
        // Default сана - компания zoneId'даги «бугун» (JVM tz эмас, қоида 12/Arbitr-044)
        form.setRrDate(LocalDate.now(settings.zoneId()));
        fillFormModel(model, form, settings);
        return "sales/refundReceiptForm";
    }

    /** HTMX partial: формага янги сатр қўшиш. */
    @GetMapping("/line-row")
    public String lineRow(@RequestParam int index, Model model) {
        model.addAttribute("index", index);
        fillLineRefs(model, settingsService.get());
        return "sales/refundReceiptLineRow";
    }

    /** Яратиш - дарҳол POSTED. */
    @PostMapping
    public String create(@ModelAttribute RefundReceiptForm form,
                         Model model, RedirectAttributes redirect) {
        // Sanjar-005: битта snapshot toData'га ҳам, хато қайтишига ҳам
        CompanySettings settings = settingsService.get();
        try {
            RefundReceipt receipt = refundReceiptService.create(toData(form, settings));
            redirect.addFlashAttribute("message",
                    msg.get("rr.saved", receipt.getRrNumber()));
            return "redirect:/refund-receipts/" + receipt.getId();
        } catch (BusinessRuleException e) {
            fillFormModel(model, form, settings);
            model.addAttribute("error", e.displayMessage());
            return "sales/refundReceiptForm";
        }
    }

    /** Кўриш: сатрлар + reverse (application йўқ - тугал ҳужжат). */
    @GetMapping("/{id}")
    public String view(@PathVariable UUID id, Model model) {
        RefundReceipt receipt = refundReceiptService.getWithLines(id);
        model.addAttribute("receipt", receipt);
        model.addAttribute("customerName",
                contactService.get(receipt.getCustomerId()).getDisplayName());
        // Item номлари - фақат шу ҳужжат сатрларидаги id'лар
        // byIds/IN сўровда (ARBITR-105б, Ulugbek-003 §1)
        model.addAttribute("itemNames", itemService.namesByIds(
                receipt.getLines().stream().map(l -> l.getItemId())
                        .filter(java.util.Objects::nonNull).distinct().toList()));
        model.addAttribute("unitNames", unitNames());
        model.addAttribute("taxRateNames", taxRateNames());
        model.addAttribute("bankAccountName",
                accountService.get(receipt.getBankAccountId()).getName());
        // Sanjar-005: созламалар snapshot'и - оқимда битта SELECT
        CompanySettings settings = settingsService.get();
        model.addAttribute("homeCurrency", settings.homeCurrencyCode());
        model.addAttribute("today",
                LocalDate.now(settings.zoneId()).toString());
        return "sales/refundReceiptView";
    }

    /** Сторно. */
    @PostMapping("/{id}/reverse")
    public String reverse(@PathVariable UUID id,
                          @RequestParam LocalDate reversalDate,
                          @RequestParam(required = false) String reason,
                          RedirectAttributes redirect) {
        try {
            RefundReceipt receipt = refundReceiptService.reverse(id, reversalDate, reason);
            redirect.addFlashAttribute("message",
                    msg.get("rr.reversed", receipt.getRrNumber()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/refund-receipts/" + id;
    }

    // ---- ички ёрдамчилар ----

    /** Форма model'и (CreditMemo формаси қолипи + пул счётлари) - settings
     * оқим бошидаги snapshot (Sanjar-005, қайта SELECT қилинмайди). */
    private void fillFormModel(Model model, RefundReceiptForm form,
                               CompanySettings settings) {
        model.addAttribute("form", form);
        model.addAttribute("customers", contactService.byType(ContactType.CUSTOMER, false));
        model.addAttribute("currencies", currencyService.active());
        model.addAttribute("homeCurrency", settings.homeCurrencyCode());
        // Пул счётлари: BANK туридан, фаол ва postable (гаров service'да ҳам)
        model.addAttribute("bankAccounts", accountService.all().stream()
                .filter(a -> a.getType() == AccountType.BANK
                        && a.isActive() && a.isPostable())
                .toList());
        fillLineRefs(model, settings);
    }

    /** Сатр select'лари (CreditMemo fillLineRefs кўзгуси). */
    private void fillLineRefs(Model model, CompanySettings settings) {
        model.addAttribute("items", itemService.list(null, false));
        model.addAttribute("units", unitService.activeUnits());
        model.addAttribute("taxRates", taxRateService.activeRates());
        model.addAttribute("warehouses", warehouseService.all().stream()
                .filter(Warehouse::isActive).toList());
        // Class tracking (class-tracking.md): режим UI'ни бошқаради
        var mode = settings.getTrackClasses();
        model.addAttribute("classMode", mode.name());
        model.addAttribute("classes",
                mode == com.averpo.erp.shared.domain.ClassTrackingMode.OFF
                        ? List.<com.averpo.erp.shared.service.TxnClassService.ClassOption>of()
                        : txnClassService.activeForSelect());
    }

    /** Формани service маълумотига айлантиради (бўш сатрлар ташланади). */
    private RefundReceiptData toData(RefundReceiptForm form, CompanySettings settings) {
        boolean perTxn = settings.getTrackClasses()
                == com.averpo.erp.shared.domain.ClassTrackingMode.PER_TXN;
        UUID headerClass = FormParsers.uuid(form.getClassId(),
                BusinessRule.BR_CLS_001, "Йўналиш");
        List<LineData> lines = new ArrayList<>();
        int no = 0;
        for (RefundReceiptForm.LineForm lf : form.getLines()) {
            no++;
            if (lf.isEmpty()) {
                continue;
            }
            lines.add(new LineData(
                    FormParsers.uuid(lf.getItemId(), BusinessRule.BR_RET_001,
                            no + "-сатр: товар"),
                    FormParsers.uuid(lf.getWarehouseId(), BusinessRule.BR_RET_002,
                            no + "-сатр: омбор"),
                    FormParsers.decimal(lf.getQuantity(), BusinessRule.BR_RET_001,
                            no + "-сатр: миқдор"),
                    FormParsers.decimal(lf.getUnitPrice(), BusinessRule.BR_RET_001,
                            no + "-сатр: нарх"),
                    lf.getMemo(),
                    FormParsers.uuid(lf.getUnitId(), BusinessRule.BR_RET_001,
                            no + "-сатр: бирлик"),
                    FormParsers.uuid(lf.getTaxRateId(), BusinessRule.BR_TAX_004,
                            no + "-сатр: ставка"),
                    FormParsers.decimal(lf.getTaxRateValue(), BusinessRule.BR_TAX_002,
                            no + "-сатр: ставка қиймати"),
                    perTxn ? headerClass
                            : FormParsers.uuid(lf.getClassId(), BusinessRule.BR_CLS_001,
                                    no + "-сатр: Йўналиш")));
        }
        return new RefundReceiptData(
                FormParsers.uuid(form.getCustomerId(), BusinessRule.BR_RET_001, "Customer"),
                FormParsers.uuid(form.getInvoiceId(), BusinessRule.NOT_FOUND, "Invoice"),
                FormParsers.uuid(form.getBankAccountId(), BusinessRule.BR_RET_001,
                        "Пул счёти"),
                form.getRrDate(), form.getCurrency(),
                FormParsers.decimal(form.getExchangeRate(), BusinessRule.BR_RET_001, "Курс"),
                form.isAmountsInclusive(), form.getMemo(), lines);
    }

    /** Unit id → ном харитаси (кўриш экранида миқдор бирлиги учун, UoM). */
    private Map<UUID, String> unitNames() {
        Map<UUID, String> names = new HashMap<>();
        for (var unit : unitService.all()) {
            names.put(unit.getId(), unit.getName());
        }
        return names;
    }

    /** Кўриш сатрларида ҚҚС ставкаси номи учун харита (нофаоллар ҳам - тарих). */
    private Map<UUID, String> taxRateNames() {
        Map<UUID, String> names = new HashMap<>();
        for (var rate : taxRateService.all()) {
            names.put(rate.getId(), rate.getName());
        }
        return names;
    }
}
