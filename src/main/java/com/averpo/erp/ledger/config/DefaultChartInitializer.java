package com.averpo.erp.ledger.config;

import com.averpo.erp.ledger.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Янги (бўш) ўрнатишда bundled default счётлар режасини автоматик юклайди
 * (Arbitr-059). Илова кўтарилганда account жадвали БУТУНЛАЙ бўш бўлса
 * 51 счётли default chart ўрнатилади - шусиз фойдаланувчи биринчи
 * ҳужжатда BR-LED-021 «тизим счёти топилмади»га урилади (жонли серверда
 * шундай бўлди). QBO'да ҳам компания яратилганда chart тайёр келади.
 * {@code AdminUserInitializer} қолипи (ApplicationRunner + бўшлик гарови).
 *
 * <p>ФАҚАТ {@code count==0} да ишлайди: мавжуд база (dev/жонли, ёки
 * қисман ўчирилган chart) ТЕГИЛМАЙДИ. {@code /accounts} экранидаги қўлда
 * «import-default» тугмаси ҳам қолади (қисман ўчирилган chart'ни тиклаш
 * учун фойдали).
 *
 * <p><b>Қатлам (ТЕМИР ҚОИДА №6):</b> ledger'нинг ЎЗ {@link AccountService}'ини
 * тўғридан ишлатади (idempotent {@code importDefaultChart}). {@link
 * com.averpo.erp.shared.service.DefaultChartInstaller} порти shared
 * -> ledger ТЕСКАРИ боғлиқлик учун мавжуд (FactoryResetService shared'да,
 * AccountService'ни компиляцияда кўра олмайди); бу initializer ledger
 * ичида бўлгани учун портга ҳожат йўқ - тўғридан-тўғри чақириш тозароқ.
 *
 * <p><b>{@code @Profile("!test")}:</b> тест профилида АТАЙЛАБ ишламайди.
 * ApplicationRunner тест транзакциясидан ТАШҚАРИ, контекст кўтарилишида
 * ишлайди - тестда ёқилса ҳар контекстда 51 счёт commit бўлиб умумий
 * тест базасини ифлослар ва @BeforeEach {@code importDefaultChart}
 * қиладиган ~40 тестнинг «бўш-старт» инвариантини (масалан AccountImportTest
 * {@code skipped==0}) бузарди. Логиканинг ўзи DefaultChartInitializerTest'да
 * bean қўлда қуриб, ҳақиқий AccountService билан текширилади.
 *
 * @author Zafar
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class DefaultChartInitializer implements ApplicationRunner {

    /** Счётлар режаси public API'си - бўшлик гарови + idempotent юклаш. */
    private final AccountService accountService;

    /** Бўш база бўлса default chart'ни юклайди; акс ҳолда индамай ўтади. */
    @Override
    public void run(ApplicationArguments args) {
        if (!accountService.isEmpty()) {
            return; // мавжуд база - тегилмайди (idempotent шарти: count==0)
        }
        AccountService.ImportResult result = accountService.importDefaultChart();
        log.info("Янги ўрнатиш: default счётлар режаси автоматик юкланди ({} счёт)",
                result.created());
    }
}
