package com.averpo.erp.purchase.web;

import com.averpo.erp.inventory.domain.StockMovement;
import com.averpo.erp.inventory.service.InventoryService;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.purchase.domain.Bill;
import com.averpo.erp.purchase.domain.LandedCostAllocation;
import com.averpo.erp.purchase.domain.LandedCostAllocationLine;
import com.averpo.erp.purchase.repo.BillRepository;
import com.averpo.erp.purchase.service.LandedCostService;
import com.averpo.erp.purchase.service.LandedCostService.AllocationData;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.CompanySettingsService;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Landed cost тақсимоти экранлари: рўйхат, форма (охирги BILL
 * киримлари checkbox билан), кўриш (inventory/COGS бўлиниши + reverse).
 * Ҳамма ёзиш LandedCostService орқали - контроллер юпқа.
 *
 * @author Zafar
 */
@Controller
@RequestMapping("/landed-costs")
@RequiredArgsConstructor
public class LandedCostController {

    /** Тақсимотнинг ягона public API'си. */
    private final LandedCostService landedCostService;

    /** Номзод receipt'лар рўйхати учун. */
    private final InventoryService inventoryService;

    /** Receipt'нинг bill рақамини кўрсатиш учун - ўз модулимиз ичида. */
    private final BillRepository billRepository;

    /** Receipt'даги товар номлари учун. */
    private final ItemService itemService;

    /** Компания вақт минтақаси (reverse формаси default санаси). */
    private final CompanySettingsService settingsService;

    /** Flash хабарлар учун i18n. */
    private final com.averpo.erp.i18n.Msg msg;

    /**
     * Рўйхат - янгидан эскига; тўлиқ филтр қатори (Arbitr-068): давр/
     * статус/матн (контактсиз ҳужжат, саҳифаланмаган - қисқа рўйхат).
     */
    @GetMapping
    public String list(@RequestParam(required = false) LocalDate from,
                       @RequestParam(required = false) LocalDate to,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String q,
                       Model model) {
        model.addAttribute("allocations", landedCostService.list(
                new LandedCostService.ListFilter(from, to, parseStatusSafe(status), q)));
        // U-конвенция: LC суммалари home'да - тепада битта ёзув (Alisa-001)
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        model.addAttribute("from", from == null ? "" : from.toString());
        model.addAttribute("to", to == null ? "" : to.toString());
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("q", q == null ? "" : q);
        return "purchase/landedCosts";
    }

    /** Query қийматидан статусни хавфсиз парслайди (бузуқ қиймат - филтрсиз). */
    private static LandedCostAllocation.Status parseStatusSafe(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return LandedCostAllocation.Status.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Янги тақсимот формаси - охирги BILL киримлари билан. */
    @GetMapping("/new")
    public String createForm(Model model) {
        LandedCostForm form = new LandedCostForm();
        // Default сана - компания zoneId'даги «бугун» (JVM tz эмас, қоида 12/Arbitr-044)
        form.setAllocationDate(LocalDate.now(settingsService.zoneId()));
        fillFormModel(model, form);
        return "purchase/landedCostForm";
    }

    /** Тақсимот яратиш - дарҳол POSTED. */
    @PostMapping
    public String create(@ModelAttribute LandedCostForm form,
                         Model model, RedirectAttributes redirect) {
        try {
            List<UUID> movementIds = new ArrayList<>();
            for (String id : form.getMovementIds()) {
                if (id != null && !id.isBlank()) {
                    // Бузуқ checkbox қиймати хом IAE эмас, BR-LC-003 билан
                    // формага қайтади (Beruniy-006 / FormParsers сиёсати)
                    movementIds.add(FormParsers.uuid(id,
                            BusinessRule.BR_LC_003, "Receipt танлови"));
                }
            }
            LandedCostAllocation allocation = landedCostService.create(new AllocationData(
                    form.getAllocationDate(), parseNumber(form.getTotalAmount()),
                    form.getMemo(), movementIds));
            redirect.addFlashAttribute("message",
                    msg.get("lc.saved", allocation.getAllocationNumber()));
            return "redirect:/landed-costs/" + allocation.getId();
        } catch (BusinessRuleException e) {
            fillFormModel(model, form);
            model.addAttribute("error", e.displayMessage());
            return "purchase/landedCostForm";
        }
    }

    /** Битта тақсимотни кўриш - қаторлар бўлиниши билан. */
    @GetMapping("/{id}")
    public String view(@PathVariable UUID id, Model model) {
        LandedCostAllocation allocation = landedCostService.get(id);
        List<LandedCostAllocationLine> lines = landedCostService.linesOf(id);
        // Ҳар қатор receipt'и ҳақида кўрсатиладиган маълумот
        Map<UUID, StockMovement> receipts = new HashMap<>();
        for (LandedCostAllocationLine line : lines) {
            receipts.put(line.getMovementId(),
                    inventoryService.movement(line.getMovementId()));
        }
        model.addAttribute("allocation", allocation);
        model.addAttribute("lines", lines);
        model.addAttribute("receipts", receipts);
        model.addAttribute("itemNames", itemNames(receipts.values()));
        model.addAttribute("billNumbers", billNumbers(receipts.values()));
        model.addAttribute("today", LocalDate.now(settingsService.zoneId()).toString());
        // U-конвенция: LC суммалари home'да - тепада битта ёзув (Alisa-001)
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        return "purchase/landedCostView";
    }

    /** POSTED тақсимотни сторно қилиш. */
    @PostMapping("/{id}/reverse")
    public String reverse(@PathVariable UUID id,
                          @RequestParam LocalDate reversalDate,
                          @RequestParam(required = false) String reason,
                          RedirectAttributes redirect) {
        try {
            LandedCostAllocation allocation =
                    landedCostService.reverse(id, reversalDate, reason);
            redirect.addFlashAttribute("message",
                    msg.get("lc.reversed", allocation.getAllocationNumber()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/landed-costs/" + id;
    }

    // ---- ички ёрдамчилар ----

    /** Форма model'ини тўлдиради - номзод receipt'лар ва номлар билан. */
    private void fillFormModel(Model model, LandedCostForm form) {
        List<StockMovement> receipts = inventoryService.billReceipts();
        model.addAttribute("form", form);
        model.addAttribute("receipts", receipts);
        model.addAttribute("selectedIds", new HashSet<>(form.getMovementIds()));
        model.addAttribute("itemNames", itemNames(receipts));
        model.addAttribute("billNumbers", billNumbers(receipts));
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
    }

    /** Сон парси - normalize (пробел/NBSP/вергул) FormParsers'да. */
    private BigDecimal parseNumber(String text) {
        return FormParsers.decimal(text, BusinessRule.BR_LC_001, "Сумма");
    }

    /**
     * Товар номлари - фақат receipt қаторларидаги id'лар byIds/IN
     * сўровда (ARBITR-105б, Ulugbek-003 §1): бутун каталог юкланмайди.
     */
    private Map<UUID, String> itemNames(Iterable<StockMovement> receipts) {
        java.util.Set<UUID> itemIds = new HashSet<>();
        for (StockMovement receipt : receipts) {
            if (receipt.getItemId() != null) {
                itemIds.add(receipt.getItemId());
            }
        }
        return itemService.namesByIds(itemIds);
    }

    /** Receipt'лар bill рақамлари: movement.referenceId → bill_number. */
    private Map<UUID, String> billNumbers(Iterable<StockMovement> receipts) {
        List<UUID> billIds = new ArrayList<>();
        for (StockMovement receipt : receipts) {
            if (receipt.getReferenceId() != null) {
                billIds.add(receipt.getReferenceId());
            }
        }
        Map<UUID, String> numbers = new HashMap<>();
        for (Bill bill : billRepository.findAllById(billIds)) {
            numbers.put(bill.getId(), bill.getBillNumber());
        }
        return numbers;
    }
}
