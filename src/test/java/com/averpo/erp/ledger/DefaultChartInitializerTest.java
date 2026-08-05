package com.averpo.erp.ledger;

import com.averpo.erp.ledger.config.DefaultChartInitializer;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Arbitr-059: янги (бўш) ўрнатишда default chart автоматик юкланиши.
 *
 * <p>{@link DefaultChartInitializer} тест профилида {@code @Profile("!test")}
 * туфайли КЎТАРИЛМАЙДИ (акс ҳолда ҳар контекстда 51 счёт commit бўлиб
 * умумий базани ифлослар - initializer JavaDoc'ига қаранг). Шунинг учун
 * bean'ни қўлда қуриб, ҳақиқий {@link AccountService} билан {@code run()}
 * чақирамиз - логика @Transactional ичида (rollback билан) синалади.
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DefaultChartInitializerTest {

    @Autowired AccountService accountService;
    @Autowired AccountRepository accountRepository;

    /** Бўш база: initializer 51 счётли (42 postable + 9 гуруҳ) chart'ни юклайди. */
    @Test
    void emptyDatabase_installsDefaultChart() {
        // Тест базаси ҳар транзакцион тестда бўш стартлайди (AccountImportTest
        // skipped==0 ҳам шунга таянади) - initializer тестда ёқилмаган
        assertThat(accountService.isEmpty()).isTrue();

        new DefaultChartInitializer(accountService).run(null);

        assertThat(accountRepository.count()).isEqualTo(51);
        // BR-LED-021 сабабчиси йўқолди: тизим счётлари энди топилади
        assertThat(accountRepository.findByName("Касса")).isPresent();
    }

    /** Тўла база: initializer ЖИМ ўтади - счётлар қайта юкланмайди (count==0 гарови). */
    @Test
    void nonEmptyDatabase_leavesChartUntouched() {
        accountService.importDefaultChart();
        long before = accountRepository.count();
        assertThat(before).isEqualTo(51);
        assertThat(accountService.isEmpty()).isFalse();

        new DefaultChartInitializer(accountService).run(null);

        // Гейт count==0'да тўхтатди - иккиланиш/қайта юклаш йўқ, айнан ўша 51
        assertThat(accountRepository.count()).isEqualTo(before);
    }
}
