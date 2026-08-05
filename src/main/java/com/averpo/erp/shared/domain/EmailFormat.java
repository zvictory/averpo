package com.averpo.erp.shared.domain;

import java.util.regex.Pattern;

/**
 * Оддий email формат текшируви (Arbitr-101/112) - профиль (BR-USR-013)
 * ва компания (BR-SET-007) email майдонлари учун умумий. Тўлиқ RFC
 * ЭМАС, қўпол хатоларни ушлайди: {@code local@domain.tld} - бўшлиқ ёки
 * иккинчи {@code @} йўқ, домен нуқтаси шарт (ContactService'даги EMAIL
 * нақшининг айнан ўзи - шу regex учта жойда такрорланмаслиги учун
 * shared helper'га чиқарилди).
 *
 * <p>Static util (янги entity/service ЭМАС) - фақат такрорланувчи
 * валидация битта манбада.
 */
public final class EmailFormat {

    /** {@code local@domain.tld} - соддалаштирилган, қўпол хатоларни ушлайди. */
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private EmailFormat() {
    }

    /**
     * Қиймат тўғри форматдами. {@code null} ёки бўш - {@code false};
     * «бўш бўлса ихтиёрий, ўтади» қарорини чақирувчи ўзи қабул қилади
     * (майдон nullable бўлса аввал бўшлигини текширади).
     */
    public static boolean isValid(String email) {
        return email != null && EMAIL.matcher(email).matches();
    }
}
