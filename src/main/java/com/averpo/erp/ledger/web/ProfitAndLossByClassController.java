package com.averpo.erp.ledger.web;

import com.averpo.erp.i18n.Msg;
import com.averpo.erp.ledger.service.ProfitAndLossByClassService;
import com.averpo.erp.shared.service.CompanySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * P&amp;L by Class ҳисоботи экрани (class-tracking.md). Алоҳида
 * controller - умумий ҳисобот контроллерларига тегилмайди (параллел
 * иш); давр default'и оддий P&amp;L билан бир хил (йил боши - бугун,
 * компания минтақасида; тескари давр default'га қайтади, Alisa-005).
 */
@Controller
@RequiredArgsConstructor
public class ProfitAndLossByClassController {

    /** Ҳисобот қурувчи service. */
    private final ProfitAndLossByClassService reportService;

    /** Давр default'лари компания минтақасида (қоида №12) + home ёрлиғи. */
    private final CompanySettingsService settingsService;

    /** «Кўрсатилмаган» устун номи i18n'дан. */
    private final Msg msg;

    /** Ҳисобот: давр филтри билан, устунлар class кесимида. */
    @GetMapping("/reports/profit-and-loss-by-class")
    public String report(@RequestParam(required = false) LocalDate from,
                         @RequestParam(required = false) LocalDate to,
                         Model model) {
        LocalDate today = LocalDate.now(settingsService.zoneId());
        LocalDate f = from != null ? from : today.withDayOfYear(1);
        LocalDate t = to != null ? to : today;
        if (f.isAfter(t)) {
            // Бузуқ URL - default даврга қайтамиз (Alisa-005 нақши)
            f = today.withDayOfYear(1);
            t = today;
        }
        ProfitAndLossByClassService.Report report = reportService.build(f, t);
        // null устун номи = «Кўрсатилмаган» (service i18n билмайди)
        List<String> columns = new ArrayList<>();
        for (String column : report.columns()) {
            columns.add(column != null ? column : msg.get("plc.unspecified"));
        }
        model.addAttribute("report", report);
        model.addAttribute("columns", columns);
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        return "reports/profitLossByClass";
    }
}
