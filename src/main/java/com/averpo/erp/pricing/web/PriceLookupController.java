package com.averpo.erp.pricing.web;

import com.averpo.erp.pricing.service.PriceListService;
import com.averpo.erp.shared.service.CompanySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Нарх рўйхатидан invoice prefill (docs/modules/price-list.md «Ечиш
 * тартиби»): мижоз/item/миқдор/валюта/санага мос per-base-unit нархни
 * матн (plain text) сифатида қайтаради, топилмаса бўш сатр - шунда
 * форма каталог нархига (item.salesPrice) қайтади.
 *
 * <p>Нега алоҳида контроллер, /settings остида ЭМАС: PriceListController
 * бутунлай /settings/price-lists остида (INVENTORY соҳаси), invoice'ни эса
 * ACCOUNTANT ҳам киритади. CurrencyController.lookup ({@code
 * /exchange-rates/lookup}) айнан шу сабабдан /settings дан ташқарида -
 * ўша прецедент такрорланади. Фақат ўқийди, GL/posting'га таъсир йўқ.
 *
 * @author Zafar
 */
@Controller
@RequiredArgsConstructor
public class PriceLookupController {

    /** Нарх ечими - ягона public API (resolvePrice). */
    private final PriceListService priceListService;

    /** Сана берилмаса компания зонасидаги бугунги кун учун. */
    private final CompanySettingsService settingsService;

    /**
     * Топилган нархни матн қилиб қайтаради ёки бўш сатр. Prefill
     * endpoint'и ҳеч қачон 400/500 бермайди - бузуқ/бўш параметр
     * оддийгина «мос нарх йўқ» деб талқин қилинади (форма бузилмасин;
     * FormParsers эмас, чунки бу ерда хато ЭМАС, graceful бўш жавоб
     * керак). Нарх base бирлик учун - форма уни танланган бирлик
     * factor'ига кўпайтиради (docs/modules/price-list.md 25-сатр).
     *
     * @param customerId мижоз id (ихтиёрий - йўқ бўлса фақат default рўйхат)
     * @param itemId     item id (мажбурий - бўш/бузуқ бўлса бўш жавоб)
     * @param qty        миқдор item BASE бирлигида; бўш → resolvePrice 1 деб олади
     * @param currency   ҳужжат валютаси коди - рўйхат валютасига мос келсин
     * @param date       ҳужжат санаси; бўш/бузуқ → компания зонасида бугун
     */
    @GetMapping("/price-lists/lookup")
    @ResponseBody
    public String lookup(@RequestParam(required = false) String customerId,
                         @RequestParam(required = false) String itemId,
                         @RequestParam(required = false) String qty,
                         @RequestParam(required = false) String currency,
                         @RequestParam(required = false) String date) {
        UUID item = parseUuid(itemId);
        if (item == null || currency == null || currency.isBlank()) {
            return "";
        }
        return priceListService.resolvePrice(parseUuid(customerId), item,
                        parseQty(qty), currency.strip(), parseDate(date))
                .map(price -> price.stripTrailingZeros().toPlainString())
                .orElse("");
    }

    /** Бузуқ/бўш UUID → null (400 эмас, prefill graceful). */
    private UUID parseUuid(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(text.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Бузуқ/бўш миқдор → null (resolvePrice уни 1 деб олади). */
    private BigDecimal parseQty(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.strip().replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Бузуқ/бўш сана → компания зонасида бугун. */
    private LocalDate parseDate(String text) {
        if (text != null && !text.isBlank()) {
            try {
                return LocalDate.parse(text.strip());
            } catch (DateTimeParseException e) {
                // graceful: бузуқ сана бугунгига қайтади
            }
        }
        return LocalDate.now(settingsService.zoneId());
    }
}
