package com.averpo.erp.shared.service;

/**
 * Inventory valuation методини қулфлаш порти (HomeCurrencyLock
 * паттерни): 5-босқичда inventory модули буни имплементация қилади -
 * биринчи StockMovement пайдо бўлиши билан метод ўзгартириш ёпилади.
 * Ҳозирча имплементация йўқ → қулф очиқ.
 */
public interface InventoryValuationLock {

    /** {@code true} - valuation методи энди ўзгартирилмайди. */
    boolean locked();
}
