package com.averpo.erp.plugins.telegram.web;

import com.averpo.erp.i18n.Msg;
import com.averpo.erp.plugins.telegram.service.TelegramService;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Telegram бот созламаси (SUPER_ADMIN) ва профил улаш амаллари
 * (docs/modules/user-profile.md 3-бўлим, Arbitr-103).
 *
 * <p><b>Икки хил ҳимоя</b>:
 * <ul>
 *   <li>{@code /settings/telegram} - SETTINGS соҳаси остида
 *       (UrlPermissionMap {@code /settings/**}): GET=SETTINGS_VIEW,
 *       POST=SETTINGS_EDIT - матрицада иккиси фақат SUPER_ADMIN'да;</li>
 *   <li>{@code /profile/telegram/*} - соҳасиз, ҳар роль ЎЗ профилини
 *       бошқаради: SecurityConfig'да АНИҚ {@code authenticated()}
 *       ёзилган (092 ТУЗОҒИ - акс ҳолда POST-catchall уларни соҳа
 *       EDIT талабига ташлаб, VIEWER_AUDITOR ўз Telegram'ини улай
 *       олмасди).</li>
 * </ul>
 *
 * <p><b>Плагин гейти</b> (Arbitr-113): плагин ўчиқ бўлса ҳар қандай
 * route 404 беради - UI яшириш кифоя эмас (092 сабоғи), plugins.md:
 * «ўчиқ плагиннинг ҳеч бир route/фичаси ишламайди». 404 (403 эмас)
 * атайлаб: фича уланмаган - демак саҳифа МАВЖУД ЭМАС.
 */
@Controller
@RequiredArgsConstructor
public class TelegramController {

    /** Бот созлаш ва улаш оқимининг ягона хизмати. */
    private final TelegramService telegramService;

    /** Flash хабарлар учун i18n. */
    private final Msg msg;

    /** Созлама саҳифаси: токен ҳолати (маскаланган) + бот номи. */
    @GetMapping("/settings/telegram")
    public String settings(Model model) {
        requirePluginEnabled();
        model.addAttribute("maskedToken", telegramService.maskedToken());
        model.addAttribute("botUsername", telegramService.botUsername());
        return "plugins/telegram";
    }

    /**
     * Токенни текшириб сақлайди (getMe). Хато (BR-TG-001/004) ўша
     * саҳифада кириллча хабар билан кўринади - хом 400 эмас.
     */
    @PostMapping("/settings/telegram")
    public String saveToken(@RequestParam String token, RedirectAttributes redirect) {
        requirePluginEnabled();
        try {
            telegramService.saveToken(token);
            redirect.addFlashAttribute("message",
                    msg.get("telegram.saved", "@" + telegramService.botUsername()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/settings/telegram";
    }

    /** Токенни ўчиради (бот узилади; уланган чатлар сақланади). */
    @PostMapping("/settings/telegram/delete")
    public String deleteToken(RedirectAttributes redirect) {
        requirePluginEnabled();
        telegramService.deleteToken();
        redirect.addFlashAttribute("message", msg.get("telegram.deleted"));
        return "redirect:/settings/telegram";
    }

    /**
     * Профилдан улашни бошлайди: бир марталик код + deep link flash
     * билан профил блокида кўрсатилади (TTL 10 дақиқа). Бот
     * созланмаган бўлса BR-TG-003 хабари.
     */
    @PostMapping("/profile/telegram/link")
    public String startLink(RedirectAttributes redirect) {
        requirePluginEnabled();
        try {
            TelegramService.LinkInfo link = telegramService.startLink();
            // Код flash'да: у бир марталик ва қисқа умрли - базадан
            // қайта ўқиб кўрсатиш (саҳифа янгиланганда) шарт эмас
            redirect.addFlashAttribute("telegramLink", link);
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.displayMessage());
        }
        return "redirect:/profile";
    }

    /** Профилдан Telegram'ни узади. */
    @PostMapping("/profile/telegram/unlink")
    public String unlink(RedirectAttributes redirect) {
        requirePluginEnabled();
        telegramService.unlink();
        redirect.addFlashAttribute("message", msg.get("telegram.unlinked"));
        return "redirect:/profile";
    }

    /**
     * Плагин гейти - ўчиқ бўлса route умуман мавжуд эмас (404).
     * Ҳар кириш нуқтасида такрорланади: гейт ЯГОНА манбадан
     * (PluginService.isEnabled) сўралади, UI яширинишига таянилмайди.
     */
    private void requirePluginEnabled() {
        if (!telegramService.enabled()) {
            throw new NotFoundException("Telegram плагини ёқилмаган");
        }
    }
}
