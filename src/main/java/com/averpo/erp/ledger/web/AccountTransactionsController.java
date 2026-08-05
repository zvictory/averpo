package com.averpo.erp.ledger.web;

import com.averpo.erp.ledger.service.AccountTransactionsService;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.web.FilterQuery;
import com.averpo.erp.shared.web.PageSizeResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * «Счёт амаллари» экрани (spec T1) - CoA қатори босилганда очилади,
 * кейинроқ Trial Balance drill-down (T2) ҳам шу манзилга келади.
 *
 * <p>AccountController'дан алоҳида: у CoA CRUD/импорт билан банд,
 * бу эса соф ўқиш экрани - мантиқ тўлиқ
 * {@link AccountTransactionsService}'да (юпқа контроллер қоидаси).
 */
@Controller
@lombok.RequiredArgsConstructor
public class AccountTransactionsController {

    /** Register'ни қурувчи public ledger service. */
    private final AccountTransactionsService transactionsService;

    /** Қолдиқ устуни home валютада эканини + давр default'и zoneId учун. */
    private final CompanySettingsService settingsService;

    /**
     * Давр танланмаса - шу йил бошидан бугунгача (TB билан бир хил default).
     * Саҳифаланган (DEC-105б): ?page= + ҳажм PageSizeResolver'дан;
     * давр филтри саҳифа линкларида ЕЧИЛГАН қийматлари билан сақланади -
     * ярим тунда default силжиса ҳам варақлаш кесими барқарор қолади.
     */
    @GetMapping("/accounts/{id}/transactions")
    public String show(@PathVariable UUID id,
                       @RequestParam(required = false) LocalDate from,
                       @RequestParam(required = false) LocalDate to,
                       @RequestParam(required = false, defaultValue = "0") int page,
                       HttpServletRequest request,
                       HttpServletResponse response,
                       Model model) {
        // Default оралиқ - компания zoneId'даги «бугун»гача (JVM tz эмас,
        // қоида 12/DEC-055)
        LocalDate f = from != null ? from
                : LocalDate.now(settingsService.zoneId()).withDayOfYear(1);
        LocalDate t = to != null ? to : LocalDate.now(settingsService.zoneId());
        // DEC-105: саҳифа ҳажми ?size=/cookie'дан (PageSizeResolver)
        int size = PageSizeResolver.resolve(request, response, "account-transactions");
        var paged = transactionsService.registerPage(id, f, t, page, size);
        var register = paged.register();
        model.addAttribute("register", register);
        model.addAttribute("page", paged.page());
        // Саҳифа линклари жорий давр филтрини сақлайди (audit қолипи)
        model.addAttribute("filterQuery", new FilterQuery()
                .add("from", f).add("to", t).toString());
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        // Контакт номлари - ФАҚАТ саҳифада кўринган сатрлардаги id'лар бўйича,
        // ledger service'нинг хом SQL name-map'и орқали (contact модулига
        // боғланмайди - ТЕМИР ҚОИДА №6; бутун каталог юкланмайди, DEC-044)
        var contactIds = register.rows().stream()
                .map(AccountTransactionsService.Row::contactId)
                .filter(Objects::nonNull).distinct().toList();
        model.addAttribute("contactNames", transactionsService.contactNames(contactIds));
        return "ledger/accountTransactions";
    }
}
