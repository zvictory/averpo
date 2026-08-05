package com.averpo.erp.shared.web;

import org.springframework.data.domain.Sort;

import java.util.Map;

/**
 * Рўйхат устун саралаши ечувчиси (DEC-105б, BA-007 P1b):
 * {@code ?sort=<калит>&dir=asc|desc} параметрларини хавфсиз
 * {@link Sort}'га айлантиради.
 *
 * <p><b>Гигиена</b>: калит → entity property ФАҚАТ чақирувчи берган
 * WHITELIST харитадан олинади - фойдаланувчидан келган хом матн ҳеч
 * қачон {@code Sort.by}'га тушмайди (order-by injection юзаси йўқ).
 * Нотаниш калит/бузуқ қиймат жим default тартибга тушади - саҳифа
 * синмайди (PageSizeResolver фалсафаси).
 *
 * <p>Танланган устун тартибига default (LIST_SORT) tie-breaker бўлиб
 * қўшилади - тенг қийматларда ҳам кетма-кетлик детерминистик қолади
 * (саҳифалараро сатр «сакраши» бўлмасин).
 */
public final class TableSort {

    /**
     * Ечилган саралаш ҳолати.
     *
     * @param sort  тайёр Sort - service/repo'га шу берилади
     * @param key   норм. танланган калит ёки {@code null} (default тартиб) -
     *              шаблон th стрелкаси шунга қарайди
     * @param dir   норм. йўналиш ("asc"/"desc") ёки {@code null}
     * @param query саҳифа/ҳажм линкларига қўшиладиган суффикс
     *              («&sort=..&dir=..» ёки бўш) - filterQuery услубида
     */
    public record Applied(Sort sort, String key, String dir, String query) { }

    private TableSort() {
    }

    /**
     * Хом sort/dir параметрларини whitelist орқали ечади.
     *
     * @param sortKey     {@code ?sort=} хом қиймати (null/нотаниш - default)
     * @param dir         {@code ?dir=} хом қиймати - фақат "asc" кўтарилиш,
     *                    қолган ҳамма нарса (null/бузуқ) desc
     * @param whitelist   калит → entity property харита (рўйхат эгаси
     *                    service'да эълон қилинади)
     * @param defaultSort рўйхатнинг мавжуд LIST_SORT'и - танлов бўлмаса
     *                    ўз ҳолича ишлайди, танланганда tie-breaker
     */
    public static Applied resolve(String sortKey, String dir,
                                  Map<String, String> whitelist, Sort defaultSort) {
        String property = sortKey == null ? null : whitelist.get(sortKey);
        if (property == null) {
            return new Applied(defaultSort, null, null, "");
        }
        boolean asc = "asc".equals(dir);
        Sort chosen = Sort.by(asc ? Sort.Order.asc(property) : Sort.Order.desc(property))
                .and(defaultSort);
        String normalizedDir = asc ? "asc" : "desc";
        // Калит whitelist'дан ўтган - линкка ЭХОси хавфсиз (хом эмас)
        return new Applied(chosen, sortKey, normalizedDir,
                "&sort=" + sortKey + "&dir=" + normalizedDir);
    }
}
