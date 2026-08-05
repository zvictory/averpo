package com.averpo.erp.plugins.core.domain;

import lombok.Getter;

/**
 * Built-in плагинлар реестри (docs/modules/plugins.md, Arbitr-113).
 *
 * <p>Plugin = код ичида олдиндан ёзилган ИХТИЁРИЙ фича; фойдаланувчи
 * код юкламайди - динамик plugin loader/marketplace атайлаб ЙЎҚ
 * (молиявий ERP'да ихтиёрий кодни ledger ҳуқуқи билан ишга тушириш
 * хавфсизлик риски - спец «Кенгайтириш стратегияси» қарори 2026-07-12).
 * Янги плагин қўшиш = янги enum константа + код; схема ЎЗГАРМАЙДИ
 * (ёқилиш ҳолати plugin_state жадвалида, қатор йўқлиги = ўчиқ).
 *
 * <p>Ёқилиш ҳолатини ФАҚАТ {@code plugins.core.service.PluginService.isEnabled}
 * орқали текширилади - меню, созлама бўлими ва фича коди бир манбадан
 * (Perms.current() нақши, спец «Гейт helper» бўлими).
 */
@Getter
public enum PluginKey {

    /**
     * Telegram интеграцияси - биринчи плагин (Arbitr-103): бот токени
     * созламаси, профиль улаш блоки ва poller. Ёқилганда
     * {@code /settings/telegram} очилади, ўчирилганда ҳаммаси яширин
     * ва poller ухлайди (токен сақланиб қолади).
     */
    TELEGRAM("/settings/telegram");

    /**
     * Плагин созлама саҳифасининг route'и ёки null (созламаси йўқ /
     * ҳали қурилмаган). null бўлмаса /settings/plugins рўйхатида
     * ёқилган плагин ёнида «Созлаш →» ҳаволаси чиқади. TELEGRAM учун
     * 103 бу майдонни {@code /settings/telegram} га тўлдиради - route
     * туғилмасдан линк қўйилса 404 га олиб борарди.
     */
    private final String settingsRoute;

    PluginKey(String settingsRoute) {
        this.settingsRoute = settingsRoute;
    }

    /** Плагин номининг i18n калити (plugin.telegram.name услубида). */
    public String nameKey() {
        return "plugin." + name().toLowerCase() + ".name";
    }

    /** Плагин тавсифининг i18n калити - рўйхатда ном остида чиқади. */
    public String descKey() {
        return "plugin." + name().toLowerCase() + ".desc";
    }
}
