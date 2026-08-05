package com.averpo.erp.purchase.web;

import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.inventory.domain.Warehouse;
import com.averpo.erp.inventory.service.WarehouseService;
import com.averpo.erp.item.domain.ItemType;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountClassification;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.purchase.domain.BillLineType;
import com.averpo.erp.purchase.domain.VendorCredit;
import com.averpo.erp.purchase.service.BillService;
import com.averpo.erp.purchase.service.VendorCreditService;
import com.averpo.erp.purchase.service.VendorCreditService.LineData;
import com.averpo.erp.purchase.service.VendorCreditService.VendorCreditData;
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
 * Таъминотчи кредит-нотаси экранлари (returns.md): саҳифаланган
 * рўйхат, FULL форма (bill қолипи, ҳаволали prefill), кўриш
 * («Қўллаш» бўлими + unapply + reverse). Ҳамма ёзиш
 * VendorCreditService орқали - контроллер юпқа.
 */
@Controller
@RequestMapping("/vendor-credits")
@RequiredArgsConstructor
public class VendorCreditController {

    /** Кредит-нотанинг ягона public API'си. */
    private final VendorCreditService vendorCreditService;

    /** Ҳаволали prefill ва «Қўллаш» рўйхати учун bill public API'си. */
    private final BillService billService;

    /** Vendor select ва номлари учун. */
    private final ContactService contactService;

    /** Сатр item select'и учун. */
    private final ItemService itemService;

    /** UoM: сатрдаги бирлик select'и учун. */
    private final com.averpo.erp.item.service.UnitService unitService;

    /** Омбор select'лари учун. */
    private final WarehouseService warehouseService;

    /** EXPENSE счёт select'и учун. */
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
                request, response, "vendor-credits");
        var creditPage = vendorCreditService.list(new VendorCreditService.ListFilter(
                from, to, parseStatusSafe(status), vendorId, q), page, size);
        model.addAttribute("credits", creditPage.getContent());
        model.addAttribute("page", creditPage);
        // Beruniy-032: бутун каталог эмас - саҳифадаги vendor id'лари бўйича IN
        Map<UUID, String> vendorNames = new HashMap<>();
        for (var ref : contactService.refsByIds(creditPage.getContent().stream()
                .map(c -> c.getVendorId()).distinct().toList())) {
            vendorNames.put(ref.id(), ref.displayName());
        }
        model.addAttribute("vendorNames", vendorNames);
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
        return "purchase/vendorCredits";
    }

    /** Query қийматидан статусни хавфсиз парслайди (бузуқ қиймат - филтрсиз). */
    private static VendorCredit.Status parseStatusSafe(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return VendorCredit.Status.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Янги форма: billId берилса асл ҳужжатдан prefill (QBO оқими). */
    @GetMapping("/new")
    public String createForm(@RequestParam(required = false) UUID billId, Model model) {
        VendorCreditForm form = billId == null
                ? VendorCreditForm.empty(3)
                : VendorCreditForm.from(billService.getWithLines(billId));
        // Sanjar-005: созламалар оқим бошида бир марта ўқилади - аввал ҳар
        // accessor (zoneId/homeCurrency/trackClasses) алоҳида SELECT берарди
        CompanySettings settings = settingsService.get();
        // Default сана - компания zoneId'даги «бугун» (JVM tz эмас, қоида 12/Arbitr-044)
        form.setVcDate(LocalDate.now(settings.zoneId()));
        fillFormModel(model, form, settings);
        return "purchase/vendorCreditForm";
    }

    /** HTMX partial: формага янги сатр қўшиш. */
    @GetMapping("/line-row")
    public String lineRow(@RequestParam int index, Model model) {
        model.addAttribute("index", index);
        fillLineRefs(model, settingsService.get());
        return "purchase/vendorCreditLineRow";
    }

    /** Яратиш - дарҳол POSTED. */
    @PostMapping
    public String create(@ModelAttribute VendorCreditForm form,
                         Model model, RedirectAttributes redirect) {
        // Sanjar-005: битта snapshot toData'га ҳам, хато қайтишига ҳам
        CompanySettings settings = settingsService.get();
        try {
            VendorCredit credit = vendorCreditService.create(toData(form, settings));
            redirect.addFlashAttribute("message",
                    msg.get("vc.saved", credit.getVcNumber()));
            return "redirect:/vendor-credits/" + credit.getId();
        } catch (BusinessRuleException e) {
            fillFormModel(model, form, settings);
            model.addAttribute("error", e.displayMessage());
            return "purchase/vendorCreditForm";
        }
    }

    /** Кўриш: сатрлар + «Қўллаш» бўлими (очиқ bill'лар) + reverse. */
    @GetMapping("/{id}")
    public String view(@PathVariable UUID id, Model model) {
        VendorCredit credit = vendorCreditService.getWithLines(id);
        model.addAttribute("credit", credit);
        model.addAttribute("vendorName",
                contactService.get(credit.getVendorId()).getDisplayName());
        // Item номлари - фақат шу ҳужжат сатрларидаги id'лар byIds/IN
        // сўровда (ARBITR-105б, Ulugbek-003 §1); EXPENSE сатрда itemId
        // null - филтрланади
        model.addAttribute("itemNames", itemService.namesByIds(
                credit.getLines().stream().map(l -> l.getItemId())
                        .filter(java.util.Objects::nonNull).distinct().toList()));
        model.addAttribute("accountNames", accountNames());
        model.addAttribute("taxRateNames", taxRateNames());
        model.addAttribute("applications", vendorCreditService.applicationsOf(id));
        // Қўллаш учун таъминотчининг очиқ bill'лари - фақат бир хил
        // валютада (BR-RET-004 UI даражасида ҳам)
        model.addAttribute("openBills",
                billService.openBills(credit.getVendorId()).stream()
                        .filter(bill -> bill.getCurrency().getCode()
                                .equals(credit.getCurrency().getCode()))
                        .toList());
        // Sanjar-005: созламалар snapshot'и - оқимда битта SELECT
        CompanySettings settings = settingsService.get();
        model.addAttribute("homeCurrency", settings.homeCurrencyCode());
        model.addAttribute("today",
                LocalDate.now(settings.zoneId()).toString());
        return "purchase/vendorCreditView";
    }

    /** Қўллаш: кредитдан танланган bill'га сумма. */
    @PostMapping("/{id}/apply")
    public String apply(@PathVariable UUID id, @RequestParam UUID billId,
                        @RequestParam String amount, RedirectAttributes redirect) {
        try {
            vendorCreditService.apply(id, billId,
                    FormParsers.decimal(amount, BusinessRule.BR_RET_001, "Сумма"));
            redirect.addFlashAttribute("message", msg.get("vc.applied"));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/vendor-credits/" + id;
    }

    /** Қўллашни бекор қилиш (unapply). */
    @PostMapping("/{id}/unapply")
    public String unapply(@PathVariable UUID id, @RequestParam UUID applicationId,
                          @RequestParam LocalDate reversalDate,
                          RedirectAttributes redirect) {
        try {
            vendorCreditService.unapply(applicationId, reversalDate);
            redirect.addFlashAttribute("message", msg.get("vc.unapplied"));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/vendor-credits/" + id;
    }

    /** Сторно (BR-RET-007 service'да). */
    @PostMapping("/{id}/reverse")
    public String reverse(@PathVariable UUID id,
                          @RequestParam LocalDate reversalDate,
                          @RequestParam(required = false) String reason,
                          RedirectAttributes redirect) {
        try {
            VendorCredit credit = vendorCreditService.reverse(id, reversalDate, reason);
            redirect.addFlashAttribute("message",
                    msg.get("vc.reversed", credit.getVcNumber()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/vendor-credits/" + id;
    }

    // ---- ички ёрдамчилар ----

    /** Форма model'и (bill формаси қолипи) - settings оқим бошидаги
     * snapshot (Sanjar-005, қайта SELECT қилинмайди). */
    private void fillFormModel(Model model, VendorCreditForm form,
                               CompanySettings settings) {
        model.addAttribute("form", form);
        model.addAttribute("vendors", contactService.byType(ContactType.VENDOR, false));
        model.addAttribute("currencies", currencyService.active());
        model.addAttribute("homeCurrency", settings.homeCurrencyCode());
        fillLineRefs(model, settings);
    }

    /** Сатр select'лари (bill fillLineRefs кўзгуси). */
    private void fillLineRefs(Model model, CompanySettings settings) {
        model.addAttribute("items", itemService.list(ItemType.INVENTORY, false));
        model.addAttribute("units", unitService.activeUnits());
        model.addAttribute("taxRates", taxRateService.activeRates());
        model.addAttribute("warehouses", warehouseService.all().stream()
                .filter(Warehouse::isActive).toList());
        // Arbitr-014: all() - EXPENSE группа счётлари ҳам киради, select'да
        // disabled жилд бўлади (нофаолларни accountOptions ташлайди)
        model.addAttribute("expenseAccounts", accountService.all().stream()
                .filter(a -> a.getClassification() == AccountClassification.EXPENSE)
                .toList());
        // Class tracking (class-tracking.md): режим UI'ни бошқаради
        var mode = settings.getTrackClasses();
        model.addAttribute("classMode", mode.name());
        model.addAttribute("classes",
                mode == com.averpo.erp.shared.domain.ClassTrackingMode.OFF
                        ? List.<com.averpo.erp.shared.service.TxnClassService.ClassOption>of()
                        : txnClassService.activeForSelect());
    }

    /** Формани service маълумотига айлантиради (бўш сатрлар ташланади). */
    private VendorCreditData toData(VendorCreditForm form, CompanySettings settings) {
        boolean perTxn = settings.getTrackClasses()
                == com.averpo.erp.shared.domain.ClassTrackingMode.PER_TXN;
        UUID headerClass = FormParsers.uuid(form.getClassId(),
                BusinessRule.BR_CLS_001, "Йўналиш");
        List<LineData> lines = new ArrayList<>();
        int no = 0;
        for (VendorCreditForm.LineForm lf : form.getLines()) {
            no++;
            if (lf.isEmpty()) {
                continue;
            }
            lines.add(new LineData(parseType(no, lf.getType()),
                    FormParsers.uuid(lf.getItemId(), BusinessRule.BR_RET_001,
                            no + "-сатр: товар"),
                    FormParsers.uuid(lf.getWarehouseId(), BusinessRule.BR_RET_002,
                            no + "-сатр: омбор"),
                    FormParsers.decimal(lf.getQuantity(), BusinessRule.BR_RET_001,
                            no + "-сатр: миқдор"),
                    FormParsers.decimal(lf.getUnitPrice(), BusinessRule.BR_RET_001,
                            no + "-сатр: нарх"),
                    FormParsers.uuid(lf.getAccountId(), BusinessRule.BR_RET_001,
                            no + "-сатр: счёт"),
                    FormParsers.decimal(lf.getAmount(), BusinessRule.BR_RET_001,
                            no + "-сатр: сумма"),
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
        return new VendorCreditData(
                FormParsers.uuid(form.getVendorId(), BusinessRule.BR_RET_001, "Vendor"),
                FormParsers.uuid(form.getBillId(), BusinessRule.NOT_FOUND, "Bill"),
                form.getVcDate(), form.getCurrency(),
                FormParsers.decimal(form.getExchangeRate(), BusinessRule.BR_RET_001, "Курс"),
                form.isAmountsInclusive(), form.getMemo(), lines);
    }

    /** Сатр турини парслайди - бузуқ қийматга BR-RET-001. */
    private BillLineType parseType(int no, String type) {
        try {
            return BillLineType.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessRuleException(BusinessRule.BR_RET_001,
                    no + "-сатр: нотўғри тур «" + type + "»");
        }
    }

    /** Счёт id → ном харитаси (кўришда EXPENSE сатрлар учун). */
    private Map<UUID, String> accountNames() {
        Map<UUID, String> names = new HashMap<>();
        for (Account account : accountService.all()) {
            names.put(account.getId(), account.getName());
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
