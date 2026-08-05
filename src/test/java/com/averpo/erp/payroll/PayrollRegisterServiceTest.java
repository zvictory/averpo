package com.averpo.erp.payroll;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.ledger.service.AccountTransactionsService;
import com.averpo.erp.payroll.domain.PayrollPayment;
import com.averpo.erp.payroll.domain.PayrollRun;
import com.averpo.erp.payroll.service.PayrollPaymentService;
import com.averpo.erp.payroll.service.PayrollPaymentService.PaymentData;
import com.averpo.erp.payroll.service.PayrollRegisterService;
import com.averpo.erp.payroll.service.PayrollRunService;
import com.averpo.erp.payroll.service.PayrollRunService.RunData;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.CompanySettingsService;
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
 * Иш ҳақи ведомости тестлари (docs/modules/payroll.md «Тестлар» 4/5/7):
 * аванс → run → ведомость қолдиғи = net - аванс; reverse нейтраллайди;
 * ведомость жамлари GL clearing қолдиқлари билан айнан тенг.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PayrollRegisterServiceTest {

    private static final String PERIOD = "2026-07";
    private static final LocalDate ADVANCE_DATE = LocalDate.of(2026, 7, 10);
    private static final LocalDate RUN_DATE = LocalDate.of(2026, 7, 31);
    private static final LocalDate EPOCH = LocalDate.of(1970, 1, 1);

    @Autowired PayrollPaymentService paymentService;
    @Autowired PayrollRunService payrollRunService;
    @Autowired PayrollRegisterService registerService;
    @Autowired ContactService contactService;
    @Autowired AccountService accountService;
    @Autowired AccountRepository accountRepository;
    @Autowired AccountTransactionsService accountTransactionsService;
    @Autowired CompanySettingsService settingsService;
    @Autowired jakarta.persistence.EntityManager em;

    private Contact employeeA;
    private UUID bank;

    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        settingsService.updatePayrollRates(new BigDecimal("12"),
                new BigDecimal("0.1"), new BigDecimal("12"));
        employeeA = employee("Ходим Алишер");
        bank = accountRepository.findByName("Банк ҳисобварағи").orElseThrow().getId();
    }

    private Contact employee(String name) {
        return contactService.create(ContactType.EMPLOYEE, new ContactData(
                name, null, null, null, null, null, null, null, null, null, null));
    }

    /** POSTED аванс/ойлик тўлови (битта ходим). */
    private PayrollPayment pay(com.averpo.erp.payroll.domain.PayrollPaymentType type,
                               LocalDate date, BigDecimal amount) {
        PayrollPayment draft = paymentService.saveDraft(null, new PaymentData(type, date, bank,
                null, List.of(new PayrollPaymentService.LineData(employeeA.getId(), amount))));
        return paymentService.post(draft.getId());
    }

    /** POSTED run (битта ходим, gross). */
    private PayrollRun postRun(BigDecimal gross) {
        PayrollRun draft = payrollRunService.saveDraft(null, new RunData(PERIOD, RUN_DATE, null,
                List.of(new PayrollRunService.LineData(employeeA.getId(), gross, null, null))));
        return payrollRunService.post(draft.getId());
    }

    /**
     * Ведомостни қуради - олдин em.flush(): register контакт-кесими
     * JdbcClient (native SQL), Hibernate flush'ни триггер қилмайди; бир
     * tx'да ёзиб-ўқийдиган тест flush'ланмаган JPA ёзувни кўрмас эди
     * (эски JPQL auto-flush эди). Прод'да ҳисобот alohida request - маълумот
     * commit'ланган, flush no-op.
     */
    private PayrollRegisterService.Register reg(String period) {
        em.flush();
        return registerService.build(period);
    }

    /** Ведомостдаги ходим қатори. */
    private PayrollRegisterService.Row rowFor(UUID employeeId) {
        return reg(PERIOD).rows().stream()
                .filter(r -> r.employeeId().equals(employeeId))
                .findFirst().orElseThrow();
    }

    /** GL clearing счётининг ходимга owed қолдиғи (Cr-Dt) asOf. */
    private BigDecimal glClearingOwed(LocalDate asOf) {
        em.flush();
        UUID clearing = accountService.requireSystemAccountId(AccountDetailType.PAYROLL_CLEARING);
        return accountTransactionsService.register(clearing, EPOCH, asOf).closing().negate();
    }

    @Test
    void advanceThenRun_registerClosing_isNetMinusAdvance() {
        // Spec 4: аванс 300 000 → clearing ходимда дебет (owed манфий)
        pay(com.averpo.erp.payroll.domain.PayrollPaymentType.ADVANCE,
                ADVANCE_DATE, new BigDecimal("300000"));
        // Run: gross 1 000 000 → net = 1 000 000 - 120 000 - 1 000 = 879 000
        postRun(new BigDecimal("1000000"));

        PayrollRegisterService.Row row = rowFor(employeeA.getId());
        assertThat(row.openingOwed()).isEqualByComparingTo("0");     // давр боши - ҳаракатсиз
        assertThat(row.gross()).isEqualByComparingTo("1000000");
        assertThat(row.incomeTax()).isEqualByComparingTo("120000");
        assertThat(row.pension()).isEqualByComparingTo("1000");
        assertThat(row.net()).isEqualByComparingTo("879000");
        assertThat(row.paid()).isEqualByComparingTo("300000");
        // Давр охири қолдиқ = net - аванс (spec 4)
        assertThat(row.closingOwed()).isEqualByComparingTo("579000");

        // Иккинчи (SALARY) тўлов қолдиқни нолга ёпади
        pay(com.averpo.erp.payroll.domain.PayrollPaymentType.SALARY,
                RUN_DATE, new BigDecimal("579000"));
        PayrollRegisterService.Row closed = rowFor(employeeA.getId());
        assertThat(closed.paid()).isEqualByComparingTo("879000"); // аванс + ойлик
        assertThat(closed.closingOwed()).isEqualByComparingTo("0");
    }

    @Test
    void reverse_neutralizesRegister() {
        pay(com.averpo.erp.payroll.domain.PayrollPaymentType.ADVANCE,
                ADVANCE_DATE, new BigDecimal("300000"));
        PayrollRun run = postRun(new BigDecimal("1000000"));
        assertThat(rowFor(employeeA.getId()).closingOwed()).isEqualByComparingTo("579000");

        // Run сторно: ҳисобланган net қайтади - қолдиқ фақат авансдаги дебет
        payrollRunService.reverse(run.getId(), RUN_DATE, "хато");
        PayrollRegisterService.Row afterRunReverse = rowFor(employeeA.getId());
        assertThat(afterRunReverse.gross()).isEqualByComparingTo("0"); // POSTED run йўқ
        assertThat(afterRunReverse.net()).isEqualByComparingTo("0");
        assertThat(afterRunReverse.paid()).isEqualByComparingTo("300000");
        assertThat(afterRunReverse.closingOwed()).isEqualByComparingTo("-300000");
    }

    @Test
    void registerTotals_equalGlClearingBalances() {
        // Spec 7: ведомость жамлари GL clearing қолдиқлари билан айнан тенг
        pay(com.averpo.erp.payroll.domain.PayrollPaymentType.ADVANCE,
                ADVANCE_DATE, new BigDecimal("300000"));
        postRun(new BigDecimal("1000000"));

        PayrollRegisterService.Register register = reg(PERIOD);
        // Per-employee closing йиғиндиси == GL clearing счёт умумий owed қолдиғи
        assertThat(register.totals().closingOwed())
                .isEqualByComparingTo(glClearingOwed(RUN_DATE));
        // Инвариант: давр охири = давр боши + net - тўланган
        assertThat(register.totals().closingOwed()).isEqualByComparingTo(
                register.totals().openingOwed()
                        .add(register.totals().net())
                        .subtract(register.totals().paid()));
        assertThat(register.totals().closingOwed()).isEqualByComparingTo("579000");
    }

    @Test
    void paymentReverse_registerInvariantHolds_bothPeriods() {
        // Komil-017/Arbitr-047: аванс июлда, августда reverse. Домен status'га
        // таянган paid синарди (REVERSED → paid 0, GL асл тўлов таъсирида);
        // GL асосида (source=PAYROLL_PAYMENT, Dt-Cr) инвариант ИККИ ойда ҳам.
        PayrollPayment advance = pay(
                com.averpo.erp.payroll.domain.PayrollPaymentType.ADVANCE,
                ADVANCE_DATE, new BigDecimal("300000"));

        PayrollRegisterService.Register julyBefore = reg(PERIOD);
        assertInvariant(julyBefore);
        assertThat(rowIn(julyBefore, employeeA.getId()).closingOwed())
                .isEqualByComparingTo("-300000");

        // Августда reverse (сторно entry_date = август)
        paymentService.reverse(advance.getId(), LocalDate.of(2026, 8, 5), "хато");

        // Июль ведомости ЎЗГАРМАЙДИ (тўлов Dt июлда, сторно августда):
        PayrollRegisterService.Register july = reg(PERIOD);
        assertInvariant(july);
        assertThat(rowIn(july, employeeA.getId()).paid()).isEqualByComparingTo("300000");
        assertThat(rowIn(july, employeeA.getId()).closingOwed()).isEqualByComparingTo("-300000");

        // Август ведомости: opening -300k, paid -300k (сторно Cr), closing 0
        PayrollRegisterService.Register august = reg("2026-08");
        assertInvariant(august);
        PayrollRegisterService.Row aug = rowIn(august, employeeA.getId());
        assertThat(aug.openingOwed()).isEqualByComparingTo("-300000");
        assertThat(aug.paid()).isEqualByComparingTo("-300000");
        assertThat(aug.closingOwed()).isEqualByComparingTo("0");
    }

    @Test
    void runDateBeforePeriodMonth_rejectedPyr004() {
        // Arbitr-047 банд 2: run_date period ойидан ОЛДИН - рад (икки томонлама)
        assertThatThrownBy(() -> payrollRunService.saveDraft(null, new RunData(
                "2026-07", LocalDate.of(2026, 6, 30), null,
                List.of(new PayrollRunService.LineData(employeeA.getId(),
                        new BigDecimal("1000000"), null, null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PYR-004"));
    }

    /** Берилган ведомостдаги ходим қатори. */
    private PayrollRegisterService.Row rowIn(PayrollRegisterService.Register reg, UUID employeeId) {
        return reg.rows().stream().filter(r -> r.employeeId().equals(employeeId))
                .findFirst().orElseThrow();
    }

    /** Инвариант: давр_охири = давр_боши + net − тўланган (жамилар). */
    private void assertInvariant(PayrollRegisterService.Register reg) {
        assertThat(reg.totals().closingOwed()).isEqualByComparingTo(
                reg.totals().openingOwed().add(reg.totals().net())
                        .subtract(reg.totals().paid()));
    }
}
