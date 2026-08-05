package com.averpo.erp.contact.domain;

/**
 * Контакт манзилининг тури (old-erp-ideas §3 дан олинган рўйхат).
 * Ҳар турда контакт учун биттагина default манзил бўлади
 * (ux_contact_address_default partial unique).
 */
public enum AddressType {

    /** Тўлов/ҳисоб-китоб манзили - invoice'га чиқади. */
    BILLING,

    /** Етказиб бериш манзили - асосан customer учун. */
    SHIPPING,

    /** Юридик манзил - шартнома ва расмий ҳужжатлар учун. */
    LEGAL;

    /** i18n сарлавҳа калити - UI select/жадвалда шу орқали кўрсатилади. */
    public String titleKey() { return "contact.address.type." + name(); }
}
