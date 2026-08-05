package com.averpo.erp.contact.web;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.ledger.service.LedgerDashboardService;
import com.averpo.erp.ledger.service.StatementService;
import com.averpo.erp.purchase.service.BillService;
import com.averpo.erp.sales.service.InvoiceService;
import com.averpo.erp.shared.exception.NotFoundException;
import com.averpo.erp.shared.service.CompanySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Контакт карточкаси (мижоз/таъминотчи КЎРИШ саҳифаси, Arbitr-002,
 * spec: docs/modules/contact-card.md) - QBO Customer/Vendor саҳифаси
 * паритети: сарлавҳа + 4 стат-карта + шу контактнинг кўчирмаси
 * (running balance, давр филтри).
 *
 * <p>КОМПОЗИЦИЯ ШУ ҚАТЛАМДА (spec қарори): ҳар модулдан фақат PUBLIC
 * service чақирилади - {@link StatementService} (кўчирма + жорий қолдиқ),
 * {@link InvoiceService}/{@link BillService} (aging → муддати ўтган),
 * {@link LedgerDashboardService} (жами сотув/харид + охирги тўлов),
 * {@link ContactService} (контакт). Ledger ҳеч кимга боғланмайди -
 * фақат тайёр маълумот беради (қоида №6). GL'га тегилмайди (read-only).
 *
 * <p>EMPLOYEE бу саҳифага КИРМАЙДИ (payroll кесими бошқа - ходим картаси
 * 2-босқич): route regex {@code customers|vendors} билан чекланган,
 * ходим қатори рўйхатда аввалгидек таҳрир формасига боради.
 *
 * @author Zafar
 */
@Controller
@RequestMapping("/{kind:customers|vendors}")
@RequiredArgsConstructor
public class ContactCardController {

    /** Контакт (ном, тип, компания) учун. */
    private final ContactService contactService;

    /** Кўчирма (AR/AP running balance) + жорий қолдиқ (closing). */
    private final StatementService statementService;

    /** AR aging - мижоз «муддати ўтган» картаси учун. */
    private final InvoiceService invoiceService;

    /** AP aging - таъминотчи «муддати ўтган» картаси учун. */
    private final BillService billService;

    /** Жами сотув/харид (классификация агрегати) + охирги тўлов. */
    private final LedgerDashboardService ledgerDashboardService;

    /** Home валюта, timezone ва компания номи (print сарлавҳа) учун. */
    private final CompanySettingsService settingsService;

    /**
     * Карточка view-model'и - 4 стат-карта + кўчирма битта объектда
     * (controller юпқа қолсин, тест model attribute орқали стат
     * қийматларни аниқ текширсин).
     *
     * @param id             контакт id'си
     * @param kind           {@code "customers"} ёки {@code "vendors"} (URL)
     * @param customer       мижозми (AR); {@code false} - таъминотчи (AP)
     * @param displayName    кўрсатиладиган ном (сарлавҳа)
     * @param contactCompany контакт компания номи ёки null (сарлавҳа ости)
     * @param balance        жорий қолдиқ, НОРМАЛ ишора мусбат (мижоз AR
     *                       қарзи / таъминотчи AP қарзимиз); манфий - аванс
     * @param advance        {@code balance < 0} - аванс (яшил белги)
     * @param overdue        муддати ўтган жами (aging: total - current)
     * @param total          жами сотув (INVOICE gross) / жами харид (BILL gross)
     * @param lastPayment    охирги тўлов санаси ёки null
     * @param statement      танланган давр кўчирмаси (opening/rows/closing)
     * @param from           давр боши (филтр)
     * @param to             давр охири (филтр)
     */
    public record ContactCard(UUID id, String kind, boolean customer,
                              String displayName, String contactCompany,
                              BigDecimal balance, boolean advance,
                              BigDecimal overdue, BigDecimal total,
                              LocalDate lastPayment,
                              StatementService.Statement statement,
                              LocalDate from, LocalDate to) {
    }

    /**
     * Контакт карточкаси: {@code GET /customers/{id}} ёки
     * {@code GET /vendors/{id}}. Тип URL сегментига мос бўлиши шарт
     * (масалан {@code /customers/{vendorId}} - 404), акс ҳолда нотўғри
     * (AR/AP) мантиқ ишлар эди. Read-only - VIEWER ҳам кўради.
     *
     * @param from ихтиёрий давр боши (default: бугун - 12 ой)
     * @param to   ихтиёрий давр охири (default: бугун, компания зонаси)
     * @throws NotFoundException контакт йўқ ёки типи URL'га мос эмас
     */
    @GetMapping("/{id}")
    public String view(@PathVariable String kind, @PathVariable UUID id,
                       @RequestParam(required = false) LocalDate from,
                       @RequestParam(required = false) LocalDate to,
                       Model model) {
        boolean customer = "customers".equals(kind);
        ContactType wanted = customer ? ContactType.CUSTOMER : ContactType.VENDOR;
        Contact contact = contactService.get(id); // йўқ бўлса NotFound → 404
        if (contact.getType() != wanted) {
            throw new NotFoundException("Контакт бу рўйхатда эмас: " + id);
        }

        // «Бугун» компания timezone'ида (қоида №12); default давр - охирги
        // 12 ой (spec). Тескари/бузилган URL - default даврга қайтамиз.
        LocalDate today = LocalDate.now(settingsService.zoneId());
        LocalDate t = to != null ? to : today;
        LocalDate f = from != null ? from : today.minusMonths(12);
        if (f.isAfter(t)) {
            f = today.minusMonths(12);
            t = today;
        }

        // Кўчирма (танланган давр) - жадвал учун; жорий қолдиқ эса ДОИМ
        // бугунги сана closing'и (to ўтмишда бўлса алоҳида бугунги прогон -
        // closing ҳар f да бугунги қолдиқни беради, opening эскисини ютади)
        StatementService.Statement periodStmt = statement(customer, id, f, t);
        BigDecimal balance = t.isEqual(today)
                ? periodStmt.closing()
                : statement(customer, id, f, today).closing();
        boolean advance = balance.signum() < 0;

        BigDecimal overdue = overdueFor(customer, id, today);
        // Жами сотув/харид = контрол счёт кесимида INVOICE/BILL gross жами
        // (инвентар харид ҳам киради - LedgerDashboardService изоҳи)
        BigDecimal total = customer
                ? ledgerDashboardService.contactBilledTotal(id, "ACCOUNTS_RECEIVABLE", "INVOICE")
                : ledgerDashboardService.contactBilledTotal(id, "ACCOUNTS_PAYABLE", "BILL");
        LocalDate lastPayment = ledgerDashboardService.lastPaymentDate(
                id, customer ? "INVOICE_PAYMENT" : "BILL_PAYMENT");

        model.addAttribute("card", new ContactCard(id, kind, customer,
                contact.getDisplayName(), contact.getCompanyName(),
                balance, advance, overdue, total, lastPayment, periodStmt, f, t));
        model.addAttribute("homeCurrency", settingsService.homeCurrency());
        model.addAttribute("companyName", settingsService.get().getName());
        return "contact/card";
    }

    /** Тип бўйича тўғри кўчирма (мижоз AR / таъминотчи AP) - service реюз. */
    private StatementService.Statement statement(boolean customer, UUID id,
                                                 LocalDate from, LocalDate to) {
        return customer
                ? statementService.statement(id, from, to)
                : statementService.vendorStatement(id, from, to);
    }

    /**
     * Контактнинг муддати ўтган жами = aging {@code total - current}
     * (мижозда AR aging, таъминотчида AP aging). Aging рўйхатида йўқ
     * (очиқ қолдиқсиз) контакт - нол. Aging ФАҚАТ бугунги санага
     * (BR-RPT-001) - шунинг учун {@code today} узатилади.
     */
    private BigDecimal overdueFor(boolean customer, UUID id, LocalDate today) {
        if (customer) {
            for (InvoiceService.AgingRow row : invoiceService.arAging(today)) {
                if (row.customerId().equals(id)) {
                    return row.total().subtract(row.current());
                }
            }
        } else {
            for (BillService.AgingRow row : billService.apAging(today)) {
                if (row.vendorId().equals(id)) {
                    return row.total().subtract(row.current());
                }
            }
        }
        return BigDecimal.ZERO;
    }
}
