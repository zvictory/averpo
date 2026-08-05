package com.averpo.erp.audit.domain;

/**
 * Аудит ҳодисаси тури (docs/modules/audit-log.md, MVP тўплами).
 *
 * <p>QBO Audit Log услуби: фойдаланувчига ҳодиса тилида кўрсатилади -
 * entity-diff эмас (дизайн қарори spec'да). Экран номлари i18n
 * bundle'да ({@link #titleKey()}). Янги тур қўшиш - аввал spec'га
 * (2-босқич рўйхати), кейин бу enum'га.
 */
public enum AuditEventType {

    /** GL'га проводка ёзилди - PostingService post нуқтасидан. */
    JE_POSTED,

    /** Проводка сторно қилинди - PostingService reverse нуқтасидан. */
    JE_REVERSED,

    /** Муваффақиятли кириш (Spring Security event'идан). */
    LOGIN_SUCCESS,

    /** Хато кириш уриниши (нотўғри парол / қулф даврида / нофаол
     * ҳисоб - сабаб details'да) - username устунида уринилган ном. */
    LOGIN_FAILURE,

    /** Кетма-кет хато уринишлардан кейин ҳисоб қулфланди (BR-USR-009). */
    LOCKOUT,

    /** Янги фойдаланувчи яратилди (UserService). */
    USER_CREATED,

    /** Фойдаланувчи маълумоти/роли/фаоллиги янгиланди (UserService). */
    USER_UPDATED,

    /** Парол алмаштирилди - admin томонидан ёки фойдаланувчи ўзи. */
    PASSWORD_CHANGED,

    /** Компания созламалари ўзгартирилди - details'да фақат ЎЗГАРГАН
     * майдонлар «эски → янги» кўринишида (Arbitr-062 кенгайиши). */
    SETTINGS_CHANGED,

    /** Заводга қайтариш - reset транзакцияси ичида TRUNCATE'дан КЕЙИН
     * ёзилади, тоза журналнинг биринчи ёзуви бўлиб қолади. */
    FACTORY_RESET,

    /** Excel'дан бошланғич import (import-excel.md) - details'да
     * туркумлаб яратилган/ўтказилган сонлар. */
    IMPORT_EXCEL,

    /** Default счётлар режаси импорти - қўлда тугма ва авто-init иккиси
     * (details'да яратилди N, ўтказилди M). */
    CHART_IMPORTED,

    /** Тизимдан чиқиш (Spring Security logout success handler). */
    LOGOUT,

    /** Янги счёт яратилди (AccountService.create - форма/import йўли). */
    ACCOUNT_CREATED,

    /** Счёт таҳрирланди - details'да ном + detail type ва ўзгарган
     * майдонлар (реактивация ҳам шу турда, active диффда кўринади). */
    ACCOUNT_UPDATED,

    /** Счёт нофаол қилинди (update'да active true → false). */
    ACCOUNT_DEACTIVATED,

    /** Плагин ёқилди/ўчирилди (Arbitr-113) - details'да калит ва янги
     * ҳолат; ёзув PluginToggledEvent орқали (shared → audit цикл йўқ). */
    PLUGIN_TOGGLED,

    /** Telegram bot токени янгиланди/ўчирилди (Arbitr-103) - details'да
     * фақат ФАКТ ва бот номи; токеннинг ўзи (маскаланган ҳолда ҳам)
     * ЁЗИЛМАЙДИ (logging.md сир қоидаси). */
    TELEGRAM_TOKEN_CHANGED,

    /** ЦБ авто-курс импорти (ExchangeRateScheduler, cron 10:00/16:00
     * Тошкент) якунланди: муваффақият (N янгиланди / M ўтказилди) ёки
     * хато (сабаб details'да) - фойдаланувчи /audit-log'дан авто
     * янгиланишни кўрсин (Arbitr-164). Actor «Тизим» (фон жараён, auth
     * контексти йўқ). Ёзув ExchangeRateImportedEvent орқали (shared →
     * audit цикл йўқ, PLUGIN_TOGGLED нақши). */
    EXCHANGE_RATE_IMPORTED;

    /** i18n калити: messages*.properties'даги audit.event.JE_POSTED ва ҳ.к. */
    public String titleKey() {
        return "audit.event." + name();
    }
}
