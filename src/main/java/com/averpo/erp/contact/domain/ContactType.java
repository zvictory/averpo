package com.averpo.erp.contact.domain;

/**
 * Контакт тури - QBO'даги Customers, Vendors ва Employees рўйхатларига
 * мос. Ном намespace'и ягона: бир display name фақат биттасида бўлади.
 */
public enum ContactType {

    /** Харидор - AR (Invoice) томонда ишлатилади. */
    CUSTOMER,

    /** Мол етказиб берувчи - AP (Bill) томонда ишлатилади. */
    VENDOR,

    /**
     * Ходим - Payroll (Иш ҳақи) модули. Мавжуд contact инфратузилмаси
     * қайта ишлатилади (карточка, фаоллик, JE contact dimension); ходим
     * кесимидаги иш ҳақи қолдиғи PAYROLL_CLEARING субледжеридан ўқилади
     * (AR/AP услуби). QBO'да ҳам Employee - core name-list.
     */
    EMPLOYEE;

    /** i18n калити: contact.type.CUSTOMER ва ҳ.к. */
    public String titleKey() {
        return "contact.type." + name();
    }
}
