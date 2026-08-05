package com.averpo.erp.plugins.core.repo;

import com.averpo.erp.plugins.core.domain.PluginState;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Плагин ҳолати қаторлари - ФАҚАТ {@code plugins.core.service.PluginService}
 * ишлатади (модуллараро мурожаат қоидаси: бошқа модуллар service орқали
 * киради). Id - {@code PluginKey} enum номи (табиий PK, PluginState изоҳи).
 *
 * @author Zafar
 */
public interface PluginStateRepository extends JpaRepository<PluginState, String> {
}
