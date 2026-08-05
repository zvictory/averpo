package com.averpo.erp.pricing.web;

import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactRef;
import com.averpo.erp.i18n.Msg;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.item.service.ItemService.ItemRef;
import com.averpo.erp.pricing.domain.PriceList;
import com.averpo.erp.pricing.domain.PriceListCustomer;
import com.averpo.erp.pricing.domain.PriceListItem;
import com.averpo.erp.pricing.service.PriceListService;
import com.averpo.erp.pricing.service.PriceListService.PriceListData;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.CurrencyService;
import com.averpo.erp.shared.web.FormParsers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Нарх рўйхатлари экранлари (docs/modules/price-list.md, INVENTORY соҳаси):
 * рўйхат + карта саҳифаси (поғоналар, мижоз бириктируви). Мантиқ
 * PriceListService'да - контроллер юпқа.
 */
@Controller
@RequestMapping("/settings/price-lists")
@RequiredArgsConstructor
public class PriceListController {

    /**
     * Карта саҳифасидаги битта поғона қатори (номлар тайёр):
     * «100+ дона → 9 000» кўриниши учун.
     */
    public record PriceRow(UUID id, String itemName, String unitName,
                           BigDecimal minQuantity, BigDecimal price) { }

    /** Мижоз қатори (ном тайёр). */
    public record CustomerRow(UUID customerId, String name) { }

    /** Нарх рўйхатлари service. */
    private final PriceListService priceListService;

    /** Поғона item select'и ва номлари учун. */
    private final ItemService itemService;

    /** Мижоз select'и ва номлари учун. */
    private final ContactService contactService;

    /** Рўйхат валютаси select'и учун. */
    private final CurrencyService currencyService;

    /** Flash хабарлар учун i18n. */
    private final Msg msg;

    /** Рўйхатлар + янги рўйхат формаси. */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("lists", priceListService.all());
        model.addAttribute("currencies", currencyService.active());
        return "pricing/priceLists";
    }

    /** Янги рўйхат (ном + валюта; қолгани картада тўлдирилади). */
    @PostMapping
    public String create(@RequestParam String name,
                         @RequestParam String currency,
                         RedirectAttributes redirect) {
        try {
            PriceList list = priceListService.create(new PriceListData(
                    name, currency, null, null, false, true));
            redirect.addFlashAttribute("message",
                    msg.get("plist.created", list.getName()));
            return "redirect:/settings/price-lists/" + list.getId();
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
            return "redirect:/settings/price-lists";
        }
    }

    /** Карта: сарлавҳа + поғоналар + мижозлар. */
    @GetMapping("/{id}")
    public String view(@PathVariable UUID id, Model model) {
        PriceList list = priceListService.get(id);
        // PERF-018: номлар фақат рўйхатда қатнашаётган id'лар бўйича
        // битта IN сўровда, select'лар енгил DTO - бутун item/contact
        // каталоги (EAGER боғлари билан) хотирага юкланмайди
        List<PriceListItem> tiers = priceListService.pricesOf(id);
        Map<UUID, ItemRef> tierItems = itemService.refsByIds(tiers.stream()
                        .map(PriceListItem::getItemId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(ItemRef::id, Function.identity()));

        List<PriceRow> prices = tiers.stream()
                .map(price -> {
                    ItemRef item = tierItems.get(price.getItemId());
                    return new PriceRow(price.getId(),
                            item != null ? item.name() : "?",
                            item != null ? item.unitName() : null,
                            price.getMinQuantity(), price.getPrice());
                })
                .sorted(Comparator.comparing(PriceRow::itemName)
                        .thenComparing(PriceRow::minQuantity))
                .toList();

        List<PriceListCustomer> assigned = priceListService.customersOf(id);
        var assignedIds = assigned.stream()
                .map(PriceListCustomer::getCustomerId).collect(Collectors.toSet());
        Map<UUID, String> customerNames = contactService.refsByIds(assignedIds).stream()
                .collect(Collectors.toMap(ContactRef::id, ContactRef::displayName));
        List<CustomerRow> customers = assigned.stream()
                .map(a -> new CustomerRow(a.getCustomerId(),
                        customerNames.getOrDefault(a.getCustomerId(), "?")))
                .toList();
        // Select: шу рўйхатга бириктирилмаган фаол мижозлар
        List<ContactRef> freeCustomers = contactService
                .activeRefsByType(ContactType.CUSTOMER).stream()
                .filter(c -> !assignedIds.contains(c.id())).toList();

        model.addAttribute("list", list);
        model.addAttribute("prices", prices);
        model.addAttribute("customers", customers);
        model.addAttribute("freeCustomers", freeCustomers);
        model.addAttribute("items", itemService.activeRefs());
        model.addAttribute("currencies", currencyService.active());
        return "pricing/priceListView";
    }

    /** Сарлавҳани янгилаш. */
    @PostMapping("/{id}")
    public String update(@PathVariable UUID id,
                         @RequestParam String name,
                         @RequestParam String currency,
                         @RequestParam(required = false) LocalDate validFrom,
                         @RequestParam(required = false) LocalDate validTo,
                         @RequestParam(defaultValue = "false") boolean defaultList,
                         @RequestParam(defaultValue = "false") boolean active,
                         RedirectAttributes redirect) {
        try {
            PriceList list = priceListService.update(id, new PriceListData(
                    name, currency, validFrom, validTo, defaultList, active));
            redirect.addFlashAttribute("message",
                    msg.get("plist.updated", list.getName()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/settings/price-lists/" + id;
    }

    /** Поғона қўшиш. */
    @PostMapping("/{id}/prices")
    public String addPrice(@PathVariable UUID id,
                           @RequestParam String itemId,
                           @RequestParam(required = false) String minQuantity,
                           @RequestParam String price,
                           RedirectAttributes redirect) {
        try {
            priceListService.addPrice(id,
                    FormParsers.requireUuid(itemId, BusinessRule.BR_PL_007, "Item"),
                    FormParsers.decimal(minQuantity, BusinessRule.BR_PL_002, "Миқдор"),
                    FormParsers.decimal(price, BusinessRule.BR_PL_002, "Нарх"));
            redirect.addFlashAttribute("message", msg.get("plist.priceAdded"));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/settings/price-lists/" + id;
    }

    /** Поғонани ўчириш - {id} scope'и service'да текширилади (DEC-030). */
    @PostMapping("/{id}/prices/{priceId}/delete")
    public String removePrice(@PathVariable UUID id, @PathVariable UUID priceId,
                              RedirectAttributes redirect) {
        priceListService.removePrice(id, priceId);
        redirect.addFlashAttribute("message", msg.get("plist.priceRemoved"));
        return "redirect:/settings/price-lists/" + id;
    }

    /** Мижоз бириктириш (бошқа рўйхатдан кўчади - BR-PL-006 семантикаси). */
    @PostMapping("/{id}/customers")
    public String assignCustomer(@PathVariable UUID id,
                                 @RequestParam String customerId,
                                 RedirectAttributes redirect) {
        try {
            priceListService.assignCustomer(id,
                    FormParsers.requireUuid(customerId, BusinessRule.BR_PL_008, "Мижоз"));
            redirect.addFlashAttribute("message", msg.get("plist.customerAssigned"));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/settings/price-lists/" + id;
    }

    /** Бириктирувни олиб ташлаш - {id} scope'и service'да текширилади. */
    @PostMapping("/{id}/customers/{customerId}/remove")
    public String unassignCustomer(@PathVariable UUID id, @PathVariable UUID customerId,
                                   RedirectAttributes redirect) {
        priceListService.unassignCustomer(id, customerId);
        redirect.addFlashAttribute("message", msg.get("plist.customerRemoved"));
        return "redirect:/settings/price-lists/" + id;
    }
}
