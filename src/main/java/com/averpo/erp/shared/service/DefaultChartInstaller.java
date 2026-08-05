package com.averpo.erp.shared.service;

/**
 * Bundled default счётлар режасини (default-chart) ўрнатиш порти.
 *
 * <p>Боғлиқлик йўналиши ledger -> shared бўлгани учун shared ledger'нинг
 * {@code AccountService}'ига мурожаат қила олмайди. Заводга қайтариш
 * ({@link FactoryResetService}) счётлар жадвалини тозалагач bundled
 * default chart'ни ҚАЙТА ўрнатиши керак - шу интерфейс тескари
 * боғлиқликни ечади ({@link HomeCurrencyLock} нақши): ledger буни bean
 * сифатида имплементация қилади, FactoryResetService интерфейс орқали
 * чақиради. Счётларда фиксирланган seed UUID йўқ (CSV импорт) - шунинг
 * учун бошқа каталоглардек «id NOT IN (seed)» билан тозалаб бўлмайди,
 * TRUNCATE + қайта импорт ягона йўл.
 *
 * @author Zafar
 */
public interface DefaultChartInstaller {

    /**
     * Bundled default chart'ни ўрнатади (idempotent - мавжуд номли счёт
     * ўтказиб юборилади). Заводга қайтаришда счётлар жадвали TRUNCATE
     * қилингач бўш жадвалга тўлиқ 51 счёт (42 postable + 9 гуруҳ ота,
     * кодлари билан) киритилади. Айнан reset транзакцияси ичида
     * чақирилади (битта tx - барчаси ёки ҳеч нарса).
     */
    void installDefaultChart();
}
