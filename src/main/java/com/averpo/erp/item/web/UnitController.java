package com.averpo.erp.item.web;

import com.averpo.erp.i18n.Msg;
import com.averpo.erp.item.domain.Unit;
import com.averpo.erp.item.domain.UnitGroup;
import com.averpo.erp.item.service.UnitService;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.web.FormParsers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ўлчов бирликлари экранлари - ИККИ ТАБ (docs/modules/uom.md):
 * «Бирликлар» - содда каталог (ном/фаоллик), «Гуруҳлар» - конверсия
 * бошқаруви (ҳар гуруҳ ўз картасида: base/factor, қўшиш/чиқариш).
 * Йўл /settings остида, лекин Arbitr-092'дан бери INVENTORY соҳасида (омбор менежери ҳам киради); контроллер
 * item модулида (қатлам йўналиши).
 *
 * @author Zafar
 */
@Controller
@RequestMapping("/settings/units")
@RequiredArgsConstructor
public class UnitController {

    /** Бирликлар service. */
    private final UnitService unitService;

    /** Flash хабарлар учун i18n. */
    private final Msg msg;

    /** «Бирликлар» таби: содда каталог (гуруҳ/factor фақат кўрсатилади). */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("units", unitService.all());
        return "item/units";
    }

    /**
     * «Гуруҳлар» таби: ҳар гуруҳ ўз картасида - бирликлари (base
     * биринчи), янги/мавжуд бирлик қўшиш формалари. Транзакция ичида -
     * lazy group майдонлари шаблонгача тайёр йиғилади.
     */
    @GetMapping("/groups")
    @Transactional(readOnly = true)
    public String groups(Model model) {
        List<UnitGroup> groups = unitService.groups();
        Map<UUID, List<Unit>> unitsByGroup = new LinkedHashMap<>();
        for (UnitGroup group : groups) {
            unitsByGroup.put(group.getId(), unitService.groupUnits(group.getId()));
        }
        // «Мавжудни қўшиш» select'и: гуруҳсиз фаол бирликлар
        List<Unit> freeUnits = unitService.activeUnits().stream()
                .filter(unit -> unit.getGroup() == null).toList();
        model.addAttribute("groups", groups);
        model.addAttribute("unitsByGroup", unitsByGroup);
        model.addAttribute("freeUnits", freeUnits);
        return "item/unitGroups";
    }

    /**
     * Янги бирлик қўшиш. Гуруҳсиз («Бирликлар» табидан) ёки тўғридан
     * гуруҳ ичига («Гуруҳлар» табидаги карта формасидан) - қайтиш
     * манзили шунга қараб танланади.
     */
    @PostMapping
    public String create(@RequestParam String name,
                         @RequestParam(required = false) String groupId,
                         @RequestParam(required = false) String factor,
                         @RequestParam(defaultValue = "false") boolean base,
                         RedirectAttributes redirect) {
        UUID group = parseGroup(groupId);
        try {
            unitService.create(name, group, parseFactor(factor), base);
            redirect.addFlashAttribute("message", msg.get("unit.created", name.strip()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return group == null ? "redirect:/settings/units"
                : "redirect:/settings/units/groups";
    }

    /** Бирликни янгилаш (ном/фаоллик) - гуруҳ майдонларига ТЕГМАЙДИ. */
    @PostMapping("/{id}")
    public String update(@PathVariable UUID id, @RequestParam String name,
                         @RequestParam(defaultValue = "false") boolean active,
                         RedirectAttributes redirect) {
        try {
            unitService.update(id, name, active);
            redirect.addFlashAttribute("message", msg.get("unit.updated", name.strip()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/settings/units";
    }

    /**
     * Гуруҳ бириктируви («Гуруҳлар» табидаги ҳамма амаллар):
     * мавжуд бирликни гуруҳга киритиш, factor'ини ўзгартириш ёки
     * гуруҳдан чиқариш (groupId бўш). Ном/фаолликка тегмайди.
     */
    @PostMapping("/group-assign")
    public String assignGroup(@RequestParam String unitId,
                              @RequestParam(required = false) String groupId,
                              @RequestParam(required = false) String factor,
                              @RequestParam(defaultValue = "false") boolean base,
                              RedirectAttributes redirect) {
        try {
            Unit unit = unitService.assignGroup(
                    FormParsers.requireUuid(unitId, BusinessRule.NOT_FOUND, "Бирлик"),
                    parseGroup(groupId), parseFactor(factor), base);
            redirect.addFlashAttribute("message",
                    msg.get("unit.updated", unit.getName()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/settings/units/groups";
    }

    /**
     * Гуруҳга бирлик қўшиш - БИТТА содда форма: ном (мавжуд гуруҳсиз
     * бирлик бўлса киради, бўлмаса яратилади) + factor. Биринчиси
     * автоматик base (UnitService.addUnitToGroup).
     */
    @PostMapping("/groups/{id}/add-unit")
    public String addUnitToGroup(@PathVariable UUID id, @RequestParam String name,
                                 @RequestParam(required = false) String factor,
                                 RedirectAttributes redirect) {
        try {
            Unit unit = unitService.addUnitToGroup(id, name, parseFactor(factor));
            redirect.addFlashAttribute("message", msg.get("unit.created", unit.getName()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/settings/units/groups";
    }

    /**
     * Стандарт UOM гуруҳларини юклайди (Arbitr-147б) - chart'даги
     * «Стандарт режани юклаш» тугмасининг units кўзгуси. Мавжуд база
     * (dev/prod) installer'ни (147, фақат fresh install/reset) ололмагани
     * учун қўлда юклаш йўли. `installDefaultUnits` idempotent: бор гуруҳга
     * тегмайди, seed бирликларни номи бўйича ютади - такрор босиш хавфсиз.
     * Гуруҳлар «Гуруҳлар» табида кўрингани учун ўша ерга қайтарилади.
     */
    @PostMapping("/import-default")
    public String importDefault(RedirectAttributes redirect) {
        unitService.installDefaultUnits();
        redirect.addFlashAttribute("message", msg.get("unit.groupsImported"));
        return "redirect:/settings/units/groups";
    }

    /** Янги гуруҳ қўшиш (BR-UOM-001). */
    @PostMapping("/groups")
    public String createGroup(@RequestParam String name, RedirectAttributes redirect) {
        try {
            unitService.createGroup(name);
            redirect.addFlashAttribute("message", msg.get("unit.group.created", name.strip()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/settings/units/groups";
    }

    /** Гуруҳ номини янгилаш. */
    @PostMapping("/groups/{id}")
    public String renameGroup(@PathVariable UUID id, @RequestParam String name,
                              RedirectAttributes redirect) {
        try {
            unitService.renameGroup(id, name);
            redirect.addFlashAttribute("message", msg.get("unit.group.updated", name.strip()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/settings/units/groups";
    }

    /** Гуруҳ id парси - бузуқ қиймат 500 эмас, тушунарли хато. */
    private UUID parseGroup(String value) {
        return FormParsers.uuid(value, BusinessRule.NOT_FOUND, "Гуруҳ");
    }

    /** Factor парси - normalize қоидаси FormParsers'да (бўш → null). */
    private BigDecimal parseFactor(String value) {
        return FormParsers.decimal(value, BusinessRule.BR_UOM_002, "Factor");
    }
}
