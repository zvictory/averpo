package com.averpo.erp.ledger.web;

import com.averpo.erp.ledger.service.ProfitAndLossService;
import com.averpo.erp.shared.service.CompanySettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/**
 * Profit &amp; Loss экрани - ҳисоблаш тўлиқ ProfitAndLossService'да,
 * бу ерда фақат default давр ва model йиғилади.
 *
 * @author Zafar
 */
@Controller
@lombok.RequiredArgsConstructor
public class ProfitAndLossController {

    /** P&L ҳисоблаш service'и. */
    private final ProfitAndLossService profitAndLossService;

    /** «Барча суммалар home да» ёзуви учун home валюта коди (U3). */
    private final CompanySettingsService settingsService;

    /** Давр танланмаса - шу йил бошидан бугунгача (TB конвенцияси). */
    @GetMapping("/reports/profit-loss")
    public String show(@RequestParam(required = false) LocalDate from,
                       @RequestParam(required = false) LocalDate to,
                       Model model) {
        // «Бугун» компания timezone'ида (қоида №12, ApAging қолипи) -
        // сервер UTC'да бўлса ярим тундан кейин кечаги кун чиқарди
        LocalDate today = LocalDate.now(settingsService.zoneId());
        LocalDate f = from != null ? from : today.withDayOfYear(1);
        LocalDate t = to != null ? to : today;
        if (f.isAfter(t)) {
            // Тескари давр (қўлда бузилган URL/autofill) - reports.md
            // талабича default даврга қайтамиз: бўш P&L'ни бухгалтер
            // ҳақиқий нол натижа деб ўқиб қолмасин
            f = today.withDayOfYear(1);
            t = today;
        }

        model.addAttribute("report", profitAndLossService.build(f, t));
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        return "ledger/profitLoss";
    }
}
