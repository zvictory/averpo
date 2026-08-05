package com.averpo.erp.security.domain;

/**
 * Фойдаланувчи жинси (DEC-101, профиль шахсий майдони,
 * docs/modules/user-profile.md). {@code null} = «кўрсатилмаган» (устун
 * nullable) - тўлдириш мажбурий эмас, соф фойдаланувчи ихтиёри. Экранда
 * i18n калити орқали таржима қилиб кўрсатилади ({@link #titleKey}) -
 * enum номи (MALE/FEMALE) DB'да сақланади, кўрсатиш тилга боғланмайди.
 */
public enum Gender {

    /** Эркак. */
    MALE,

    /** Аёл. */
    FEMALE;

    /**
     * messages калити - экранда таржима қилинган ном
     * ({@code gender.male} / {@code gender.female}).
     * InventoryValuationMethod.titleKey() нақши.
     */
    public String titleKey() {
        return "gender." + name().toLowerCase();
    }
}
