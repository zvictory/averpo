package com.averpo.erp.sales.web;

import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.inventory.domain.Warehouse;
import com.averpo.erp.inventory.service.WarehouseService;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.ledger.domain.AccountType;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.ledger.service.LedgerDashboardService;
import com.averpo.erp.sales.domain.SalesReceipt;
import com.averpo.erp.sales.service.SalesReceiptService;
import com.averpo.erp.sales.service.SalesReceiptService.LineData;
import com.averpo.erp.sales.service.SalesReceiptService.SalesReceiptData;
import com.averpo.erp.shared.domain.CompanySettings;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.service.CurrencyService;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Сотув чеки экранлари (posting-rules «Сотув чеки»): саҳифаланган
 * рўйхат, FULL форма (invoice қолипи + тўлов счёти жонли Balance билан),
 * кўриш (reverse шу ерда - allocation йўқ). Ҳамма ёзиш
 * SalesReceiptService орқали.
 *
 * @author Zafar
 */
@Controller
@RequestMapping("/sales-receipts")
@RequiredArgsConstructor
public class SalesReceiptController {

    /** Чекнинг ягона public API'си. */
    private final SalesReceiptService salesReceiptService;

    /** Customer select ва номлари учун. */
    private final ContactService contactService;

    /** Сатр item select'и учун. */
    private final ItemService itemService;

    /** UoM: сатрдаги бирлик select'и учун. */
    private final com.averpo.erp.item.service.UnitService unitService;

    /** Омбор select'лари учун. */
    private final WarehouseService warehouseService;

    /** Тўлов счёти select'и учун. */
    private final AccountService accountService;

    /** Тўлов счёти жонли Balance'и учун (read-only қолдиқлар). */
    private final LedgerDashboardService dashboardService;

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
                request, response, "sales-receipts");
        var receiptPage = salesReceiptService.list(new SalesReceiptService.ListFilter(
                from, to, parseStatusSafe(status), customerId, q), page, size);
        model.addAttribute("receipts", receiptPage.getContent());
        model.addAttribute("page", receiptPage);
        // Beruniy-032: бутун каталог эмас - саҳифадаги мижоз id'лари бўйича IN
        Map<UUID, String> customerNames = new HashMap<>();
        for (var ref : contactService.refsByIds(receiptPage.getContent().stream()
                .map(sr -> sr.getCustomerId()).distinct().toList())) {
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
        return "sales/salesReceipts";
    }

    /** Query қийматидан статусни хавфсиз парслайди (бузуқ қиймат - филтрсиз). */
    private static SalesReceipt.Status parseStatusSafe(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return SalesReceipt.Status.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Янги форма - 3 бўш сатр билан. */
    @GetMapping("/new")
    public String createForm(Model model) {
        SalesReceiptForm form = SalesReceiptForm.empty(3);
        // Sanjar-005: созламалар оқим бошида бир марта ўқилади - аввал ҳар
        // accessor (zoneId/homeCurrency/trackClasses) алоҳида SELECT берарди
        CompanySettings settings = settingsService.get();
        // Default сана - компания zoneId'даги «бугун» (JVM tz эмас, қоида 12/Arbitr-044)
        form.setSrDate(LocalDate.now(settings.zoneId()));
        fillFormModel(model, form, settings);
        return "sales/salesReceiptForm";
    }

    /** HTMX partial: формага янги сатр қўшиш. */
    @GetMapping("/line-row")
    public String lineRow(@RequestParam int index, Model model) {
        model.addAttribute("index", index);
        fillLineRefs(model, settingsService.get());
        return "sales/salesReceiptLineRow";
    }

    /** Яратиш - дарҳол POSTED. */
    @PostMapping
    public String create(@ModelAttribute SalesReceiptForm form,
                         Model model, RedirectAttributes redirect) {
        // Sanjar-005: битта snapshot toData'га ҳам, хато қайтишига ҳам
        CompanySettings settings = settingsService.get();
        try {
            SalesReceipt receipt = salesReceiptService.create(toData(form, settings));
            redirect.addFlashAttribute("message",
                    msg.get("sr.saved", receipt.getSrNumber()));
            return "redirect:/sales-receipts/" + receipt.getId();
        } catch (BusinessRuleException e) {
            fillFormModel(model, form, settings);
            model.addAttribute("error", e.displayMessage());
            return "sales/salesReceiptForm";
        }
    }

    /** Кўриш: сатрлар + reverse (allocation йўқ - тугал ҳужжат). */
    @GetMapping("/{id}")
    public String view(@PathVariable UUID id, Model model) {
        SalesReceipt receipt = salesReceiptService.getWithLines(id);
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
        return "sales/salesReceiptView";
    }

    /** Сторно. */
    @PostMapping("/{id}/reverse")
    public String reverse(@PathVariable UUID id,
                          @RequestParam LocalDate reversalDate,
                          @RequestParam(required = false) String reason,
                          RedirectAttributes redirect) {
        try {
            SalesReceipt receipt = salesReceiptService.reverse(id, reversalDate, reason);
            redirect.addFlashAttribute("message",
                    msg.get("sr.reversed", receipt.getSrNumber()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/sales-receipts/" + id;
    }

    // ---- ички ёрдамчилар ----

    /** Форма model'и (invoice форма қолипи + тўлов счётлари жонли Balance) -
     * settings оқим бошидаги snapshot (Sanjar-005, қайта SELECT қилинмайди). */
    private void fillFormModel(Model model, SalesReceiptForm form,
                               CompanySettings settings) {
        model.addAttribute("form", form);
        model.addAttribute("customers", contactService.byType(ContactType.CUSTOMER, false));
        model.addAttribute("currencies", currencyService.active());
        model.addAttribute("homeCurrency", settings.homeCurrencyCode());
        // Тўлов счётлари: BANK туридан, фаол ва postable (гаров service'да ҳам)
        model.addAttribute("bankAccounts", accountService.all().stream()
                .filter(a -> a.getType() == AccountType.BANK
                        && a.isActive() && a.isPostable())
                .toList());
        // Жонли Balance: транзфер формаси қолипи (LedgerDashboardService
        // read-only қолдиқлари, банк ўз валютасида)
        Map<String, String> bankBalances = new HashMap<>();
        String home = settings.homeCurrencyCode();
        for (LedgerDashboardService.BankBalance balance : dashboardService.bankBalances()) {
            String code = balance.currencyCode() != null ? balance.currencyCode() : home;
            bankBalances.put(balance.accountId().toString(),
                    Fmt.money(balance.amount()) + " " + code);
        }
        model.addAttribute("bankBalances", bankBalances);
        fillLineRefs(model, settings);
    }

    /** Сатр select'лари (invoice/refund fillLineRefs кўзгуси). */
    private void fillLineRefs(Model model, CompanySettings settings) {
        model.addAttribute("items", itemService.list(null, false));
        model.addAttribute("units", unitService.activeUnits());
        model.addAttribute("taxRates", taxRateService.activeRates());
        model.addAttribute("warehouses", warehouseService.all().stream()
                .filter(Warehouse::isActive).toList());
        var mode = settings.getTrackClasses();
        model.addAttribute("classMode", mode.name());
        model.addAttribute("classes",
                mode == com.averpo.erp.shared.domain.ClassTrackingMode.OFF
                        ? List.<com.averpo.erp.shared.service.TxnClassService.ClassOption>of()
                        : txnClassService.activeForSelect());
    }

    /** Формани service маълумотига айлантиради (бўш сатрлар ташланади). */
    private SalesReceiptData toData(SalesReceiptForm form, CompanySettings settings) {
        boolean perTxn = settings.getTrackClasses()
                == com.averpo.erp.shared.domain.ClassTrackingMode.PER_TXN;
        UUID headerClass = FormParsers.uuid(form.getClassId(),
                BusinessRule.BR_CLS_001, "Йўналиш");
        List<LineData> lines = new ArrayList<>();
        int no = 0;
        for (SalesReceiptForm.LineForm lf : form.getLines()) {
            no++;
            if (lf.isEmpty()) {
                continue;
            }
            lines.add(new LineData(
                    FormParsers.uuid(lf.getItemId(), BusinessRule.BR_SR_001,
                            no + "-сатр: товар"),
                    FormParsers.uuid(lf.getWarehouseId(), BusinessRule.BR_SR_001,
                            no + "-сатр: омбор"),
                    FormParsers.decimal(lf.getQuantity(), BusinessRule.BR_SR_001,
                            no + "-сатр: миқдор"),
                    FormParsers.decimal(lf.getUnitPrice(), BusinessRule.BR_SR_001,
                            no + "-сатр: нарх"),
                    lf.getMemo(),
                    FormParsers.uuid(lf.getUnitId(), BusinessRule.BR_SR_001,
                            no + "-сатр: бирлик"),
                    FormParsers.uuid(lf.getTaxRateId(), BusinessRule.BR_TAX_004,
                            no + "-сатр: ставка"),
                    FormParsers.decimal(lf.getTaxRateValue(), BusinessRule.BR_TAX_002,
                            no + "-сатр: ставка қиймати"),
                    perTxn ? headerClass
                            : FormParsers.uuid(lf.getClassId(), BusinessRule.BR_CLS_001,
                                    no + "-сатр: Йўналиш")));
        }
        return new SalesReceiptData(
                FormParsers.uuid(form.getCustomerId(), BusinessRule.BR_SR_001, "Customer"),
                FormParsers.uuid(form.getBankAccountId(), BusinessRule.BR_SR_002,
                        "Тўлов счёти"),
                form.getSrDate(), form.getCurrency(),
                FormParsers.decimal(form.getExchangeRate(), BusinessRule.BR_SR_001, "Курс"),
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
