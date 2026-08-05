package com.averpo.erp.search.service;

/**
 * Глобал қидирувнинг статик экран/ҳисобот реестри
 * (docs/modules/global-search.md «Экранлар/ҳисоботлар»): сайдбар
 * бандлари ва ҳисоботлар - номи (i18n калит) + route + роль.
 *
 * <p>ДИҚҚАТ: бу рўйхат {@code layout/main.jte} сайдбари билан бир
 * манбадан келиб чиқади - у ерга банд қўшилса/ўчирилса шу enum ҳам
 * янгиланиши шарт (иккиси адашмаслиги учун). Route'лар айнан сайдбар
 * {@code href}'лари. {@code adminOnly} бандлар (Созламалар гуруҳи)
 * фақат SUPER_ADMIN'га чиқади - SecurityConfig'даги SETTINGS/USERS соҳа
 * қоидаларининг кўзгуси (user-roles.md: бошқа роллар кўрмайди).
 *
 * @author Zafar
 */
public enum SearchScreen {

    /** Бош саҳифа (dashboard). */
    HOME("nav.home", "/", false),

    // ---- Сотув ----
    /** Мижозлар рўйхати. */
    CUSTOMERS("nav.customers", "/customers", false),
    /** Invoice'лар рўйхати. */
    INVOICES("nav.invoices", "/invoices", false),
    /** Сотув чеклари рўйхати. */
    SALES_RECEIPTS("nav.salesReceipts", "/sales-receipts", false),
    /** Estimate'лар рўйхати. */
    ESTIMATES("nav.estimates", "/estimates", false),
    /** Мижоз тушумлари (invoice-payments). */
    RECEIPTS("nav.receipts", "/invoice-payments", false),
    /** Кредит-ноталар. */
    CREDIT_MEMOS("nav.creditMemos", "/credit-memos", false),
    /** Пул қайтариш чеклари. */
    REFUND_RECEIPTS("nav.refundReceipts", "/refund-receipts", false),
    /** Маҳсулот ва хизматлар. */
    ITEMS("nav.items", "/items", false),

    // ---- Харид ----
    /** Етказувчилар рўйхати. */
    VENDORS("nav.vendors", "/vendors", false),
    /** Bill'лар рўйхати. */
    BILLS("nav.bills", "/bills", false),
    /** Буюртмалар (PO). */
    PURCHASE_ORDERS("nav.purchaseOrders", "/purchase-orders", false),
    /** Vendor тўловлари. */
    PAYMENTS("nav.payments", "/payments", false),
    /** Таъминотчи кредитлари. */
    VENDOR_CREDITS("nav.vendorCredits", "/vendor-credits", false),
    /** Landed cost тақсимоти. */
    LANDED_COSTS("nav.landedCosts", "/landed-costs", false),

    // ---- Банк ----
    /** Банк транзакциялари. */
    BANK_TRANSACTIONS("nav.bankTransactions", "/bank-transactions", false),
    /** Чиқимлар (харажат). */
    EXPENSES("nav.expenses", "/expenses", false),
    /** Ўтказмалар (счётлараро). */
    TRANSFERS("nav.transfers", "/transfers", false),
    /** Reconciliation. */
    RECONCILIATION("nav.reconciliation", "/reconciliation", false),

    // ---- Иш ҳақи ----
    /** Ходимлар рўйхати. */
    EMPLOYEES("nav.employees", "/employees", false),
    /** Иш ҳақи ҳисоблашлари. */
    PAYROLL_RUNS("nav.payrollRuns", "/payroll", false),
    /** Иш ҳақи тўловлари. */
    PAYROLL_PAYMENTS("nav.payrollPayments", "/payroll/payments", false),

    // ---- Бухгалтерия ----
    /** Счётлар режаси. */
    ACCOUNTS("nav.accounts", "/accounts", false),
    /** Проводкалар (журнал). */
    ENTRIES("nav.entries", "/journal-entries", false),

    // ---- Омбор ----
    /** Қолдиқлар. */
    STOCK_BALANCES("nav.stockBalances", "/inventory/balances", false),
    /** Омбор ҳаракатлари. */
    STOCK_MOVEMENTS("nav.stockMovements", "/inventory/movements", false),

    // ---- Ҳисоботлар ----
    /** Баланс. */
    BALANCE_SHEET("nav.balanceSheet", "/reports/balance-sheet", false),
    /** Фойда ва зарар. */
    PROFIT_LOSS("nav.profitLoss", "/reports/profit-loss", false),
    /** P&L йўналишлар кесимида. */
    PL_BY_CLASS("nav.plByClass", "/reports/profit-and-loss-by-class", false),
    /** Айланма қолдиқ. */
    TRIAL_BALANCE("nav.trialBalance", "/reports/trial-balance", false),
    /** Inventory valuation. */
    INV_VALUATION("nav.invValuation", "/reports/inventory-valuation", false),
    /** Кредитор қарзлар (муддатлар). */
    AP_AGING("nav.apAging", "/reports/ap-aging", false),
    /** Дебитор қарзлар (муддатлар). */
    AR_AGING("nav.arAging", "/reports/ar-aging", false),
    /** Кўчирма (Statement). */
    AR_STATEMENT("nav.arStatement", "/reports/statement", false),
    /** Иш ҳақи ведомости. */
    PAYROLL_REGISTER("nav.payrollRegister", "/reports/payroll-register", false),

    // ---- Созламалар (фақат ADMIN) ----
    /** Компания созламалари. */
    SETTINGS("nav.settings", "/settings", true),
    /** Фойдаланувчилар бошқаруви. */
    USERS("nav.users", "/users", true),
    /** Аудит журнали. */
    AUDIT_LOG("nav.auditLog", "/audit-log", true);

    /** i18n калити - жорий тилдаги сарлавҳа шу орқали олинади. */
    private final String messageKey;

    /** Экран манзили (сайдбар href'и билан айнан бир хил). */
    private final String route;

    /** Фақат ADMIN кўрадими (Созламалар гуруҳи) - VIEWER/ACCOUNTANT'дан яширин. */
    private final boolean adminOnly;

    SearchScreen(String messageKey, String route, boolean adminOnly) {
        this.messageKey = messageKey;
        this.route = route;
        this.adminOnly = adminOnly;
    }

    /** i18n калити. */
    public String getMessageKey() {
        return messageKey;
    }

    /** Экран манзили. */
    public String getRoute() {
        return route;
    }

    /** Фақат ADMIN учунми. */
    public boolean isAdminOnly() {
        return adminOnly;
    }
}
