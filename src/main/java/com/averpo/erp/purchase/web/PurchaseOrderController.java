package com.averpo.erp.purchase.web;

import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.purchase.domain.PurchaseOrder;
import com.averpo.erp.purchase.domain.PurchaseOrderStatus;
import com.averpo.erp.purchase.service.BillService;
import com.averpo.erp.purchase.service.PurchaseOrderService;
import com.averpo.erp.purchase.service.PurchaseOrderService.LineData;
import com.averpo.erp.purchase.service.PurchaseOrderService.PurchaseOrderData;
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
 * PurchaseOrder экранлари (docs/modules/estimates-po.md) -
 * EstimateController'нинг харид томонидаги кўзгуси. «Bill'га
 * айлантириш» тугмаси /bills/new?purchaseOrderId=... га олиб боради -
 * prefill/markConverted оқими BillController'да.
 */
@Controller
@RequestMapping("/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    /** Буюртманинг ягона public API'си. */
    private final PurchaseOrderService purchaseOrderService;

    /** Кўришда linked bill рақамини кўрсатиш учун. */
    private final BillService billService;

    /** Vendor select ва номлари учун. */
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
     * Рўйхат - саҳифаланган (PERF-perf1); тўлиқ филтр қатори
     * (DEC-068): давр/статус/vendor/матн, саҳифа линклари филтрни
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
                request, response, "purchase-orders");
        var orderPage = purchaseOrderService.list(new PurchaseOrderService.ListFilter(
                from, to, parseStatusSafe(status), vendorId, q), page, size);
        model.addAttribute("orders", orderPage.getContent());
        model.addAttribute("page", orderPage);
        model.addAttribute("filterQuery", new com.averpo.erp.shared.web.FilterQuery()
                .add("from", from).add("to", to).add("status", status)
                .add("vendorId", vendorId).add("q", q).toString());
        // Vendor номлари - фақат саҳифадаги сатрлар (+ филтр id'си)
        // byIds/IN сўровда (DEC-105б, AUD-003 §1)
        java.util.Set<UUID> vendorIds = new java.util.HashSet<>();
        for (PurchaseOrder order : orderPage.getContent()) {
            vendorIds.add(order.getVendorId());
        }
        if (vendorId != null) {
            vendorIds.add(vendorId);
        }
        model.addAttribute("vendorNames", contactService.namesByIds(vendorIds));
        // Филтр select'и учун фаол таъминотчиларнинг енгил рўйхати
        model.addAttribute("vendors", contactService.activeRefsByType(ContactType.VENDOR));
        model.addAttribute("from", from == null ? "" : from.toString());
        model.addAttribute("to", to == null ? "" : to.toString());
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("vendorId", vendorId == null ? "" : vendorId.toString());
        model.addAttribute("q", q == null ? "" : q);
        return "purchase/purchaseOrders";
    }

    /** Янги буюртма формаси - 3 та бўш сатр билан. */
    @GetMapping("/new")
    public String createForm(Model model) {
        PurchaseOrderForm form = PurchaseOrderForm.empty(3);
        // Default сана - компания zoneId'даги «бугун» (JVM tz эмас, қоида 12/DEC-044)
        form.setPoDate(LocalDate.now(settingsService.zoneId()));
        fillFormModel(model, form);
        return "purchase/purchaseOrderForm";
    }

    /** Мавжуд буюртмани таҳрирлаш формаси (BR-PO-002 guard'и билан). */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model,
                           RedirectAttributes redirect) {
        PurchaseOrder po = purchaseOrderService.getWithLines(id);
        if (po.getStatus() == PurchaseOrderStatus.CLOSED) {
            redirect.addFlashAttribute("error", msg.get("po.onlyEditable"));
            return "redirect:/purchase-orders/" + id;
        }
        fillFormModel(model, PurchaseOrderForm.from(po));
        return "purchase/purchaseOrderForm";
    }

    /** HTMX partial: формага янги сатр қўшиш. */
    @GetMapping("/line-row")
    public String lineRow(@RequestParam int index, Model model) {
        model.addAttribute("index", index);
        fillLineRefs(model);
        return "purchase/purchaseOrderLineRow";
    }

    /** Сақлаш: яратиш ёки таҳрир (GL'сиз - post тугмаси йўқ). */
    @PostMapping
    public String save(@ModelAttribute PurchaseOrderForm form,
                       Model model, RedirectAttributes redirect) {
        try {
            PurchaseOrderData data = toData(form);
            PurchaseOrder po = form.getId() == null || form.getId().isBlank()
                    ? purchaseOrderService.create(data)
                    : purchaseOrderService.update(FormParsers.uuid(form.getId(),
                            BusinessRule.NOT_FOUND, "Буюртма"), data);
            redirect.addFlashAttribute("message", msg.get("po.saved", po.getPoNumber()));
            return "redirect:/purchase-orders/" + po.getId();
        } catch (BusinessRuleException e) {
            fillFormModel(model, form);
            model.addAttribute("error", e.displayMessage());
            return "purchase/purchaseOrderForm";
        }
    }

    /** Битта буюртмани кўриш - status амаллари билан. */
    @GetMapping("/{id}")
    public String view(@PathVariable UUID id, Model model) {
        PurchaseOrder po = purchaseOrderService.getWithLines(id);
        model.addAttribute("po", po);
        model.addAttribute("vendorName",
                contactService.get(po.getVendorId()).getDisplayName());
        // Item номлари - фақат шу ҳужжат сатрларидаги id'лар byIds/IN
        // сўровда (DEC-105б, AUD-003 §1)
        model.addAttribute("itemNames", itemService.namesByIds(
                po.getLines().stream().map(l -> l.getItemId())
                        .filter(java.util.Objects::nonNull).distinct().toList()));
        model.addAttribute("unitNames", unitNames());
        model.addAttribute("taxRateNames", taxRateNames());
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        // Linked bill рақами - CLOSED+linked ҳужжатда ҳавола учун
        model.addAttribute("linkedBillNumber", po.getBillId() == null
                ? null : billService.get(po.getBillId()).getBillNumber());
        return "purchase/purchaseOrderView";
    }

    /** Status ўтиши (қоидалар PurchaseOrder.changeStatus'да). */
    @PostMapping("/{id}/status")
    public String changeStatus(@PathVariable UUID id, @RequestParam String status,
                               RedirectAttributes redirect) {
        try {
            PurchaseOrderStatus to = PurchaseOrderStatus.valueOf(status);
            PurchaseOrder po = purchaseOrderService.changeStatus(id, to);
            redirect.addFlashAttribute("message", msg.get("po.statusChanged",
                    po.getPoNumber(), msg.get(to.titleKey())));
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", msg.get("po.onlyEditable"));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/purchase-orders/" + id;
    }

    /** Ўчириш (BR-PO-003 guard'и билан). */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            String number = purchaseOrderService.get(id).getPoNumber();
            purchaseOrderService.delete(id);
            redirect.addFlashAttribute("message", msg.get("po.deleted", number));
            return "redirect:/purchase-orders";
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
            return "redirect:/purchase-orders/" + id;
        }
    }

    // ---- ички ёрдамчилар ----

    /** Форма model'ини тўлдиради (select маълумотлари билан). */
    private void fillFormModel(Model model, PurchaseOrderForm form) {
        model.addAttribute("form", form);
        model.addAttribute("vendors", contactService.byType(ContactType.VENDOR, false));
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

    /** Формани PurchaseOrderService маълумотига айлантиради. */
    private PurchaseOrderData toData(PurchaseOrderForm form) {
        List<LineData> lines = new ArrayList<>();
        int no = 0;
        for (PurchaseOrderForm.LineForm lf : form.getLines()) {
            no++;
            if (lf.isEmpty()) {
                continue;
            }
            lines.add(new LineData(
                    FormParsers.uuid(lf.getItemId(), BusinessRule.BR_PO_001,
                            no + "-сатр: товар"),
                    parseNumber(no, lf.getQuantity(), "миқдор"),
                    parseNumber(no, lf.getUnitPrice(), "нарх"),
                    FormParsers.uuid(lf.getUnitId(), BusinessRule.BR_PO_001,
                            no + "-сатр: бирлик"),
                    FormParsers.uuid(lf.getTaxRateId(), BusinessRule.BR_TAX_004,
                            no + "-сатр: ставка"),
                    lf.getMemo()));
        }
        return new PurchaseOrderData(
                FormParsers.uuid(form.getVendorId(), BusinessRule.BR_PO_001, "Таъминотчи"),
                form.getPoDate(), form.getExpectedDate(), form.getCurrency(),
                FormParsers.decimal(form.getExchangeRate(), BusinessRule.BR_PO_001, "Курс"),
                form.getMemo(), form.isAmountsInclusive(), lines);
    }

    /** Сон парси - normalize (пробел/NBSP/вергул) FormParsers'да. */
    private BigDecimal parseNumber(int no, String text, String field) {
        return FormParsers.decimal(text, BusinessRule.BR_PO_001, no + "-сатр: " + field);
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
    private static PurchaseOrderStatus parseStatusSafe(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return PurchaseOrderStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
