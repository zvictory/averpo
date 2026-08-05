package com.averpo.erp.ledger.web;

import com.averpo.erp.ledger.service.TrialBalanceService;
import com.averpo.erp.shared.domain.CompanySettings;
import com.averpo.erp.shared.service.CompanySettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Айланма-қолдиқ ведомости экрани. Сатрлар TrialBalanceService'дан,
 * бу ерда фақат кўрсатиш учун Dt/Cr устунларга ажратилади.
 *
 * @author Zafar
 */
@Controller
@lombok.RequiredArgsConstructor
public class TrialBalanceController {

    /**
     * Экранга тайёрланган сатр: signed қийматлар Dt/Cr жуфтларига
     * ажратилган (мусбат - дебет устуни, манфий - кредит устуни).
     *
     * @param accountId счёт id'си - қатор босилганда «Счёт амаллари»га
     *                  drill-down (spec T2)
     * @param name    счёт номи
     * @param code    ихтиёрий счёт рақами
     * @param typeKey счёт тури номининг i18n калити
     */
    public record DisplayRow(java.util.UUID accountId, String name, String code, String typeKey,
                             BigDecimal openDt, BigDecimal openCt,
                             BigDecimal turnDt, BigDecimal turnCt,
                             BigDecimal closeDt, BigDecimal closeCt) { }

    /** TB ҳисоблаш service'и. */
    private final TrialBalanceService trialBalanceService;

    /** «Барча суммалар home да» ёзуви учун home валюта коди (U3). */
    private final CompanySettingsService settingsService;

    /** Давр танланмаса - шу йил бошидан бугунгача. */
    @GetMapping("/reports/trial-balance")
    public String show(@RequestParam(required = false) LocalDate from,
                       @RequestParam(required = false) LocalDate to,
                       Model model) {
        // Sanjar-005: созламалар оқим бошида бир марта ўқилади - аввал ҳар
        // accessor (zoneId ×2 + homeCurrency) алоҳида SELECT берарди
        CompanySettings settings = settingsService.get();
        // Давр default'и - компания zoneId'даги «бугун» (JVM tz эмас, қоида 12/Arbitr-055)
        LocalDate f = from != null ? from
                : LocalDate.now(settings.zoneId()).withDayOfYear(1);
        LocalDate t = to != null ? to : LocalDate.now(settings.zoneId());

        List<TrialBalanceService.Row> rows = trialBalanceService.build(f, t);
        List<DisplayRow> display = rows.stream().map(TrialBalanceController::toDisplay).toList();

        // Умумий йиғиндилар - Dt ва Cr устунлар тенг бўлиши шарт (баланс назорати)
        model.addAttribute("rows", display);
        model.addAttribute("totalOpenDt", sum(display, DisplayRow::openDt));
        model.addAttribute("totalOpenCt", sum(display, DisplayRow::openCt));
        model.addAttribute("totalTurnDt", sum(display, DisplayRow::turnDt));
        model.addAttribute("totalTurnCt", sum(display, DisplayRow::turnCt));
        model.addAttribute("totalCloseDt", sum(display, DisplayRow::closeDt));
        model.addAttribute("totalCloseCt", sum(display, DisplayRow::closeCt));
        model.addAttribute("from", f);
        model.addAttribute("to", t);
        model.addAttribute("homeCurrency", settings.homeCurrencyCode());
        return "ledger/trialBalance";
    }

    /** Signed сатрни Dt/Cr устунли кўринишга ўгиради. */
    private static DisplayRow toDisplay(TrialBalanceService.Row row) {
        return new DisplayRow(row.accountId(), row.name(), row.code(), safeTypeKey(row.typeName()),
                positive(row.opening()), negative(row.opening()),
                row.debitTurnover(), row.creditTurnover(),
                positive(row.closing()), negative(row.closing()));
    }

    /**
     * Тур номини i18n калитига хавфсиз ўгиради. Нотаниш қиймат хом
     * ҳолда қайтади - use-code-as-default туфайли экранда ўзи кўринади,
     * 500 бермайди.
     */
    private static String safeTypeKey(String typeName) {
        try {
            return com.averpo.erp.ledger.domain.AccountType
                    .valueOf(typeName).titleKey();
        } catch (IllegalArgumentException e) {
            return typeName;
        }
    }

    /** Мусбат қисми (дебет устуни учун), акс ҳолда 0. */
    private static BigDecimal positive(BigDecimal value) {
        return value.signum() > 0 ? value : BigDecimal.ZERO;
    }

    /** Манфий қисмнинг абсолюти (кредит устуни учун), акс ҳолда 0. */
    private static BigDecimal negative(BigDecimal value) {
        return value.signum() < 0 ? value.abs() : BigDecimal.ZERO;
    }

    /** Устун йиғиндиси. */
    private static BigDecimal sum(List<DisplayRow> rows,
                                  java.util.function.Function<DisplayRow, BigDecimal> getter) {
        return rows.stream().map(getter).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
