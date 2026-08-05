package com.averpo.erp.ledger.web;

import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * Combobox «+ Янги қўшиш» қуйма endpoint'лари - счёт
 * (spec: docs/modules/combobox.md, Arbitr-066).
 *
 * <p>Қуйма формада ном + detail type (группаланган select) + валюта
 * (фақат валютага боғланган турда - Arbitr-161) - код, ота, opening
 * balance кейин тўлиқ формада. Мантиқ бутунлай {@link AccountService#create}
 * реюзи (BR-COA-001/008/009/010/011 ўша ерда) - янги бизнес қоида ЙЎҚ.
 * VIEWER ҳимояси SecurityConfig POST қоидасида.
 */
@Controller
@RequiredArgsConstructor
public class AccountQuickController {

    /** Счётлар service - қуйма яратишда валидацияси реюз қилинади. */
    private final AccountService accountService;

    /** Фаол валюталар рўйхати - қуйма формадаги валюта select учун (Arbitr-161). */
    private final com.averpo.erp.shared.service.CurrencyService currencyService;

    /** Модал ичи учун мини форма fragment'и (CSRF token + валюталар рўйхати). */
    @GetMapping("/accounts/quick-form")
    public String quickForm(Model model) {
        model.addAttribute("currencies", currencyService.active());
        return "ledger/accountQuickForm";
    }

    /**
     * Қуйма яратиш: ном + detail type + (валютага боғланган турда) валюта;
     * postable=true (оддий ишчи счёт), қолгани default.
     *
     * @return муваффақиятда {id, label, [currency]} - currency фақат валюта
     *         счётида қайтади (даромад/харажат каби турда бўлмайди, шунга
     *         Map'га шартли қўшилади); бизнес қоида бузилса 422 {message}
     */
    @PostMapping("/accounts/quick")
    @ResponseBody
    public ResponseEntity<Map<String, String>> quick(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String detailType,
            @RequestParam(required = false) String currency) {
        try {
            Account account = accountService.create(name, parseDetailType(detailType),
                    null, null, null, true, currency);
            // Map.of null қиймат қабул қилмайди - currency йўқ турда HashMap шартли
            Map<String, String> body = new java.util.HashMap<>();
            body.put("id", account.getId().toString());
            body.put("label", account.getName());
            if (account.getCurrency() != null) {
                body.put("currency", account.getCurrency().getCode());
            }
            return ResponseEntity.ok(body);
        } catch (BusinessRuleException e) {
            return ResponseEntity.unprocessableEntity()
                    .body(Map.of("message", e.displayMessage()));
        }
    }

    /** Detail type парси - бўш/бузуқ қийматга BR-COA-008 (AccountController нақши). */
    private AccountDetailType parseDetailType(String value) {
        if (value == null || value.isBlank()) {
            return null; // AccountService BR-COA-008 билан рад этади
        }
        try {
            return AccountDetailType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException(BusinessRule.BR_COA_008,
                    "Нотўғри detail type: " + value);
        }
    }
}
