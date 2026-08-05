package com.averpo.erp.ledger.domain;

/**
 * Счёт тури - QBO'даги «Account Type» билан бир хил 15 та қиймат.
 *
 * <p>Balance Sheet'да жойлашувни (current/fixed, short/long term) ва
 * P&amp;L тузилмасини (Income → COGS → Expense → Other) айнан шу тур
 * белгилайди. Ҳар тур ўз {@link AccountClassification}'ига боғланган -
 * фойдаланувчи {@link AccountDetailType} танлаганда иккиси ҳам
 * автоматик аниқланади. Экран номлари i18n bundle'да ({@link #titleKey()}).
 *
 * @author Zafar
 */
public enum AccountType {

    /** Банк ва касса счётлари - пул айланадиган жой. */
    BANK(AccountClassification.ASSET),

    /** Харидорлар қарзи - AR назорат тури. */
    ACCOUNTS_RECEIVABLE(AccountClassification.ASSET),

    /** Бир йил ичида пулга айланадиган бошқа активлар. */
    OTHER_CURRENT_ASSET(AccountClassification.ASSET),

    /** Асосий воситалар - узоқ муддатли моддий активлар. */
    FIXED_ASSET(AccountClassification.ASSET),

    /** Узоқ муддатли номоддий/бошқа активлар. */
    OTHER_ASSET(AccountClassification.ASSET),

    /** Мол етказиб берувчиларга қарз - AP назорат тури. */
    ACCOUNTS_PAYABLE(AccountClassification.LIABILITY),

    /** Кредит карта мажбуриятлари. */
    CREDIT_CARD(AccountClassification.LIABILITY),

    /** Бир йил ичида тўланадиган бошқа мажбуриятлар. */
    OTHER_CURRENT_LIABILITY(AccountClassification.LIABILITY),

    /** Бир йилдан узоқ мажбуриятлар (кредитлар). */
    LONG_TERM_LIABILITY(AccountClassification.LIABILITY),

    /** Капитал счётлари. */
    EQUITY(AccountClassification.EQUITY),

    /** Асосий фаолият даромади. */
    INCOME(AccountClassification.REVENUE),

    /** Асосий фаолиятдан ташқари даромад (фоиз, дивиденд...). */
    OTHER_INCOME(AccountClassification.REVENUE),

    /** Сотилган товар/хизмат таннархи. */
    COST_OF_GOODS_SOLD(AccountClassification.EXPENSE),

    /** Операцион харажатлар. */
    EXPENSE(AccountClassification.EXPENSE),

    /** Операциядан ташқари харажат (амортизация, курс фарқи...). */
    OTHER_EXPENSE(AccountClassification.EXPENSE);

    /** Шу турга мос фундаментал синф. */
    private final AccountClassification classification;

    AccountType(AccountClassification classification) {
        this.classification = classification;
    }

    /** Тур қайси фундаментал синфга тегишли. */
    public AccountClassification getClassification() { return classification; }

    /** i18n калити: messages*.properties'даги account.type.BANK ва ҳ.к. */
    public String titleKey() {
        return "account.type." + name();
    }

    /**
     * Счёт ўз валютасида юритиладими - фақат валютага боғланган турлар
     * {@code Account.currency} қабул қилади: банк, дебиторлик (AR),
     * кредиторлик (AP), кредит карта (QBO услуби). Қолган турлар доим home
     * валютада (currency null) - BR-COA-011 буни мажбурлайди. Бу ЯГОНА
     * манба: {@link com.averpo.erp.ledger.service.AccountService}
     * валидацияси ва форма x-show иккиси шундан ўқийди - дубль йўқ.
     *
     * <p>Эслатма: Finance.xsd Account.CurrencyRef "Product: ALL" бўлса-да,
     * QBO UI валютани айнан шу тўрт турга беради.
     */
    public boolean isCurrencyDenominated() {
        return switch (this) {
            case BANK, ACCOUNTS_RECEIVABLE, ACCOUNTS_PAYABLE, CREDIT_CARD -> true;
            default -> false;
        };
    }
}
