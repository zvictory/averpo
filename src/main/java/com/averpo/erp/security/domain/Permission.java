package com.averpo.erp.security.domain;

/**
 * Рухсат СОҲАси (docs/modules/user-roles.md). Рухсат рольга эмас,
 * соҳага текширилади - SecurityConfig URL'ларни шу соҳаларга маплайди
 * (UrlPermissionMap), роль эса ҳар соҳада қайси {@link PermissionLevel}
 * олишини {@link RolePermissions} матрицаси айтади.
 *
 * <p>Spring Security authority формати: {@code <СОҲА>_VIEW} /
 * {@code <СОҲА>_EDIT} (масалан {@code SALES_EDIT}) - EDIT доим VIEW'ни
 * ўз ичига олгани учун EDIT даражали фойдаланувчига ИККАЛА authority
 * берилади (hasAuthority текширувлари содда қолсин).
 *
 * <p>Тартиб = спец матрицаси қатор тартиби (RolePermissions.row
 * позицион параметрлари шу тартибга таянади).
 *
 * @author Zafar
 */
public enum Permission {

    /** Мижозлар, invoice, estimate, SR/CM/RR, мижоз тўлови, AR ҳисоботлари. */
    SALES,

    /** Таъминотчилар, PO, bill, expense, VC, landed cost, тўлов, AP ҳисоботлари. */
    PURCHASE,

    /** Товарлар, категория, омбор ҳаракати/каталоги, бирлик ва прайс каталоги. */
    INVENTORY,

    /** Банк транзакциялари, нақд кирим-чиқим, ўтказма, солиштириш. */
    BANKING,

    /** Счётлар режаси, қўлда JournalEntry, POSTED ҳужжат кўриниши. */
    GL,

    /** Ходимлар, иш ҳақи run/тўлов, ведомость. */
    PAYROLL,

    /** Умумий молиявий ҳисоботлар: P&L, Balance Sheet, TB, Cash Flow, солиқ. */
    FIN_REPORTS,

    /** Компания созламалари, солиқ/тўлов усуллари, класслар, валюта, import, reset. */
    SETTINGS,

    /** Фойдаланувчилар, роллар, аудит журнали. */
    USERS
}
