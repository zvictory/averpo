package com.averpo.erp.security.domain;

/**
 * Фойдаланувчи роли - соҳага асосланган 8 роллик тизим
 * (docs/modules/user-roles.md, DEC-092). Ҳар роль ортида қатъий
 * permission тўплами туради ({@link RolePermissions} матрицаси) -
 * рухсат рольга эмас, СОҲАга текширилади, шунинг учун янги роль қўшиш
 * SecurityConfig'ни ўзгартирмайди.
 *
 * <p>Эски 3 роль миграцияси (changeset 052): ADMIN→SUPER_ADMIN,
 * ACCOUNTANT→ACCOUNTANT (ном сақланади, ҳуқуқ торайган),
 * VIEWER→VIEWER_AUDITOR. Қийматлар тартиби = user формасидаги select
 * тартиби (кучлидан кучсизга, спец матрица устунлари).
 */
public enum UserRole {

    /**
     * Тўлиқ ҳуқуқ: барча соҳалар EDIT + SETTINGS/USERS фақат унда.
     * QBO аналоги: Primary/Company admin. BR-USR-007: тизимда камида
     * битта фаол SUPER_ADMIN қолиши шарт.
     */
    SUPER_ADMIN,

    /**
     * Бизнес эгаси/директор: ҳамма операцион соҳа фақат КЎРИШ (EXPORT
     * доим), SETTINGS/USERS йўқ. QBO аналоги: Advanced custom role
     * (кенг view-only). Approve оқими АТАЙЛАБ йўқ - QBO ядросида йўқ.
     */
    DIRECTOR_ADMIN,

    /**
     * Бош бухгалтер: бухгалтерия ядроси тўлиқ (GL, PAYROLL, PERIOD_CLOSE
     * имконияти), молиявий ҳисоботлар кўриш. QBO аналоги: Standard user -
     * All access.
     */
    CHIEF_ACCOUNTANT,

    /**
     * Кундалик ҳужжат киритувчи: сотув/харид/банк EDIT, омбор кўриш,
     * лекин GL (қўлда проводка), payroll, молиявий ҳисоботлар ва period
     * close ЙЎҚ - улар CHIEF_ACCOUNTANT иши. QBO аналоги: Advanced
     * custom role. Эски кенг ACCOUNTANT'дан ТОРАЙГАН (миграция изоҳи
     * spec'да).
     */
    ACCOUNTANT,

    /**
     * Сотув менежери: фақат мижозлар ва сотув ҳужжатлари (AR/сотув
     * ҳисоботлари SALES орқали). QBO аналоги: Standard user - Limited:
     * Customers & Sales.
     */
    SALES_MANAGER,

    /**
     * Харид менежери: таъминотчилар ва харид ҳужжатлари EDIT + омбор
     * кўриш (келган товарни кузатади). QBO аналоги: Standard user -
     * Limited: Vendors & Purchases.
     */
    PURCHASE_MANAGER,

    /**
     * Омбор менежери: омбор ҳаракатлари/каталоги EDIT, бошқа соҳа йўқ.
     * QBO'да аналоги ЙЎҚ - рухсат этилган фарқ №1 (multi-warehouse)
     * доирасидаги роль.
     */
    WAREHOUSE_MANAGER,

    /**
     * Аудитор/кузатувчи: ҳамма операцион соҳа фақат кўриш, ҳеч нарса
     * яратмайди/ўзгартирмайди - ҳар қандай ёзувчи сўров SecurityConfig
     * қатламида 403 (эски VIEWER глобал POST-блоки мероси permission
     * моделига сингдирилган). QBO аналоги: Reports only.
     */
    VIEWER_AUDITOR
}
