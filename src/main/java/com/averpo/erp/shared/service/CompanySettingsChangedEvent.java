package com.averpo.erp.shared.service;

/**
 * Компания созламалари ўзгартирилди - {@code CompanySettingsService.update}
 * якунида, фақат камида битта майдон ростдан ўзгарганда эълон қилинади
 * (DEC-062, SETTINGS_CHANGED). Ўзгаришсиз сақлаш event бермайди -
 * журнал шовқинланмайди.
 *
 * <p>Event shared ичида туради, тингловчи audit модулида - ledger
 * JournalEntryPostedEvent нақши: shared audit'ни import қила олмайди
 * (audit ўзи shared BaseEntity'га боғлиқ - цикл чиқарди).
 *
 * @param details фақат ЎЗГАРГАН майдонлар «майдон: эски → янги»
 *                кўринишида, «; » билан бирлашган
 */
public record CompanySettingsChangedEvent(String details) {
}
