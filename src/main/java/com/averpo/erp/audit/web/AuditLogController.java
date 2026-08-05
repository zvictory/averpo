package com.averpo.erp.audit.web;

import com.averpo.erp.audit.domain.AuditEvent;
import com.averpo.erp.audit.domain.AuditEventType;
import com.averpo.erp.audit.service.AuditLogService;
import com.averpo.erp.audit.service.AuditLogService.AuditFilter;
import com.averpo.erp.i18n.Msg;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.web.FilterQuery;
import com.averpo.erp.shared.web.Fmt;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Аудит журнали экрани (docs/modules/audit-log.md, QBO Audit Log) -
 * фақат USERS соҳаси эгаси - SUPER_ADMIN (user-roles.md), фақат ўқиш: ёзувни
 * тизим ўзи киритади, update/delete умуман йўқ.
 *
 * <p>Сана филтри фойдаланувчидан компания минтақасидаги КУН сифатида
 * келади - UTC оралиққа шу ерда ўгирилади (қоида №12): from = кун
 * боши, to = кейинги кун боши (эксклюзив) - «Гача» куни тўлиқ киради.
 */
@Controller
@RequestMapping("/audit-log")
@RequiredArgsConstructor
public class AuditLogController {

    /** Default саҳифа ҳажми (spec). */
    private static final int DEFAULT_SIZE = 50;

    /** Саҳифа ҳажмининг юқори чегараси - query tampering'га қалқон. */
    private static final int MAX_SIZE = 200;

    /** Аудит public API'си. */
    private final AuditLogService auditLogService;

    /** Вақтларни компания минтақасида кўрсатиш учун (қоида №12). */
    private final CompanySettingsService settingsService;

    /** Қурилма тури ёрлиқлари (мобил/десктоп) жорий тилда чиқиши учун. */
    private final Msg msg;

    /** Рўйхат: филтрлар + пагинация, доим янгидан эскига. */
    @GetMapping
    public String list(@RequestParam(required = false) LocalDate from,
                       @RequestParam(required = false) LocalDate to,
                       @RequestParam(required = false) String eventType,
                       @RequestParam(required = false) String username,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "" + DEFAULT_SIZE) int size,
                       Model model) {
        ZoneId zone = settingsService.zoneId();
        AuditEventType type = parseTypeSafe(eventType);
        Instant fromTs = from == null ? null : from.atStartOfDay(zone).toInstant();
        Instant toTs = to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        // Тартиб: created_at, кейин UUIDv7 id - бир транзакция ичидаги
        // кетма-кетлик ҳам тўғри кўринади (курс тарихи нақши)
        Page<AuditEvent> events = auditLogService.page(
                new AuditFilter(fromTs, toTs, type, username),
                PageRequest.of(safePage, safeSize,
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        // Вақт матни олдиндан тайёрланади - шаблонда формат мантиқи
        // бўлмасин (UserController.lockedTexts нақши)
        Map<UUID, String> times = new HashMap<>();
        // «IP: x · Chrome 126 · Windows · десктоп · UZ» матни ҳам олдиндан
        // (Arbitr-062, кенгайиши Arbitr-091): тўлиқ UA шаблонда title'га
        // боради, қисқа client шу ерда ажратилади
        Map<UUID, String> clients = new HashMap<>();
        for (AuditEvent event : events.getContent()) {
            times.put(event.getId(), Fmt.dt(event.getCreatedAt(), zone));
            String client = clientLine(event);
            if (client != null) {
                clients.put(event.getId(), client);
            }
        }
        model.addAttribute("events", events.getContent());
        model.addAttribute("times", times);
        model.addAttribute("clients", clients);
        model.addAttribute("page", events.getNumber());
        model.addAttribute("totalPages", Math.max(events.getTotalPages(), 1));
        model.addAttribute("size", safeSize);
        model.addAttribute("hasPrev", events.hasPrevious());
        model.addAttribute("hasNext", events.hasNext());
        model.addAttribute("from", from == null ? "" : from.toString());
        model.addAttribute("to", to == null ? "" : to.toString());
        model.addAttribute("eventType", type == null ? "" : type.name());
        model.addAttribute("username", username == null ? "" : username);
        // Default ҳажм query'га ёзилмайди - пагинация линклари тоза қолсин
        model.addAttribute("filterQuery", new FilterQuery()
                .add("from", from)
                .add("to", to)
                .add("eventType", type == null ? null : type.name())
                .add("username", username)
                .add("size", safeSize == DEFAULT_SIZE ? null : safeSize)
                .toString());
        return "audit/auditLog";
    }

    /**
     * Тафсилот устунидаги «IP: 240.1.2.3 · v6: 2a05:…40c1 · Chrome 126 ·
     * Windows · десктоп · UZ» матни (Arbitr-062, кенгайиши Arbitr-091) -
     * ҳамма бўлак null-safe: эски ёзувларда ipV4/country бўш, dev'да CF
     * header'лар йўқ, фон ёзувларида IP/UA умуман йўқ (ҳаммаси бўш бўлса
     * null - span умуман чиқмайди). IPv4 бор бўлса БИРИНЧИ (фойдаланувчи
     * талаби: IPv4 муҳимроқ), уланишнинг IPv6 манзили ёнида қисқартирилган
     * кўринишда қолади. OS/қурилма raw user_agent'дан парсланади - тарихий
     * ёзувлар ҳам янги форматда очилади.
     */
    private String clientLine(AuditEvent event) {
        List<String> parts = new ArrayList<>();
        String primaryIp = event.getIpV4() != null
                ? event.getIpV4() : event.getIpAddress();
        if (primaryIp != null) {
            parts.add("IP: " + primaryIp);
        }
        // IPv4 устувор бўлганда IPv6 уланиш манзили йўқолмайди - қисқа
        // кўринишда ёнида туради (тўлиғи ip_address устунида сақланган)
        if (event.getIpV4() != null && event.getIpAddress() != null
                && event.getIpAddress().contains(":")) {
            parts.add("v6: " + shortV6(event.getIpAddress()));
        }
        String browser = clientShort(event.getUserAgent());
        if (browser != null) {
            parts.add(browser);
        }
        String os = osShort(event.getUserAgent());
        if (os != null) {
            parts.add(os);
            // Қурилма тури фақат OS танилганда - curl каби CLI UA'га
            // «десктоп» ёзиш чалғитарди
            parts.add(msg.lookup(deviceKey(event.getUserAgent())));
        }
        if (event.getCountry() != null) {
            parts.add(event.getCountry());
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    /**
     * User-Agent'дан қисқа браузер номи + major версия («Chrome 126»).
     * Тартиб муҳим: Chrome-асосли браузерлар (Edge/Opera) UA'сида
     * «Chrome/» ҳам бор - аввал ўзи текширилади; Safari эса Chrome'сиз
     * UA'дагина Safari (версияси «Version/» токенида). Major кифоя - UA
     * reduction сабаб minor барибир аниқ эмас (Arbitr-091). Нотаниш UA -
     * биринчи токен (масалан curl/8.5) 30 белгигача. Package-private -
     * unit тест тўғридан чақиради.
     */
    static String clientShort(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        if (userAgent.contains("Edg/") || userAgent.contains("Edge/")) {
            return withMajor("Edge", userAgent, "Edg/", "Edge/");
        }
        if (userAgent.contains("OPR/") || userAgent.contains("Opera")) {
            return withMajor("Opera", userAgent, "OPR/");
        }
        if (userAgent.contains("Firefox/")) {
            return withMajor("Firefox", userAgent, "Firefox/");
        }
        if (userAgent.contains("Chrome/")) {
            return withMajor("Chrome", userAgent, "Chrome/");
        }
        if (userAgent.contains("Safari/")) {
            return withMajor("Safari", userAgent, "Version/");
        }
        String first = userAgent.strip().split("\\s+")[0];
        return first.length() > 30 ? first.substring(0, 30) : first;
    }

    /**
     * Ном + биринчи топилган маркердан кейинги major рақам; маркер ёки
     * рақам топилмаса ном ўзи қолади (эски/ғалати UA'да версиясиз ҳам
     * браузер номи кўринаверсин).
     */
    private static String withMajor(String name, String userAgent, String... markers) {
        for (String marker : markers) {
            int at = userAgent.indexOf(marker);
            if (at < 0) {
                continue;
            }
            int start = at + marker.length();
            int end = start;
            while (end < userAgent.length() && Character.isDigit(userAgent.charAt(end))) {
                end++;
            }
            if (end > start) {
                return name + " " + userAgent.substring(start, end);
            }
        }
        return name;
    }

    /**
     * User-Agent'дан операцион тизим номи ёки null (нотаниш/CLI UA).
     * Тартиб муҳим: Android UA'сида «Linux» ҳам бор - аввал Android;
     * iPhone/iPad UA'сида «like Mac OS X» бор - iOS Macintosh'дан олдин
     * текширилади. Package-private - unit тест тўғридан чақиради.
     */
    static String osShort(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        if (userAgent.contains("Windows")) {
            return "Windows";
        }
        if (userAgent.contains("Android")) {
            return "Android";
        }
        if (userAgent.contains("iPhone") || userAgent.contains("iPad")
                || userAgent.contains("iPod")) {
            return "iOS";
        }
        if (userAgent.contains("Macintosh") || userAgent.contains("Mac OS X")) {
            return "macOS";
        }
        if (userAgent.contains("X11") || userAgent.contains("Linux")) {
            return "Linux";
        }
        return null;
    }

    /**
     * UA'даги Mobile белгисидан қурилма тури message калити: браузер UA'лари
     * мобилда «Mobile» токенини киритади (десктопда йўқ) - шу белги кифоя,
     * UA reduction уни ўзгартирмайди. Package-private - unit тест тўғридан
     * чақиради.
     */
    static String deviceKey(String userAgent) {
        return userAgent.contains("Mobile")
                ? "audit.device.mobile" : "audit.device.desktop";
    }

    /**
     * IPv6'ни экран учун қисқартиради: биринчи группа + … + охирги 4 белги
     * («2a05:…40c1»). Фақат КЎРСАТИШ учун - тўлиқ манзил ip_address
     * устунида сақланган. Қисқартириш фойда бермайдиган калта манзил
     * («::1») ўзгармай қайтади. Package-private - unit тест тўғридан
     * чақиради.
     */
    static String shortV6(String v6) {
        int firstColon = v6.indexOf(':');
        if (firstColon < 0 || v6.length() <= firstColon + 6) {
            return v6;
        }
        return v6.substring(0, firstColon + 1) + "…" + v6.substring(v6.length() - 4);
    }

    /** Query қийматидан турни хавфсиз парслайди - бузуқ қиймат филтрсиз. */
    private static AuditEventType parseTypeSafe(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AuditEventType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
