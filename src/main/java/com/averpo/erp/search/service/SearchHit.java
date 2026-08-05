package com.averpo.erp.search.service;

/**
 * Битта глобал қидирув натижаси - dropdown ва /search саҳифасидаги
 * ягона сатр (docs/modules/global-search.md).
 *
 * <p>Соф кўрсатиш DTO'си: барча форматлаш (пул + валюта коди, сана)
 * {@link GlobalSearchService}'да тайёрланади, шаблон фақат чиқаради.
 *
 * @param label    асосий матн - ҳужжат рақами, контакт/товар/счёт номи
 *                 ёки экран сарлавҳаси
 * @param sublabel иккиламчи қатор (контакт + сана + сумма ва ҳ.к.) ёки
 *                 null (экранларда қўшимча йўқ)
 * @param url      бир босишда ўтиладиган ички манзил (кўриш/таҳрир экрани)
 *
 * @author Zafar
 */
public record SearchHit(String label, String sublabel, String url) {
}
