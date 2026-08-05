package com.averpo.erp.audit;

import com.averpo.erp.audit.repo.AuditEventRepository;
import com.averpo.erp.ledger.domain.JournalEntry;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.repo.JournalEntryRepository;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.ledger.service.JournalEntryRequest;
import com.averpo.erp.ledger.service.PostingService;
import com.averpo.erp.shared.domain.Money;
import com.averpo.erp.shared.service.CompanySettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Rollback исботи (docs/modules/audit-log.md «Тестлар» 3-банд):
 * аудит listener'и СИНХРОН, ўша транзакцияда ёзади - post'дан кейин
 * exception чиқса на JE, на аудит ёзуви қолади. Журнал фақат ҳақиқатан
 * содир бўлган ишни акс эттиради.
 *
 * <p>Класс АТАЙЛАБ @Transactional эмас: rollback'ни тест framework'и
 * эмас, кодда очилган TransactionTemplate қилади - ҳамма ёзув (chart
 * import ҳам) ўша битта транзакция ичида бўлгани учун база тоза қолади.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuditRollbackTest {

    @Autowired PlatformTransactionManager transactionManager;
    @Autowired PostingService postingService;
    @Autowired AccountService accountService;
    @Autowired AccountRepository accountRepository;
    @Autowired JournalEntryRepository entryRepository;
    @Autowired AuditEventRepository auditRepository;
    @Autowired CompanySettingsService settingsService;

    @Test
    void exceptionAfterPost_leavesNeitherJeNorAuditRow() {
        AtomicReference<UUID> entryId = new AtomicReference<>();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            accountService.importDefaultChart();
            String home = settingsService.homeCurrency();
            UUID bank = accountRepository.findByName("Банк ҳисобварағи").orElseThrow().getId();
            UUID cash = accountRepository.findByName("Касса").orElseThrow().getId();

            JournalEntry entry = postingService.createAndPost(JournalEntryRequest.manual(
                    LocalDate.of(2026, 7, 7), "rollback исботи", List.of(
                            JournalEntryRequest.Line.debit(cash,
                                    Money.ofBase(new BigDecimal("1000"), home), null),
                            JournalEntryRequest.Line.credit(bank,
                                    Money.ofBase(new BigDecimal("1000"), home), null))));
            entryId.set(entry.getId());
            // Синхрон listener: аудит ёзуви ШУ транзакция ичида кўринади
            assertThat(auditRepository.existsByEntryId(entry.getId())).isTrue();

            throw new IllegalStateException("атайлаб rollback");
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("атайлаб");

        // Rollback'дан кейин: на JE, на аудит ёзуви (иккиси бирга йўқолди)
        assertThat(entryRepository.findById(entryId.get())).isEmpty();
        assertThat(auditRepository.existsByEntryId(entryId.get())).isFalse();
    }
}
