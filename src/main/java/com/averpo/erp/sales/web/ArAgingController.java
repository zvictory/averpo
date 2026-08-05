package com.averpo.erp.sales.web;

import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.sales.service.InvoiceService;
import com.averpo.erp.shared.service.CompanySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;

/**
 * AR aging ҳисоботи (QBO A/R Aging Summary услуби): мижоз бўйича очиқ
 * дебиторлик home валютада, кечикиш корзиналарида. Ҳисоб-китоб
 * InvoiceService.arAging'да - контроллер юпқа (ApAging кўзгуси).
 */
@Controller
@RequiredArgsConstructor
public class ArAgingController {

    /** Aging ҳисоб-китоби. */
    private final InvoiceService invoiceService;

    /** Мижоз номлари учун. */
    private final ContactService contactService;

    /** Home currency ва компания вақт минтақаси учун. */
    private final CompanySettingsService settingsService;

    /**
     * Ҳисобот экрани - ФАҚАТ жорий ҳолат (BR-RPT-001, Komil-004):
     * сана танлаш олиб ташланган, доим бугун (компания вақт
     * минтақасида). Тарихий as-of реконструкцияси 9-босқичда.
     */
    @GetMapping("/reports/ar-aging")
    public String arAging(Model model) {
        LocalDate today = LocalDate.now(settingsService.zoneId());
        var rows = invoiceService.arAging(today);
        model.addAttribute("rows", rows);
        // Мижоз номлари - фақат aging қаторларидаги id'лар
        // byIds/IN сўровда (ARBITR-105б, Ulugbek-003 §1);
        // нофаоллар ҳам киради - эски қарз кўринсин
        model.addAttribute("customerNames", contactService.namesByIds(
                rows.stream().map(r -> r.customerId())
                        .filter(java.util.Objects::nonNull).distinct().toList()));
        model.addAttribute("asOf", today);
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        return "sales/arAging";
    }
}
