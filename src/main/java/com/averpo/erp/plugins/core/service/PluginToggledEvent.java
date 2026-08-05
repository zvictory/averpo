package com.averpo.erp.plugins.core.service;

import com.averpo.erp.plugins.core.domain.PluginKey;

/**
 * Плагин ёқилди/ўчирилди - {@link PluginService#setEnabled} якунида,
 * фақат ҳолат РОСТДАН ўзгарганда эълон қилинади (айнан шу қийматга
 * қайта toggle event бермайди - аудит журнали шовқинланмайди,
 * CompanySettingsChangedEvent нақши).
 *
 * <p>Тингловчи audit модулида (SharedAuditListener → PLUGIN_TOGGLED):
 * plugins audit'ни import қила олмайди - audit ўзи BaseEntity орқали
 * shared'га боғлиқ, тескари йўналиш цикл чиқарарди.
 *
 * @param key     қайси плагин
 * @param enabled янги ҳолат (true - ёқилди, false - ўчирилди)
 *
 * @author Zafar
 */
public record PluginToggledEvent(PluginKey key, boolean enabled) {
}
