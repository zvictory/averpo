package com.averpo.erp.inventory.web;

import com.averpo.erp.inventory.service.InventoryValuationService;
import com.averpo.erp.inventory.service.WarehouseService;
import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.shared.service.CompanySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Inventory valuation ҳисоботи экрани: ҳисоблаш service'да, бу ерда
 * фақат номлар бойитилади ва item кесимида гуруҳланади (item сатри +
 * остида омбор сатрлари - multi-warehouse кенгайтмамиз).
 *
 * @author Zafar
 */
@Controller
@RequiredArgsConstructor
public class InventoryValuationController {

    /**
     * Омбор ости сатри - drill-down (T9: item + омбор ҳаракатлари).
     *
     * @param avgCost value/qty; миқдор ноль бўлса null (шаблон «-» кўрсатади)
     */
    public record WarehouseRow(UUID itemId, UUID warehouseId, String warehouseName,
                               BigDecimal qty, BigDecimal avgCost, BigDecimal value) { }

    /**
     * Item гуруҳи: барча омборлари йиғиндиси + ости сатрлари.
     *
     * @param unit миқдор бирлиги (U4: миқдор доим бирлиги билан), бўлмаса null
     */
    public record ItemGroup(String itemName, String unit,
                            BigDecimal qty, BigDecimal avgCost, BigDecimal value,
                            List<WarehouseRow> warehouses) { }

    /** Валюация ҳисоблаш service'и. */
    private final InventoryValuationService valuationService;

    /** Item номлари/бирликлари - item модулининг public API'си. */
    private final ItemService itemService;

    /** Омбор номлари ва фильтр рўйхати. */
    private final WarehouseService warehouseService;

    /** Home валюта ва компания вақт минтақаси (default сана). */
    private final CompanySettingsService settingsService;

    /** Сана танланмаса - бугунга (компания минтақасида). */
    @GetMapping("/reports/inventory-valuation")
    @Transactional(readOnly = true)
    public String show(@RequestParam(required = false) java.time.LocalDate asOf,
                       @RequestParam(required = false) UUID warehouseId,
                       Model model) {
        java.time.LocalDate date = asOf != null ? asOf
                : java.time.LocalDate.now(settingsService.zoneId());

        InventoryValuationService.Report report = valuationService.build(date, warehouseId);

        Map<UUID, Item> items = itemService.list(null, true).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity(), (a, b) -> a));
        Map<UUID, String> warehouseNames = warehouseService.all().stream()
                .collect(Collectors.toMap(w -> w.getId(),
                        w -> w.getName(), (a, b) -> a));

        // Item кесимида гуруҳлаш: ном тартибида, ичида омбор номи тартибида
        Map<UUID, List<InventoryValuationService.Row>> byItem = report.rows().stream()
                .collect(Collectors.groupingBy(InventoryValuationService.Row::itemId,
                        LinkedHashMap::new, Collectors.toList()));
        List<ItemGroup> groups = new ArrayList<>();
        byItem.forEach((itemId, rows) -> {
            Item item = items.get(itemId);
            BigDecimal qty = rows.stream().map(InventoryValuationService.Row::qty)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal value = rows.stream().map(InventoryValuationService.Row::value)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            List<WarehouseRow> warehouses = rows.stream()
                    .map(r -> new WarehouseRow(r.itemId(), r.warehouseId(),
                            warehouseNames.getOrDefault(r.warehouseId(), "?"),
                            r.qty(), avg(r.value(), r.qty()), r.value()))
                    .sorted(Comparator.comparing(WarehouseRow::warehouseName))
                    .toList();
            groups.add(new ItemGroup(item != null ? item.getName() : "?",
                    item == null || item.getUnit() == null ? null : item.getUnit().getName(),
                    qty, avg(value, qty), value, warehouses));
        });
        groups.sort(Comparator.comparing(ItemGroup::itemName));

        model.addAttribute("groups", groups);
        model.addAttribute("report", report);
        model.addAttribute("warehouses", warehouseService.all());
        model.addAttribute("selectedWarehouseId",
                warehouseId == null ? "" : warehouseId.toString());
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        return "inventory/valuation";
    }

    /**
     * Ўртача таннарх - тўлиқ аниқликда (12 хона), яхлитлаш фақат
     * кўрсатишда (Fmt). Миқдор ноль бўлса null - бўлишга уриниб 500
     * бермайди, шаблон «-» кўрсатади.
     */
    private static BigDecimal avg(BigDecimal value, BigDecimal qty) {
        return qty.signum() == 0 ? null : value.divide(qty, 12, RoundingMode.HALF_UP);
    }
}
