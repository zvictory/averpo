package com.averpo.erp.shared;

import com.averpo.erp.shared.domain.DocumentType;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.repo.DocumentSequenceRepository;
import com.averpo.erp.shared.service.DocumentSequenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Мажбурий тестлар рўйхати: docs/modules/document-sequence.md → «Тестлар».
 * Транзакцион тестлар rollback билан изоляцияланган; parallel тест
 * атайлаб commit қилади (PAYMENT рақамлари олдинга юради - test база
 * ҳар прогонда нолдан қурилади, муаммо эмас).
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
class DocumentSequenceServiceTest {

    /** Тест ҳужжатлар санаси - рақамдаги йил шундан. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 6);

    @Autowired DocumentSequenceService service;
    @Autowired DocumentSequenceRepository repository;
    @Autowired PlatformTransactionManager txManager;

    @Test
    @Transactional
    void next_formatsPrefixYearAndPadding() {
        // Seed'дан кейин INVOICE ҳали ишлатилмаган - биринчи рақам 00001
        assertThat(service.next(DocumentType.INVOICE, DATE))
                .isEqualTo("INV-2026-00001");
        assertThat(service.next(DocumentType.INVOICE, DATE))
                .isEqualTo("INV-2026-00002");
    }

    @Test
    @Transactional
    void next_numberContinuesAcrossYears() {
        // Йил алмашганда рақам reset бўлмайди (QBO услуби) - фақат
        // кўрсатиладиган йил ўзгаради, кетма-кетлик давом этади
        assertThat(service.next(DocumentType.BILL, DATE))
                .isEqualTo("BILL-2026-00001");
        assertThat(service.next(DocumentType.BILL, LocalDate.of(2027, 1, 15)))
                .isEqualTo("BILL-2027-00002");
    }

    @Test
    @Transactional
    void next_missingSeedRow_throwsBrSeq001() {
        // Seed қатори йўқолган ҳолат (deploy хатоси) - аниқ BR код
        repository.delete(repository.lockByDocumentType(DocumentType.PAYMENT).orElseThrow());
        repository.flush();

        assertThatThrownBy(() -> service.next(DocumentType.PAYMENT, DATE))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-SEQ-001"));
    }

    @Test
    void next_withoutTransaction_failsFast() {
        // MANDATORY: рақам ҳужжат транзакцияси ичида олиниши шарт -
        // транзакциясиз чақириш дастурчи хатоси сифатида дарҳол йиқилади
        assertThatThrownBy(() -> service.next(DocumentType.INVOICE, DATE))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void next_parallelTransactions_noDuplicates() throws Exception {
        // Row lock тести: 8 та parallel транзакция бир турга рақам сўраса
        // ҳаммаси ҳар хил рақам олиши шарт (PESSIMISTIC_WRITE навбати)
        TransactionTemplate tx = new TransactionTemplate(txManager);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> tasks = java.util.Collections.nCopies(8,
                    (Callable<String>) () -> tx.execute(status ->
                            service.next(DocumentType.PAYMENT, DATE)));
            Set<String> numbers = new HashSet<>();
            for (Future<String> future : executor.invokeAll(tasks)) {
                numbers.add(future.get());
            }
            assertThat(numbers).hasSize(8);
            assertThat(numbers).allMatch(n -> n.matches("PAY-2026-\\d{5}"));
        } finally {
            executor.shutdown();
        }
    }
}
