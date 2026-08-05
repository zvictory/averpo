package com.averpo.erp.shared.service;

/**
 * Заводга қайтариш бажарилди - {@code FactoryResetService.reset} ичида
 * TRUNCATE'дан ДАРҲОЛ КЕЙИН эълон қилинади: синхрон listener аудит
 * ёзувини ўша транзакцияда киритади ва у тоза журналнинг БИРИНЧИ ёзуви
 * бўлиб қолади (Arbitr-062, FACTORY_RESET; кейин chart қайта ўрнатилиши
 * CHART_IMPORTED бўлиб иккинчи туради).
 *
 * <p>Payload йўқ - актор username/IP/UA'ни audit ўзи жорий контекстдан
 * олади. Event shared ичида туради, тингловчи audit модулида
 * (CompanySettingsChangedEvent изоҳидаги цикл сабаби).
 *
 * @author Zafar
 */
public record FactoryResetEvent() {
}
