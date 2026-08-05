package com.averpo.erp.inventory.web;

import com.averpo.erp.i18n.Msg;
import com.averpo.erp.inventory.domain.Warehouse;
import com.averpo.erp.inventory.service.WarehouseService;
import com.averpo.erp.shared.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Омборлар каталоги экрани (units паттерни: рўйхат + қўшиш + қатор
 * ичида таҳрир). /settings остида, лекин INVENTORY соҳасида (user-roles.md: омбор менежерига ҳам очиқ).
 */
@Controller
@RequestMapping("/settings/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    /** Омборлар service. */
    private final WarehouseService warehouseService;

    /** Flash хабарлар учун i18n. */
    private final Msg msg;

    /**
     * Рўйхат + қўшиш формаси; стандарт каталог филтри (Arbitr-068):
     * фаоллик + матн (ном/код). Default - ҳаммаси (settings экрани:
     * нофаолни қайта фаоллаштириш ҳам шу ерда, яшириб бўлмайди).
     */
    @GetMapping
    public String list(@RequestParam(required = false) String activity,
                       @RequestParam(required = false) String q,
                       Model model) {
        String act = activity == null || activity.isBlank() ? "ALL" : activity;
        Boolean active = switch (act) {
            case "ACTIVE" -> Boolean.TRUE;
            case "INACTIVE" -> Boolean.FALSE;
            default -> null;
        };
        model.addAttribute("warehouses", warehouseService.list(
                new WarehouseService.ListFilter(active, q)));
        model.addAttribute("activity", act);
        model.addAttribute("q", q == null ? "" : q);
        return "inventory/warehouses";
    }

    /** Янги омбор қўшиш. */
    @PostMapping
    public String create(@RequestParam String name,
                         @RequestParam(required = false) String code,
                         RedirectAttributes redirect) {
        try {
            Warehouse warehouse = warehouseService.create(name, code);
            redirect.addFlashAttribute("message",
                    msg.get("warehouse.created", warehouse.getName()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/settings/warehouses";
    }

    /** Омборни янгилаш (ном/код/фаоллик). */
    @PostMapping("/{id}")
    public String update(@PathVariable UUID id,
                         @RequestParam String name,
                         @RequestParam(required = false) String code,
                         @RequestParam(defaultValue = "false") boolean active,
                         RedirectAttributes redirect) {
        try {
            Warehouse warehouse = warehouseService.update(id, name, code, active);
            redirect.addFlashAttribute("message",
                    msg.get("warehouse.updated", warehouse.getName()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/settings/warehouses";
    }
}
