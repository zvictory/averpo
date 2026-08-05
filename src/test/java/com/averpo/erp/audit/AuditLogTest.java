package com.averpo.erp.audit;

import com.averpo.erp.audit.domain.AuditEvent;
import com.averpo.erp.audit.domain.AuditEventType;
import com.averpo.erp.audit.repo.AuditEventRepository;
import com.averpo.erp.audit.service.AuditLogService;
import com.averpo.erp.audit.service.AuditLogService.AuditFilter;
import com.averpo.erp.bank.domain.BankTransaction;
import com.averpo.erp.bank.service.BankTransactionService;
import com.averpo.erp.bank.service.BankTransactionService.TransferData;
import com.averpo.erp.ledger.domain.EntryStatus;
import com.averpo.erp.ledger.domain.JournalEntry;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.repo.JournalEntryRepository;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.security.domain.AppUser;
import com.averpo.erp.security.domain.UserRole;
import com.averpo.erp.security.service.UserService;
import com.averpo.erp.testsupport.WithMockRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Аудит журнали тестлари (docs/modules/audit-log.md «Тестлар» 1, 2, 5,
 * 7-бандлар): ledger post/reverse ҳодисалари, user-management
 * ҳодисалари, филтр кесимлари. Rollback исботи (3-банд) алоҳида
 * транзакциясиз классда - {@link AuditRollbackTest}; login ҳодисалари
 * (4-банд) LoginLockoutTest'да.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockRole(username = "auditchi")
class AuditLogTest {

    /** Барча тест ҳужжатлар санаси. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 7);

    @Autowired BankTransactionService bankService;
    @Autowired AccountService accountService;
    @Autowired AccountRepository accountRepository;
    @Autowired JournalEntryRepository entryRepository;
    @Autowired AuditEventRepository auditRepository;
    @Autowired AuditLogService auditLogService;
    @Autowired UserService userService;

    /** Home валютали банк счёти. */
    private UUID bank;

    /** Касса - транзфер манзили. */
    private UUID cash;

    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        bank = accountRepository.findByName("Банк ҳисобварағи").orElseThrow().getId();
        cash = accountRepository.findByName("Касса").orElseThrow().getId();
    }

    /** Тур бўйича ёзувлар - тест кичик тўпламда findAll кифоя. */
    private List<AuditEvent> eventsOfType(AuditEventType type) {
        return auditRepository.findAll().stream()
                .filter(e -> e.getEventType() == type).toList();
    }

    /** Экран тартиби: янгидан эскига (created_at, кейин UUIDv7 id). */
    private static Pageable firstPage() {
        return PageRequest.of(0, 50,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    /** Транзфер қилиб фаол GL ёзувини қайтаради. */
    private JournalEntry postTransfer() {
        BankTransaction txn = bankService.transfer(new TransferData(bank, cash, DATE,
                new BigDecimal("100000"), null, null, null, "аудит тести"));
        return entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                BankTransactionService.SOURCE_MODULE, txn.getId()).orElseThrow();
    }

    /** Spec 1-банд: transfer post → JE_POSTED (entry_id, doc_number, username). */
    @Test
    void transferPost_writesJePosted() {
        JournalEntry entry = postTransfer();

        List<AuditEvent> posted = eventsOfType(AuditEventType.JE_POSTED);
        assertThat(posted).hasSize(1);
        AuditEvent event = posted.get(0);
        assertThat(event.getEntryId()).isEqualTo(entry.getId());
        assertThat(event.getDocNumber()).isEqualTo(entry.getEntryNumber());
        // Ҳаракат эгаси - жорий auth контекстидаги фойдаланувчи
        assertThat(event.getUsername()).isEqualTo("auditchi");
    }

    /** Spec 2-банд: reverse → JE_REVERSED, details'да асл entry рақами. */
    @Test
    void reverse_writesJeReversed_withOriginalNumber() {
        JournalEntry original = postTransfer();
        bankService.reverse(original.getSourceDocumentId(), DATE, "аудит сторноси");

        List<AuditEvent> reversed = eventsOfType(AuditEventType.JE_REVERSED);
        assertThat(reversed).hasSize(1);
        AuditEvent event = reversed.get(0);
        assertThat(event.getDetails()).contains(original.getEntryNumber());
        // Ёзув сторно entry'сига боғланади (асл эмас) - у POSTED,
        // reversalOf орқали аслга ишора қилади
        JournalEntry storno = entryRepository.findById(event.getEntryId()).orElseThrow();
        assertThat(storno.getReversalOf()).isEqualTo(original);
        assertThat(storno.getStatus()).isEqualTo(EntryStatus.POSTED);
        assertThat(event.getDocNumber()).isEqualTo(storno.getEntryNumber());
        // Сторно JE_POSTED сифатида ҚАЙТА саналмайди - фақат транзфернинг ўзи
        assertThat(eventsOfType(AuditEventType.JE_POSTED)).hasSize(1);
    }

    /** Spec 5-банд: USER_CREATED ва PASSWORD_CHANGED ёзилади. */
    @Test
    void userLifecycle_writesUserEvents_withoutPasswordLeak() {
        AppUser user = userService.create("yangi01", "Аудит Фойдаланувчи",
                UserRole.VIEWER_AUDITOR, "juda-maxfiy-parol");
        userService.changePassword(user.getId(), "yana-maxfiy-parol");

        List<AuditEvent> created = eventsOfType(AuditEventType.USER_CREATED);
        assertThat(created).hasSize(1);
        // username устуни - актор (ким яратди), тафсилотда - ким яратилди
        assertThat(created.get(0).getUsername()).isEqualTo("auditchi");
        assertThat(created.get(0).getDetails()).contains("yangi01");

        List<AuditEvent> changed = eventsOfType(AuditEventType.PASSWORD_CHANGED);
        assertThat(changed).hasSize(1);
        assertThat(changed.get(0).getDetails()).isEqualTo("yangi01");

        // Парол ҳеч бир аудит ёзувига сизмайди (user-management.md қоидаси)
        assertThat(auditRepository.findAll())
                .noneMatch(e -> e.getDetails() != null && e.getDetails().contains("maxfiy"));
    }

    /** Spec 7-банд: event_type ва сана оралиғи филтрлари тўғри кесади. */
    @Test
    void filter_byTypeDateRangeAndUsername() {
        auditLogService.record(AuditEventType.LOGIN_SUCCESS, "birinchi",
                null, null, null, "10.0.0.1");
        auditLogService.record(AuditEventType.LOGIN_FAILURE, "ikkinchi",
                null, null, null, null);
        auditLogService.record(AuditEventType.LOGIN_FAILURE, "uchinchi",
                null, null, null, null);

        // Тур филтри: фақат LOGIN_FAILURE, иккитаси
        Page<AuditEvent> failures = auditLogService.page(
                new AuditFilter(null, null, AuditEventType.LOGIN_FAILURE, null), firstPage());
        assertThat(failures.getContent()).hasSize(2)
                .allMatch(e -> e.getEventType() == AuditEventType.LOGIN_FAILURE);

        // Сана оралиғи: from келажакда - ҳеч нарса тушмайди
        Page<AuditEvent> future = auditLogService.page(
                new AuditFilter(Instant.now().plusSeconds(3600), null, null, null),
                firstPage());
        assertThat(future.getContent()).isEmpty();

        // Оралиқ ичида + username филтри - фақат биттаси
        Page<AuditEvent> inRange = auditLogService.page(
                new AuditFilter(Instant.now().minusSeconds(3600),
                        Instant.now().plusSeconds(3600), null, "birinchi"),
                firstPage());
        assertThat(inRange.getContent()).hasSize(1);
        assertThat(inRange.getContent().get(0).getUsername()).isEqualTo("birinchi");
        assertThat(inRange.getContent().get(0).getIpAddress()).isEqualTo("10.0.0.1");
    }

    /** U-008: username филтри регистрга сезгисиз, ёзув терилганича қолади. */
    @Test
    void filter_usernameCaseInsensitive() {
        // LOGIN_FAILURE'да username терилганича сақланади - «Admin»
        auditLogService.record(AuditEventType.LOGIN_FAILURE, "Admin",
                null, null, "Нотўғри парол билан уриниш", null);

        Page<AuditEvent> found = auditLogService.page(
                new AuditFilter(null, null, null, "admin"), firstPage());
        assertThat(found.getContent()).hasSize(1);
        // Ёзиш томони нормаллаштирилмаган - форензика кўриниши сақланган
        assertThat(found.getContent().get(0).getUsername()).isEqualTo("Admin");
    }
}
