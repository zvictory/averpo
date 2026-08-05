package com.averpo.erp.plugins.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Битта плагиннинг ёқилиш ҳолати (changeset 065, docs/modules/plugins.md).
 *
 * <p>Атайлаб {@code BaseEntity} ЭМАС (лойиҳада ягона истисно шу оилада):
 * PK - табиий калит ({@link PluginKey} номи), плагин учун кўпи билан
 * битта қатор; UUID id/version/created* майдонлар key-value ҳолат
 * қаторига ортиқча (optimistic lock тўқнашувида ҳам «охирги toggle
 * ютади» семантикаси тўғри). Ким/қачон ўзгартиргани {@code updatedAt}/
 * {@code updatedBy}да - тўлиқ из эса аудит журналида (PLUGIN_TOGGLED).
 *
 * <p>Қатор ФАҚАТ биринчи toggle'да туғилади - enum'да бор-у жадвалда
 * йўқ плагин ЎЧИҚ ҳисобланади ({@code PluginService.isEnabled}).
 *
 * @author Zafar
 */
@Entity
@Table(name = "plugin_state")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PluginState {

    /** Плагин калити - {@link PluginKey} enum номи (табиий PK). */
    @Id
    @Column(name = "plugin_key", length = 40)
    private String pluginKey;

    /** Плагин ёқиқми - гейтнинг ягона манба қиймати. */
    @Column(nullable = false)
    private boolean enabled;

    /** Охирги toggle вақти (UTC, темир қоида 12). */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Охирги toggle қилган фойдаланувчи id'си ёки null (auth
     * контекстисиз). FK ЭМАС - dimension (BaseEntity.createdBy нақши):
     * фойдаланувчи ўчирилса ҳолат қатори осилиб қолмайди.
     */
    @Column(name = "updated_by")
    private UUID updatedBy;

    /**
     * Биринчи toggle'да қатор яратиш - калит enum'дан, ҳолат параметрдан.
     * Ягона чақирувчи - {@code plugins.core.service.PluginService} (ҳолат
     * ўзгариши фақат ўша ердан ўтади).
     */
    public PluginState(PluginKey key, boolean enabled, UUID updatedBy) {
        this.pluginKey = key.name();
        this.enabled = enabled;
        this.updatedAt = Instant.now();
        this.updatedBy = updatedBy;
    }

    /** Ҳолатни алмаштиради ва изини (вақт/ким) янгилайди - фақат service чақиради. */
    public void changeEnabled(boolean enabled, UUID updatedBy) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
        this.updatedBy = updatedBy;
    }
}
