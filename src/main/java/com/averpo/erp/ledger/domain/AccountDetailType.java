package com.averpo.erp.ledger.domain;

import java.util.List;

/**
 * Счётнинг батафсил тури - QBO'даги «Detail Type» билан бир хил.
 *
 * <p>Фойдаланувчи счёт яратганда ФАҚАТ шу қийматни танлайди;
 * {@link AccountType} ва {@link AccountClassification} ундан автоматик
 * келиб чиқади. Тизим счётлари (AR, AP, Undeposited Funds, Exchange
 * Gain/Loss...) кодга эмас, айнан detail type'га қараб топилади -
 * QBO услуби, счёт рақами ихтиёрий бўлгани учун.
 *
 * <p>Экран номлари i18n bundle'да ({@link #titleKey()} - уч тилда).
 * {@code INVENTORY_CLEARING} - Averpo кенгайтмаси (QBO'да йўқ):
 * landed cost ҳужжатлари inventory'га тақсимлангунча суммани тутиб
 * турадиган клиринг счёти.
 *
 * <p>Константа номлари расмий {@code AccountSubTypeEnum} (Finance.xsd,
 * docs/qbo-reference) CamelCase номларининг SNAKE_CASE кўриниши -
 * 4.5-босқичда 1:1 мосликка келтирилган (detail-type-rename-plan.md).
 */
public enum AccountDetailType {

    // ---- BANK ----

    /** Асосий ҳисоб-китоб (checking) счёти. */
    CHECKING(AccountType.BANK),

    /** Жамғарма счёти. */
    SAVINGS(AccountType.BANK),

    /** Пул бозори счёти. */
    MONEY_MARKET(AccountType.BANK),

    /** Нақд пул - касса. */
    CASH_ON_HAND(AccountType.BANK),

    /** Ишончли (траст) счёт. */
    TRUST_ACCOUNTS(AccountType.BANK),

    // ---- ACCOUNTS RECEIVABLE ----

    /** Харидорлар қарзи - AR назорат счёти (валюта бўйича биттадан). */
    ACCOUNTS_RECEIVABLE(AccountType.ACCOUNTS_RECEIVABLE),

    // ---- OTHER CURRENT ASSET ----

    /** Товар-моддий заҳиралар - AVCO қиймати шу ерда туради. */
    INVENTORY(AccountType.OTHER_CURRENT_ASSET),

    /** Қабул қилинган, ҳали банкка топширилмаган тушумлар (QBO услуби). */
    UNDEPOSITED_FUNDS(AccountType.OTHER_CURRENT_ASSET),

    /** Олдиндан тўланган харажатлар. */
    PREPAID_EXPENSES(AccountType.OTHER_CURRENT_ASSET),

    /** Ходимларга берилган нақд аванслар. */
    EMPLOYEE_CASH_ADVANCES(AccountType.OTHER_CURRENT_ASSET),

    /** Учинчи шахсларга берилган қисқа муддатли қарзлар. */
    LOANS_TO_OTHERS(AccountType.OTHER_CURRENT_ASSET),

    /** Шубҳали қарзлар резерви (контра-актив). */
    ALLOWANCE_FOR_BAD_DEBTS(AccountType.OTHER_CURRENT_ASSET),

    /** Averpo кенгайтмаси: landed cost тақсимотигача клиринг. */
    INVENTORY_CLEARING(AccountType.OTHER_CURRENT_ASSET),

    /** Бошқа жорий активлар. */
    OTHER_CURRENT_ASSETS(AccountType.OTHER_CURRENT_ASSET),

    // ---- FIXED ASSET ----

    /** Мебель ва жиҳозлар. */
    FURNITURE_AND_FIXTURES(AccountType.FIXED_ASSET),

    /** Машина ва ускуналар. */
    MACHINERY_AND_EQUIPMENT(AccountType.FIXED_ASSET),

    /** Транспорт воситалари. */
    VEHICLES(AccountType.FIXED_ASSET),

    /** Бинолар. */
    BUILDINGS(AccountType.FIXED_ASSET),

    /** Ер участкалари (амортизация қилинмайди). */
    LAND(AccountType.FIXED_ASSET),

    /** Ижарага олинган объектни яхшилаш харажатлари. */
    LEASEHOLD_IMPROVEMENTS(AccountType.FIXED_ASSET),

    /** Жамғарилган амортизация (контра-актив). */
    ACCUMULATED_DEPRECIATION(AccountType.FIXED_ASSET),

    /**
     * Жамғарилган амортизация - номоддий активлар (контра-актив):
     * AMORTIZATION харажатининг актив томони, ACCUMULATED_DEPRECIATION
     * жуфти (DEC-016 - жуфти чала тур тўлдирилди).
     */
    ACCUMULATED_AMORTIZATION(AccountType.FIXED_ASSET),

    /** Бошқа асосий воситалар. */
    OTHER_FIXED_ASSETS(AccountType.FIXED_ASSET),

    // ---- OTHER ASSET ----

    /** Гудвилл. */
    GOODWILL(AccountType.OTHER_ASSET),

    /**
     * Номоддий активлар (GOODWILL'дан бошқа: патент, лицензия, товар
     * белгиси) - шусиз AMORTIZATION бошланган сиёсатни жойлаштирадиган
     * актив тури йўқ эди (DEC-016).
     */
    INTANGIBLE_ASSETS(AccountType.OTHER_ASSET),

    /**
     * Бошқа (узоқ муддатли) активлар жамғарилган амортизацияси
     * (контра-актив). Ном айнан 40 белги - detail_type VARCHAR(40)
     * чегарасига сиғади (003-ledger.sql), Finance.xsd расмий номи.
     */
    ACCUMULATED_AMORTIZATION_OF_OTHER_ASSETS(AccountType.OTHER_ASSET),

    /** Берилган кафолат депозитлари. */
    SECURITY_DEPOSITS(AccountType.OTHER_ASSET),

    /** Ташкил этиш харажатлари. */
    ORGANIZATIONAL_COSTS(AccountType.OTHER_ASSET),

    /** Бошқа узоқ муддатли активлар. */
    OTHER_LONG_TERM_ASSETS(AccountType.OTHER_ASSET),

    // ---- ACCOUNTS PAYABLE ----

    /** Мол етказиб берувчиларга қарз - AP назорат счёти. */
    ACCOUNTS_PAYABLE(AccountType.ACCOUNTS_PAYABLE),

    // ---- CREDIT CARD ----

    /** Кредит карта счёти. */
    CREDIT_CARD(AccountType.CREDIT_CARD),

    // ---- OTHER CURRENT LIABILITY ----

    /** ҚҚС/сотув солиғи мажбурияти. */
    SALES_TAX_PAYABLE(AccountType.OTHER_CURRENT_LIABILITY),

    /** Иш ҳақи клиринги - ҳисобланган иш ҳақи тўлангунча туради. */
    PAYROLL_CLEARING(AccountType.OTHER_CURRENT_LIABILITY),

    /** Иш ҳақидан ушланадиган солиқлар мажбурияти. */
    PAYROLL_TAX_PAYABLE(AccountType.OTHER_CURRENT_LIABILITY),

    /** Даромад солиғи мажбурияти. */
    INCOME_TAX_PAYABLE(AccountType.OTHER_CURRENT_LIABILITY),

    /** Кредит линияси. */
    LINE_OF_CREDIT(AccountType.OTHER_CURRENT_LIABILITY),

    /** Қисқа муддатли кредит. */
    LOAN_PAYABLE(AccountType.OTHER_CURRENT_LIABILITY),

    /** Бошқа жорий мажбуриятлар; олинган аванслар (deferred revenue)
     * ҳам шу ерда - расмий enum'да алоҳида тури йўқ (QBO услуби). */
    OTHER_CURRENT_LIABILITIES(AccountType.OTHER_CURRENT_LIABILITY),

    // ---- LONG TERM LIABILITY ----

    /** Узоқ муддатли векселлар/кредитлар. */
    NOTES_PAYABLE(AccountType.LONG_TERM_LIABILITY),

    /** Таъсисчилардан олинган қарзлар. */
    SHAREHOLDER_NOTES_PAYABLE(AccountType.LONG_TERM_LIABILITY),

    /** Бошқа узоқ муддатли мажбуриятлар. */
    OTHER_LONG_TERM_LIABILITIES(AccountType.LONG_TERM_LIABILITY),

    // ---- EQUITY ----

    /** Очилиш қолдиқлари учун тизим счёти (QBO услуби). */
    OPENING_BALANCE_EQUITY(AccountType.EQUITY),

    /** Тақсимланмаган фойда - йил ёпилишида тизим ишлатади. */
    RETAINED_EARNINGS(AccountType.EQUITY),

    /** Таъсисчи капитали. */
    OWNERS_EQUITY(AccountType.EQUITY),

    /** Шериклар киритган капитал. */
    PARTNER_CONTRIBUTIONS(AccountType.EQUITY),

    /** Шерикларга тақсимотлар. */
    PARTNER_DISTRIBUTIONS(AccountType.EQUITY),

    /** Устав капитали (акциядорлик). */
    COMMON_STOCK(AccountType.EQUITY),

    /** Қўшимча киритилган капитал. */
    PAID_IN_CAPITAL_OR_SURPLUS(AccountType.EQUITY),

    // ---- INCOME ----

    /** Товар сотишдан даромад. */
    SALES_OF_PRODUCT_INCOME(AccountType.INCOME),

    /** Хизмат кўрсатишдан даромад. */
    SERVICE_FEE_INCOME(AccountType.INCOME),

    /** Берилган чегирма ва қайтаришлар (контра-даромад). */
    DISCOUNTS_REFUNDS_GIVEN(AccountType.INCOME),

    /** Бошқа асосий даромад. */
    OTHER_PRIMARY_INCOME(AccountType.INCOME),

    /** Ҳужжатга боғланмаган тўловлар учун QBO тизим счёти. */
    UNAPPLIED_CASH_PAYMENT_INCOME(AccountType.INCOME),

    // ---- OTHER INCOME ----

    /** Фоиз даромади. */
    INTEREST_EARNED(AccountType.OTHER_INCOME),

    /** Дивиденд даромади. */
    DIVIDEND_INCOME(AccountType.OTHER_INCOME),

    /** Бошқа турли даромадлар. */
    OTHER_MISCELLANEOUS_INCOME(AccountType.OTHER_INCOME),

    // ---- COST OF GOODS SOLD ----

    /** Сотилган товар/материал таннархи - inventory чиқими шу ерга. */
    SUPPLIES_MATERIALS_COGS(AccountType.COST_OF_GOODS_SOLD),

    /** Сотув билан боғлиқ ташиш харажатлари (таннархга). */
    SHIPPING_FREIGHT_DELIVERY_COS(AccountType.COST_OF_GOODS_SOLD),

    /** Хизмат таннархидаги меҳнат ҳақи. */
    COST_OF_LABOR_COS(AccountType.COST_OF_GOODS_SOLD),

    /** Таннархдаги ускуна ижараси. */
    EQUIPMENT_RENTAL_COS(AccountType.COST_OF_GOODS_SOLD),

    /** Хизматнинг бошқа таннарх харажатлари; inventory камомади ҳам шу ерга. */
    OTHER_COSTS_OF_SERVICE_COS(AccountType.COST_OF_GOODS_SOLD),

    // ---- EXPENSE ----

    /** Реклама ва маркетинг. */
    ADVERTISING_PROMOTIONAL(AccountType.EXPENSE),

    /**
     * Умидсиз (шубҳали) қарзлар харажати - ALLOWANCE_FOR_BAD_DEBTS
     * захираси билан жуфт: захира проводкаси Дт шу счёт / Кт захира
     * (DEC-016 - жуфти чала тур тўлдирилди).
     */
    BAD_DEBTS(AccountType.EXPENSE),

    /** Банк хизматлари. */
    BANK_CHARGES(AccountType.EXPENSE),

    /** Аъзолик ва обуналар. */
    DUES_SUBSCRIPTIONS(AccountType.EXPENSE),

    /** Ускуна ижараси (операцион). */
    EQUIPMENT_RENTAL(AccountType.EXPENSE),

    /** Суғурта. */
    INSURANCE(AccountType.EXPENSE),

    /** Тўланган фоизлар. */
    INTEREST_PAID(AccountType.EXPENSE),

    /** Юридик ва профессионал хизматлар. */
    LEGAL_PROFESSIONAL_FEES(AccountType.EXPENSE),

    /** Офис ва умумий маъмурий харажатлар. */
    OFFICE_GENERAL_ADMINISTRATIVE_EXPENSES(AccountType.EXPENSE),

    /** Иш ҳақи харажатлари. */
    PAYROLL_EXPENSES(AccountType.EXPENSE),

    /** Бино ижараси. */
    RENT_OR_LEASE_OF_BUILDINGS(AccountType.EXPENSE),

    /** Таъмирлаш ва техник хизмат. */
    REPAIR_MAINTENANCE(AccountType.EXPENSE),

    /** Ташиш ва етказиб бериш (операцион). */
    SHIPPING_FREIGHT_DELIVERY(AccountType.EXPENSE),

    /** Сарф материаллари. */
    SUPPLIES_MATERIALS(AccountType.EXPENSE),

    /** Тўланган солиқлар. */
    TAXES_PAID(AccountType.EXPENSE),

    /** Хизмат сафари. */
    TRAVEL(AccountType.EXPENSE),

    /** Сафар овқатланиши. */
    TRAVEL_MEALS(AccountType.EXPENSE),

    /**
     * Ҳужжатга боғланмаган vendor тўловлари учун QBO тизим счёти -
     * UNAPPLIED_CASH_PAYMENT_INCOME'нинг AP томондаги жуфти
     * (DEC-016). Оқими кейинги босқич иши - ҳозирча фақат каталогда.
     */
    UNAPPLIED_CASH_BILL_PAYMENT_EXPENSE(AccountType.EXPENSE),

    /** Коммунал хизматлар. */
    UTILITIES(AccountType.EXPENSE),

    /** Бошқа операцион харажатлар. */
    OTHER_MISCELLANEOUS_SERVICE_COST(AccountType.EXPENSE),

    // ---- OTHER EXPENSE ----

    /** Амортизация харажати (асосий воситалар). */
    DEPRECIATION(AccountType.OTHER_EXPENSE),

    /** Амортизация харажати (номоддий активлар). */
    AMORTIZATION(AccountType.OTHER_EXPENSE),

    /** Валюта курси фарқи - тизим счёти (фойда кредит, зарар дебет). */
    EXCHANGE_GAIN_OR_LOSS(AccountType.OTHER_EXPENSE),

    /** Жарима ва келишувлар. */
    PENALTIES_SETTLEMENTS(AccountType.OTHER_EXPENSE),

    /** Бошқа турли харажатлар. */
    OTHER_MISCELLANEOUS_EXPENSE(AccountType.OTHER_EXPENSE);

    /** Шу detail type қайси {@link AccountType}'га тегишли. */
    private final AccountType type;

    AccountDetailType(AccountType type) {
        this.type = type;
    }

    /** Тегишли счёт тури. */
    public AccountType getType() { return type; }

    /** Тегишли фундаментал синф - тур орқали келиб чиқади. */
    public AccountClassification getClassification() { return type.getClassification(); }

    /**
     * Тизим-бошқарув назорат счёти белгиси: бу detail type'даги счётга
     * ёзувни фақат ўз subledger хизмати (Invoice/Bill, inventory, landed
     * cost, deposit оқими, йил ёпилиши) киритади. Қўлда ўтказма (Transfer)
     * бундай счётга рухсат этилса GL қолдиғи subledger'дан (AR/AP aging,
     * StockMovement valuation) ажралиб кетади - шунинг учун транзфер
     * гарови (BR-TXF-002) ва счёт select'лари шу белгига таянади
     * (IFRS-008; QBO Transfer UI ҳам тизим счётларини кўрсатмайди).
     *
     * <p>{@code SALES_TAX_PAYABLE} атайлаб КИРМАЙДИ: алоҳида tax payment
     * оқими ҳали йўқ, ҚҚС тўлови ҳозирча айнан transfer/expense орқали
     * қилинади - киритилса солиқни тўлашнинг бирдан-бир йўли ёпилиб
     * қоларди. Tax payment оқими қурилганда қайта кўрилади.
     *
     * <p>{@code PAYROLL_CLEARING} КИРАДИ: ҳисобланган иш ҳақи фақат
     * PayrollPayment орқали тўланади (AR/AP услуби) - транзфер/қўлда банк
     * сатрида танланса ходим кесимидаги субледжер қолдиғи GL'дан ажралиб
     * кетарди (payroll.md). {@code PAYROLL_TAX_PAYABLE} эса КИРМАЙДИ:
     * солиқ тўлови мавжуд Чиқим (Expense) орқали - SALES_TAX_PAYABLE
     * прецеденти.
     */
    public boolean systemManaged() {
        return switch (this) {
            case ACCOUNTS_RECEIVABLE, ACCOUNTS_PAYABLE, INVENTORY,
                 INVENTORY_CLEARING, UNDEPOSITED_FUNDS, PAYROLL_CLEARING,
                 OPENING_BALANCE_EQUITY, RETAINED_EARNINGS -> true;
            default -> false;
        };
    }

    /** i18n калити: messages*.properties'даги account.detail.CHECKING ва ҳ.к. */
    public String titleKey() {
        return "account.detail." + name();
    }

    /** Берилган турга тегишли detail type'лар - UI select'ни гуруҳлаш учун. */
    public static List<AccountDetailType> forType(AccountType type) {
        return java.util.Arrays.stream(values())
                .filter(d -> d.type == type)
                .toList();
    }
}
