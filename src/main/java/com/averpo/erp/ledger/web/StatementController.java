package com.averpo.erp.ledger.web;

import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.ledger.service.StatementService;
import com.averpo.erp.shared.service.CompanySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Мижоз кўчирмаси экрани (Statement, QBO паритети). Ҳисоблаш тўлиқ
 * {@link StatementService}'да - бу ерда мижоз танлови, default давр
 * (ЖОРИЙ ОЙ) ва model йиғилади. Read-only, GL'га тегмайди.
 *
 * <p>Мижоз танланмаса - фақат форма (кўчирма ҳисобланмайди). Print
 * кўриниши учун компания номи + мижоз + давр сарлавҳага узатилади.
 */
@Controller
@RequiredArgsConstructor
public class StatementController {

    /** Кўчирма ҳисоблаш service'и. */
    private final StatementService statementService;

    /** Мижоз select ва танланган мижоз номи учун. */
    private final ContactService contactService;

    /** Home валюта, timezone ва компания номи (print сарлавҳа) учун. */
    private final CompanySettingsService settingsService;

    /** Мижоз + давр → кўчирма. Давр танланмаса жорий ой. */
    @GetMapping("/reports/statement")
    public String show(@RequestParam(required = false) String customerId,
                       @RequestParam(required = false) LocalDate from,
                       @RequestParam(required = false) LocalDate to,
                       Model model) {
        // «Бугун» компания timezone'ида (қоида №12, P&L қолипи); default
        // давр - ЖОРИЙ ОЙ (кўчирма одатда ойма-ой юборилади, йил боши эмас)
        LocalDate today = LocalDate.now(settingsService.zoneId());
        LocalDate f = from != null ? from : today.withDayOfMonth(1);
        LocalDate t = to != null ? to : today;
        if (f.isAfter(t)) {
            // Тескари давр (қўлда бузилган URL) - default даврга қайтамиз
            f = today.withDayOfMonth(1);
            t = today;
        }

        UUID selected = parseUuidOrNull(customerId);
        model.addAttribute("from", f);
        model.addAttribute("to", t);
        model.addAttribute("customers", contactService.activeRefsByType(ContactType.CUSTOMER));
        model.addAttribute("selectedCustomerId", selected == null ? "" : selected.toString());
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        model.addAttribute("companyName", settingsService.get().getName());
        if (selected != null) {
            model.addAttribute("statement", statementService.statement(selected, f, t));
            model.addAttribute("customerName", contactService.get(selected).getDisplayName());
        }
        return "ledger/statement";
    }

    /** Мижоз id'сини лениент парслайди: бўш/бузуқ - null (бўш форма). */
    private static UUID parseUuidOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
