package com.averpo.erp.ledger.domain;

/**
 * Счётнинг фундаментал синфи - баланс/натижа ҳисоботларида йўналишни
 * белгилайди. QBO'даги «Classification» билан бир хил.
 *
 * <p>Фойдаланувчи буни танламайди: {@link AccountDetailType} →
 * {@link AccountType} → classification занжиридан автоматик келиб чиқади.
 * Экран номлари i18n bundle'да ({@link #titleKey()}).
 */
public enum AccountClassification {

    /** Актив - дебет қолдиқли (пул, дебиторлик, inventory...). */
    ASSET,

    /** Мажбурият - кредит қолдиқли (кредиторлик, қарзлар...). */
    LIABILITY,

    /** Капитал - таъсисчилар улуши, тақсимланмаган фойда. */
    EQUITY,

    /** Даромад - P&amp;L'нинг кирим томони. */
    REVENUE,

    /** Харажат - P&amp;L'нинг чиқим томони (COGS ҳам шу ерда). */
    EXPENSE;

    /** i18n калити: messages*.properties'даги classification.ASSET ва ҳ.к. */
    public String titleKey() {
        return "classification." + name();
    }

    /**
     * Инглизча ном - i18n локализациядан ФАРҚли, доим english (CoA тур
     * badge'и: фойдаланувчи талаби: «badge'да classification фақат
     * english»). Ҳар синф битта сўз, шунга enum name'ни Capitalize кифоя
     * (ASSET → Asset, LIABILITY → Liability ва ҳ.к.).
     */
    public String englishName() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }

    /**
     * Balance Sheet классификацияси (Актив/Мажбурият/Капитал) - QBO
     * «Must be a Balance Sheet account». Транзфер (docs/modules/transfer.md)
     * айнан шундай счётлар орасида бўлади; Даромад/Харажат кирмайди.
     */
    public boolean isBalanceSheet() {
        return this == ASSET || this == LIABILITY || this == EQUITY;
    }
}
