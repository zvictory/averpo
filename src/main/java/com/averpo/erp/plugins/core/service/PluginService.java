package com.averpo.erp.plugins.core.service;

import com.averpo.erp.plugins.core.domain.PluginKey;
import com.averpo.erp.plugins.core.domain.PluginState;
import com.averpo.erp.plugins.core.repo.PluginStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Плагин гейтининг ЯГОНА манбаси (docs/modules/plugins.md, DEC-113):
 * меню кўриниши, созлама бўлими ва фича коди (масалан Telegram poller,
 * 103) ҳаммаси {@link #isEnabled} дан сўрайди - Perms.current() нақши,
 * UI яшириш билан server ҳақиқати ажралмайди (092 сабоғи: backend route
 * ҳам шу гейт билан ёпилади).
 *
 * <p>Атайлаб кросс-request КЭШ ЙЎҚ («кэшли ёки енгил сўров» - карта
 * тузоқ 2 танлови): жадвал митти (плагин сони = enum константалари),
 * PK ўқиш арзон; static кэш эса @Transactional тест rollback'ида ва
 * factory reset TRUNCATE'ида stale қолиб флейки берарди. Web қатламда
 * request бошига битта {@code enabledKeys()} ўқиш bor - request ичи
 * «кэш» шу (web.Plugins request attribute).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PluginService {

    /** Ҳолат қаторлари - фақат шу service орқали ёзилади/ўқилади. */
    private final PluginStateRepository repository;

    /**
     * Toggle изи (updated_by) учун жорий фойдаланувчи id'си -
     * BaseEntity @CreatedBy билан бир хил манба (SecurityAuditorAware),
     * лекин бу entity BaseEntity эмас - қўлда ёзилади.
     */
    private final AuditorAware<UUID> auditorAware;

    /**
     * Аудит event'и учун (PLUGIN_TOGGLED): shared audit'ни import қила
     * олмайди (цикл) - CompanySettingsService нақшидаги event йўли.
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Плагин ёқиқми - барча гейт нуқталарининг ягона саволи. Қатор ЙЎҚ
     * плагин ЎЧИҚ (default, карта тузоқ 4): enum'га янги плагин
     * қўшилганда seed/миграция талаб қилинмайди.
     */
    @Transactional(readOnly = true)
    public boolean isEnabled(PluginKey key) {
        return repository.findById(key.name())
                .map(PluginState::isEnabled)
                .orElse(false);
    }

    /**
     * Ёқилган плагинлар тўплами - web қатлам request бошида БИР марта
     * ўқийди (web.Plugins request attribute, JTE меню/созлама гейти),
     * ҳар меню банди учун алоҳида сўров кетмайди.
     */
    @Transactional(readOnly = true)
    public Set<PluginKey> enabledKeys() {
        Set<PluginKey> keys = EnumSet.noneOf(PluginKey.class);
        for (PluginState state : repository.findAll()) {
            if (!state.isEnabled()) {
                continue;
            }
            // Жадвалда бор-у enum'дан ўчирилган калит жимгина ташлаб
            // юборилади - эски қатор саҳифани йиқитмасин
            for (PluginKey key : PluginKey.values()) {
                if (key.name().equals(state.getPluginKey())) {
                    keys.add(key);
                }
            }
        }
        return keys;
    }

    /**
     * Плагинни ёқади/ўчиради (фақат SUPER_ADMIN - route гарови
     * SecurityConfig'да, /settings/** SETTINGS соҳаси). Қатор биринчи
     * toggle'да туғилади; ҳолат РОСТДАН ўзгарсагина ёзилади ва
     * {@link PluginToggledEvent} эълон қилинади (аудит PLUGIN_TOGGLED) -
     * айнан шу қийматга қайта toggle шовқин бермайди.
     *
     * <p>Ўчириш плагин МАЪЛУМОТИНИ ўчирмайди (спец қарори: Telegram
     * токени сақланади, фақат фича яширинади) - фақат enabled байроғи.
     */
    public void setEnabled(PluginKey key, boolean enabled) {
        UUID actor = auditorAware.getCurrentAuditor().orElse(null);
        PluginState state = repository.findById(key.name()).orElse(null);
        if (state == null) {
            if (!enabled) {
                return; // қатор йўқ = аллақачон ўчиқ - ёзмаймиз ҳам, event ҳам йўқ
            }
            repository.save(new PluginState(key, true, actor));
        } else {
            if (state.isEnabled() == enabled) {
                return; // ўзгармаган toggle - журнал шовқинланмайди
            }
            state.changeEnabled(enabled, actor);
        }
        eventPublisher.publishEvent(new PluginToggledEvent(key, enabled));
    }
}
