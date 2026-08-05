package com.averpo.erp.inventory.web;

import com.averpo.erp.i18n.Msg;
import com.averpo.erp.inventory.domain.MovementType;
import com.averpo.erp.inventory.domain.StockAdjustment;
import com.averpo.erp.inventory.domain.StockAdjustmentLine;
import com.averpo.erp.inventory.domain.StockBalance;
import com.averpo.erp.inventory.domain.StockMovement;
import com.averpo.erp.inventory.domain.StockTransfer;
import com.averpo.erp.inventory.domain.StockTransferLine;
import com.averpo.erp.inventory.repo.StockBalanceRepository;
import com.averpo.erp.inventory.service.InventoryService;
import com.averpo.erp.inventory.service.WarehouseService;
import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.domain.ItemType;
import com.averpo.erp.item.service.ItemCategoryService;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.shared.Strings;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.web.FilterQuery;
import com.averpo.erp.shared.web.FormParsers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Омбор экранлари: қолдиқлар, ҳаракатлар (мукаммал филтр), ва ҲУЖЖАТЛИ
 * инвентаризация/кўчириш актлари (Arbitr-093: кўп сатрли ҳужжат + рўйхат
 * + view). Контроллер юпқа - мантиқ InventoryService'да; ўз модул
 * repo'ларидан фақат ўқийди (lazy view қаторлари транзакция ичида
 * йиғилади - open-in-view=false).
 *
 * @author Zafar
 */
@Controller
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    /**
     * Қолдиқлар экранининг битта қатори (lazy'сиз, тайёр матнлар).
     * itemId/warehouseId - T9 drill-down линки; unit - «миқдор доим
     * бирлиги билан» (U4); categoryId - категория филтри учун.
     */
    public record BalanceRow(UUID itemId, UUID warehouseId, UUID categoryId,
                             String itemName, String warehouseName, String unit,
                             BigDecimal qty, BigDecimal avgCost, BigDecimal value) { }

    /**
     * Ҳаракатлар экранининг битта қатори. docUrl - манба ҳужжат кўриш
     * саҳифаси ({@link #referenceUrl}); ҳужжатли акт (STOCK_ADJUSTMENT/
     * STOCK_TRANSFER) ҳам view'га боради (Arbitr-093).
     */
    public record MovementRow(LocalDate date, String typeKey,
                              String itemName, String unit,
                              String warehouseName, String counterpartName,
                              BigDecimal qty, BigDecimal unitCost,
                              BigDecimal totalCost, String memo,
                              String docUrl) { }

    /**
     * stock_movement reference тури → ҳужжат кўриш URL'и (Arbitr-063/093).
     * Харита ЛОКАЛ (қоида №6 руҳи - URL string шунчаки манзил); саҳифасиз
     * турлар (тест ёзувлари, эски ADJUSTMENT/TRANSFER) null - линк
     * чиқмайди. Ҳужжатли акт турлари view саҳифасига боради.
     */
    private static String referenceUrl(String referenceType, UUID referenceId) {
        if (referenceType == null || referenceId == null) {
            return null;
        }
        String prefix = switch (referenceType) {
            case "BILL" -> "/bills/";
            case "INVOICE" -> "/invoices/";
            case "SALES_RECEIPT" -> "/sales-receipts/";
            case "CREDIT_MEMO" -> "/credit-memos/";
            case "VENDOR_CREDIT" -> "/vendor-credits/";
            case "REFUND_RECEIPT" -> "/refund-receipts/";
            case "LANDED_COST" -> "/landed-costs/";
            case "STOCK_ADJUSTMENT" -> "/inventory/adjustments/";
            case "STOCK_TRANSFER" -> "/inventory/transfers/";
            default -> null;
        };
        return prefix == null ? null : prefix + referenceId;
    }

    /** Омбор мантиғи service'и. */
    private final InventoryService inventoryService;

    /** Омборлар - филтр/select'лар учун. */
    private final WarehouseService warehouseService;

    /** Item номлари ва INVENTORY select'и учун public API. */
    private final ItemService itemService;

    /** Категория филтри select'и учун (Arbitr-093 қолдиқлар филтри). */
    private final ItemCategoryService itemCategoryService;

    /** Бугунги сана (компания вақт минтақасида) - форма default'и. */
    private final CompanySettingsService settingsService;

    /** Қолдиқлар (ўқиш - ўз модул repo'си). */
    private final StockBalanceRepository balanceRepository;

    /** Flash хабарлар учун i18n. */
    private final Msg msg;

    // ---- Қолдиқлар ----

    /**
     * Қолдиқлар экрани - мукаммал филтр (Arbitr-093): омбор, item ном
     * қидируви, категория, «нолни яшир» (default: яширилган - QBO услуби).
     *
     * <p>Саҳифаланган (ARBITR-105б): филтр in-memory қолади (item номи
     * бошқа модул каталогида - DB-даражали Specification учун модул
     * чегарасини бузувчи join керак бўларди; ҳажм item×омбор билан
     * чегараланган), тайёр филтрланган рўйхат PageImpl билан кесилади -
     * филтр+саҳифа+ҳажм учаласи линкларда бирга сақланади.
     */
    @GetMapping("/balances")
    @Transactional(readOnly = true)
    public String balances(@RequestParam(required = false) UUID warehouseId,
                           @RequestParam(required = false) String q,
                           @RequestParam(required = false) UUID categoryId,
                           @RequestParam(required = false, defaultValue = "true") boolean hideZero,
                           @RequestParam(required = false, defaultValue = "0") int page,
                           jakarta.servlet.http.HttpServletRequest request,
                           jakarta.servlet.http.HttpServletResponse response,
                           Model model) {
        Map<UUID, Item> items = itemsById();
        List<StockBalance> balances = warehouseId == null
                ? balanceRepository.findAll()
                : balanceRepository.findByWarehouseId(warehouseId);
        String needle = q == null ? null : q.strip().toLowerCase();
        List<BalanceRow> rows = balances.stream()
                .filter(b -> !hideZero || b.getQty().signum() != 0)
                .map(balance -> {
                    Item item = items.get(balance.getItemId());
                    UUID catId = item != null && item.getCategory() != null
                            ? item.getCategory().getId() : null;
                    return new BalanceRow(balance.getItemId(),
                            balance.getWarehouse().getId(), catId,
                            item != null ? item.getName() : "?",
                            balance.getWarehouse().getName(), unitName(item),
                            balance.getQty(), balance.getAvgCost(),
                            balance.getQty().multiply(balance.getAvgCost()));
                })
                .filter(r -> needle == null || needle.isBlank()
                        || r.itemName().toLowerCase().contains(needle))
                .filter(r -> categoryId == null || categoryId.equals(r.categoryId()))
                .sorted(Comparator.comparing(BalanceRow::itemName)
                        .thenComparing(BalanceRow::warehouseName))
                .toList();
        // ARBITR-105: саҳифа ҳажми ?size=/cookie'дан (PageSizeResolver)
        int size = com.averpo.erp.shared.web.PageSizeResolver.resolve(
                request, response, "inventory-balances");
        var pageable = org.springframework.data.domain.PageRequest.of(
                Math.max(0, page), size);
        // Филтрлангандан кейин кесим - total филтр кесимининг сони
        int fromIdx = (int) Math.min(pageable.getOffset(), rows.size());
        int toIdx = Math.min(fromIdx + size, rows.size());
        var rowPage = new org.springframework.data.domain.PageImpl<>(
                rows.subList(fromIdx, toIdx), pageable, rows.size());
        model.addAttribute("rows", rowPage.getContent());
        model.addAttribute("page", rowPage);
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        model.addAttribute("categories", itemCategoryService.all());
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("selectedCategoryId", categoryId == null ? "" : categoryId.toString());
        model.addAttribute("hideZero", hideZero);
        model.addAttribute("filterQuery", new FilterQuery()
                .add("warehouseId", warehouseId).add("q", q)
                .add("categoryId", categoryId).add("hideZero", hideZero ? null : "false")
                .toString());
        fillFilterModel(model, warehouseId);
        return "inventory/balances";
    }

    // ---- Ҳаракатлар ----

    /**
     * Ҳаракатлар экрани - мукаммал филтр (Arbitr-093): тур/омбор/item/
     * сана оралиғи/ҳужжат рақами, саҳифаланган (Beruniy-perf1). Server-
     * side Specification (InventoryService.MovementFilter).
     */
    @GetMapping("/movements")
    @Transactional(readOnly = true)
    public String movements(@RequestParam(required = false) UUID warehouseId,
                            @RequestParam(required = false) UUID itemId,
                            @RequestParam(required = false) String type,
                            @RequestParam(required = false) LocalDate from,
                            @RequestParam(required = false) LocalDate to,
                            @RequestParam(required = false) String docNumber,
                            @RequestParam(required = false, defaultValue = "0") int page,
                            Model model) {
        MovementType movementType = parseTypeSafe(type);
        var movementPage = inventoryService.movements(new InventoryService.MovementFilter(
                movementType, warehouseId, itemId, from, to, Strings.blankToNull(docNumber)), page);
        // Item ном/бирликлари - фақат саҳифадаги қаторлар (+ филтр чипи
        // itemId'си) byIds/IN сўровда (ARBITR-105б, Ulugbek-003 §1:
        // бутун каталог юкланмайди)
        java.util.Set<UUID> itemIds = new java.util.HashSet<>();
        for (StockMovement m : movementPage.getContent()) {
            if (m.getItemId() != null) {
                itemIds.add(m.getItemId());
            }
        }
        if (itemId != null) {
            itemIds.add(itemId);
        }
        Map<UUID, ItemService.ItemRef> items = itemService.refsByIds(itemIds)
                .stream().collect(Collectors.toMap(ItemService.ItemRef::id, Function.identity()));
        List<MovementRow> rows = movementPage.getContent().stream()
                .map(m -> {
                    ItemService.ItemRef item = items.get(m.getItemId());
                    return new MovementRow(m.getMovementDate(), m.getType().titleKey(),
                            item != null ? item.name() : "?",
                            item == null ? null : item.unitName(),
                            m.getWarehouse().getName(),
                            m.getCounterpartWarehouse() == null
                                    ? null : m.getCounterpartWarehouse().getName(),
                            m.getQuantity(), m.getUnitCost(), m.getTotalCost(), m.getMemo(),
                            referenceUrl(m.getReferenceType(), m.getReferenceId()));
                })
                .toList();
        model.addAttribute("rows", rows);
        model.addAttribute("page", movementPage);
        model.addAttribute("filterQuery", new FilterQuery()
                .add("warehouseId", warehouseId).add("itemId", itemId)
                .add("type", type).add("from", from).add("to", to)
                .add("docNumber", docNumber).toString());
        ItemService.ItemRef selectedItem = itemId == null ? null : items.get(itemId);
        model.addAttribute("selectedItemId", itemId == null ? "" : itemId.toString());
        model.addAttribute("selectedItemName",
                selectedItem == null ? null : selectedItem.name());
        model.addAttribute("selectedType", type == null ? "" : type);
        model.addAttribute("from", from == null ? "" : from.toString());
        model.addAttribute("to", to == null ? "" : to.toString());
        model.addAttribute("docNumber", docNumber == null ? "" : docNumber);
        model.addAttribute("movementTypes", MovementType.values());
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        fillFilterModel(model, warehouseId);
        return "inventory/movements";
    }

    /** Query қийматидан ҳаракат турини хавфсиз парслайди (бузуқ - филтрсиз). */
    private static MovementType parseTypeSafe(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return MovementType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Форма учун жонли қолдиқ харитаси: танланган омбордаги (item → qty).
     * Форма JS'и item танланганда «жорий qty» ва delta'ни шундан
     * ҳисоблайди (курс prefill нақши). Матн - тўлиқ аниқликда (plain).
     */
    @GetMapping("/on-hand")
    @ResponseBody
    @Transactional(readOnly = true)
    public Map<String, String> onHand(@RequestParam(required = false) UUID warehouseId) {
        Map<String, String> map = new LinkedHashMap<>();
        if (warehouseId == null) {
            return map;
        }
        for (StockBalance balance : balanceRepository.findByWarehouseId(warehouseId)) {
            map.put(balance.getItemId().toString(),
                    balance.getQty().stripTrailingZeros().toPlainString());
        }
        return map;
    }

    // ---- Инвентаризация актлари (Arbitr-093) ----

    /** Актлар рўйхати - саҳифаланган + филтр (омбор/сана оралиғи). */
    @GetMapping("/adjustments")
    @Transactional(readOnly = true)
    public String adjustments(@RequestParam(required = false) UUID warehouseId,
                              @RequestParam(required = false) LocalDate from,
                              @RequestParam(required = false) LocalDate to,
                              @RequestParam(required = false, defaultValue = "0") int page,
                              Model model) {
        var actPage = inventoryService.adjustments(
                new InventoryService.DocumentFilter(warehouseId, from, to), page);
        model.addAttribute("acts", actPage.getContent());
        model.addAttribute("page", actPage);
        model.addAttribute("from", from == null ? "" : from.toString());
        model.addAttribute("to", to == null ? "" : to.toString());
        model.addAttribute("filterQuery", new FilterQuery()
                .add("warehouseId", warehouseId).add("from", from).add("to", to).toString());
        fillFilterModel(model, warehouseId);
        return "inventory/adjustments";
    }

    /** Янги инвентаризация акти формаси - 3 бўш сатр. */
    @GetMapping("/adjustments/new")
    public String adjustmentForm(Model model) {
        AdjustmentForm form = AdjustmentForm.empty(3);
        form.setDate(LocalDate.now(settingsService.zoneId()));
        fillAdjustmentModel(model, form);
        return "inventory/adjustmentForm";
    }

    /** Актни сақлаш - дарҳол POSTED, актга БИТТА JE. */
    @PostMapping("/adjustments")
    public String saveAdjustment(@ModelAttribute AdjustmentForm form,
                                 Model model, RedirectAttributes redirect) {
        try {
            StockAdjustment act = inventoryService.adjustDocument(toAdjustData(form));
            redirect.addFlashAttribute("message", msg.get("inv.adjust.saved", act.getAdjNumber()));
            return "redirect:/inventory/adjustments/" + act.getId();
        } catch (BusinessRuleException e) {
            fillAdjustmentModel(model, form);
            model.addAttribute("error", e.displayMessage());
            return "inventory/adjustmentForm";
        }
    }

    /** Акт кўриш: сатрлар + GL ҳавола (080) + reverse (қарши-акт). */
    @GetMapping("/adjustments/{id}")
    @Transactional(readOnly = true)
    public String adjustmentView(@PathVariable UUID id, Model model) {
        StockAdjustment act = inventoryService.adjustment(id);
        model.addAttribute("act", act);
        // Item номлари - фақат акт сатрларидаги id'лар byIds/IN сўровда
        // (ARBITR-105б, Ulugbek-003 §1)
        model.addAttribute("itemNames", itemService.namesByIds(
                act.getLines().stream().map(StockAdjustmentLine::getItemId)
                        .filter(java.util.Objects::nonNull).distinct().toList()));
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        // GL линки фақат актда нолдан фарқли қиймат бўлса (JE ёзилган)
        model.addAttribute("hasGl", act.getLines().stream()
                .anyMatch(l -> l.getLineCost().signum() != 0));
        return "inventory/adjustmentView";
    }

    /**
     * Reverse (қарши-акт prefill, spec): янги форма ўша омбор билан, ҳар
     * сатр ЯНГИ qty = актдан олдинги қолдиқ (newQty − deltaQty). Фойда-
     * ланувчи кўриб сақлайди - POSTED ўзгармас қоидаси бузилмайди.
     */
    @GetMapping("/adjustments/{id}/reverse")
    @Transactional(readOnly = true)
    public String adjustmentReverse(@PathVariable UUID id, Model model) {
        StockAdjustment act = inventoryService.adjustment(id);
        AdjustmentForm form = new AdjustmentForm();
        form.setWarehouseId(act.getWarehouse().getId().toString());
        form.setDate(LocalDate.now(settingsService.zoneId()));
        form.setMemo(msg.get("inv.adjust.reverseMemo", act.getAdjNumber()));
        for (StockAdjustmentLine line : act.getLines()) {
            AdjustmentForm.LineForm lf = new AdjustmentForm.LineForm();
            lf.setItemId(line.getItemId().toString());
            // Актдан ОЛДИНГИ қолдиқ = янги − ўзгариш (тескари таъсир)
            lf.setNewQty(line.getNewQty().subtract(line.getDeltaQty())
                    .stripTrailingZeros().toPlainString());
            form.getLines().add(lf);
        }
        fillAdjustmentModel(model, form);
        return "inventory/adjustmentForm";
    }

    // ---- Кўчириш актлари (Arbitr-093) ----

    /** Кўчириш актлари рўйхати - саҳифаланган + филтр. */
    @GetMapping("/transfers")
    @Transactional(readOnly = true)
    public String transfers(@RequestParam(required = false) UUID warehouseId,
                            @RequestParam(required = false) LocalDate from,
                            @RequestParam(required = false) LocalDate to,
                            @RequestParam(required = false, defaultValue = "0") int page,
                            Model model) {
        var actPage = inventoryService.transfers(
                new InventoryService.DocumentFilter(warehouseId, from, to), page);
        model.addAttribute("acts", actPage.getContent());
        model.addAttribute("page", actPage);
        model.addAttribute("from", from == null ? "" : from.toString());
        model.addAttribute("to", to == null ? "" : to.toString());
        model.addAttribute("filterQuery", new FilterQuery()
                .add("warehouseId", warehouseId).add("from", from).add("to", to).toString());
        fillFilterModel(model, warehouseId);
        return "inventory/transfers";
    }

    /** Янги кўчириш акти формаси - 3 бўш сатр. */
    @GetMapping("/transfers/new")
    public String transferForm(Model model) {
        TransferForm form = TransferForm.empty(3);
        form.setDate(LocalDate.now(settingsService.zoneId()));
        fillTransferModel(model, form);
        return "inventory/transferForm";
    }

    /** Актни сақлаш - дарҳол POSTED, GL'сиз. */
    @PostMapping("/transfers")
    public String saveTransfer(@ModelAttribute TransferForm form,
                               Model model, RedirectAttributes redirect) {
        try {
            StockTransfer act = inventoryService.transferDocument(toTransferData(form));
            redirect.addFlashAttribute("message", msg.get("inv.transfer.saved", act.getWtrNumber()));
            return "redirect:/inventory/transfers/" + act.getId();
        } catch (BusinessRuleException e) {
            fillTransferModel(model, form);
            model.addAttribute("error", e.displayMessage());
            return "inventory/transferForm";
        }
    }

    /** Акт кўриш: сатрлар (GL линк ЙЎҚ - transfer GL'сиз) + reverse. */
    @GetMapping("/transfers/{id}")
    @Transactional(readOnly = true)
    public String transferView(@PathVariable UUID id, Model model) {
        StockTransfer act = inventoryService.transferDoc(id);
        model.addAttribute("act", act);
        // Item номлари - фақат акт сатрларидаги id'лар byIds/IN сўровда
        // (ARBITR-105б, Ulugbek-003 §1)
        model.addAttribute("itemNames", itemService.namesByIds(
                act.getLines().stream().map(StockTransferLine::getItemId)
                        .filter(java.util.Objects::nonNull).distinct().toList()));
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        return "inventory/transferView";
    }

    /** Reverse (қарши-акт prefill): манба/манзил алмашади, item/qty ўшандай. */
    @GetMapping("/transfers/{id}/reverse")
    @Transactional(readOnly = true)
    public String transferReverse(@PathVariable UUID id, Model model) {
        StockTransfer act = inventoryService.transferDoc(id);
        TransferForm form = new TransferForm();
        form.setFromWarehouseId(act.getToWarehouse().getId().toString());
        form.setToWarehouseId(act.getFromWarehouse().getId().toString());
        form.setDate(LocalDate.now(settingsService.zoneId()));
        form.setMemo(msg.get("inv.transfer.reverseMemo", act.getWtrNumber()));
        for (StockTransferLine line : act.getLines()) {
            TransferForm.LineForm lf = new TransferForm.LineForm();
            lf.setItemId(line.getItemId().toString());
            lf.setQty(line.getQuantity().stripTrailingZeros().toPlainString());
            form.getLines().add(lf);
        }
        fillTransferModel(model, form);
        return "inventory/transferForm";
    }

    // ---- model ёрдамчилари ----

    /**
     * Item id → item луғати - қолдиқлар экранига ном ва бирлик (U4)
     * керак. ТЎЛИҚ каталог АТАЙЛАБ юкланади (byIds эмас): item ном
     * қидируви (q) ва ном бўйича тартиб харитага филтрлашДАН ОЛДИН
     * муҳтож - қайси id'лар кераклиги олдиндан маълум эмас.
     */
    private Map<UUID, Item> itemsById() {
        return itemService.list(null, true).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity(), (a, b) -> a));
    }

    /** Item бирлигининг номи; item ёки бирлиги йўқ бўлса null (Fmt думсиз). */
    private static String unitName(Item item) {
        return item == null || item.getUnit() == null ? null : item.getUnit().getName();
    }

    /** Филтр модели: омборлар рўйхати + танланган омбор. */
    private void fillFilterModel(Model model, UUID warehouseId) {
        model.addAttribute("warehouses", warehouseService.all());
        model.addAttribute("selectedWarehouseId",
                warehouseId == null ? "" : warehouseId.toString());
    }

    /** Инвентаризация акти формаси модели: INVENTORY item'лар, фаол омборлар. */
    private void fillAdjustmentModel(Model model, AdjustmentForm form) {
        model.addAttribute("form", form);
        model.addAttribute("items", itemService.list(ItemType.INVENTORY, false));
        model.addAttribute("warehouses", warehouseService.active());
        model.addAttribute("today", LocalDate.now(settingsService.zoneId()).toString());
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
    }

    /** Кўчириш акти формаси модели. */
    private void fillTransferModel(Model model, TransferForm form) {
        model.addAttribute("form", form);
        model.addAttribute("items", itemService.list(ItemType.INVENTORY, false));
        model.addAttribute("warehouses", warehouseService.active());
        model.addAttribute("today", LocalDate.now(settingsService.zoneId()).toString());
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
    }

    // ---- форма → service маълумоти ----

    /** Инвентаризация формасини service маълумотига (бўш сатрлар ташланади). */
    private InventoryService.DocumentAdjustData toAdjustData(AdjustmentForm form) {
        List<InventoryService.AdjustLineData> lines = new ArrayList<>();
        int no = 0;
        for (AdjustmentForm.LineForm lf : form.getLines()) {
            no++;
            if (lf.isEmpty()) {
                continue;
            }
            lines.add(new InventoryService.AdjustLineData(
                    FormParsers.requireUuid(lf.getItemId(), BusinessRule.BR_INV_001,
                            no + "-сатр: товар"),
                    FormParsers.decimal(lf.getNewQty(), BusinessRule.BR_INV_002,
                            no + "-сатр: янги қолдиқ"),
                    FormParsers.decimal(lf.getUnitCost(), BusinessRule.BR_INV_004,
                            no + "-сатр: нарх"),
                    Strings.blankToNull(lf.getMemo())));
        }
        return new InventoryService.DocumentAdjustData(
                FormParsers.requireUuid(form.getWarehouseId(), BusinessRule.BR_INV_006, "Омбор"),
                form.getDate(), Strings.blankToNull(form.getMemo()),
                Strings.blankToNull(form.getExternalRef()), lines);
    }

    /** Кўчириш формасини service маълумотига (бўш сатрлар ташланади). */
    private InventoryService.DocumentTransferData toTransferData(TransferForm form) {
        List<InventoryService.TransferLineData> lines = new ArrayList<>();
        int no = 0;
        for (TransferForm.LineForm lf : form.getLines()) {
            no++;
            if (lf.isEmpty()) {
                continue;
            }
            lines.add(new InventoryService.TransferLineData(
                    FormParsers.requireUuid(lf.getItemId(), BusinessRule.BR_INV_001,
                            no + "-сатр: товар"),
                    FormParsers.decimal(lf.getQty(), BusinessRule.BR_INV_002,
                            no + "-сатр: миқдор"),
                    Strings.blankToNull(lf.getMemo())));
        }
        return new InventoryService.DocumentTransferData(
                FormParsers.requireUuid(form.getFromWarehouseId(), BusinessRule.BR_INV_006, "Манба омбор"),
                FormParsers.requireUuid(form.getToWarehouseId(), BusinessRule.BR_INV_006, "Манзил омбор"),
                form.getDate(), Strings.blankToNull(form.getMemo()),
                Strings.blankToNull(form.getExternalRef()), lines);
    }
}
