package com.averpo.erp.payroll;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.ledger.domain.EntryStatus;
import com.averpo.erp.ledger.domain.JournalEntry;
import com.averpo.erp.ledger.domain.JournalEntryLine;
import com.averpo.erp.ledger.repo.JournalEntryRepository;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.payroll.domain.PayrollRun;
import com.averpo.erp.payroll.domain.PayrollRunLine;
import com.averpo.erp.payroll.service.PayrollRunService;
import com.averpo.erp.payroll.service.PayrollRunService.LineData;
import com.averpo.erp.payroll.service.PayrollRunService.RunData;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.service.TxnClassService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PayrollRun тестлари (docs/modules/payroll.md «Тестлар» 1/2/3
 * бандлари; 8-банд PayrollRunWebTest'да). Ҳар posting'да
 * debit == credit (ТЕМИР ҚОИДА №7).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PayrollRunServiceTest {

    /** Тест даври ва проводка санаси (ой охири). */
    private static final String PERIOD = "2026-07";
    private static final LocalDate RUN_DATE = LocalDate.of(2026, 7, 31);

    @Autowired PayrollRunService payrollRunService;
    @Autowired ContactService contactService;
    @Autowired AccountService accountService;
    @Autowired CompanySettingsService settingsService;
    @Autowired TxnClassService txnClassService;
    @Autowired JournalEntryRepository entryRepository;

    private Contact employeeA;
    private Contact employeeB;

    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        // Ставкалар аниқ белгилаб олинади - default ўзгарса тест синмасин
        settingsService.updatePayrollRates(new BigDecimal("12"),
                new BigDecimal("0.1"), new BigDecimal("12"));
        employeeA = employee("Ходим Алишер");
        employeeB = employee("Ходим Барно");
    }

    /** Фаол EMPLOYEE контакт ясайди. */
    private Contact employee(String name) {
        return contactService.create(ContactType.EMPLOYEE, new ContactData(
                name, null, null, null, null, null, null, null, null, null, null));
    }

    /** Манба бўйича фаол GL ёзуви. */
    private JournalEntry glEntry(UUID runId) {
        return entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                PayrollRunService.SOURCE_MODULE, runId).orElseThrow();
    }

    /** ТЕМИР ҚОИДА №7: home'да дебет == кредит. */
    private void assertBalanced(JournalEntry entry) {
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (JournalEntryLine line : entry.getLines()) {
            if (line.getDebit() != null) debit = debit.add(line.getDebit().getBaseAmount());
            if (line.getCredit() != null) credit = credit.add(line.getCredit().getBaseAmount());
        }
        assertThat(debit).isEqualByComparingTo(credit);
    }

    /** Счёт НОМИ бўйича дебет/кредит base йиғиндиси (payroll'да ном ҳал қилувчи). */
    private BigDecimal baseOfName(JournalEntry entry, String accountName, boolean debit) {
        BigDecimal sum = BigDecimal.ZERO;
        for (JournalEntryLine line : entry.getLines()) {
            var money = debit ? line.getDebit() : line.getCredit();
            if (money != null && line.getAccount().getName().equals(accountName)) {
                sum = sum.add(money.getBaseAmount());
            }
        }
        return sum;
    }

    /** Иккита ходимли DRAFT run (A gross 3 000 000 + class, B gross 2 000 000). */
    private PayrollRun draftTwoEmployees(UUID classId) {
        return payrollRunService.saveDraft(null, new RunData(PERIOD, RUN_DATE, null,
                List.of(new LineData(employeeA.getId(), new BigDecimal("3000000"),
                                classId, null),
                        new LineData(employeeB.getId(), new BigDecimal("2000000"),
                                null, null))));
    }

    /**
     * Arbitr-071 (Asrorxoja-014): ушланмалар ставкаси йиғиндиси (99% + 2%)
     * gross'дан ошса saveDraft АНИҚ BR-PYR-003 билан рад - аввал манфий
     * net post'гача етиб, хом BR-LED-006 (ledger баланс хатоси) бўлиб
     * отиларди, сабаб фойдаланувчига тушунарсиз эди.
     */
    @Test
    void saveDraft_deductionsExceedGross_rejectedPyr003NotLed006() {
        settingsService.updatePayrollRates(new BigDecimal("99"),
                new BigDecimal("2"), new BigDecimal("12"));
        assertThatThrownBy(() -> payrollRunService.saveDraft(null, new RunData(PERIOD,
                RUN_DATE, null, List.of(new LineData(employeeA.getId(),
                        new BigDecimal("1000000"), null, null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PYR-003"));
    }

    /**
     * Arbitr-071 (Asrorxoja-016): POSTED run'ни period ойидан ТАШҚАРИ сана
     * билан reverse қилиш рад (BR-PYR-004 кенгайтмаси) - кросс-ой сторно
     * ведомость инвариантини (давр_охири = давр_боши + net − тўланган)
     * ретроактив бузарди. Period ичидаги сана билан ўтади.
     */
    @Test
    void reverse_crossMonthDate_rejected_withinPeriodPasses() {
        PayrollRun run = payrollRunService.post(draftTwoEmployees(null).getId());

        assertThatThrownBy(() -> payrollRunService.reverse(run.getId(),
                LocalDate.of(2026, 8, 5), "кросс-ой сторно"))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PYR-004"));

        // Рад run'ни ўзгартирмаган - period ичидаги сана билан ўтади
        PayrollRun reversed = payrollRunService.reverse(run.getId(), RUN_DATE, "тест");
        assertThat(reversed.getStatus()).isEqualTo(PayrollRun.Status.REVERSED);
        assertThat(glEntry(run.getId()).getStatus()).isEqualTo(EntryStatus.REVERSED);
    }

    /**
     * Spec 1-банд: debit == credit; gross = ушланмалар + net; счётлар
     * айнан detail type/ном бўйича; харажат леги ходим+class, clearing
     * леги ходим, солиқ леглари contact'сиз/class'сиз.
     */
    @Test
    void post_glMatchesPostingRules() {
        UUID classId = txnClassService.create("Маъмурият", null).getId();
        PayrollRun run = payrollRunService.post(draftTwoEmployees(classId).getId());

        assertThat(run.getStatus()).isEqualTo(PayrollRun.Status.POSTED);
        assertThat(run.getRunNumber()).startsWith("PAYR-2026-");
        // Snapshot формуласи: 12% / 0.1% / 12% (HALF_UP 2)
        PayrollRunLine lineA = run.getLines().get(0);
        assertThat(lineA.getIncomeTax()).isEqualByComparingTo("360000");
        assertThat(lineA.getPension()).isEqualByComparingTo("3000");
        assertThat(lineA.getSocialTax()).isEqualByComparingTo("360000");
        assertThat(lineA.getNet()).isEqualByComparingTo("2637000");
        // gross = ушланмалар + net (ҳар сатрда)
        for (PayrollRunLine line : run.getLines()) {
            assertThat(line.getIncomeTax().add(line.getPension()).add(line.getNet()))
                    .isEqualByComparingTo(line.getGross());
        }

        JournalEntry entry = glEntry(run.getId());
        assertBalanced(entry);
        // Dr иш ҳақи харажати = жами gross; Dr солиқ харажати = жами ижтимоий
        assertThat(baseOfName(entry, PayrollRunService.SALARY_EXPENSE_NAME, true))
                .isEqualByComparingTo("5000000");
        assertThat(baseOfName(entry, PayrollRunService.TAX_EXPENSE_NAME, true))
                .isEqualByComparingTo("600000");
        // Cr солиқ мажбурияти = жами (даромад+пенсия+ижтимоий); Cr clearing = жами net
        assertThat(baseOfName(entry, "Иш ҳақи солиқлари мажбурияти", false))
                .isEqualByComparingTo("1205000");
        assertThat(baseOfName(entry, "Иш ҳақи бўйича мажбурият", false))
                .isEqualByComparingTo("4395000");

        for (JournalEntryLine glLine : entry.getLines()) {
            String name = glLine.getAccount().getName();
            switch (name) {
                case PayrollRunService.SALARY_EXPENSE_NAME -> {
                    // Харажат леги - ходим кесимида; A сатрида class ҳам
                    assertThat(glLine.getContactId()).isNotNull();
                    if (employeeA.getId().equals(glLine.getContactId())) {
                        assertThat(glLine.getClassId()).isEqualTo(classId);
                    } else {
                        assertThat(glLine.getClassId()).isNull();
                    }
                }
                case PayrollRunService.TAX_EXPENSE_NAME,
                     "Иш ҳақи солиқлари мажбурияти" -> {
                    // Солиқ леглари - бюджетга жами: contact'сиз, class'сиз
                    assertThat(glLine.getContactId()).isNull();
                    assertThat(glLine.getClassId()).isNull();
                }
                case "Иш ҳақи бўйича мажбурият" -> {
                    // Clearing - ходим кесимида (субледжер), class'сиз
                    assertThat(glLine.getContactId()).isNotNull();
                    assertThat(glLine.getClassId()).isNull();
                }
                default -> throw new AssertionError("Кутилмаган счёт: " + name);
            }
        }
    }

    /** Spec 2-банд: ставка ўзгарса эски POSTED run ва JE ўзгармайди. */
    @Test
    void snapshot_rateChangeDoesNotAffectPostedRun() {
        PayrollRun run = payrollRunService.post(draftTwoEmployees(null).getId());
        BigDecimal taxBefore = baseOfName(glEntry(run.getId()),
                "Иш ҳақи солиқлари мажбурияти", false);

        // Ставкалар кескин ўзгартирилади
        settingsService.updatePayrollRates(new BigDecimal("20"),
                new BigDecimal("1"), new BigDecimal("25"));

        // Эски run сатрлари ва JE суммалари ЎЗГАРМАГАН (snapshot)
        PayrollRun after = payrollRunService.getWithLines(run.getId());
        assertThat(after.getLines().get(0).getIncomeTax()).isEqualByComparingTo("360000");
        assertThat(after.getLines().get(0).getNet()).isEqualByComparingTo("2637000");
        assertThat(baseOfName(glEntry(run.getId()), "Иш ҳақи солиқлари мажбурияти", false))
                .isEqualByComparingTo(taxBefore);

        // Янги run (бошқа ойга) янги ставкада ҳисобланади
        PayrollRun fresh = payrollRunService.saveDraft(null, new RunData(
                "2026-08", LocalDate.of(2026, 8, 31), null,
                List.of(new LineData(employeeA.getId(), new BigDecimal("1000000"),
                        null, null))));
        assertThat(fresh.getLines().get(0).getIncomeTax()).isEqualByComparingTo("200000");
        assertThat(fresh.getLines().get(0).getSocialTax()).isEqualByComparingTo("250000");
    }

    /** Spec 3-банд: BR-PYR-002 - иккинчи POSTED ўша ойга рад; reverse'дан кейин мумкин. */
    @Test
    void onePostedRunPerPeriod_reverseFreesThePeriod() {
        PayrollRun first = payrollRunService.post(draftTwoEmployees(null).getId());

        PayrollRun second = payrollRunService.saveDraft(null, new RunData(
                PERIOD, RUN_DATE, null,
                List.of(new LineData(employeeA.getId(), new BigDecimal("1000000"),
                        null, null))));
        assertThatThrownBy(() -> payrollRunService.post(second.getId()))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PYR-002"));

        // Reverse - сторно + давр бўшайди
        payrollRunService.reverse(first.getId(), RUN_DATE, "тест");
        assertThat(glEntry(first.getId()).getStatus()).isEqualTo(EntryStatus.REVERSED);
        assertThat(payrollRunService.get(first.getId()).getStatus())
                .isEqualTo(PayrollRun.Status.REVERSED);

        PayrollRun reposted = payrollRunService.post(second.getId());
        assertThat(reposted.getStatus()).isEqualTo(PayrollRun.Status.POSTED);
        assertBalanced(glEntry(reposted.getId()));
    }

    /**
     * Arbitr-045 (BR-PYR-005/006): lifecycle гаровлари period уникаллигидан
     * (002) алоҳида код билан - фақат DRAFT post қилинади, фақат POSTED
     * reverse қилинади.
     */
    @Test
    void lifecycle_onlyDraftPostAndPostedReverse() {
        PayrollRun run = payrollRunService.post(draftTwoEmployees(null).getId());
        // POSTED'ни қайта post - BR-PYR-005 (фақат DRAFT post қилинади)
        assertThatThrownBy(() -> payrollRunService.post(run.getId()))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PYR-005"));
        // POSTED reverse - ўтади, давр бўшайди
        payrollRunService.reverse(run.getId(), RUN_DATE, "тест");
        // REVERSED'ни яна reverse - BR-PYR-006 (фақат POSTED reverse қилинади)
        assertThatThrownBy(() -> payrollRunService.reverse(run.getId(), RUN_DATE, "тест"))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PYR-006"));
    }

    /** BR-PYR-003/004 гаровлари: ёт контакт, такрор ходим, кейинги ой run_date. */
    @Test
    void guards_wrongContactDuplicateAndLateRunDate() {
        Contact customer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Мижоз (ходим эмас)", null, null, null, null, null,
                null, null, null, null, null));
        // CUSTOMER контакт сатрда рад (BR-PYR-003)
        assertThatThrownBy(() -> payrollRunService.saveDraft(null, new RunData(
                PERIOD, RUN_DATE, null,
                List.of(new LineData(customer.getId(), BigDecimal.TEN, null, null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PYR-003"));
        // Такрор ходим рад (BR-PYR-003)
        assertThatThrownBy(() -> payrollRunService.saveDraft(null, new RunData(
                PERIOD, RUN_DATE, null,
                List.of(new LineData(employeeA.getId(), BigDecimal.TEN, null, null),
                        new LineData(employeeA.getId(), BigDecimal.ONE, null, null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PYR-003"));
        // Run санаси period ойидан кейинги ойда - рад (BR-PYR-004)
        assertThatThrownBy(() -> payrollRunService.saveDraft(null, new RunData(
                PERIOD, LocalDate.of(2026, 8, 1), null,
                List.of(new LineData(employeeA.getId(), BigDecimal.TEN, null, null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PYR-004"));
    }

    /**
     * Arbitr-052 (006): BR-PYR-003 сатр чегаралари - бўш рўйхат ва gross
     * null/0/манфий. Аввалги гаровлар фақат ёт-контакт + такрор ходимни
     * қоплаган эди; сумма чегараси текширилмаган эди.
     */
    @Test
    void saveDraft_lineBoundaries_rejectedPyr003() {
        // Бўш сатр рўйхати - камида битта сатр киритилиши шарт
        assertPyr003(List.of());
        // gross null / 0 / манфий - мусбат бўлиши шарт
        assertPyr003(List.of(new LineData(employeeA.getId(), null, null, null)));
        assertPyr003(List.of(new LineData(employeeA.getId(), BigDecimal.ZERO, null, null)));
        assertPyr003(List.of(new LineData(employeeA.getId(), new BigDecimal("-100"), null, null)));
    }

    /** saveDraft'ни берилган сатрлар билан чақириб BR-PYR-003 кутади. */
    private void assertPyr003(List<LineData> lines) {
        assertThatThrownBy(() -> payrollRunService.saveDraft(null, new RunData(
                PERIOD, RUN_DATE, null, lines)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PYR-003"));
    }

    /**
     * Arbitr-054: рўйхат экрани жами gross/net энди JPQL агрегатдан ўқилади
     * ({@link PayrollRunService#totalsByRun}) - сатр {@code run.totalGross()}
     * lazy {@code lines}ни айланарди, render open-in-view=false да
     * LazyInitializationException берарди (жонли демо блокери). Бу тест
     * агрегатнинг ТЎҒРИ ҚИЙМАТИНИ ва эски домен методи билан эквивалентлигини
     * тасдиқлайди (POSTED run сатрлари билан).
     *
     * <p>ЭСЛАТМА: @Transactional тест lazy хатони ЎЗИ кўрсата олмайди (сессия
     * очиқ - сохта яшил); жонли текширув - Ҳабиба ҳисоботида /payroll саҳифаси
     * POSTED run билан очилгани (арбитр/preview smoke).
     */
    @Test
    void totalsByRun_aggregatesGrossAndNet_matchesDomain() {
        PayrollRun run = payrollRunService.post(draftTwoEmployees(null).getId());

        var totals = payrollRunService.totalsByRun(List.of(run.getId()));

        // Жами gross = 3 000 000 + 2 000 000; net = 2 637 000 + 1 758 000
        assertThat(totals).containsKey(run.getId());
        assertThat(totals.get(run.getId()).gross()).isEqualByComparingTo("5000000");
        assertThat(totals.get(run.getId()).net()).isEqualByComparingTo("4395000");

        // Эски домен методи (сатрлар билан) билан айнан тенг - эквивалентлик
        PayrollRun withLines = payrollRunService.getWithLines(run.getId());
        assertThat(totals.get(run.getId()).gross())
                .isEqualByComparingTo(withLines.totalGross());
        assertThat(totals.get(run.getId()).net())
                .isEqualByComparingTo(withLines.totalNet());

        // Бўш вход - бўш харита (controller getOrDefault → RunTotals.ZERO)
        assertThat(payrollRunService.totalsByRun(List.of())).isEmpty();
    }
}
