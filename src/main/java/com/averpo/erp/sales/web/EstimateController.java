package com.averpo.erp.sales.web;

import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.sales.domain.Estimate;
import com.averpo.erp.sales.domain.EstimateStatus;
import com.averpo.erp.sales.service.EstimateService;
import com.averpo.erp.sales.service.EstimateService.EstimateData;
import com.averpo.erp.sales.service.EstimateService.LineData;
import com.averpo.erp.sales.service.InvoiceService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Estimate экранлари (docs/modules/estimates-po.md): рўйхат (статус
 * филтри), FULL транзакция формаси (invoice қолипи - HTMX сатр,
 * жонли жами), кўриш (status амаллари + «Invoice'га айлантириш»).
 * Айлантириш тугмаси /invoices/new?estimateId=... га олиб боради -
 * prefill/markConverted оқими InvoiceController'да. Ҳамма ёзиш
 * EstimateService орқали - контроллер юпқа.
 */
@Controller
@RequestMapping("/estimates")
@RequiredArgsConstructor
public class EstimateController {

    /** Estimate'нинг ягона public API'си. */
    private final EstimateService estimateService;

    /** Кўришда linked invoice рақамини кўрсатиш учун. */
    private final InvoiceService invoiceService;

    /** Customer select ва номлари учун. */
    private final ContactService contactService;

    /** Сатр item select'и учун. */
    private final ItemService itemService;

    /** UoM: сатрдаги бирлик select'и ва кўришда бирлик номлари учун. */
    private final com.averpo.erp.item.service.UnitService unitService;

    /** Валюта select'и учун. */
    private final CurrencyService currencyService;

    /** Home currency - курс майдонининг default'и учун. */
    private final CompanySettingsService settingsService;

    /** ҚҚС ставкаси select'и ва кўришда ном учун. */
    private final com.averpo.erp.tax.service.TaxRateService taxRateService;

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
                request, response, "estimates");
        var estimatePage = estimateService.list(new EstimateService.ListFilter(
                from, to, parseStatusSafe(status), customerId, q), page, size);
        model.addAttribute("estimates", estimatePage.getContent());
        model.addAttribute("page", estimatePage);
        model.addAttribute("filterQuery", new com.averpo.erp.shared.web.FilterQuery()
                .add("from", from).add("to", to).add("status", status)
                .add("customerId", customerId).add("q", q).toString());
        // Мижоз номлари - фақат саҳифа қаторларидаги id'лар
        // byIds/IN сўровда (ARBITR-105б, Ulugbek-003 §1)
        model.addAttribute("customerNames", contactService.namesByIds(
                estimatePage.getContent().stream().map(e -> e.getCustomerId())
                        .filter(java.util.Objects::nonNull).distinct().toList()));
        // Филтр select'и учун фаол мижозларнинг енгил рўйхати
        model.addAttribute("customers", contactService.activeRefsByType(ContactType.CUSTOMER));
        model.addAttribute("from", from == null ? "" : from.toString());
        model.addAttribute("to", to == null ? "" : to.toString());
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("customerId", customerId == null ? "" : customerId.toString());
        model.addAttribute("q", q == null ? "" : q);
        return "sales/estimates";
    }

    /** Янги estimate формаси - 3 та бўш сатр билан. */
    @GetMapping("/new")
    public String createForm(Model model) {
        EstimateForm form = EstimateForm.empty(3);
        // Default сана - компания zoneId'даги «бугун» (JVM tz эмас, қоида 12/Arbitr-044)
        form.setEstimateDate(LocalDate.now(settingsService.zoneId()));
        fillFormModel(model, form);
        return "sales/estimateForm";
    }

    /** Мавжуд ҳужжатни таҳрирлаш формаси (BR-EST-002 guard'и билан). */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model,
                           RedirectAttributes redirect) {
        Estimate estimate = estimateService.getWithLines(id);
        if (estimate.getStatus() == EstimateStatus.CLOSED
                || estimate.getStatus() == EstimateStatus.REJECTED) {
            redirect.addFlashAttribute("error", msg.get("est.onlyEditable"));
            return "redirect:/estimates/" + id;
        }
        fillFormModel(model, EstimateForm.from(estimate));
        return "sales/estimateForm";
    }

    /** HTMX partial: формага янги сатр қўшиш. */
    @GetMapping("/line-row")
    public String lineRow(@RequestParam int index, Model model) {
        model.addAttribute("index", index);
        fillLineRefs(model);
        return "sales/estimateLineRow";
    }

    /** Сақлаш: яратиш ёки таҳрир (GL'сиз - post тугмаси йўқ). */
    @PostMapping
    public String save(@ModelAttribute EstimateForm form,
                       Model model, RedirectAttributes redirect) {
        try {
            EstimateData data = toData(form);
            Estimate estimate = form.getId() == null || form.getId().isBlank()
                    ? estimateService.create(data)
                    : estimateService.update(FormParsers.uuid(form.getId(),
                            BusinessRule.NOT_FOUND, "Estimate"), data);
            redirect.addFlashAttribute("message",
                    msg.get("est.saved", estimate.getEstimateNumber()));
            return "redirect:/estimates/" + estimate.getId();
        } catch (BusinessRuleException e) {
            fillFormModel(model, form);
            model.addAttribute("error", e.displayMessage());
            return "sales/estimateForm";
        }
    }

    /** Битта estimate'ни кўриш - status амаллари билан. */
    @GetMapping("/{id}")
    public String view(@PathVariable UUID id, Model model) {
        Estimate estimate = estimateService.getWithLines(id);
        model.addAttribute("estimate", estimate);
        model.addAttribute("customerName",
                contactService.get(estimate.getCustomerId()).getDisplayName());
        // Item номлари - фақат шу ҳужжат сатрларидаги id'лар
        // byIds/IN сўровда (ARBITR-105б, Ulugbek-003 §1)
        model.addAttribute("itemNames", itemService.namesByIds(
                estimate.getLines().stream().map(l -> l.getItemId())
                        .filter(java.util.Objects::nonNull).distinct().toList()));
        model.addAttribute("unitNames", unitNames());
        model.addAttribute("taxRateNames", taxRateNames());
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        // Linked invoice рақами - CLOSED+linked ҳужжатда ҳавола учун
        model.addAttribute("linkedInvoiceNumber", estimate.getInvoiceId() == null
                ? null : invoiceService.get(estimate.getInvoiceId()).getInvoiceNumber());
        return "sales/estimateView";
    }

    /** Status ўтиши (қоидалар Estimate.changeStatus'да). */
    @PostMapping("/{id}/status")
    public String changeStatus(@PathVariable UUID id, @RequestParam String status,
                               RedirectAttributes redirect) {
        try {
            EstimateStatus to = EstimateStatus.valueOf(status);
            Estimate estimate = estimateService.changeStatus(id, to);
            redirect.addFlashAttribute("message", msg.get("est.statusChanged",
                    estimate.getEstimateNumber(), msg.get(to.titleKey())));
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", msg.get("est.onlyEditable"));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/estimates/" + id;
    }

    /** Ўчириш (BR-EST-003 guard'и билан). */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            String number = estimateService.get(id).getEstimateNumber();
            estimateService.delete(id);
            redirect.addFlashAttribute("message", msg.get("est.deleted", number));
            return "redirect:/estimates";
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
            return "redirect:/estimates/" + id;
        }
    }

    // ---- ички ёрдамчилар ----

    /** Форма model'ини тўлдиради (select маълумотлари билан). */
    private void fillFormModel(Model model, EstimateForm form) {
        model.addAttribute("form", form);
        model.addAttribute("customers", contactService.byType(ContactType.CUSTOMER, false));
        model.addAttribute("currencies", currencyService.active());
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        fillLineRefs(model);
    }

    /** Сатр select'лари: барча фаол item'лар, бирликлар, ставкалар. */
    private void fillLineRefs(Model model) {
        model.addAttribute("items", itemService.list(null, false));
        model.addAttribute("units", unitService.activeUnits());
        model.addAttribute("taxRates", taxRateService.activeRates());
    }

    /** Формани EstimateService маълумотига айлантиради (бўш сатрлар ташланади). */
    private EstimateData toData(EstimateForm form) {
        List<LineData> lines = new ArrayList<>();
        int no = 0;
        for (EstimateForm.LineForm lf : form.getLines()) {
            no++;
            if (lf.isEmpty()) {
                continue;
            }
            lines.add(new LineData(
                    FormParsers.uuid(lf.getItemId(), BusinessRule.BR_EST_001,
                            no + "-сатр: товар"),
                    parseNumber(no, lf.getQuantity(), "миқдор"),
                    parseNumber(no, lf.getUnitPrice(), "нарх"),
                    FormParsers.uuid(lf.getUnitId(), BusinessRule.BR_EST_001,
                            no + "-сатр: бирлик"),
                    FormParsers.uuid(lf.getTaxRateId(), BusinessRule.BR_TAX_004,
                            no + "-сатр: ставка"),
                    lf.getMemo()));
        }
        return new EstimateData(
                FormParsers.uuid(form.getCustomerId(), BusinessRule.BR_EST_001, "Мижоз"),
                form.getEstimateDate(), form.getExpirationDate(), form.getCurrency(),
                FormParsers.decimal(form.getExchangeRate(), BusinessRule.BR_EST_001, "Курс"),
                form.getMemo(), form.isAmountsInclusive(), lines);
    }

    /** Сон парси - normalize (пробел/NBSP/вергул) FormParsers'да. */
    private BigDecimal parseNumber(int no, String text, String field) {
        return FormParsers.decimal(text, BusinessRule.BR_EST_001, no + "-сатр: " + field);
    }

    /** Кўриш сатрларида бирлик номлари учун харита (UoM). */
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

    /** Query параметрдан статусни хавфсиз парслайди (?status=abc → филтрсиз). */
    private static EstimateStatus parseStatusSafe(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return EstimateStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
