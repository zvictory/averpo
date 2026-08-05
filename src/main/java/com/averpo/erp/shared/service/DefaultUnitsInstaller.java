package com.averpo.erp.shared.service;

/**
 * Стандарт UOM гуруҳларини (default units) ўрнатиш порти (Arbitr-147) -
 * {@link DefaultChartInstaller} кўзгуси.
 *
 * <p>Боғлиқлик йўналиши item -> shared бўлгани учун shared item'нинг
 * {@code UnitService}'ига мурожаат қила олмайди. Заводга қайтариш
 * ({@link FactoryResetService}) бирлик каталогини seed ҳолатига
 * келтиргач стандарт гуруҳларни ҚАЙТА ўрнатиши керак - шу интерфейс
 * тескари боғлиқликни ечади ({@link DefaultChartInstaller} нақши): item
 * буни bean сифатида имплементация қилади ({@code UnitService}),
 * FactoryResetService интерфейс орқали чақиради.
 */
public interface DefaultUnitsInstaller {

    /**
     * Стандарт UOM гуруҳларини ўрнатади - гуруҳ НОМИ бўйича idempotent
     * (мавжуд номли гуруҳ ўтказиб юборилади, фойдаланувчи ўзгартиргани
     * бузилмайди). Мавжуд seed бирликлар (дона, кг, литр, метр, соат)
     * ном бўйича тегишли гуруҳга ютилади (дубликат яратилмайди).
     * Заводга қайтаришда бирлик каталоги seed ҳолатига келгач айнан ўша
     * транзакцияда чақирилади.
     */
    void installDefaultUnits();
}
