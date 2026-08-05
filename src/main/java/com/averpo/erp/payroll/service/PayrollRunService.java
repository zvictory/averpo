package com.averpo.erp.payroll.service;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.domain.JournalEntry;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.ledger.service.JournalEntryRequest;
import com.averpo.erp.ledger.service.PostingService;
import com.averpo.erp.payroll.domain.PayrollRun;
import com.averpo.erp.payroll.domain.PayrollRunLine;
import com.averpo.erp.payroll.repo.PayrollRunRepository;
import com.averpo.erp.shared.Strings;
import com.averpo.erp.shared.domain.CompanySettings;
import com.averpo.erp.shared.domain.DocumentType;
import com.averpo.erp.shared.domain.Money;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.exception.NotFoundException;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.service.DocumentSequenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Ойлик иш ҳақи ҳисоблашининг ягона public API'си (payroll.md).
 * DRAFT сақланади/таҳрирланади, {@link #post} GL'га ёзади
 * (posting-rules «Иш ҳақи» - ЭТАЛОН), {@link #reverse} сторно.
 *
 * <p>Ҳамма суммалар home валютада (BR-PYR-001). Сатрда ҳисобланган
 * СУММА snapshot'лари сақланади - кейин ставка ўзгарса тарихий POSTED
 * run ўзгармайди. GL фақат PostingService (қоида №2); счётлар detail
 * type орқали (инвариант 6) - PAYROLL_EXPENSES'да атайлаб ИККИТА счёт
 * бор (043 seed), шунга улар detail type + НОМ бўйича ажратилади.
 *
 * @author Zafar
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PayrollRunService {

    /** GL ҳаволаларидаги манба модул белгиси (posting-rules). */
    public static final String SOURCE_MODULE = "PAYROLL_RUN";

    /** Иш ҳақи (gross) харажат счётининг seed номи (043/CSV). */
    public static final String SALARY_EXPENSE_NAME = "Иш ҳақи харажати";

    /** Иш берувчи солиқ харажати счётининг seed номи (043/CSV). */
    public static final String TAX_EXPENSE_NAME = "Иш ҳақи солиқ харажати";

    /** Рўйхат саҳифаси ҳажми (Beruniy-perf1 қолипи - рўйхат саҳифаланган туғилади). */
    public static final int LIST_PAGE_SIZE = 25;

    /** BR-PYR-004: period қатъий YYYY-MM (ой 01..12). */
    private static final Pattern PERIOD_PATTERN =
            Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    /** Рўйхат тартиби: period DESC, тенгда яратилиш вақти DESC. */
    private static final Sort LIST_SORT = Sort.by(
            Sort.Order.desc("period"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    /** Ҳисоблаш формаси маълумотлари (home валютада - BR-PYR-001). */
    public record RunData(String period, LocalDate runDate, String memo,
                          List<LineData> lines) { }

    /**
     * Битта сатр: ходим + gross + ихтиёрий Йўналиш. Ушланма/солиқ
     * суммалари формадан КЕЛМАЙДИ - service жорий ставкалардан
     * ҳисоблаб snapshot қилади (форма фақат жонли кўрсатади).
     */
    public record LineData(UUID employeeId, BigDecimal gross, UUID classId,
                           String memo) { }

    /**
     * Рўйхат экрани учун битта run кесимидаги жами (home валютада, Arbitr-054):
     * gross ва net сатрлар йиғиндиси. Сатрсиз/агрегатда йўқ run учун
     * {@link #ZERO}.
     */
    public record RunTotals(BigDecimal gross, BigDecimal net) {
        /** Сатрсиз ёки харитада йўқ run учун default (нол/нол). */
        public static final RunTotals ZERO = new RunTotals(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /** Ҳисоблашлар репозиторийси. */
    private final PayrollRunRepository repository;

    /** Ҳужжат рақамлари (PAYR-2026-NNNNN). */
    private final DocumentSequenceService sequenceService;

    /** Ходим текшируви ва prefill - contact модулининг public API'си. */
    private final ContactService contactService;

    /** Payroll счётларини detail type/ном бўйича топиш. */
    private final AccountService accountService;

    /** GL'га ёзишнинг ягона йўли. */
    private final PostingService postingService;

    /** Ставкалар ва home currency манбаси. */
    private final CompanySettingsService settingsService;

    /** Id бўйича топади ёки тушунарли хато отади. */
    @Transactional(readOnly = true)
    public PayrollRun get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Ҳисоблаш топилмади: " + id));
    }

    /** Кўриш/post учун - сатрлари билан. */
    @Transactional(readOnly = true)
    public PayrollRun getWithLines(UUID id) {
        return repository.findWithLinesById(id)
                .orElseThrow(() -> new NotFoundException("Ҳисоблаш топилмади: " + id));
    }

    /**
     * Рўйхат филтри (Arbitr-068, list-filters.md): барча майдонлар
     * ихтиёрий (null - чекланмаган); давр run санаси бўйича; q -
     * рақам/изоҳ contains (катта-кичик фарқсиз, кирилл ҳам).
     */
    public record ListFilter(java.time.LocalDate from, java.time.LocalDate to,
                             PayrollRun.Status status, String q) {
    }

    /**
     * Рўйхат экрани - саҳифаланган (period DESC), тўлиқ филтр
     * (Arbitr-068): давр/статус/матн битта Specification'да (audit
     * услуби, ListSpecs бўлаклари).
     */
    @Transactional(readOnly = true)
    public Page<PayrollRun> list(ListFilter filter, int page, int size) {
        return repository.findAll(org.springframework.data.jpa.domain.Specification.allOf(
                        com.averpo.erp.shared.repo.ListSpecs.dateFrom("runDate", filter.from()),
                        com.averpo.erp.shared.repo.ListSpecs.dateTo("runDate", filter.to()),
                        com.averpo.erp.shared.repo.ListSpecs.eq("status", filter.status()),
                        com.averpo.erp.shared.repo.ListSpecs.textContains(filter.q(),
                                "runNumber", "memo")),
                PageRequest.of(Math.max(0, page), size, LIST_SORT));
    }

    /** Default ҳажм ({@link #LIST_PAGE_SIZE}) билан - эски чақирувчилар/тестлар (ARBITR-105). */
    @Transactional(readOnly = true)
    public Page<PayrollRun> list(ListFilter filter, int page) {
        return list(filter, page, LIST_PAGE_SIZE);
    }

    /**
     * Рўйхат экрани учун run кесими жами суммалари (Arbitr-054):
     * {@code runId → (gross, net)}. JPQL агрегат орқали - lazy {@code lines}
     * коллекцияси айланмайди (open-in-view=false да render'да
     * LazyInitializationException бўларди). Сатрсиз/топилмаган run харитада
     * БЎЛМАЙДИ; чақирувчи {@link RunTotals#ZERO} билан ўқийди.
     */
    @Transactional(readOnly = true)
    public Map<UUID, RunTotals> totalsByRun(Collection<UUID> runIds) {
        Map<UUID, RunTotals> totals = new HashMap<>();
        if (runIds.isEmpty()) {
            return totals;
        }
        for (var row : repository.totalsByRun(runIds)) {
            totals.put(row.getRunId(), new RunTotals(row.getGross(), row.getNet()));
        }
        return totals;
    }

    /**
     * Форма prefill сатрлари: фаол EMPLOYEE'лар oklad билан (payroll.md).
     * Oklad киритилмаган ходим ҳам рўйхатга киради (gross null - форма
     * бўш кўрсатади, фойдаланувчи тўлдиради ёки сатрни ўчиради).
     */
    @Transactional(readOnly = true)
    public List<LineData> prefillLines() {
        List<LineData> lines = new ArrayList<>();
        for (Contact employee : contactService.byType(ContactType.EMPLOYEE, false)) {
            lines.add(new LineData(employee.getId(), employee.getMonthlySalary(),
                    null, null));
        }
        return lines;
    }

    /**
     * DRAFT сақлаш - янги яратади ёки мавжуд DRAFT'ни тўлиқ қайта
     * теради (bill қолипи). Ҳар сатрга ушланма/солиқ суммалари ЖОРИЙ
     * ставкалардан ҳисобланиб snapshot қилинади (HALF_UP 2 хона):
     * income_tax = gross × ставка; pension = gross × ставка;
     * social_tax = gross × ставка; net = gross − income_tax − pension.
     *
     * @throws BusinessRuleException BR-PYR-003/004
     */
    public PayrollRun saveDraft(UUID id, RunData data) {
        validateHeader(data);
        PayrollRun run;
        if (id == null) {
            run = new PayrollRun(
                    sequenceService.next(DocumentType.PAYROLL_RUN, data.runDate()),
                    data.period(), data.runDate(), Strings.blankToNull(data.memo()));
        } else {
            run = getWithLines(id);
            run.updateHeader(data.period(), data.runDate(),
                    Strings.blankToNull(data.memo()));
            run.clearLines();
            // uq_payroll_run_line_* билан: Hibernate flush'да INSERT
            // DELETE'дан олдин бажарилади - эски сатрлар аввал ўчирилсин
            repository.flush();
        }
        applyLines(run, data.lines());
        return repository.saveAndFlush(run);
    }

    /**
     * Post: BR-PYR-005 (фақат DRAFT), BR-PYR-002 (бир ойга битта POSTED) + сатр қайта валидацияси
     * (ходим ҳали фаол EMPLOYEE'ми - bill post қолипи), кейин JE
     * (posting-rules «Иш ҳақи»). Davр қулфи (BR-LED-020, run_date
     * бўйича) PostingService'дан автоматик.
     */
    public PayrollRun post(UUID id) {
        PayrollRun run = getWithLines(id);
        if (run.getStatus() != PayrollRun.Status.DRAFT) {
            throw new BusinessRuleException(BusinessRule.BR_PYR_005,
                    "Фақат DRAFT ҳисоблаш post қилинади: " + run.getRunNumber()
                    + " ҳозир " + run.getStatus());
        }
        if (repository.existsByPeriodAndStatus(run.getPeriod(), PayrollRun.Status.POSTED)) {
            throw new BusinessRuleException(BusinessRule.BR_PYR_002,
                    run.getPeriod() + " ойига POSTED ҳисоблаш аллақачон бор");
        }
        // Draft сақлангандан кейин ходим ҳолати ўзгарган бўлиши мумкин -
        // ходимларни битта batch'да юклаб текширамиз (N+1'дан қочиш)
        Map<UUID, Contact> employees = employeesByIds(run.getLines().stream()
                .map(PayrollRunLine::getEmployeeId).toList());
        for (PayrollRunLine line : run.getLines()) {
            requireActiveEmployee(employees, line.getEmployeeId());
        }
        JournalEntry entry = postingService.createAndPost(new JournalEntryRequest(
                run.getRunDate(),
                "Иш ҳақи " + run.getPeriod() + " (" + run.getRunNumber() + ")",
                SOURCE_MODULE, run.getId(), buildGlLines(run)));
        run.markPosted(entry.getId());
        flushGuarded(run);
        return run;
    }

    /**
     * Reverse: стандарт GL сторноси (фақат POSTED - domain guard).
     * Сторно санаси period ойи ичида бўлиши шарт (BR-PYR-004 кенгайтмаси,
     * Arbitr-071/Asrorxoja-016): кейинги ойдаги сторно ведомость
     * accrual'ини (status-асосли) ретроактив йўқотиб, инвариантни
     * (давр_охири = давр_боши + net − тўланган) бузарди - GL эса тўғри
     * қоларди. run_date чекловининг айнан кўзгуси.
     */
    public PayrollRun reverse(UUID id, LocalDate reversalDate, String reason) {
        PayrollRun run = get(id);
        if (run.getStatus() != PayrollRun.Status.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_PYR_006,
                    "Фақат POSTED ҳисоблаш reverse қилинади: " + run.getRunNumber()
                    + " ҳозир " + run.getStatus());
        }
        // null сана PostingService'нинг BR-LED-008 гаровига қолдирилади
        if (reversalDate != null
                && !YearMonth.from(reversalDate).equals(YearMonth.parse(run.getPeriod()))) {
            throw new BusinessRuleException(BusinessRule.BR_PYR_004,
                    "Сторно санаси (" + reversalDate + ") period ойи ("
                    + run.getPeriod() + ") ичида бўлиши шарт");
        }
        postingService.reverseBySource(SOURCE_MODULE, run.getId(), reversalDate,
                reason == null || reason.isBlank() ? "Иш ҳақи ҳисоблаши reverse" : reason);
        run.markReversed();
        return run;
    }

    // ---- ички ёрдамчилар ----

    /** Сарлавҳа валидацияси (BR-PYR-004). */
    private void validateHeader(RunData data) {
        if (data.period() == null || !PERIOD_PATTERN.matcher(data.period()).matches()) {
            throw new BusinessRuleException(BusinessRule.BR_PYR_004,
                    "Period YYYY-MM форматида бўлиши шарт: " + data.period());
        }
        if (data.runDate() == null) {
            throw new BusinessRuleException(BusinessRule.BR_PYR_004,
                    "Run санаси киритилиши шарт");
        }
        // Run санаси ЎША period ойи ИЧИДА бўлиши шарт (икки томонлама -
        // Arbitr-047). Кейинги ой: ёпилган ойни кечроқ (очиқ) сана билан
        // киритиб бўларди (closing BR-LED-020 run_date бўйича). Олдинги ой:
        // ведомостда net икки марта кўринарди (net - period матни,
        // opening/closing - run_date=entry_date бўйича).
        if (!YearMonth.from(data.runDate()).equals(YearMonth.parse(data.period()))) {
            throw new BusinessRuleException(BusinessRule.BR_PYR_004,
                    "Run санаси (" + data.runDate() + ") period ойи ("
                    + data.period() + ") ичида бўлиши шарт");
        }
    }

    /** Сатрларни валидация қилиб (BR-PYR-003), snapshot суммалар билан теради. */
    private void applyLines(PayrollRun run, List<LineData> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessRuleException(BusinessRule.BR_PYR_003,
                    "Камида битта сатр киритилиши шарт");
        }
        CompanySettings settings = settingsService.get();
        // Ходимларни битта batch'да юклаб хотирада текширамиз - аввал ҳар
        // сатрга алоҳида get() эди (N сатр = N round-trip, N+1'дан қочиш)
        Map<UUID, Contact> employees = employeesByIds(lines.stream()
                .map(LineData::employeeId).filter(id -> id != null).toList());
        Set<UUID> seen = new HashSet<>();
        int no = 0;
        for (LineData line : lines) {
            no++;
            if (line.employeeId() == null) {
                throw new BusinessRuleException(BusinessRule.BR_PYR_003,
                        no + "-сатр: ходим танланиши шарт");
            }
            requireActiveEmployee(employees, line.employeeId());
            if (!seen.add(line.employeeId())) {
                throw new BusinessRuleException(BusinessRule.BR_PYR_003,
                        no + "-сатр: ходим ҳужжатда такрорланмайди");
            }
            if (line.gross() == null || line.gross().signum() <= 0) {
                throw new BusinessRuleException(BusinessRule.BR_PYR_003,
                        no + "-сатр: gross мусбат бўлиши шарт");
            }
            // Snapshot ҳисоб (HALF_UP 2 хона) - ставка кейин ўзгарса
            // тарихий ҳужжат ўзгармайди
            BigDecimal incomeTax = percentOf(line.gross(), settings.getIncomeTaxRate());
            BigDecimal pension = percentOf(line.gross(), settings.getPensionRate());
            BigDecimal socialTax = percentOf(line.gross(), settings.getSocialTaxRate());
            BigDecimal net = line.gross().subtract(incomeTax).subtract(pension);
            // BR-PYR-003 кенгайтмаси (Arbitr-071/Asrorxoja-014): ушланмалар
            // gross'дан ошмасин - ставкалар йиғиндиси 100% дан ошган
            // созламада манфий net post'да хом BR-LED-006 бўлиб отиларди,
            // энди сабаб аниқ айтилади
            if (net.signum() < 0) {
                throw new BusinessRuleException(BusinessRule.BR_PYR_003,
                        no + "-сатр: ушланмалар (" + incomeTax.add(pension)
                        + ") gross (" + line.gross() + ") дан ошмайди - "
                        + "даромад солиғи + пенсия ставкалари йиғиндисини текширинг");
            }
            run.addLine(line.employeeId(), line.gross(), incomeTax, pension,
                    socialTax, net, line.classId(), Strings.blankToNull(line.memo()));
        }
    }

    /** Ходимларни битта IN сўровда Map'га юклайди (сатр-цикл N+1'дан қочиш). */
    private Map<UUID, Contact> employeesByIds(Collection<UUID> employeeIds) {
        Map<UUID, Contact> byId = new HashMap<>();
        for (Contact c : contactService.findAllById(employeeIds)) {
            byId.put(c.getId(), c);
        }
        return byId;
    }

    /**
     * BR-PYR-003: контакт фаол EMPLOYEE бўлиши шарт - олдиндан юкланган
     * batch Map'дан текширилади (сатр-циклда get() йўқ). Map'да топилмаган
     * (null) ҳам рад: ходим мавжуд ва фаол EMPLOYEE бўлиши керак.
     */
    private void requireActiveEmployee(Map<UUID, Contact> employees, UUID employeeId) {
        Contact employee = employees.get(employeeId);
        if (employee == null || employee.getType() != ContactType.EMPLOYEE
                || !employee.isActive()) {
            throw new BusinessRuleException(BusinessRule.BR_PYR_003,
                    "Ходим фаол EMPLOYEE типдаги контакт бўлиши шарт"
                    + (employee != null ? ": " + employee.getDisplayName() : ""));
        }
    }

    /** gross × ставка / 100 (HALF_UP 2 хона - payroll.md формуласи). */
    private BigDecimal percentOf(BigDecimal gross, BigDecimal ratePercent) {
        return gross.multiply(ratePercent)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    /**
     * GL сатрлари posting-rules «Иш ҳақи» жадвалига қатъий мос:
     * Dr иш ҳақи харажати gross (ҳар ходим сатри - contact + class) +
     * Dr солиқ харажати Σижтимоий (жамланган, contact'сиз/class'сиз) /
     * Cr PAYROLL_TAX_PAYABLE Σ(даромад+пенсия+ижтимоий) (жамланган,
     * contact'сиз/class'сиз) + Cr PAYROLL_CLEARING net (ҳар ходим
     * кесимида, class'сиз - назорат сатри). Нол сумма леглар ёзилмайди
     * (BR-LED-002 XOR). Баланс: gross + ижтимоий == солиқлар + net.
     */
    private List<JournalEntryRequest.Line> buildGlLines(PayrollRun run) {
        String home = settingsService.homeCurrency();
        // Каталог битта марта сканланади - иккита харажат счёти (иш ҳақи ва
        // солиқ) айнан шу рўйхатдан топилади (аввал ҳар бирига алоҳида all())
        List<Account> accounts = accountService.all();
        UUID salaryExpense = payrollExpenseAccount(accounts, SALARY_EXPENSE_NAME).getId();
        UUID taxExpense = payrollExpenseAccount(accounts, TAX_EXPENSE_NAME).getId();
        UUID taxPayable = accountService.requireSystemAccountId(
                AccountDetailType.PAYROLL_TAX_PAYABLE);
        UUID clearing = accountService.requireSystemAccountId(
                AccountDetailType.PAYROLL_CLEARING);

        List<JournalEntryRequest.Line> glLines = new ArrayList<>();
        BigDecimal totalSocial = BigDecimal.ZERO;
        BigDecimal totalTaxes = BigDecimal.ZERO;
        for (PayrollRunLine line : run.getLines()) {
            // Харажат леги - ходим (contact) + Йўналиш (class) кесимида
            glLines.add(new JournalEntryRequest.Line(salaryExpense,
                    Money.ofBase(line.getGross(), home), null,
                    line.getEmployeeId(), null, null, line.getMemo(),
                    line.getClassId()));
            totalSocial = totalSocial.add(line.getSocialTax());
            totalTaxes = totalTaxes.add(line.getIncomeTax())
                    .add(line.getPension()).add(line.getSocialTax());
        }
        if (totalSocial.signum() > 0) {
            // Иш берувчи солиқ харажати - бюджетга жами (contact'сиз/class'сиз)
            glLines.add(new JournalEntryRequest.Line(taxExpense,
                    Money.ofBase(totalSocial, home), null, null, null, null, null));
        }
        if (totalTaxes.signum() > 0) {
            glLines.add(new JournalEntryRequest.Line(taxPayable,
                    null, Money.ofBase(totalTaxes, home), null, null, null, null));
        }
        for (PayrollRunLine line : run.getLines()) {
            if (line.getNet().signum() > 0) {
                // Ходим кесимидаги мажбурият - субледжер назорати, class'сиз
                glLines.add(new JournalEntryRequest.Line(clearing,
                        null, Money.ofBase(line.getNet(), home),
                        line.getEmployeeId(), null, null, null));
            }
        }
        return glLines;
    }

    /**
     * PAYROLL_EXPENSES счётини НОМ бўйича топади: бу detail type'да
     * атайлаб иккита счёт бор (иш ҳақи / солиқ харажати - 043 seed),
     * шунга requireSystemAccount (ягона кутади) ишламайди. Топилмаса
     * инвариант 6 услубидаги аниқ хато - фойдаланувчи счётни тиклайди.
     *
     * @throws BusinessRuleException BR-LED-021 - ном бўйича фаол
     *         postable PAYROLL_EXPENSES счёти йўқ
     */
    private Account payrollExpenseAccount(List<Account> accounts, String name) {
        return accounts.stream()
                .filter(a -> a.getDetailType() == AccountDetailType.PAYROLL_EXPENSES
                        && a.isActive() && a.isPostable() && name.equals(a.getName()))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(BusinessRule.BR_LED_021,
                        "PAYROLL_EXPENSES счёти топилмади: «" + name
                        + "» (фаол, postable) - default chart'ни текширинг"));
    }

    /**
     * saveAndFlush + ux_payroll_run_period_posted'ни аниқ BR кодга ўраш:
     * parallel икки post'да service текшируви ўтиб кетиши мумкин,
     * ҳақиқий кафолат DB partial unique (saveGuarded қолипи).
     */
    private void flushGuarded(PayrollRun run) {
        try {
            repository.saveAndFlush(run);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (t.getMessage() != null
                        && t.getMessage().contains("ux_payroll_run_period_posted")) {
                    throw new BusinessRuleException(BusinessRule.BR_PYR_002,
                            run.getPeriod() + " ойига POSTED ҳисоблаш аллақачон бор");
                }
            }
            throw e;
        }
    }
}
