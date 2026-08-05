package com.averpo.erp.plugins.core;

import com.averpo.erp.audit.domain.AuditEvent;
import com.averpo.erp.audit.domain.AuditEventType;
import com.averpo.erp.audit.repo.AuditEventRepository;
import com.averpo.erp.plugins.core.domain.PluginKey;
import com.averpo.erp.plugins.core.domain.PluginState;
import com.averpo.erp.plugins.core.repo.PluginStateRepository;
import com.averpo.erp.plugins.core.service.PluginService;
import com.averpo.erp.testsupport.WithMockRole;
import com.averpo.erp.web.Plugins;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Плагин гейти тестлари (docs/modules/plugins.md «Тестлар»): isEnabled
 * default/enabled/disabled хулқи, plugin_state қатор lifecycle'и,
 * PLUGIN_TOGGLED аудити (шовқинсиз - фақат ростдан ўзгарганда) ва
 * JTE гейт helper'ининг request'сиз fallback'и. Web қатлам (роль 403,
 * toggle POST) - {@link PluginWebTest}да.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockRole(username = "plgadmin")
class PluginServiceTest {

    @Autowired PluginService pluginService;
    @Autowired PluginStateRepository stateRepository;
    @Autowired AuditEventRepository auditRepository;
    @Autowired JdbcClient jdbcClient;

    /** PLUGIN_TOGGLED ёзувлари (тест кичик тўпламда findAll кифоя - AuditLogTest нақши). */
    private List<AuditEvent> toggleEvents() {
        return auditRepository.findAll().stream()
                .filter(e -> e.getEventType() == AuditEventType.PLUGIN_TOGGLED).toList();
    }

    /** Карта тузоқ 4: жадвалда қатор ЙЎҚ плагин ўчиқ - seed/миграциясиз default. */
    @Test
    void isEnabled_noRow_defaultsToDisabled() {
        assertThat(stateRepository.findById(PluginKey.TELEGRAM.name())).isEmpty();
        assertThat(pluginService.isEnabled(PluginKey.TELEGRAM)).isFalse();
        assertThat(pluginService.enabledKeys()).isEmpty();
    }

    /**
     * Ёқиш: қатор биринчи toggle'да туғилади, isEnabled/enabledKeys дарҳол
     * янги ҳолатни кўради, updated_by жорий фойдаланувчига тўлади
     * (SecurityAuditorAware - BaseEntity @CreatedBy билан бир манба).
     */
    @Test
    void setEnabled_createsRow_gateSeesIt_actorRecorded() {
        // updated_by текшируви учун mock username'га мос реал app_user қатори
        UUID adminId = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO app_user (id, username, password_hash, display_name, role) "
                        + "VALUES (?, 'plgadmin', 'x', 'Плагин админ', 'SUPER_ADMIN')")
                .param(adminId).update();

        pluginService.setEnabled(PluginKey.TELEGRAM, true);

        assertThat(pluginService.isEnabled(PluginKey.TELEGRAM)).isTrue();
        assertThat(pluginService.enabledKeys()).containsExactly(PluginKey.TELEGRAM);
        PluginState state = stateRepository.findById(PluginKey.TELEGRAM.name()).orElseThrow();
        assertThat(state.isEnabled()).isTrue();
        assertThat(state.getUpdatedAt()).isNotNull();
        assertThat(state.getUpdatedBy()).isEqualTo(adminId);
    }

    /**
     * Ўчириш маълумотни ЎЧИРМАЙДИ (спец қарори): қатор қолади, фақат
     * enabled=false - плагин ички созламалари (103'да Telegram токени)
     * қайта ёқилганда тикланади.
     */
    @Test
    void setEnabled_off_keepsRowOnlyFlagChanges() {
        pluginService.setEnabled(PluginKey.TELEGRAM, true);
        pluginService.setEnabled(PluginKey.TELEGRAM, false);

        assertThat(pluginService.isEnabled(PluginKey.TELEGRAM)).isFalse();
        assertThat(stateRepository.findById(PluginKey.TELEGRAM.name()))
                .isPresent()
                .hasValueSatisfying(state -> assertThat(state.isEnabled()).isFalse());
    }

    /**
     * Аудит: ёқиш ва ўчириш PLUGIN_TOGGLED ёзади (details'да калит +
     * ҳолат, username мок фойдаланувчидан); айнан шу қийматга қайта
     * toggle ва «қатор йўқлигида ўчириш» ёзув БЕРМАЙДИ - журнал
     * шовқинланмайди (SETTINGS_CHANGED прецеденти).
     */
    @Test
    void toggle_writesAuditOnlyOnRealChange() {
        // Қатор йўқ + ўчириш = аллақачон ўчиқ - на қатор, на ёзув
        pluginService.setEnabled(PluginKey.TELEGRAM, false);
        assertThat(stateRepository.findById(PluginKey.TELEGRAM.name())).isEmpty();
        assertThat(toggleEvents()).isEmpty();

        pluginService.setEnabled(PluginKey.TELEGRAM, true);
        assertThat(toggleEvents()).hasSize(1);
        AuditEvent enabledEvent = toggleEvents().get(0);
        assertThat(enabledEvent.getUsername()).isEqualTo("plgadmin");
        assertThat(enabledEvent.getDetails()).isEqualTo("TELEGRAM: ёқилди");

        // Айнан шу қийматга қайта toggle - янги ёзув ЙЎҚ
        pluginService.setEnabled(PluginKey.TELEGRAM, true);
        assertThat(toggleEvents()).hasSize(1);

        pluginService.setEnabled(PluginKey.TELEGRAM, false);
        assertThat(toggleEvents()).hasSize(2);
        assertThat(toggleEvents().get(1).getDetails()).isEqualTo("TELEGRAM: ўчирилди");
    }

    /**
     * JTE гейт helper fallback'и: request контекстисиз (тест/фон оқим -
     * login саҳифаси ҳолати билан бир хил) ҳамма плагин ўчиқ кўринади,
     * NPE эмас. Request ичидаги тўлдирилган ҳолат жонли smoke'да
     * текширилади (GlobalModelAttributes web оқимида қўяди).
     */
    @Test
    void pluginsHelper_withoutRequest_allOff() {
        pluginService.setEnabled(PluginKey.TELEGRAM, true);
        assertThat(Plugins.current().on(PluginKey.TELEGRAM)).isFalse();
    }
}
