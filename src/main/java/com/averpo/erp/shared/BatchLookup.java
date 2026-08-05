package com.averpo.erp.shared;

import com.averpo.erp.shared.domain.BaseEntity;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Сатр-циклдаги N+1 lookup'ларга қарши батч нақшининг умумий қисми
 * (Arbitr-045 findAllById, Sanjar-003).
 *
 * <p>Ҳужжат service'лари сатрлардан id'ларни олдиндан йиғиб битта IN
 * сўров билан Map тайёрлайди, циклда {@code Map.get()} ишлатади - ҳар
 * service ўз private helper'ини такрорламаслиги учун бу икки қадам
 * shared'га чиқарилган (entity турига боғланмаган).
 *
 * <p>Топилмаган id Map'да бўлмайди - бу батч API'ларнинг умумий
 * шартномаси ({@code findAllById} throw қилмайди); чақирувчи
 * мавжудликни ўз сатр хатоси (BR/NotFound) билан текширади, шунда
 * аввалги {@code get()} хулқи айнан сақланади.
 */
public final class BatchLookup {

    private BatchLookup() { }

    /**
     * Сатрлардан null бўлмаган id'ларни ягона тўпламга йиғади - бир
     * id неча марта такрорланса ҳам IN сўровга бир марта киради.
     *
     * @param rows ҳужжат сатрлари (ёки бошқа манба қатори)
     * @param idOf сатрдан id ажратгич; null қайтарса сатр ташланади
     */
    public static <R> Set<UUID> ids(Collection<R> rows, Function<R, UUID> idOf) {
        Set<UUID> ids = new HashSet<>();
        for (R row : rows) {
            UUID id = idOf.apply(row);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** Юкланган entity'ларни id калити билан Map'га жойлайди. */
    public static <T extends BaseEntity> Map<UUID, T> byId(Iterable<T> entities) {
        Map<UUID, T> byId = new HashMap<>();
        for (T entity : entities) {
            byId.put(entity.getId(), entity);
        }
        return byId;
    }
}
