package com.averpo.erp.ledger.web;

import com.averpo.erp.ledger.service.BalanceSheetService;
import com.averpo.erp.shared.service.CompanySettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/**
 * Balance Sheet экрани - ҳисоблаш тўлиқ BalanceSheetService'да,
 * бу ерда фақат default сана ва model йиғилади.
 *
 * @author Zafar
 */
@Controller
@lombok.RequiredArgsConstructor
public class BalanceSheetController {

    /** Баланс ҳисоблаш service'и. */
    private final BalanceSheetService balanceSheetService;

    /** «Барча суммалар home да» ёзуви учун home валюта коди (U3). */
    private final CompanySettingsService settingsService;

    /** Сана танланмаса - бугунга (компания timezone'ида, қоида №12). */
    @GetMapping("/reports/balance-sheet")
    public String show(@RequestParam(required = false) LocalDate asOf, Model model) {
        LocalDate date = asOf != null ? asOf
                : LocalDate.now(settingsService.zoneId());

        model.addAttribute("report", balanceSheetService.build(date));
        // Drill-down даври QBO услубида жорий молия йилидан бошланади
        model.addAttribute("fyStart", settingsService.get().fiscalYearStart(date));
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        return "ledger/balanceSheet";
    }
}
