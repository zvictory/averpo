package com.averpo.erp.search.service;

import com.averpo.erp.i18n.Msg;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Экран/ҳисобот реестри бўйича қидирув (docs/modules/global-search.md).
 * {@link SearchScreen} рўйхатини жорий тилдаги сарлавҳа бўйича филтрлайди
 * («баланс» деб ёзса Balance Sheet чиқади) ва роль (adminOnly) бўйича
 * чеклайди. DB'га тегмайди - соф статик реестр.
 *
 * @author Zafar
 */
@Component
@RequiredArgsConstructor
public class ScreenRegistry {

    /** Ҳар гуруҳ каби экранлар ҳам кўпи билан 5 та қайтади. */
    private static final int LIMIT = 5;

    /** Сарлавҳани жорий тилда олиш учун i18n кўприги. */
    private final Msg msg;

    /**
     * Сарлавҳасида {@code query} substring'и бор экранларни қайтаради
     * (регистрсиз, жорий тил). {@code isAdmin=false} бўлса adminOnly
     * бандлар (Созламалар) чиқмайди. Кўпи билан {@value #LIMIT} та.
     *
     * @param query   нормалланган сўров (бўш эмас деб фараз қилинади)
     * @param isAdmin фойдаланувчи ADMIN'ми - реестр роль филтри
     */
    public List<SearchHit> search(String query, boolean isAdmin) {
        String needle = query.toLowerCase(Locale.ROOT);
        List<SearchHit> hits = new ArrayList<>();
        for (SearchScreen screen : SearchScreen.values()) {
            if (screen.isAdminOnly() && !isAdmin) {
                continue;
            }
            String label = msg.lookup(screen.getMessageKey());
            if (label.toLowerCase(Locale.ROOT).contains(needle)) {
                hits.add(new SearchHit(label, null, screen.getRoute()));
                if (hits.size() >= LIMIT) {
                    break;
                }
            }
        }
        return hits;
    }
}
