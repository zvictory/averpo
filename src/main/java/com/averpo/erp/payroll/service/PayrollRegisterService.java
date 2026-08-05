package com.averpo.erp.payroll.service;

import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.ledger.service.LedgerDashboardService;
import com.averpo.erp.payroll.repo.PayrollRegisterRepository;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.CompanySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ойлик ведомость (docs/modules/payroll.md 23в) - ходим кесимида давр
 * ҳаракати: давр боши clearing қолдиқ / ҳисобланган (gross, солиқлар,
 * net) / даврда тўланган / давр охири clearing қолдиқ.
 *
 * <p>Манба (spec, Arbitr-047 - ТЎЛИҚ GL асосида): PAYROLL_CLEARING GL
 * контакт кесими - давр боши/охири owed (Cr − Dt) ledger JdbcClient
 * агрегатидан; «даврда тўланган» ҳам GL'дан (давр ичи PAYROLL_PAYMENT
 * манбали Dt − Cr - тўлов reverse'да жуфти нолга тушади, домен status
 * ЭМАС). net/gross POSTED run сатрларидан. Инвариант: давр_охири =
 * давр_боши + net − тўланган (тўлов сторноси остида ҳам сақланади);
 * per-employee closing йиғиндиси = GL clearing счёт қолдиғи (test 7).
 * Ledger'га фақат public service орқали (қоида №6). Суммалар home (UZS).
 *
 * @author Zafar
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PayrollRegisterService {

    /** Ведомостнинг битта ходим қатори (ҳамма сумма home валютада). */
    public record Row(UUID employeeId, String employeeName, BigDecimal openingOwed,
                      BigDecimal gross, BigDecimal incomeTax, BigDecimal pension,
                      BigDecimal net, BigDecimal paid, BigDecimal closingOwed) { }

    /** Пастки жами сатри. */
    public record Totals(BigDecimal openingOwed, BigDecimal gross, BigDecimal incomeTax,
                         BigDecimal pension, BigDecimal net, BigDecimal paid,
                         BigDecimal closingOwed) { }

    /** Тайёр ведомость - экран учун (period нормаллашган «YYYY-MM»). */
    public record Register(String period, List<Row> rows, Totals totals) { }

    /** PAYROLL_CLEARING id ва счёт валидацияси. */
    private final AccountService accountService;

    /** GL clearing контакт кесими owed + давр ҳаракати (paid) - ledger JdbcClient агрегати (қоида №6). */
    private final LedgerDashboardService ledgerDashboardService;

    /** Run/тўлов агрегатлари (ҳисобланган/тўланган) - ведомость ўз ўқишлари. */
    private final PayrollRegisterRepository registerRepository;

    /** Ходим номлари - қатор id'лари бўйича byIds/IN сўровда (нофаоллар ҳам - тарихий давр). */
    private final ContactService contactService;

    /** Default давр (жорий ой) учун компания вақт минтақаси. */
    private final CompanySettingsService settingsService;

    /**
     * Берилган давр (period «YYYY-MM», бўш - жорий ой) ведомостини қуради.
     *
     * @throws BusinessRuleException BR-PYR-004 - period формати нотўғри
     */
    public Register build(String periodParam) {
        YearMonth ym = parsePeriod(periodParam);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        // Clearing GL контакт кесими (JdbcClient агрегат - хотирага entity
        // ЮКЛАНМАЙДИ, Beruniy-028): давр боши owed (start'дан ОЛДИН) ва
        // давр охири owed (end'гача). «Даврда тўланган» ҳам GL'дан - давр
        // ичи PAYROLL_PAYMENT манбали Dt − Cr (тўлов сторноси жуфти нолга
        // тушади - домен status'дан фарқли, инвариант reverse'да ҳам
        // сақланади, Arbitr-047 банд 1).
        UUID clearing = accountService.requireSystemAccountId(AccountDetailType.PAYROLL_CLEARING);
        Map<UUID, BigDecimal> opening = ledgerDashboardService.contactBalances(
                clearing, start.minusDays(1));
        Map<UUID, BigDecimal> closing = ledgerDashboardService.contactBalances(clearing, end);
        Map<UUID, BigDecimal> paid = ledgerDashboardService.contactSourceMovement(
                clearing, start, end, PayrollPaymentService.SOURCE_MODULE);

        Map<UUID, PayrollRegisterRepository.Accrual> accrual = new HashMap<>();
        for (var a : registerRepository.accrualByEmployee(ym.toString())) {
            accrual.put(a.getEmployeeId(), a);
        }

        Set<UUID> ids = new HashSet<>();
        ids.addAll(opening.keySet());
        ids.addAll(closing.keySet());
        ids.addAll(accrual.keySet());
        ids.addAll(paid.keySet());

        // Ходим номлари фақат қатнашган id'лар бўйича - byIds/IN сўровда
        // (ARBITR-105б, Ulugbek-003 §1); нофаоллар ҳам келади - тарихий
        // даврда ном кўриниши шарт, топилмагани «?» билан чиқади.
        Map<UUID, String> names = contactService.namesByIds(ids);
        List<Row> rows = new ArrayList<>(ids.size());
        BigDecimal tOpen = BigDecimal.ZERO, tGross = BigDecimal.ZERO,
                tIncome = BigDecimal.ZERO, tPension = BigDecimal.ZERO,
                tNet = BigDecimal.ZERO, tPaid = BigDecimal.ZERO, tClose = BigDecimal.ZERO;
        for (UUID id : ids) {
            PayrollRegisterRepository.Accrual a = accrual.get(id);
            BigDecimal openOwed = opening.getOrDefault(id, BigDecimal.ZERO);
            BigDecimal gross = a == null ? BigDecimal.ZERO : a.getGross();
            BigDecimal incomeTax = a == null ? BigDecimal.ZERO : a.getIncomeTax();
            BigDecimal pension = a == null ? BigDecimal.ZERO : a.getPension();
            BigDecimal net = a == null ? BigDecimal.ZERO : a.getNet();
            BigDecimal paidAmt = paid.getOrDefault(id, BigDecimal.ZERO);
            BigDecimal closeOwed = closing.getOrDefault(id, BigDecimal.ZERO);
            rows.add(new Row(id, names.getOrDefault(id, "?"),
                    openOwed, gross, incomeTax, pension, net, paidAmt, closeOwed));
            tOpen = tOpen.add(openOwed);
            tGross = tGross.add(gross);
            tIncome = tIncome.add(incomeTax);
            tPension = tPension.add(pension);
            tNet = tNet.add(net);
            tPaid = tPaid.add(paidAmt);
            tClose = tClose.add(closeOwed);
        }
        rows.sort(Comparator.comparing(Row::employeeName, String.CASE_INSENSITIVE_ORDER));
        return new Register(ym.toString(), List.copyOf(rows),
                new Totals(tOpen, tGross, tIncome, tPension, tNet, tPaid, tClose));
    }

    /** Период «YYYY-MM» парси - бўш бўлса жорий ой (компания минтақаси). */
    private YearMonth parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            return YearMonth.from(LocalDate.now(settingsService.zoneId()));
        }
        try {
            return YearMonth.parse(period.strip());
        } catch (DateTimeParseException e) {
            throw new BusinessRuleException(BusinessRule.BR_PYR_004,
                    "Ведомость даври YYYY-MM форматида бўлиши шарт: " + period);
        }
    }

}
