package com.averpo.erp.search.service;

import java.util.List;

/**
 * Глобал қидирувнинг гуруҳланган натижаси (docs/modules/global-search.md
 * «Кўлам»): ҳар манба - алоҳида рўйхат, ҳар бирида кўпи билан 5 та.
 * Гуруҳлар тартиби QBO Navigate услубида қатъий: ҳужжат → контакт →
 * товар → счёт → экран.
 *
 * @param documents ҳужжатлар (рақам бўйича) - invoice/bill/JE ва ҳ.к.
 * @param contacts  мижоз/таъминотчи/ходим (ном/компания бўйича)
 * @param items     товар/хизмат (ном/SKU бўйича)
 * @param accounts  счётлар режаси (ном/код бўйича)
 * @param screens   экран/ҳисобот реестри (жорий тил + роль филтри)
 */
public record SearchResults(List<SearchHit> documents, List<SearchHit> contacts,
                            List<SearchHit> items, List<SearchHit> accounts,
                            List<SearchHit> screens) {

    /** Сўров жуда қисқа ёки ҳеч нарса топилмаган ҳолат учун бўш натижа. */
    public static SearchResults empty() {
        return new SearchResults(List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /** Бирор гуруҳда натижа борми - «Ҳеч нарса топилмади» ни ҳал қилади. */
    public boolean isEmpty() {
        return documents.isEmpty() && contacts.isEmpty() && items.isEmpty()
                && accounts.isEmpty() && screens.isEmpty();
    }

    /** Барча гуруҳлардаги натижалар умумий сони (смоук/тест текшируви учун). */
    public int total() {
        return documents.size() + contacts.size() + items.size()
                + accounts.size() + screens.size();
    }
}
