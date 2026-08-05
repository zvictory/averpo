package com.averpo.erp.shared.domain;

/**
 * Inventory баҳолаш методи - компания даражасида танланади
 * (CompanySettings), биринчи омбор ҳаракатидан кейин қулфланади
 * (home currency паттерни): методни ўртада алмаштириш тарихий
 * COGS'ни қайта ҳисоблашни талаб қилар эди.
 *
 * <p>Иккала метод ҳам (item, warehouse) даражасида ишлайди.
 * Имплементация - 5-босқич (inventory модули).
 *
 * @author Zafar
 */
public enum InventoryValuationMethod {

    /** Ўртача тортилган қиймат: newAvg = (qty*avg + inQty*inCost) / (qty+inQty). */
    AVCO,

    /**
     * First-In-First-Out: киримлар cost layer сифатида сақланади
     * (inventory_cost_layer), чиқим энг эски layer'лардан ейилади.
     * QBO Advanced ҳам FIFO ишлатади.
     */
    FIFO;

    /** i18n калити: valuation.AVCO ва ҳ.к. */
    public String titleKey() {
        return "valuation." + name();
    }
}
