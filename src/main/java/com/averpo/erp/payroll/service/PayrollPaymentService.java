package com.averpo.erp.payroll.service;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.domain.AccountType;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.ledger.service.JournalEntryRequest;
import com.averpo.erp.ledger.service.LedgerDashboardService;
import com.averpo.erp.ledger.service.PostingService;
import com.averpo.erp.payroll.domain.PayrollPayment;
import com.averpo.erp.payroll.domain.PayrollPaymentLine;
import com.averpo.erp.payroll.domain.PayrollPaymentType;
import com.averpo.erp.payroll.repo.PayrollPaymentRepository;
import com.averpo.erp.shared.BatchLookup;
import com.averpo.erp.shared.Strings;
import com.averpo.erp.shared.domain.DocumentType;
import com.averpo.erp.shared.domain.Money;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.exception.NotFoundException;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.service.DocumentSequenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Иш ҳақи тўловининг ягона public API'си (docs/modules/payroll.md 23в;
 * posting-rules «Иш ҳақи»). Ҳаёт цикли invoice қолипи: DRAFT (таҳрир/
 * ўчириш) → POSTED (GL, ўзгармас) → REVERSED (GL сторно). Ҳамма суммалар
 * ФАҚАТ home валютада (BR-PYR-001).
 *
 * <p>Проводка: Dr PAYROLL_CLEARING (ҳар ходим кесимида) / Cr банк-касса.
 * GL - фақат PostingService (ТЕМИР ҚОИДА №2); бошқа модулларга фақат
 * public service орқали (№6). Тўлов run'га боғланМАЙДИ - clearing қолдиғи
 * (GL контакт кесими) ўзи ҳақиқат манбаи (Lite соддалиги).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PayrollPaymentService {

    /** GL манба модул белгиси (posting-rules «Иш ҳақи»). */
    public static final String SOURCE_MODULE = "PAYROLL_PAYMENT";

    /** Битта тўлов формаси маълумотлари (create/update учун умумий). */
    public record PaymentData(PayrollPaymentType paymentType, LocalDate paymentDate,
                              UUID accountId, String memo, List<LineData> lines) { }

    /** Битта сатр: қайси ходимга қанча (home валютада). */
    public record LineData(UUID employeeId, BigDecimal amount) { }

    /** Тўлов репозиторийси. */
    private final PayrollPaymentRepository repository;

    /** Ҳужжат рақамлари (PAYP-2026-NNNNN). */
    private final DocumentSequenceService sequenceService;

    /** Ходим текшируви (EMPLOYEE, фаол) - contact модулининг public API'си. */
    private final ContactService contactService;

    /** Тўлов счёти валидацияси ва PAYROLL_CLEARING тизим счёти. */
    private final AccountService accountService;

    /** GL'га ёзишнинг ягона йўли. */
    private final PostingService postingService;

    /** Home currency текшируви учун. */
    private final CompanySettingsService settingsService;

    /** Clearing контакт кесими қолдиғи (unpaidByEmployee) - ledger public API'си (қоида №6). */
    private final LedgerDashboardService ledgerDashboardService;

    /** Id бўйича топади ёки тушунарли хато отади. */
    @Transactional(readOnly = true)
    public PayrollPayment get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Тўлов топилмади: " + id));
    }

    /** Кўриш/post учун - сатрлари билан. */
    @Transactional(readOnly = true)
    public PayrollPayment getWithLines(UUID id) {
        return repository.findWithLinesById(id)
                .orElseThrow(() -> new NotFoundException("Тўлов топилмади: " + id));
    }

    /** Рўйхат саҳифаси ҳажми («туғилишда пагинация», perf1). */
    public static final int LIST_PAGE_SIZE = 25;

    /**
     * Рўйхат тартиби - янгидан эскига (тўлов санаси, тенг санада яратилиш
     * вақти). Bill/тўлов рўйхатлари билан бир хил нақш (Beruniy-perf1).
     */
    private static final org.springframework.data.domain.Sort LIST_SORT =
            org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Order.desc("paymentDate"),
                    org.springframework.data.domain.Sort.Order.desc("createdAt"),
                    org.springframework.data.domain.Sort.Order.desc("id"));

    /** Рўйхат экрани - саҳифаланган (perf1: туғилишда пагинация); size - ARBITR-105. */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<PayrollPayment> list(int page, int size) {
        return repository.findAll(org.springframework.data.domain.PageRequest.of(
                Math.max(0, page), size, LIST_SORT));
    }

    /** Default ҳажм ({@link #LIST_PAGE_SIZE}) билан - эски чақирувчилар/тестлар (ARBITR-105). */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<PayrollPayment> list(int page) {
        return list(page, LIST_PAGE_SIZE);
    }

    /**
     * Ходим кесимида PAYROLL_CLEARING очиқ қолдиғи (форма prefill'и): owed
     * = Cr − Dt (компания ходимга қарзи; мусбат - тўланиши керак). Манба -
     * GL clearing контакт кесими (spec: «clearing қолдиғи ҳақиқат манбаи»),
     * ledger public JdbcClient агрегати орқали (қоида №6 - ledger repo'сига
     * тегмайди). Arbitr-047/Beruniy-028: аввалги EPOCH register бутун
     * clearing тарихини объект қилиб хотирага тортарди - энди SQL агрегат.
     *
     * @param asOf шу санагача (инклюзив) бўлган POSTED/REVERSED ҳаракат
     */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> unpaidByEmployee(LocalDate asOf) {
        UUID clearing = accountService.requireSystemAccountId(AccountDetailType.PAYROLL_CLEARING);
        return ledgerDashboardService.contactBalances(clearing, asOf);
    }

    /**
     * DRAFT яратади ёки мавжуд DRAFT'ни тўлиқ янгилайди (id null - янги).
     * Рақам DocumentSequence'дан дарҳол (draft ҳам рақамли, Bill қолипи).
     *
     * @throws BusinessRuleException BR-PYR-001 (счёт/валюта), BR-PYR-003 (сатрлар)
     */
    public PayrollPayment saveDraft(UUID id, PaymentData data) {
        validate(data);
        PayrollPayment payment;
        if (id == null) {
            payment = new PayrollPayment(
                    sequenceService.next(DocumentType.PAYROLL_PAYMENT, data.paymentDate()),
                    data.paymentType(), data.paymentDate(), data.accountId(),
                    Strings.blankToNull(data.memo()));
        } else {
            payment = getWithLines(id);
            payment.updateHeader(data.paymentType(), data.paymentDate(),
                    data.accountId(), Strings.blankToNull(data.memo()));
            payment.clearLines();
            // uq_payroll_payment_line_employee (Beruniy-010 нақши): Hibernate
            // flush'да INSERT DELETE'дан олдин - эски сатрлар аввал ўчирилиши
            // шарт, акс ҳолда ўша ходим билан янги сатр эскиси билан тўқнашади
            repository.flush();
        }
        for (LineData line : data.lines()) {
            payment.addLine(line.employeeId(), line.amount());
        }
        return repository.saveAndFlush(payment);
    }

    /**
     * Post: GL проводка (posting-rules «Иш ҳақи») - Dr PAYROLL_CLEARING
     * ҳар ходим кесимида / Cr тўлов счёти (жами). Давр қулфи (BR-LED-020)
     * ва idempotency PostingService'дан. Post олдидан счёт қайта
     * текширилади (draft'дан кейин счёт валютаси ўзгарган бўлиши мумкин).
     *
     * @throws BusinessRuleException BR-PYR-005 (фақат DRAFT), BR-PYR-003 (сатр), BR-PYR-001 (счёт)
     */
    public PayrollPayment post(UUID id) {
        PayrollPayment payment = getWithLines(id);
        if (payment.getStatus() != PayrollPayment.Status.DRAFT) {
            throw new BusinessRuleException(BusinessRule.BR_PYR_005,
                    "Фақат DRAFT тўлов post қилинади: " + payment.getPaypNumber()
                    + " - " + payment.getStatus());
        }
        if (payment.getLines().isEmpty()) {
            throw new BusinessRuleException(BusinessRule.BR_PYR_003,
                    "Тўловда камида битта сатр бўлиши шарт: " + payment.getPaypNumber());
        }
        requireHomeBankAccount(payment.getAccountId());
        // Ходим қайта текшируви (Arbitr-071/Asrorxoja-012, run post кўзгуси):
        // DRAFT сақлангандан кейин ходим нофаол қилинган бўлиши мумкин.
        // Батч (Sanjar-008): N ходим битта IN сўровда, циклда Map.get()
        Map<UUID, Contact> employees = BatchLookup.byId(contactService.findAllById(
                BatchLookup.ids(payment.getLines(), PayrollPaymentLine::getEmployeeId)));
        for (PayrollPaymentLine line : payment.getLines()) {
            requireActiveEmployee(line.getEmployeeId(), employees);
        }

        String home = settingsService.homeCurrency();
        UUID clearing = accountService.requireSystemAccountId(AccountDetailType.PAYROLL_CLEARING);
        List<JournalEntryRequest.Line> glLines = new ArrayList<>();
        for (PayrollPaymentLine line : payment.getLines()) {
            // Clearing ходим кесимида дебет (назорат сатри - class'сиз)
            glLines.add(new JournalEntryRequest.Line(clearing,
                    Money.ofBase(line.getAmount(), home), null,
                    line.getEmployeeId(), null, null, null));
        }
        // Банк-касса кредити = жами (contact'сиз)
        glLines.add(new JournalEntryRequest.Line(payment.getAccountId(),
                null, Money.ofBase(payment.getTotal(), home), null, null, null, null));
        postingService.createAndPost(new JournalEntryRequest(
                payment.getPaymentDate(),
                "Иш ҳақи тўлови " + payment.getPaypNumber(),
                SOURCE_MODULE, payment.getId(), glLines));
        payment.markPosted(Instant.now());
        return payment;
    }

    /**
     * Reverse: оддий GL сторно (омбор/денормализация йўқ). Ходим clearing
     * қолдиғи автоматик тикланади (сторно жуфти contact кесимида нетто
     * нолга тушади) - ведомость нейтралланади.
     *
     * @throws BusinessRuleException BR-PYR-006 - POSTED эмас
     */
    public PayrollPayment reverse(UUID id, LocalDate reversalDate, String reason) {
        PayrollPayment payment = get(id);
        if (payment.getStatus() != PayrollPayment.Status.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_PYR_006,
                    "Фақат POSTED тўлов reverse қилинади: " + payment.getPaypNumber()
                    + " - " + payment.getStatus());
        }
        postingService.reverseBySource(SOURCE_MODULE, payment.getId(), reversalDate,
                reason == null || reason.isBlank() ? "Иш ҳақи тўлови reverse" : reason);
        payment.markReversed();
        return payment;
    }

    /** DRAFT'ни ўчиради - POSTED/REVERSED ўчирилмайди (қоида №3). */
    public void deleteDraft(UUID id) {
        PayrollPayment payment = getWithLines(id);
        if (payment.getStatus() != PayrollPayment.Status.DRAFT) {
            throw new BusinessRuleException(BusinessRule.BR_PYR_005,
                    "Фақат DRAFT тўлов ўчирилади: " + payment.getPaypNumber());
        }
        repository.delete(payment);
    }

    // ---- ички ёрдамчилар ----

    /** Сарлавҳа + сатрлар валидацияси (saveDraft'да). */
    private void validate(PaymentData data) {
        if (data.paymentDate() == null) {
            throw new BusinessRuleException(BusinessRule.BR_PYR_001,
                    "Тўлов санаси киритилиши шарт");
        }
        if (data.paymentType() == null) {
            throw new BusinessRuleException(BusinessRule.BR_PYR_001,
                    "Тўлов тури (аванс/ойлик) танланиши шарт");
        }
        requireHomeBankAccount(data.accountId());
        validateLines(data.lines());
    }

    /**
     * BR-PYR-001: тўлов счёти танланган, фаол postable BANK туридан (касса
     * ҳам BANK) ва home валютали (payroll фақат home). Чет валютали счёт рад.
     */
    private void requireHomeBankAccount(UUID accountId) {
        if (accountId == null) {
            throw new BusinessRuleException(BusinessRule.BR_PYR_001,
                    "Тўлов счёти танланиши шарт (BANK/касса, home валютали)");
        }
        Account account = accountService.get(accountId);
        if (account.getType() != AccountType.BANK || !account.isActive()
                || !account.isPostable()) {
            throw new BusinessRuleException(BusinessRule.BR_PYR_001,
                    "Тўлов счёти фаол postable BANK/касса счёти бўлиши шарт: "
                    + account.getName());
        }
        String home = settingsService.homeCurrency();
        if (account.getCurrency() != null && !account.getCurrency().getCode().equals(home)) {
            throw new BusinessRuleException(BusinessRule.BR_PYR_001,
                    "Иш ҳақи тўлови фақат home валютали (" + home + ") счётдан: "
                    + account.getName());
        }
    }

    /**
     * BR-PYR-003: сатрлар бўш эмас; ҳар сатр ходими фаол EMPLOYEE; сумма
     * мусбат; бир тўловда ходим такрорланмайди.
     */
    private void validateLines(List<LineData> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessRuleException(BusinessRule.BR_PYR_003,
                    "Тўловда камида битта сатр (ходим + сумма) бўлиши шарт");
        }
        // Батч (Sanjar-008): seen такрорни тақиқлайди - N сатр айнан N ноёб
        // контакт эди; энди ҳаммаси битта IN сўровда, циклда Map.get()
        Map<UUID, Contact> employees = BatchLookup.byId(
                contactService.findAllById(BatchLookup.ids(lines, LineData::employeeId)));
        Set<UUID> seen = new HashSet<>();
        for (LineData line : lines) {
            if (line.employeeId() == null) {
                throw new BusinessRuleException(BusinessRule.BR_PYR_003,
                        "Сатрда ходим танланиши шарт");
            }
            if (!seen.add(line.employeeId())) {
                throw new BusinessRuleException(BusinessRule.BR_PYR_003,
                        "Ходим бир тўловда бир марта бўлиши шарт");
            }
            Contact employee = requireActiveEmployee(line.employeeId(), employees);
            if (line.amount() == null || line.amount().signum() <= 0) {
                throw new BusinessRuleException(BusinessRule.BR_PYR_003,
                        "Сумма мусбат бўлиши шарт: " + employee.getDisplayName());
            }
        }
    }

    /**
     * BR-PYR-003: контакт фаол EMPLOYEE бўлиши шарт - saveDraft ҳам,
     * post ҳам шу ягона текширувдан ўтади (run post кўзгуси, Arbitr-071).
     * Контакт олдиндан юкланган батч Map'дан ўқилади (Sanjar-008) -
     * топилмаса {@link NotFoundException} (аввалги get() хулқи айнан).
     */
    private Contact requireActiveEmployee(UUID employeeId, Map<UUID, Contact> employees) {
        Contact employee = employees.get(employeeId);
        if (employee == null) {
            throw new NotFoundException("Контакт топилмади: " + employeeId);
        }
        if (employee.getType() != ContactType.EMPLOYEE || !employee.isActive()) {
            throw new BusinessRuleException(BusinessRule.BR_PYR_003,
                    "Сатр ходими фаол EMPLOYEE турида бўлиши шарт: "
                    + employee.getDisplayName());
        }
        return employee;
    }
}
