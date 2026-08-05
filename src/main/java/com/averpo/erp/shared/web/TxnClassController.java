package com.averpo.erp.shared.web;

import com.averpo.erp.i18n.Msg;
import com.averpo.erp.shared.domain.ClassTrackingMode;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.service.TxnClassService;
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

import java.util.UUID;

/**
 * Йўналишлар (Class) каталог экрани (class-tracking.md): дарахт
 * кўриниш, яратиш/номлаш/ота алмаштириш/актив-деактив + tracking
 * режими танлагичи. Йўл /settings остида - SETTINGS (SUPER_ADMIN) чекловига автоматик
 * тушади; мантиқ TxnClassService'да, контроллер юпқа.
 *
 * @author Zafar
 */
@Controller
@RequestMapping("/settings/classes")
@RequiredArgsConstructor
public class TxnClassController {

    /** Йўналишлар service. */
    private final TxnClassService txnClassService;

    /** Tracking режими шу экранда алмаштирилади. */
    private final CompanySettingsService settingsService;

    /** Flash хабарлар учун i18n. */
    private final Msg msg;

    /** Рўйхат (дарахт) + қўшиш формаси + режим танлагичи. */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("classes", txnClassService.all());
        model.addAttribute("mode", settingsService.trackClasses().name());
        return "shared/txnClasses";
    }

    /** Tracking режимини алмаштириш (OFF/PER_TXN/PER_LINE). */
    @PostMapping("/mode")
    public String changeMode(@RequestParam String mode, RedirectAttributes redirect) {
        settingsService.changeTrackClasses(parseModeSafe(mode));
        redirect.addFlashAttribute("message", msg.get("cls.modeSaved"));
        return "redirect:/settings/classes";
    }

    /** Янги йўналиш (ихтиёрий ота билан). */
    @PostMapping
    public String create(@RequestParam String name,
                         @RequestParam(required = false) String parentId,
                         RedirectAttributes redirect) {
        try {
            txnClassService.create(name,
                    FormParsers.uuid(parentId, com.averpo.erp.shared.exception
                            .BusinessRule.NOT_FOUND, "Ота йўналиш"));
            redirect.addFlashAttribute("message", msg.get("cls.created", name.strip()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/settings/classes";
    }

    /** Таҳрир: ном + ота + фаоллик (BR-CLS-002/003 service'да). */
    @PostMapping("/{id}")
    public String update(@PathVariable UUID id, @RequestParam String name,
                         @RequestParam(required = false) String parentId,
                         @RequestParam(defaultValue = "false") boolean active,
                         RedirectAttributes redirect) {
        try {
            txnClassService.rename(id, name);
            txnClassService.changeParent(id,
                    FormParsers.uuid(parentId, com.averpo.erp.shared.exception
                            .BusinessRule.NOT_FOUND, "Ота йўналиш"));
            txnClassService.setActive(id, active);
            redirect.addFlashAttribute("message", msg.get("cls.updated", name.strip()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/settings/classes";
    }

    /** Query қийматидан режимни хавфсиз парслайди - бузуқ қиймат OFF. */
    private static ClassTrackingMode parseModeSafe(String value) {
        try {
            return ClassTrackingMode.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return ClassTrackingMode.OFF;
        }
    }
}
