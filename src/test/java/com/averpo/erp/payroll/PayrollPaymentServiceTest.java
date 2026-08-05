package com.averpo.erp.payroll;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.ledger.domain.EntryStatus;
import com.averpo.erp.ledger.domain.JournalEntry;
import com.averpo.erp.ledger.domain.JournalEntryLine;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.repo.JournalEntryRepository;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.payroll.domain.PayrollPayment;
import com.averpo.erp.payroll.domain.PayrollPaymentType;
import com.averpo.erp.payroll.service.PayrollPaymentService;
import com.averpo.erp.payroll.service.PayrollPaymentService.LineData;
import com.averpo.erp.payroll.service.PayrollPaymentService.PaymentData;
import com.averpo.erp.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PayrollPayment тестлари (docs/modules/payroll.md «Тестлар» 4-6;
 * ведомость 4/5/7 PayrollRegisterServiceTest'да). Ҳар posting'да
 * debit == credit (ТЕМИР ҚОИДА №7). Проводка: Dr PAYROLL_CLEARING
 * (ходим кесимида) / Cr банк-касса.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PayrollPaymentServiceTest {

    /** Тест санаси (июль ичи). */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 15);

    /** Clearing счёти номи (043 seed) - payroll'да ном ҳал қилувчи. */
    private static final String CLEARING = "Иш ҳақи бўйича мажбурият";

    @Autowired PayrollPaymentService paymentService;
    @Autowired ContactService contactService;
    @Autowired AccountService accountService;
    @Autowired AccountRepository accountRepository;
    @Autowired JournalEntryRepository entryRepository;
    @Autowired jakarta.persistence.EntityManager em;

    private Contact employeeA;
    private Contact employeeB;
    private UUID bank;

    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        employeeA = employee("Ходим Алишер");
        employeeB = employee("Ходим Барно");
        bank = accountRepository.findByName("Банк ҳисобварағи").orElseThrow().getId();
    }

    /**
     * unpaidByEmployee - JdbcClient native SQL (Arbitr-047), Hibernate
     * flush'ни триггер қилмайди; бир tx'да ёзиб-ўқийдиган тест олдин flush
     * қилиши шарт (прод'да prefill alohida request, маълумот commit'ланган).
     */
    private java.util.Map<UUID, BigDecimal> unpaid(LocalDate asOf) {
        em.flush();
        return paymentService.unpaidByEmployee(asOf);
    }

    /** Фаол EMPLOYEE контакт ясайди. */
    private Contact employee(String name) {
        return contactService.create(ContactType.EMPLOYEE, new ContactData(
                name, null, null, null, null, null, null, null, null, null, null));
    }

    /** POSTED тўлов ясайди (тур + сатрлар). */
    private PayrollPayment postPayment(PayrollPaymentType type, UUID accountId,
                                       List<LineData> lines) {
        PayrollPayment draft = paymentService.saveDraft(null,
                new PaymentData(type, DATE, accountId, null, lines));
        return paymentService.post(draft.getId());
    }

    /** Манба бўйича фаол GL ёзуви. */
    private JournalEntry glEntry(UUID paymentId) {
        return entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                PayrollPaymentService.SOURCE_MODULE, paymentId).orElseThrow();
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

    /** Счёт НОМИ бўйича дебет/кредит base йиғиндиси. */
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

    /**
     * Arbitr-071 (Asrorxoja-012): DRAFT сақлангандан кейин ходим нофаол
     * қилинса post РАД (run post кўзгуси) - аввал тўлов post'да ходим
     * қайта текширилмасди (асимметрия), нофаол ходимга тўлов ўтарди.
     */
    @Test
    void post_inactiveEmployeeAfterDraft_rejectedPyr003() {
        PayrollPayment draft = paymentService.saveDraft(null, new PaymentData(
                PayrollPaymentType.ADVANCE, DATE, bank, null,
                List.of(new LineData(employeeA.getId(), new BigDecimal("100000")))));
        contactService.update(employeeA.getId(), new ContactData(
                employeeA.getDisplayName(), null, null, null, null, null,
                null, null, null, null, null), false);

        assertThatThrownBy(() -> paymentService.post(draft.getId()))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PYR-003"));
    }

    @Test
    void post_glMatchesPostingRules_drClearingPerEmployee_crBank() {
        PayrollPayment payment = postPayment(PayrollPaymentType.ADVANCE, bank, List.of(
                new LineData(employeeA.getId(), new BigDecimal("300000")),
                new LineData(employeeB.getId(), new BigDecimal("200000"))));

        assertThat(payment.getStatus()).isEqualTo(PayrollPayment.Status.POSTED);
        assertThat(payment.getPaypNumber()).startsWith("PAYP-2026-");
        assertThat(payment.getTotal()).isEqualByComparingTo("500000");

        JournalEntry entry = glEntry(payment.getId());
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.POSTED);
        assertBalanced(entry);
        // Dr PAYROLL_CLEARING (ҳар ходим кесимида) - жами 500 000
        assertThat(baseOfName(entry, CLEARING, true)).isEqualByComparingTo("500000");
        // Cr банк = жами
        assertThat(baseOfName(entry, "Банк ҳисобварағи", false)).isEqualByComparingTo("500000");
        // Ҳар clearing леги ходим кесимида (contactId тўлдирилган)
        long clearingWithContact = entry.getLines().stream()
                .filter(l -> l.getDebit() != null && CLEARING.equals(l.getAccount().getName()))
                .filter(l -> l.getContactId() != null)
                .count();
        assertThat(clearingWithContact).isEqualTo(2);
        // Банк леги contact'сиз (жами назорат сатри)
        assertThat(entry.getLines().stream()
                .filter(l -> l.getCredit() != null)
                .allMatch(l -> l.getContactId() == null)).isTrue();
    }

    @Test
    void reverse_stornosGl_neutralizesClearing() {
        PayrollPayment payment = postPayment(PayrollPaymentType.ADVANCE, bank,
                List.of(new LineData(employeeA.getId(), new BigDecimal("300000"))));
        // Аванс: clearing ходимда ДЕБЕТ (аванс - ходим прекра тўланган)
        assertThat(unpaid(DATE).get(employeeA.getId()))
                .isEqualByComparingTo("-300000");

        paymentService.reverse(payment.getId(), DATE, "хато");

        assertThat(payment.getStatus()).isEqualTo(PayrollPayment.Status.REVERSED);
        assertThat(glEntry(payment.getId()).getStatus()).isEqualTo(EntryStatus.REVERSED);
        // Сторно жуфти: ходим clearing қолдиғи нолга тушади (нейтралланди)
        BigDecimal owed = unpaid(DATE)
                .getOrDefault(employeeA.getId(), BigDecimal.ZERO);
        assertThat(owed).isEqualByComparingTo("0");
        // Иккинчи reverse тақиқ (BR-PYR-006 - POSTED эмас)
        assertThatThrownBy(() -> paymentService.reverse(payment.getId(), DATE, null))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PYR-006"));
    }

    @Test
    void foreignCurrencyAccount_rejectedPyr001() {
        UUID usd = accountRepository.findByName("Валюта ҳисобварағи (USD)").orElseThrow().getId();
        assertThatThrownBy(() -> postPayment(PayrollPaymentType.SALARY, usd,
                List.of(new LineData(employeeA.getId(), new BigDecimal("100000")))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PYR-001"));
    }

    @Test
    void customerContactLine_rejectedPyr003() {
        Contact customer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Мижоз Санжар", null, null, null, null, null, null, null, null, null, null));
        assertThatThrownBy(() -> postPayment(PayrollPaymentType.SALARY, bank,
                List.of(new LineData(customer.getId(), new BigDecimal("100000")))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PYR-003"));
    }

    @Test
    void inactiveEmployeeLine_rejectedPyr003() {
        contactService.update(employeeA.getId(), new ContactData(
                "Ходим Алишер", null, null, null, null, null, null, null, null, null, null),
                false);
        assertThatThrownBy(() -> postPayment(PayrollPaymentType.SALARY, bank,
                List.of(new LineData(employeeA.getId(), new BigDecimal("100000")))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PYR-003"));
    }

    @Test
    void validation_guards() {
        // Дубликат ходим бир тўловда
        assertThatThrownBy(() -> paymentService.saveDraft(null, new PaymentData(
                PayrollPaymentType.SALARY, DATE, bank, null, List.of(
                        new LineData(employeeA.getId(), new BigDecimal("1000")),
                        new LineData(employeeA.getId(), new BigDecimal("2000"))))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PYR-003"));
        // Нол/манфий сумма
        assertThatThrownBy(() -> paymentService.saveDraft(null, new PaymentData(
                PayrollPaymentType.SALARY, DATE, bank, null,
                List.of(new LineData(employeeA.getId(), BigDecimal.ZERO)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PYR-003"));
        // Сатрсиз
        assertThatThrownBy(() -> paymentService.saveDraft(null, new PaymentData(
                PayrollPaymentType.SALARY, DATE, bank, null, List.of())))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PYR-003"));
    }

    @Test
    void lifecycle_onlyDraftEditableAndDeletable() {
        PayrollPayment payment = postPayment(PayrollPaymentType.ADVANCE, bank,
                List.of(new LineData(employeeA.getId(), new BigDecimal("300000"))));
        // POSTED - таҳрир/ўчириш/қайта post тақиқ (BR-PYR-005 - фақат DRAFT)
        assertThatThrownBy(() -> paymentService.saveDraft(payment.getId(), new PaymentData(
                PayrollPaymentType.SALARY, DATE, bank, null,
                List.of(new LineData(employeeA.getId(), new BigDecimal("100000"))))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PYR-005"));
        assertThatThrownBy(() -> paymentService.deleteDraft(payment.getId()))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PYR-005"));
        assertThatThrownBy(() -> paymentService.post(payment.getId()))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PYR-005"));
    }

    @Test
    void saveDraft_thenUpdate_replacesLines() {
        PayrollPayment draft = paymentService.saveDraft(null, new PaymentData(
                PayrollPaymentType.SALARY, DATE, bank, null,
                List.of(new LineData(employeeA.getId(), new BigDecimal("100000")))));
        // Ўша ходимни қайта тўлдириш (Beruniy-010: flush INSERT DELETE'дан олдин)
        PayrollPayment updated = paymentService.saveDraft(draft.getId(), new PaymentData(
                PayrollPaymentType.SALARY, DATE, bank, null,
                List.of(new LineData(employeeA.getId(), new BigDecimal("250000")))));
        assertThat(updated.getLines()).hasSize(1);
        assertThat(updated.getTotal()).isEqualByComparingTo("250000");

        Map<UUID, BigDecimal> owed = unpaid(DATE);
        // DRAFT GL'га тегмаган - clearing қолдиғи ҳали ўзгармаган (бўш)
        assertThat(owed.getOrDefault(employeeA.getId(), BigDecimal.ZERO))
                .isEqualByComparingTo("0");
    }
}
