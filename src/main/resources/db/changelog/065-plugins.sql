--liquibase formatted sql

--changeset averpo:065-01-plugin-state
-- Plugins реестри ҳолати (docs/modules/plugins.md, DEC-113): ҳар
-- built-in ихтиёрий фича (PluginKey enum) учун битта қатор - ёқилганми.
-- PK табиий калит (enum номи): плагин учун кўпи билан битта қатор,
-- UUID id/version'га эҳтиёж йўқ (key-value ҳолат қатори). Қатор ЙЎҚ
-- бўлса плагин ЎЧИҚ ҳисобланади (default) - янги enum қўшилганда
-- схема/seed ўзгармайди. updated_by FK ЭМАС - dimension (created_by
-- нақши): фойдаланувчи ўчирилса ҳолат қатори осилиб қолмайди.
CREATE TABLE plugin_state (
    plugin_key VARCHAR(40) PRIMARY KEY,
    enabled    BOOLEAN NOT NULL DEFAULT false,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by UUID
);
--rollback DROP TABLE plugin_state;
