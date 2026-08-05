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
import com.averpo.erp.purchase.domain.Bill;
import com.averpo.erp.purchase.domain.BillLineType;
import com.averpo.erp.purchase.domain.BillStatus;
import com.averpo.erp.purchase.service.BillService;
import com.averpo.erp.purchase.service.BillService.BillData;
import com.averpo.erp.purchase.service.BillService.LineData;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bill экранлари: рўйхат (статус филтри), форма (HTMX сатр қўшиш,
 * валюта курси prefill), кўриш, post/reverse/draft-delete. Ҳамма ёзиш
 * BillService орқали - контроллер юпқа (validation + view render).
 */
@Controller
@RequestMapping("/bills")
@RequiredArgsConstructor
public class BillController {

    /** Bill'нинг ягона public API'си. */
    private final BillService billService;

    /** Қайтариш интеграцияси (returns.md): қўлланган/яратилган кредитлар. */
    private final com.averpo.erp.purchase.service.VendorCreditService vendorCreditService;

    /** PO айлантириш оқими (estimates-po.md): prefill + markConverted. */
    private final com.averpo.erp.purchase.service.PurchaseOrderService purchaseOrderService;

    /** Vendor select ва номлари учун. */
    private final ContactService contactService;

    /** ITEM сатр select'лари учун. */
    private final ItemService itemService;

    /** UoM: сатрдаги бирлик select'и ва кўришда бирлик номлари учун. */
    private final com.averpo.erp.item.service.UnitService unitService;

    /** Омбор select'лари учун. */
    private final WarehouseService warehouseService;

    /** EXPENSE счёт select'и ва кўришда счёт номлари учун. */
    private final AccountService accountService;

    /** Валюта select'и учун. */
    private final CurrencyService currencyService;

    /** Home currency - курс майдонининг default'и учун. */
    private final CompanySettingsService settingsService;

    /** ҚҚС ставкаси select'и ва кўришда ном/фоиз учун - tax модули public API'си. */
    private final com.averpo.erp.tax.service.TaxRateService taxRateService;

    /** Йўналиш select'и (class-tracking.md) - shared каталог. */
    private final com.averpo.erp.shared.service.TxnClassService txnClassService;

    /** Flash хабарлар учун i18n. */
    private final com.averpo.erp.i18n.Msg msg;

    /**
     * Рўйхат - ихтиёрий статус филтри; T11 drill-down учун vendor ва
     * «фақат очиқ» филтрлари (AP aging қатори шу манзилга боради).
     * Саҳифаланган (Beruniy-perf1): ?page=, филтрлар саҳифа линкларида
     * сақланади; open drill-down йўли аввалгидек саҳифасиз. Устун
     * саралаш (ARBITR-105б): ?sort=/&dir= service whitelist'и орқали;
     * th линклари филтрни (sort'сиз), саҳифа линклари филтр+sort'ни
     * бирга ташийди.
     */
    @GetMapping
    public String list(@RequestParam(required = false) LocalDate from,
                       @RequestParam(required = false) LocalDate to,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) UUID vendorId,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false, defaultValue = "false") boolean open,
                       @RequestParam(required = false, defaultValue = "0") int page,
                       @RequestParam(required = false) String sort,
                       @RequestParam(required = false) String dir,
                       jakarta.servlet.http.HttpServletRequest request,
                       jakarta.servlet.http.HttpServletResponse response,
                       Model model) {
        List<Bill> bills;
        if (vendorId != null && open) {
            // Aging'даги маънонинг ўзи: POSTED ва қолдиғи > 0
            bills = billService.openBills(vendorId);
        } else {
            // ARBITR-105: саҳифа ҳажми ?size=/cookie'дан (PageSizeResolver)
            int size = com.averpo.erp.shared.web.PageSizeResolver.resolve(
                    request, response, "bills");
            // ARBITR-105б: хом sort/dir whitelist орқали (Sort'га тушмайди)
            var sorted = BillService.sortOf(sort, dir);
            org.springframework.data.domain.Page<Bill> billPage = billService.list(
                    new BillService.ListFilter(from, to, parseStatusSafe(status),
                            vendorId, q), page, size, sorted.sort());
            bills = billPage.getContent();
            model.addAttribute("page", billPage);
            // Саҳифа линклари жорий филтрларни сақлайди (audit қолипи);
            // th саралаш линклари учун sort'сиз, pager учун sort билан
            String filterQuery = new com.averpo.erp.shared.web.FilterQuery()
                    .add("from", from).add("to", to).add("status", status)
                    .add("vendorId", vendorId).add("q", q).toString();
            model.addAttribute("filterQuery", filterQuery);
            model.addAttribute("pageQuery", filterQuery + sorted.query());
            model.addAttribute("sortKey", sorted.key());
            model.addAttribute("sortDir", sorted.dir());
        }
        // Vendor номлари - фақат саҳифадаги сатрлар (+ филтр чипи id'си)
        // byIds/IN сўровда (ARBITR-105б, Ulugbek-003 §1)
        java.util.Set<UUID> vendorIds = new java.util.HashSet<>();
        for (Bill bill : bills) {
            vendorIds.add(bill.getVendorId());
        }
        if (vendorId != null) {
            vendorIds.add(vendorId);
        }
        Map<UUID, String> vendorNames = contactService.namesByIds(vendorIds);
        model.addAttribute("bills", bills);
        model.addAttribute("vendorNames", vendorNames);
        // Филтр select'и учун фаол таъминотчиларнинг енгил рўйхати
        model.addAttribute("vendors", contactService.activeRefsByType(ContactType.VENDOR));
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("from", from == null ? "" : from.toString());
        model.addAttribute("to", to == null ? "" : to.toString());
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("vendorId", vendorId == null ? "" : vendorId.toString());
        // Open drill-down чипи: рўйхат нега қисқалигини кўрсатади
        model.addAttribute("filterVendorName",
                vendorId == null ? null : vendorNames.get(vendorId));
        model.addAttribute("filterOpen", open);
        return "purchase/bills";
    }

    /**
     * Янги bill формаси - 3 та бўш сатр билан. purchaseOrderId берилса
     * (айлантириш, estimates-po.md) форма буюртмадан PREFILL бўлади:
     * таъминотчи/валюта/сатрлар/ставкалар; сана - бугунги.
     */
    @GetMapping("/new")
    public String createForm(@RequestParam(required = false) UUID purchaseOrderId,
                             Model model) {
        // Sanjar-005: созламалар оқим бошида бир марта ўқилади - аввал ҳар
        // accessor (zoneId/homeCurrency/trackClasses) алоҳида SELECT берарди
        CompanySettings settings = settingsService.get();
        BillForm form = purchaseOrderId == null
                ? BillForm.empty(3)
                : BillForm.fromPurchaseOrder(
                        purchaseOrderService.requireConvertible(purchaseOrderId),
                        settings.homeCurrencyCode());
        // Default сана - компания zoneId'даги «бугун» (JVM tz эмас, қоида 12/Arbitr-044)
        form.setBillDate(LocalDate.now(settings.zoneId()));
        fillFormModel(model, form, settings);
        return "purchase/billForm";
    }

    /** Мавжуд DRAFT'ни таҳрирлаш формаси. */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model,
                           RedirectAttributes redirect) {
        Bill bill = billService.getWithLines(id);
        if (bill.getStatus() != BillStatus.DRAFT) {
            redirect.addFlashAttribute("error", msg.get("bill.onlyDraftEditable"));
            return "redirect:/bills/" + id;
        }
        fillFormModel(model, BillForm.from(bill), settingsService.get());
        return "purchase/billForm";
    }

    /** HTMX partial: формага янги сатр қўшиш. */
    @GetMapping("/line-row")
    public String lineRow(@RequestParam int index, Model model) {
        model.addAttribute("index", index);
        fillLineRefs(model, settingsService.get());
        return "purchase/billLineRow";
    }

    /** Сақлаш: action=draft - фақат сақлаш, action=post - сақлаш + post. */
    @PostMapping
    public String save(@ModelAttribute BillForm form,
                       @RequestParam String action,
                       Model model, RedirectAttributes redirect) {
        // Sanjar-005: битта snapshot toData'га ҳам, хато қайтишига ҳам
        CompanySettings settings = settingsService.get();
        try {
            BillData data = toData(form, settings);
            // Айлантириш (estimates-po.md): bill яратилишидан ОЛДИН
            // текширилади - айлантириб бўлмайдиган буюртма учун ҳужжат
            // яратилиб қолмасин (BR-PO-002/003)
            UUID purchaseOrderId = FormParsers.uuid(form.getPurchaseOrderId(),
                    BusinessRule.NOT_FOUND, "Буюртма");
            if (purchaseOrderId != null) {
                purchaseOrderService.requireConvertible(purchaseOrderId);
            }
            Bill bill = form.getId() == null || form.getId().isBlank()
                    ? billService.createDraft(data)
                    : billService.updateDraft(FormParsers.uuid(form.getId(),
                            BusinessRule.NOT_FOUND, "Bill"), data);
            if ("post".equals(action)) {
                // post ўз транзакциясида янги ҳолатни қайтаради - flash
                // хабар эскирган DRAFT ҳолатини кўрсатмасин
                bill = billService.post(bill.getId());
            }
            if (purchaseOrderId != null) {
                // Сақлангач манба CLOSED + linked bill id (spec)
                purchaseOrderService.markConverted(purchaseOrderId, bill.getId());
            }
            redirect.addFlashAttribute("message", msg.get("bill.saved",
                    bill.getBillNumber(),
                    msg.get("status." + bill.getStatus().name())));
            return "redirect:/bills/" + bill.getId();
        } catch (BusinessRuleException e) {
            fillFormModel(model, form, settings);
            model.addAttribute("error", e.displayMessage());
            return "purchase/billForm";
        }
    }

    /** Битта bill'ни кўриш. */
    @GetMapping("/{id}")
    public String view(@PathVariable UUID id, Model model) {
        Bill bill = billService.getWithLines(id);
        model.addAttribute("bill", bill);
        model.addAttribute("vendorName",
                contactService.get(bill.getVendorId()).getDisplayName());
        // Item номлари - фақат шу ҳужжат сатрларидаги id'лар byIds/IN
        // сўровда (ARBITR-105б, Ulugbek-003 §1: бутун каталог юкланмайди)
        model.addAttribute("itemNames", itemService.namesByIds(
                bill.getLines().stream().map(l -> l.getItemId())
                        .filter(java.util.Objects::nonNull).distinct().toList()));
        model.addAttribute("warehouseNames", warehouseNames());
        model.addAttribute("accountNames", accountNames());
        model.addAttribute("unitNames", unitNames());
        model.addAttribute("taxRateNames", taxRateNames());
        // Sanjar-005: созламалар snapshot'и - оқимда битта SELECT
        CompanySettings settings = settingsService.get();
        model.addAttribute("homeCurrency", settings.homeCurrencyCode());
        model.addAttribute("today", LocalDate.now(settings.zoneId()).toString());
        // «Буюртмадан» белгиси (estimates-po.md): linked манба бўлса
        model.addAttribute("sourcePurchaseOrder",
                purchaseOrderService.findByBillId(bill.getId()).orElse(null));
        // Қайтариш интеграцияси (returns.md): қўлланган/яратилган кредитлар
        model.addAttribute("vendorCreditApplications",
                vendorCreditService.applicationsForBill(bill.getId()));
        model.addAttribute("vendorCreditsFromThis",
                vendorCreditService.byBill(bill.getId()));
        return "purchase/billView";
    }

    /** Draft'ни post қилиш. */
    @PostMapping("/{id}/post")
    public String post(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            Bill bill = billService.post(id);
            redirect.addFlashAttribute("message",
                    msg.get("bill.posted", bill.getBillNumber()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/bills/" + id;
    }

    /** POSTED bill'ни сторно қилиш (GL + омбор қайтади). */
    @PostMapping("/{id}/reverse")
    public String reverse(@PathVariable UUID id,
                          @RequestParam LocalDate reversalDate,
                          @RequestParam(required = false) String reason,
                          RedirectAttributes redirect) {
        try {
            Bill bill = billService.reverse(id, reversalDate, reason);
            redirect.addFlashAttribute("message",
                    msg.get("bill.reversed", bill.getBillNumber()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/bills/" + id;
    }

    /** Draft'ни ўчириш. */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            Bill bill = billService.get(id);
            String number = bill.getBillNumber();
            billService.deleteDraft(id);
            redirect.addFlashAttribute("message", msg.get("bill.deleted", number));
            return "redirect:/bills";
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
            return "redirect:/bills/" + id;
        }
    }

    // ---- ички ёрдамчилар ----

    /** Форма model'ини тўлдиради (select маълумотлари билан) - settings
     * оқим бошидаги snapshot (Sanjar-005, қайта SELECT қилинмайди). */
    private void fillFormModel(Model model, BillForm form,
                               CompanySettings settings) {
        model.addAttribute("form", form);
        model.addAttribute("vendors", contactService.byType(ContactType.VENDOR, false));
        model.addAttribute("currencies", currencyService.active());
        model.addAttribute("homeCurrency", settings.homeCurrencyCode());
        fillLineRefs(model, settings);
    }

    /** Сатр select'лари: товарлар, бирликлар, омборлар, харажат счётлари. */
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
        // Class tracking (class-tracking.md): режим UI'ни бошқаради -
        // OFF'да рўйхат сўралмайди ҳам (майдонлар render бўлмайди)
        var mode = settings.getTrackClasses();
        model.addAttribute("classMode", mode.name());
        model.addAttribute("classes",
                mode == com.averpo.erp.shared.domain.ClassTrackingMode.OFF
                        ? List.<com.averpo.erp.shared.service.TxnClassService.ClassOption>of()
                        : txnClassService.activeForSelect());
    }

    /** Формани BillService маълумотига айлантиради (бўш сатрлар ташланади). */
    private BillData toData(BillForm form, CompanySettings settings) {
        // PER_TXN (class-tracking.md): сарлавҳадаги битта Йўналиш ҳамма
        // сатрга тарқатилади - схема ягона, class доим сатрда туради
        boolean perTxn = settings.getTrackClasses()
                == com.averpo.erp.shared.domain.ClassTrackingMode.PER_TXN;
        UUID headerClass = FormParsers.uuid(form.getClassId(),
                BusinessRule.BR_CLS_001, "Йўналиш");
        List<LineData> lines = new ArrayList<>();
        int no = 0;
        for (BillForm.LineForm lf : form.getLines()) {
            no++;
            if (lf.isEmpty()) {
                continue;
            }
            lines.add(new LineData(parseType(no, lf.getType()),
                    FormParsers.uuid(lf.getItemId(), BusinessRule.BR_BILL_004,
                            no + "-сатр: товар"),
                    FormParsers.uuid(lf.getWarehouseId(), BusinessRule.BR_BILL_004,
                            no + "-сатр: омбор"),
                    parseNumber(no, lf.getQuantity(), "миқдор"),
                    parseNumber(no, lf.getUnitPrice(), "нарх"),
                    FormParsers.uuid(lf.getAccountId(), BusinessRule.BR_BILL_005,
                            no + "-сатр: счёт"),
                    parseNumber(no, lf.getAmount(), "сумма"),
                    lf.getMemo(),
                    // unitFactor/taxRateValue/taxAmount формадан келмайди -
                    // service snapshot қилади ва net/tax'ни ҳисоблайди
                    FormParsers.uuid(lf.getUnitId(), BusinessRule.BR_BILL_004,
                            no + "-сатр: бирлик"),
                    null,
                    FormParsers.uuid(lf.getTaxRateId(), BusinessRule.BR_TAX_004,
                            no + "-сатр: ставка"),
                    null, null,
                    perTxn ? headerClass
                            : FormParsers.uuid(lf.getClassId(), BusinessRule.BR_CLS_001,
                                    no + "-сатр: Йўналиш")));
        }
        return new BillData(FormParsers.uuid(form.getVendorId(),
                        BusinessRule.BR_BILL_001, "Vendor"),
                form.getVendorInvoiceNumber(), form.getBillDate(), form.getDueDate(),
                form.getCurrency(), parseRate(form.getExchangeRate()),
                form.getMemo(), form.isAmountsInclusive(), lines);
    }

    /** Сатр турини парслайди - бузуқ қийматга BR-BILL-003. */
    private BillLineType parseType(int no, String type) {
        try {
            return BillLineType.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessRuleException(BusinessRule.BR_BILL_003,
                    no + "-сатр: нотўғри тур «" + type + "»");
        }
    }

    /** Курс матни - бўш → null (home'да сервер 1 қилади); FormParsers қоидаси. */
    private BigDecimal parseRate(String text) {
        return FormParsers.decimal(text, BusinessRule.BR_BILL_009, "Курс");
    }

    /** Сон парси - normalize (пробел/NBSP/вергул) FormParsers'да. */
    private BigDecimal parseNumber(int no, String text, String field) {
        return FormParsers.decimal(text, BusinessRule.BR_BILL_003, no + "-сатр: " + field);
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

    /** Кўриш сатрларида счёт номлари учун харита. */
    private Map<UUID, String> accountNames() {
        Map<UUID, String> names = new HashMap<>();
        for (Account account : accountService.all()) {
            names.put(account.getId(), account.getName());
        }
        return names;
    }

    /** Query параметрдан статусни хавфсиз парслайди (?status=abc → филтрсиз). */
    private static BillStatus parseStatusSafe(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return BillStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
