package com.averpo.erp.sales.web;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.inventory.domain.Warehouse;
import com.averpo.erp.inventory.service.WarehouseService;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.sales.domain.Invoice;
import com.averpo.erp.sales.domain.InvoiceStatus;
import com.averpo.erp.sales.service.InvoicePaymentService;
import com.averpo.erp.sales.service.InvoiceService;
import com.averpo.erp.sales.service.InvoiceService.InvoiceData;
import com.averpo.erp.sales.service.InvoiceService.LineData;
import com.averpo.erp.shared.domain.CompanySettings;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Invoice экранлари: рўйхат (статус филтри), форма (HTMX сатр қўшиш,
 * item танланганда сотув нархи prefill, курс prefill), кўриш (credit
 * limit огоҳлантириши билан), post/reverse/draft-delete. Ҳамма ёзиш
 * InvoiceService орқали - контроллер юпқа.
 */
@Controller
@RequestMapping("/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    /** Invoice'нинг ягона public API'си. */
    private final InvoiceService invoiceService;

    /** Кўришда тўлов тақсимотларини кўрсатиш учун. */
    private final InvoicePaymentService paymentService;

    /** Кўришда қўлланган кредитлар + «Қайтариш яратиш» (returns.md). */
    private final com.averpo.erp.sales.service.CreditMemoService creditMemoService;

    /** Estimate айлантириш оқими (estimates-po.md): prefill + markConverted. */
    private final com.averpo.erp.sales.service.EstimateService estimateService;

    /** Customer select ва номлари учун. */
    private final ContactService contactService;

    /** Сатр item select'и учун. */
    private final ItemService itemService;

    /** UoM: сатрдаги бирлик select'и ва кўришда бирлик номлари учун. */
    private final com.averpo.erp.item.service.UnitService unitService;

    /** Омбор select'лари учун. */
    private final WarehouseService warehouseService;

    /** Валюта select'и учун. */
    private final CurrencyService currencyService;

    /** Home currency - курс майдонининг default'и учун. */
    private final CompanySettingsService settingsService;

    /** ҚҚС ставкаси select'и ва кўришда ном учун - tax модули public API'си. */
    private final com.averpo.erp.tax.service.TaxRateService taxRateService;

    /** Йўналиш select'и (class-tracking.md) - shared каталог. */
    private final com.averpo.erp.shared.service.TxnClassService txnClassService;

    /** Flash хабарлар учун i18n. */
    private final com.averpo.erp.i18n.Msg msg;

    /**
     * Рўйхат - тўлиқ филтр қатори (DEC-068, list-filters.md): давр
     * (from/to), статус, мижоз select, матн (рақам/изоҳ). AR aging
     * drill-down (customer + «фақат очиқ», T11 паттерни) аввалгидек
     * саҳифасиз. Саҳифаланган (PERF-perf1): ?page=, филтрлар саҳифа
     * линкларида сақланади (FilterQuery, audit қолипи). Устун саралаш
     * (DEC-105б): ?sort=/&dir= service whitelist'и орқали ечилади;
     * th линклари филтрни (sort'сиз), саҳифа линклари филтр+sort'ни
     * бирга ташийди.
     */
    @GetMapping
    public String list(@RequestParam(required = false) LocalDate from,
                       @RequestParam(required = false) LocalDate to,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) UUID customerId,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false, defaultValue = "false") boolean open,
                       @RequestParam(required = false, defaultValue = "0") int page,
                       @RequestParam(required = false) String sort,
                       @RequestParam(required = false) String dir,
                       jakarta.servlet.http.HttpServletRequest request,
                       jakarta.servlet.http.HttpServletResponse response,
                       Model model) {
        List<Invoice> invoices;
        if (customerId != null && open) {
            // Aging'даги маънонинг ўзи: POSTED ва қолдиғи > 0
            invoices = invoiceService.openInvoices(customerId);
        } else {
            // DEC-105: саҳифа ҳажми ?size=/cookie'дан (PageSizeResolver)
            int size = com.averpo.erp.shared.web.PageSizeResolver.resolve(
                    request, response, "invoices");
            // DEC-105б: хом sort/dir whitelist орқали (Sort'га тушмайди)
            var sorted = InvoiceService.sortOf(sort, dir);
            org.springframework.data.domain.Page<Invoice> invoicePage = invoiceService.list(
                    new InvoiceService.ListFilter(from, to, parseStatusSafe(status),
                            customerId, q), page, size, sorted.sort());
            invoices = invoicePage.getContent();
            model.addAttribute("page", invoicePage);
            // Саҳифа линклари жорий филтрларни сақлайди (audit қолипи);
            // th саралаш линклари учун sort'сиз, pager учун sort билан
            String filterQuery = new com.averpo.erp.shared.web.FilterQuery()
                    .add("from", from).add("to", to).add("status", status)
                    .add("customerId", customerId).add("q", q).toString();
            model.addAttribute("filterQuery", filterQuery);
            model.addAttribute("pageQuery", filterQuery + sorted.query());
            model.addAttribute("sortKey", sorted.key());
            model.addAttribute("sortDir", sorted.dir());
        }
        // Филтр select'и учун мижозлар (ном тартибида) + ном харитаси
        List<Contact> customers = contactService.byType(ContactType.CUSTOMER, true);
        Map<UUID, String> customerNames = new HashMap<>();
        for (Contact contact : customers) {
            customerNames.put(contact.getId(), contact.getDisplayName());
        }
        model.addAttribute("invoices", invoices);
        model.addAttribute("customerNames", customerNames);
        model.addAttribute("customers", customers);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("from", from == null ? "" : from.toString());
        model.addAttribute("to", to == null ? "" : to.toString());
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("customerId", customerId == null ? "" : customerId.toString());
        // Open drill-down чипи: рўйхат нега қисқалигини кўрсатади
        model.addAttribute("filterCustomerName",
                customerId == null ? null : customerNames.get(customerId));
        model.addAttribute("filterOpen", open);
        return "sales/invoices";
    }

    /**
     * Янги invoice формаси - 3 та бўш сатр билан. estimateId берилса
     * (айлантириш, estimates-po.md) форма estimate'дан PREFILL бўлади:
     * мижоз/валюта/сатрлар/ставкалар; сана - бугунги.
     */
    @GetMapping("/new")
    public String createForm(@RequestParam(required = false) UUID estimateId,
                             Model model) {
        // OPT-005: созламалар оқим бошида бир марта ўқилади - аввал ҳар
        // accessor (zoneId/homeCurrency/trackClasses) алоҳида SELECT берарди
        CompanySettings settings = settingsService.get();
        InvoiceForm form = estimateId == null
                ? InvoiceForm.empty(3)
                : InvoiceForm.fromEstimate(estimateService.requireConvertible(estimateId),
                        settings.homeCurrencyCode());
        // Default сана - компания zoneId'даги «бугун» (JVM tz эмас, қоида 12/DEC-044)
        form.setInvoiceDate(LocalDate.now(settings.zoneId()));
        fillFormModel(model, form, settings);
        return "sales/invoiceForm";
    }

    /** Мавжуд DRAFT'ни таҳрирлаш формаси. */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model,
                           RedirectAttributes redirect) {
        Invoice invoice = invoiceService.getWithLines(id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            redirect.addFlashAttribute("error", msg.get("sinv.onlyDraftEditable"));
            return "redirect:/invoices/" + id;
        }
        fillFormModel(model, InvoiceForm.from(invoice), settingsService.get());
        return "sales/invoiceForm";
    }

    /** HTMX partial: формага янги сатр қўшиш. */
    @GetMapping("/line-row")
    public String lineRow(@RequestParam int index, Model model) {
        model.addAttribute("index", index);
        fillLineRefs(model, settingsService.get());
        return "sales/invoiceLineRow";
    }

    /** Сақлаш: action=draft - фақат сақлаш, action=post - сақлаш + post. */
    @PostMapping
    public String save(@ModelAttribute InvoiceForm form,
                       @RequestParam String action,
                       Model model, RedirectAttributes redirect) {
        // OPT-005: битта snapshot toData'га ҳам, хато қайтишига ҳам
        CompanySettings settings = settingsService.get();
        try {
            InvoiceData data = toData(form, settings);
            // Айлантириш (estimates-po.md): invoice яратилишидан ОЛДИН
            // текширилади - айлантириб бўлмайдиган estimate учун ҳужжат
            // яратилиб қолмасин (BR-EST-002/003)
            UUID estimateId = FormParsers.uuid(form.getEstimateId(),
                    BusinessRule.NOT_FOUND, "Estimate");
            if (estimateId != null) {
                estimateService.requireConvertible(estimateId);
            }
            Invoice invoice = form.getId() == null || form.getId().isBlank()
                    ? invoiceService.createDraft(data)
                    : invoiceService.updateDraft(FormParsers.uuid(form.getId(),
                            BusinessRule.NOT_FOUND, "Invoice"), data);
            if ("post".equals(action)) {
                // post ўз транзакциясида янги ҳолатни қайтаради - flash
                // хабар эскирган DRAFT ҳолатини кўрсатмасин
                invoice = invoiceService.post(invoice.getId());
            }
            if (estimateId != null) {
                // Сақлангач манба CLOSED + linked invoice id (spec)
                estimateService.markConverted(estimateId, invoice.getId());
            }
            redirect.addFlashAttribute("message", msg.get("sinv.saved",
                    invoice.getInvoiceNumber(),
                    msg.get("status." + invoice.getStatus().name())));
            return "redirect:/invoices/" + invoice.getId();
        } catch (BusinessRuleException e) {
            fillFormModel(model, form, settings);
            model.addAttribute("error", e.displayMessage());
            return "sales/invoiceForm";
        }
    }

    /** Битта invoice'ни кўриш - credit limit огоҳлантириши билан. */
    @GetMapping("/{id}")
    public String view(@PathVariable UUID id, Model model) {
        Invoice invoice = invoiceService.getWithLines(id);
        model.addAttribute("invoice", invoice);
        model.addAttribute("customerName",
                contactService.get(invoice.getCustomerId()).getDisplayName());
        // Item номлари - фақат шу ҳужжат сатрларидаги id'лар byIds/IN
        // сўровда (DEC-105б, AUD-003 §1: бутун каталог юкланмайди)
        model.addAttribute("itemNames", itemService.namesByIds(
                invoice.getLines().stream().map(l -> l.getItemId())
                        .filter(java.util.Objects::nonNull).distinct().toList()));
        model.addAttribute("warehouseNames", warehouseNames());
        model.addAttribute("unitNames", unitNames());
        model.addAttribute("taxRateNames", taxRateNames());
        model.addAttribute("allocations",
                paymentService.allocationsForInvoice(invoice.getId()));
        // Қайтариш интеграцияси (returns.md): қўлланган кредитлар +
        // шу ҳужжатдан яратилган кредит-ноталар
        model.addAttribute("creditApplications",
                creditMemoService.applicationsForInvoice(invoice.getId()));
        model.addAttribute("creditsFromThis",
                creditMemoService.byInvoice(invoice.getId()));
        // OPT-005: созламалар snapshot'и - оқимда битта SELECT
        CompanySettings settings = settingsService.get();
        model.addAttribute("homeCurrency", settings.homeCurrencyCode());
        model.addAttribute("today", LocalDate.now(settings.zoneId()).toString());
        // «Estimate'дан» белгиси (estimates-po.md): linked манба бўлса
        model.addAttribute("sourceEstimate",
                estimateService.findByInvoiceId(invoice.getId()).orElse(null));
        // Credit limit: DRAFT'да шу ҳужжат ҳали очиқ AR'да йўқ - қўшиб
        // текширилади (мижоз валютасида: getTotal, home getTotalBase эмас -
        // лимит мижоз валютасида); POSTED'да аллақачон очиқ AR ичида
        BigDecimal additional = invoice.getStatus() == InvoiceStatus.DRAFT
                ? invoice.getTotal() : null;
        model.addAttribute("creditCheck",
                invoiceService.creditCheck(invoice.getCustomerId(), additional));
        return "sales/invoiceView";
    }

    /** Draft'ни post қилиш. */
    @PostMapping("/{id}/post")
    public String post(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            Invoice invoice = invoiceService.post(id);
            redirect.addFlashAttribute("message",
                    msg.get("sinv.posted", invoice.getInvoiceNumber()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/invoices/" + id;
    }

    /** POSTED invoice'ни сторно қилиш (GL + товар омборга қайтади). */
    @PostMapping("/{id}/reverse")
    public String reverse(@PathVariable UUID id,
                          @RequestParam LocalDate reversalDate,
                          @RequestParam(required = false) String reason,
                          RedirectAttributes redirect) {
        try {
            Invoice invoice = invoiceService.reverse(id, reversalDate, reason);
            redirect.addFlashAttribute("message",
                    msg.get("sinv.reversed", invoice.getInvoiceNumber()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/invoices/" + id;
    }

    /** Draft'ни ўчириш. */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            String number = invoiceService.get(id).getInvoiceNumber();
            invoiceService.deleteDraft(id);
            redirect.addFlashAttribute("message", msg.get("sinv.deleted", number));
            return "redirect:/invoices";
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
            return "redirect:/invoices/" + id;
        }
    }

    // ---- ички ёрдамчилар ----

    /** Форма model'ини тўлдиради (select маълумотлари билан) - settings
     * оқим бошидаги snapshot (OPT-005, қайта SELECT қилинмайди). */
    private void fillFormModel(Model model, InvoiceForm form,
                               CompanySettings settings) {
        model.addAttribute("form", form);
        model.addAttribute("customers", contactService.byType(ContactType.CUSTOMER, false));
        model.addAttribute("currencies", currencyService.active());
        model.addAttribute("homeCurrency", settings.homeCurrencyCode());
        fillLineRefs(model, settings);
    }

    /** Сатр select'лари: барча фаол item'лар, бирликлар ва фаол омборлар. */
    private void fillLineRefs(Model model, CompanySettings settings) {
        model.addAttribute("items", itemService.list(null, false));
        model.addAttribute("units", unitService.activeUnits());
        model.addAttribute("taxRates", taxRateService.activeRates());
        model.addAttribute("warehouses", warehouseService.all().stream()
                .filter(Warehouse::isActive).toList());
        // Class tracking (class-tracking.md): режим UI'ни бошқаради -
        // OFF'да рўйхат сўралмайди ҳам (майдонлар render бўлмайди)
        var mode = settings.getTrackClasses();
        model.addAttribute("classMode", mode.name());
        model.addAttribute("classes",
                mode == com.averpo.erp.shared.domain.ClassTrackingMode.OFF
                        ? List.<com.averpo.erp.shared.service.TxnClassService.ClassOption>of()
                        : txnClassService.activeForSelect());
    }

    /** Формани InvoiceService маълумотига айлантиради (бўш сатрлар ташланади). */
    private InvoiceData toData(InvoiceForm form, CompanySettings settings) {
        // PER_TXN (class-tracking.md): сарлавҳадаги битта Йўналиш ҳамма
        // сатрга тарқатилади - схема ягона, class доим сатрда туради
        boolean perTxn = settings.getTrackClasses()
                == com.averpo.erp.shared.domain.ClassTrackingMode.PER_TXN;
        UUID headerClass = FormParsers.uuid(form.getClassId(),
                BusinessRule.BR_CLS_001, "Йўналиш");
        List<LineData> lines = new ArrayList<>();
        int no = 0;
        for (InvoiceForm.LineForm lf : form.getLines()) {
            no++;
            if (lf.isEmpty()) {
                continue;
            }
            lines.add(new LineData(
                    FormParsers.uuid(lf.getItemId(), BusinessRule.BR_SINV_004,
                            no + "-сатр: товар"),
                    FormParsers.uuid(lf.getWarehouseId(), BusinessRule.BR_SINV_004,
                            no + "-сатр: омбор"),
                    parseNumber(no, lf.getQuantity(), "миқдор"),
                    parseNumber(no, lf.getUnitPrice(), "нарх"),
                    null, lf.getMemo(),
                    // unitFactor/taxRateValue/taxAmount формадан келмайди -
                    // service snapshot қилади ва net/tax'ни ҳисоблайди
                    FormParsers.uuid(lf.getUnitId(), BusinessRule.BR_SINV_004,
                            no + "-сатр: бирлик"),
                    FormParsers.uuid(lf.getTaxRateId(), BusinessRule.BR_TAX_004,
                            no + "-сатр: ставка"),
                    null, null,
                    perTxn ? headerClass
                            : FormParsers.uuid(lf.getClassId(), BusinessRule.BR_CLS_001,
                                    no + "-сатр: Йўналиш")));
        }
        return new InvoiceData(
                FormParsers.uuid(form.getCustomerId(), BusinessRule.BR_SINV_001,
                        "Customer"),
                form.getInvoiceDate(),
                form.getDueDate(), form.getCurrency(), parseRate(form.getExchangeRate()),
                form.getMemo(), form.isAmountsInclusive(), lines);
    }

    /** Курс матни - бўш → null (home'да сервер 1 қилади); FormParsers қоидаси. */
    private BigDecimal parseRate(String text) {
        return FormParsers.decimal(text, BusinessRule.BR_SINV_008, "Курс");
    }

    /** Сон парси - normalize (пробел/NBSP/вергул) FormParsers'да. */
    private BigDecimal parseNumber(int no, String text, String field) {
        return FormParsers.decimal(text, BusinessRule.BR_SINV_003, no + "-сатр: " + field);
    }

    /** Кўриш сатрларида бирлик номлари учун харита (UoM). */
    private Map<UUID, String> unitNames() {
        Map<UUID, String> names = new HashMap<>();
        for (var unit : unitService.all()) {
            names.put(unit.getId(), unit.getName());
        }
        return names;
    }

    /** Кўриш сатрларида омбор номлари учун харита. */
    private Map<UUID, String> warehouseNames() {
        Map<UUID, String> names = new HashMap<>();
        for (Warehouse warehouse : warehouseService.all()) {
            names.put(warehouse.getId(), warehouse.getName());
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

    /** Query параметрдан статусни хавфсиз парслайди (?status=abc → филтрсиз). */
    private static InvoiceStatus parseStatusSafe(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return InvoiceStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
