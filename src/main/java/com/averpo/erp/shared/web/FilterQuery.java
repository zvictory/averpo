package com.averpo.erp.shared.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Саҳифалаш линклари учун филтр query-string йиғувчи (Arbitr-068,
 * audit-log контроллеридаги filterQuery нақшининг умумлашгани):
 * бўш қийматлар ташлаб кетилади, қийматлар URL-encode қилинади,
 * натижа «&name=value&...» кўринишида - «page» параметрини
 * shared/pagination.jte линкнинг ўзи қўшади.
 */
public final class FilterQuery {

    /** Йиғилаётган query («&» билан бошланувчи жуфтликлар). */
    private final StringBuilder query = new StringBuilder();

    /** Бўш бўлмаган матнни қўшади; null/бланк индамай ташланади. */
    public FilterQuery add(String name, String value) {
        if (value != null && !value.isBlank()) {
            query.append('&').append(name).append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
        return this;
    }

    /** UUID/enum/сана каби қийматни toString билан қўшади (null - ташланади). */
    public FilterQuery add(String name, Object value) {
        return add(name, value == null ? null : value.toString());
    }

    /** Тайёр query string («&...» ёки бўш) - JTE'га шу берилади. */
    @Override
    public String toString() {
        return query.toString();
    }
}
