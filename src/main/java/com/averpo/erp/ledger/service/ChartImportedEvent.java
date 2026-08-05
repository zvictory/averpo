package com.averpo.erp.ledger.service;

/**
 * Default счётлар режаси импорт қилинди - {@code AccountService.importDefaultChart}
 * якунида эълон қилинади: қўлда тугма (AccountController) ва авто-init
 * (DefaultChartInitializer, FactoryReset ичидаги қайта ўрнатиш) - учала йўл
 * ҳам шу нуқтадан ўтади (Arbitr-062, CHART_IMPORTED).
 *
 * <p>Event ledger ичида туради, тингловчи audit модулида -
 * {@link JournalEntryPostedEvent} нақши (қоида №6: ledger audit'ни билмайди).
 *
 * @param created нечта счёт яратилди (идемпотент қайта импортда 0)
 * @param skipped нечтаси мавжудлиги учун ўтказилди
 *
 * @author Zafar
 */
public record ChartImportedEvent(int created, int skipped) {
}
