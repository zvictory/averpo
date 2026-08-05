package com.averpo.erp.purchase.web;

import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.purchase.service.BillService;
import com.averpo.erp.shared.service.CompanySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * AP aging ҳисоботи (QBO A/P Aging Summary услуби): vendor бўйича
 * очиқ қарз home валютада, кечикиш корзиналарида. Ҳисоб-китоб
 * BillService.apAging'да - контроллер юпқа.
 *
 * @author Zafar
 */
@Controller
@RequiredArgsConstructor
public class ApAgingController {

    /** Aging ҳисоб-китоби. */
    private final BillService billService;

    /** Vendor номлари учун. */
    private final ContactService contactService;

    /** Home currency ва компания вақт минтақаси учун. */
    private final CompanySettingsService settingsService;

    /**
     * Ҳисобот экрани - ФАҚАТ жорий ҳолат (BR-RPT-001, Komil-004):
     * сана танлаш олиб ташланган, доим бугун (компания вақт
     * минтақасида). Тарихий as-of реконструкцияси 9-босқичда.
     */
    @GetMapping("/reports/ap-aging")
    public String apAging(Model model) {
        LocalDate today = LocalDate.now(settingsService.zoneId());
        var rows = billService.apAging(today);
        model.addAttribute("rows", rows);
        // Vendor номлари - фақат aging қаторларидаги id'лар byIds/IN
        // сўровда (ARBITR-105б, Ulugbek-003 §1); namesByIds фаолликни
        // филтрламайди - нофаол vendor'нинг эски қарзи ҳам ном билан кўринади
        Set<UUID> vendorIds = new HashSet<>();
        for (var row : rows) {
            vendorIds.add(row.vendorId());
        }
        model.addAttribute("vendorNames", contactService.namesByIds(vendorIds));
        model.addAttribute("asOf", today);
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        return "purchase/apAging";
    }
}
