package com.averpo.erp.item.domain;

/**
 * Товар/хизмат тури - QBO Products and Services типларига мос.
 * Bundle кейинги босқичларда.
 *
 * @author Zafar
 */
public enum ItemType {

    /** Омборда сақланадиган товар - StockBalance/AVCO'га киради (5-босқич). */
    INVENTORY,

    /** Сақланмайдиган товар - қолдиқ юритилмайди. */
    NON_INVENTORY,

    /** Хизмат. */
    SERVICE;

    /** i18n калити: item.type.INVENTORY ва ҳ.к. */
    public String titleKey() {
        return "item.type." + name();
    }
}
