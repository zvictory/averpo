package com.averpo.erp.security.domain;

/**
 * Соҳадаги рухсат даражаси: {@code NONE < VIEW < EDIT}
 * (docs/modules/user-roles.md). EDIT доим VIEW'ни ўз ичига олади -
 * таққослаш ordinal орқали ({@link #atLeast}), шунинг учун эълон
 * тартиби ўзгартирилмайди.
 *
 * @author Zafar
 */
public enum PermissionLevel {

    /** Соҳа умуман кўринмайди - GET ҳам 403. */
    NONE,

    /** Фақат кўриш: GET очиқ, ҳар қандай ёзувчи сўров 403. */
    VIEW,

    /** Тўлиқ: кўриш + яратиш/ўзгартириш (GET+POST). */
    EDIT;

    /** Жорий даража талаб қилинганидан паст эмасми (NONE<VIEW<EDIT занжири). */
    public boolean atLeast(PermissionLevel required) {
        return ordinal() >= required.ordinal();
    }
}
