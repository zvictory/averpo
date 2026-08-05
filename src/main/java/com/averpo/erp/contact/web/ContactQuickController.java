package com.averpo.erp.contact.web;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.shared.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * Combobox «+ Янги қўшиш» қуйма endpoint'лари - мижоз/таъминотчи
 * (spec: docs/modules/combobox.md, Arbitr-066).
 *
 * <p>Алоҳида controller, чунки {@link ContactController}'нинг class-mapping
 * regex'ида employees ҳам бор - қуйма қўшиш эса фақат мижоз/таъминотчига
 * рухсат этилган (ходим формаси оғир, spec 2-босқич). Мантиқ бутунлай
 * {@link ContactService#create} реюзи - янги бизнес қоида ЙЎҚ.
 *
 * <p>VIEWER ҳимояси SecurityConfig'даги умумий POST қоидасида (403).
 *
 * @author Zafar
 */
@Controller
@RequiredArgsConstructor
public class ContactQuickController {

    /** Контактлар service - қуйма яратишда тўлиқ валидацияси реюз қилинади. */
    private final ContactService contactService;

    /** Home currency - қуйма контакт валютаси default'и (форма home'ни олдиндан танлайди; Arbitr-159/161). */
    private final com.averpo.erp.shared.service.CompanySettingsService settingsService;

    /** Фаол валюталар рўйхати - қуйма формадаги валюта select учун (Arbitr-161). */
    private final com.averpo.erp.shared.service.CurrencyService currencyService;

    /**
     * Модал ичи учун мини форма fragment'и (CSRF token билан) -
     * penguin-combobox.js'даги openAdd() («+ Янги ...» банди) GET
     * билан Penguin modal ичига юклайди (Arbitr-123).
     */
    @GetMapping("/{kind:customers|vendors}/quick-form")
    public String quickForm(@PathVariable String kind, Model model) {
        model.addAttribute("kind", kind);
        model.addAttribute("currencies", currencyService.active());
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        return "contact/quickForm";
    }

    /**
     * Қуйма яратиш: фақат кўрсатиладиган ном - қолган майдонлар кейин
     * тўлиқ формада тўлдирилади (QBO «+ Add new» услуби).
     *
     * @return муваффақиятда {id, label, currency} - JS option қўшиб
     *         танлайди; currency (қуйма формадан танланган; бўш бўлса home -
     *         Arbitr-159) option data-currency'сига кўчади, шунда контакт-
     *         валюта занжири (Arbitr-087) янги контактда ҳам ишлайди; бизнес
     *         қоида бузилса 422 {message} (модал ичида кўрсатилади)
     */
    @PostMapping("/{kind:customers|vendors}/quick")
    @ResponseBody
    public ResponseEntity<Map<String, String>> quick(
            @PathVariable String kind,
            @RequestParam(required = false) String displayName,
            @RequestParam(required = false) String currency) {
        try {
            Contact contact = contactService.create(
                    "customers".equals(kind) ? ContactType.CUSTOMER : ContactType.VENDOR,
                    new ContactService.ContactData(displayName, null, null, null,
                            null, null, currency, null, null, null, null));
            return ResponseEntity.ok(Map.of(
                    "id", contact.getId().toString(),
                    "label", contact.getDisplayName(),
                    "currency", contact.getCurrency().getCode()));
        } catch (BusinessRuleException e) {
            return ResponseEntity.unprocessableEntity()
                    .body(Map.of("message", e.displayMessage()));
        }
    }
}
