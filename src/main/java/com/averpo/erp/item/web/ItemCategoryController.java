package com.averpo.erp.item.web;

import com.averpo.erp.i18n.Msg;
import com.averpo.erp.item.service.ItemCategoryService;
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
 * Товар категориялари - кичик экран: иерархик рўйхат + битта форма
 * (яратиш ёки ?edit=id билан таҳрирлаш). Full-screen форма шарт эмас -
 * майдонлар иккита холос (spec: item.md).
 */
@Controller
@RequestMapping("/item-categories")
@RequiredArgsConstructor
public class ItemCategoryController {

    /** Категориялар service. */
    private final ItemCategoryService categoryService;

    /** Flash хабарлар учун i18n. */
    private final Msg msg;

    /** Рўйхат + форма (edit параметри билан таҳрир режими). */
    @GetMapping
    public String list(@RequestParam(required = false) UUID edit, Model model) {
        fillModel(model, edit);
        return "item/categories";
    }

    /** Янги категория. */
    @PostMapping
    public String create(@RequestParam String name,
                         @RequestParam(required = false) String parentId,
                         Model model, RedirectAttributes redirect) {
        try {
            categoryService.create(name.strip(), parseUuid(parentId));
        } catch (BusinessRuleException e) {
            fillModel(model, null);
            model.addAttribute("error", e.displayMessage());
            return "item/categories";
        }
        redirect.addFlashAttribute("message", msg.get("cat.created", name.strip()));
        return "redirect:/item-categories";
    }

    /** Категорияни янгилаш. */
    @PostMapping("/{id}")
    public String update(@PathVariable UUID id, @RequestParam String name,
                         @RequestParam(required = false) String parentId,
                         @RequestParam(defaultValue = "false") boolean active,
                         Model model, RedirectAttributes redirect) {
        try {
            categoryService.update(id, name.strip(), parseUuid(parentId), active);
        } catch (BusinessRuleException e) {
            fillModel(model, id);
            model.addAttribute("error", e.displayMessage());
            return "item/categories";
        }
        redirect.addFlashAttribute("message", msg.get("cat.updated", name.strip()));
        return "redirect:/item-categories";
    }

    /** Рўйхат ва форма model'и. */
    private void fillModel(Model model, UUID editId) {
        model.addAttribute("nodes", categoryService.tree());
        model.addAttribute("all", categoryService.all());
        ItemCategoryService.CategoryEdit editing =
                editId == null ? null : categoryService.editView(editId);
        model.addAttribute("editing", editing);
        model.addAttribute("editId", editing == null ? null : editing.id().toString());
    }

    /** Ота категория танлови - parse қоидаси FormParsers'да (бўш → null). */
    private UUID parseUuid(String value) {
        return com.averpo.erp.shared.web.FormParsers.uuid(value,
                com.averpo.erp.shared.exception.BusinessRule.NOT_FOUND,
                "Ота категория");
    }
}
