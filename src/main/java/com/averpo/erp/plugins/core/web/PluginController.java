package com.averpo.erp.plugins.core.web;

import com.averpo.erp.i18n.Msg;
import com.averpo.erp.plugins.core.domain.PluginKey;
import com.averpo.erp.plugins.core.service.PluginService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Плагинлар бошқаруви экрани (docs/modules/plugins.md): реестр рўйхати +
 * toggle. Фақат SUPER_ADMIN - route {@code /settings/**} бўлгани учун
 * SETTINGS соҳаси қоидаси остида (UrlPermissionMap: GET=SETTINGS_VIEW,
 * POST=SETTINGS_EDIT - матрицада иккиси фақат SUPER_ADMIN'да); контроллерга
 * алоҳида аннотация керак эмас, ҳимоя SecurityConfig'да (092 нақши).
 */
@Controller
@RequestMapping("/settings/plugins")
@RequiredArgsConstructor
public class PluginController {

    /** Гейт/toggle'нинг ягона манбаси. */
    private final PluginService pluginService;

    /** Flash хабарлар учун i18n. */
    private final Msg msg;

    /** Рўйхат сатри: enum метадатаси + жорий ёқилиш ҳолати бирга. */
    public record PluginRow(PluginKey key, boolean enabled) {
    }

    /** Плагинлар рўйхати - реестр (enum) тартибида, ҳолати билан. */
    @GetMapping
    public String list(Model model) {
        List<PluginRow> rows = java.util.Arrays.stream(PluginKey.values())
                .map(key -> new PluginRow(key, pluginService.isEnabled(key)))
                .toList();
        model.addAttribute("rows", rows);
        return "plugins/list";
    }

    /**
     * Toggle: янги ҳолат hidden input'дан келади (currencies.jte актив/
     * нофаол нақши). Бузуқ калит (enum'да йўқ) Spring конверсиясида 400
     * беради - бу form tampering ҳолати, BR эмас.
     */
    @PostMapping("/{key}/toggle")
    public String toggle(@PathVariable PluginKey key,
                         @RequestParam boolean enabled,
                         RedirectAttributes redirect) {
        pluginService.setEnabled(key, enabled);
        redirect.addFlashAttribute("message",
                msg.get(enabled ? "plugin.msg.enabled" : "plugin.msg.disabled",
                        msg.get(key.nameKey())));
        return "redirect:/settings/plugins";
    }
}
