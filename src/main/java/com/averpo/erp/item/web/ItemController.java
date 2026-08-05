package com.averpo.erp.item.web;

import com.averpo.erp.i18n.Msg;
import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.domain.ItemType;
import com.averpo.erp.item.service.ItemCategoryService;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.item.service.UnitService;
import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountClassification;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.domain.AccountType;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.CompanySettingsService;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Маҳсулот ва хизматлар экранлари (QBO Products and Services).
 * Счёт select'лари ledger'нинг public AccountService'идан олинади
 * (ТЕМИР ҚОИДА №6).
 */
@Controller
@RequestMapping("/items")
@RequiredArgsConstructor
public class ItemController {

    /** Item service. */
    private final ItemService itemService;

    /** Категория select'и ва рўйхати учун. */
    private final ItemCategoryService categoryService;

    /** Бирлик select'и учун. */
    private final UnitService unitService;

    /** Счёт select'лари учун ledger public API. */
    private final AccountService accountService;

    /** Нарх майдонлари home валютада эканини кўрсатиш учун (U5). */
    private final CompanySettingsService settingsService;

    /** ҚҚС ставкаси select'и (item default'лари) - tax модули public API'си. */
    private final com.averpo.erp.tax.service.TaxRateService taxRateService;

    /** Flash хабарлар учун i18n. */
    private final Msg msg;

    /**
     * Рўйхат: стандарт каталог филтри (DEC-068) - тур select, фаоллик
     * (фаол/нофаол/ҳаммаси, default фаол) ва матн (ном/SKU). Эски
     * showInactive=true линклари «ҳаммаси» деб тушунилади (бузилмайди).
     */
    @GetMapping
    public String list(@RequestParam(required = false) String type,
                       @RequestParam(required = false) String activity,
                       @RequestParam(defaultValue = "false") boolean showInactive,
                       @RequestParam(required = false) String q,
                       Model model) {
        ItemType filter = parseType(type);
        String act = activity != null && !activity.isBlank()
                ? activity : (showInactive ? "ALL" : "ACTIVE");
        Boolean active = switch (act) {
            case "INACTIVE" -> Boolean.FALSE;
            case "ALL" -> null;
            default -> Boolean.TRUE;
        };
        model.addAttribute("items", itemService.list(
                new ItemService.ListFilter(filter, active, q)));
        model.addAttribute("typeFilter", filter == null ? "" : filter.name());
        model.addAttribute("activity", active == null ? "ALL"
                : (active ? "ACTIVE" : "INACTIVE"));
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        return "item/list";
    }

    /** Янги item формаси - HTMX'да drawer partial, оддийда тўлиқ саҳифа (fallback). */
    @GetMapping("/new")
    public String createForm(Model model, jakarta.servlet.http.HttpServletRequest request) {
        fillFormModel(model, new ItemForm(), null);
        return com.averpo.erp.shared.web.Htmx.isHtmx(request)
                ? "item/formDrawer" : "item/form";
    }

    /** Янги item сақлаш. */
    @PostMapping
    public String create(@ModelAttribute ItemForm form,
                         Model model, RedirectAttributes redirect,
                         jakarta.servlet.http.HttpServletRequest request,
                         jakarta.servlet.http.HttpServletResponse response) {
        try {
            ItemType type = requireType(form.getType());
            itemService.create(type, form.toData());
        } catch (BusinessRuleException e) {
            fillFormModel(model, form, null);
            model.addAttribute("error", e.displayMessage());
            // DEC-024: хато drawer ичида қайта render бўлади
            return com.averpo.erp.shared.web.Htmx.isHtmx(request)
                    ? "item/formDrawer" : "item/form";
        }
        if (com.averpo.erp.shared.web.Htmx.isHtmx(request)) {
            return com.averpo.erp.shared.web.Htmx.redirect(request, response,
                    "/items", "message", msg.get("item.created", form.getName()));
        }
        redirect.addFlashAttribute("message", msg.get("item.created", form.getName()));
        return "redirect:/items";
    }

    /** Таҳрир формаси - HTMX'да drawer partial, оддийда тўлиқ саҳифа (fallback). */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model,
                           jakarta.servlet.http.HttpServletRequest request) {
        Item item = itemService.get(id);
        fillFormModel(model, ItemForm.from(item), id);
        return com.averpo.erp.shared.web.Htmx.isHtmx(request)
                ? "item/formDrawer" : "item/form";
    }

    /** Таҳрирни сақлаш - тип ўзгармайди (spec қарори). */
    @PostMapping("/{id}")
    public String update(@PathVariable UUID id, @ModelAttribute ItemForm form,
                         Model model, RedirectAttributes redirect,
                         jakarta.servlet.http.HttpServletRequest request,
                         jakarta.servlet.http.HttpServletResponse response) {
        try {
            itemService.update(id, form.toData(), form.isActive());
        } catch (BusinessRuleException e) {
            fillFormModel(model, form, id);
            model.addAttribute("error", e.displayMessage());
            // DEC-024: хато drawer ичида қайта render бўлади
            return com.averpo.erp.shared.web.Htmx.isHtmx(request)
                    ? "item/formDrawer" : "item/form";
        }
        if (com.averpo.erp.shared.web.Htmx.isHtmx(request)) {
            return com.averpo.erp.shared.web.Htmx.redirect(request, response,
                    "/items", "message", msg.get("item.updated", form.getName()));
        }
        redirect.addFlashAttribute("message", msg.get("item.updated", form.getName()));
        return "redirect:/items";
    }

    /** Форма model'ини тўлдиради - счётлар туркум бўйича ажратилган. */
    private void fillFormModel(Model model, ItemForm form, UUID editId) {
        // DEC-014: all() - группа счётлар select'да disabled жилд бўлади
        // (нофаолларни accountOptions partial'и ташлайди)
        List<Account> accounts = accountService.all();
        model.addAttribute("form", form);
        model.addAttribute("editId", editId == null ? null : editId.toString());
        model.addAttribute("categories", categoryService.all());
        model.addAttribute("units", unitService.activeUnits());
        model.addAttribute("taxRates", taxRateService.activeRates());
        // U5: нарх/таннарх майдонлари home валютада - ёнида кўрсатилади
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        // QBO услуби: даромад select'ида фақат REVENUE, asset'да фақат
        // INVENTORY detail type. Харажат счётига иккала тур узатилади
        // (COGS + EXPENSE); item типига қараб JS филтрлайди (formFields.jte):
        // INVENTORY→COGS (QBO COGSAccountRef), бошқа→EXPENSE (ExpenseAccountRef)
        model.addAttribute("incomeAccounts", accounts.stream()
                .filter(a -> a.getClassification() == AccountClassification.REVENUE).toList());
        model.addAttribute("expenseAccounts", accounts.stream()
                .filter(a -> a.getType() == AccountType.COST_OF_GOODS_SOLD
                        || a.getType() == AccountType.EXPENSE).toList());
        model.addAttribute("assetAccounts", accounts.stream()
                .filter(a -> a.getDetailType() == AccountDetailType.INVENTORY).toList());
        // Тип ўзгарганда JS default счётларни қўяди (фақат яратишда)
        model.addAttribute("defaultsByType", Map.of(
                ItemType.INVENTORY.name(), itemService.defaultsFor(ItemType.INVENTORY),
                ItemType.NON_INVENTORY.name(), itemService.defaultsFor(ItemType.NON_INVENTORY),
                ItemType.SERVICE.name(), itemService.defaultsFor(ItemType.SERVICE)));
    }

    /** Филтр учун типни хавфсиз парслайди - бузуқ қиймат филтрсиз. */
    private ItemType parseType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ItemType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Яратишда тип мажбурий - бўш/бузуқ қийматга BR-ITM-011 (tampered
     * request ҳам global 400 эмас, формага киритилган маълумот билан қайтади). */
    private ItemType requireType(String value) {
        ItemType type = parseType(value);
        if (type == null) {
            throw new BusinessRuleException(BusinessRule.BR_ITM_011, "Item типи танланиши шарт");
        }
        return type;
    }
}
