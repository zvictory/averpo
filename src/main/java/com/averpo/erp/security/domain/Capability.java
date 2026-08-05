package com.averpo.erp.security.domain;

/**
 * Соҳа даражасидан ТАШҚАРИ алоҳида boolean имкониятлар
 * (docs/modules/user-roles.md). Authority сифатида enum номи ўзи
 * берилади (масалан {@code PERIOD_CLOSE}) - соҳа_VIEW/_EDIT қолипидан
 * атайлаб фарқли, чунки булар даражасиз ёқиқ/ўчиқ байроқлар.
 */
public enum Capability {

    /**
     * Давр ёпилиш санасини очиш/ёпиш. GL'дан АТАЙЛАБ ажратилган:
     * CHIEF_ACCOUNTANT'да бор, оддий ACCOUNTANT'да йўқ (spec «Тестлар»
     * 3-банди). Endpoint: /settings/closing-date.
     */
    PERIOD_CLOSE,

    /**
     * Ҳисоботни export қилиш. Ҳозирча export endpoint йўқ - имконият
     * матрица тўлиқлиги учун олдиндан эълон қилинган (spec матрицаси);
     * VIEWER_AUDITOR'да «ихтиёрий» - v1 қатъий тўпламда БЕРИЛМАЙДИ
     * (кам ҳуқуқ - хавфсиз томон), 2-босқич per-user созлашда очилади.
     */
    EXPORT
}
