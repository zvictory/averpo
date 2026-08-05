package com.averpo.erp.shared.repo;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Рўйхат филтрлари учун умумий Specification бўлаклари (Arbitr-068,
 * docs/modules/list-filters.md). Ҳар модул ЎЗ repo'сида
 * JpaSpecificationExecutor'ни очиб, ЎЗ service'ида шу бўлаклардан
 * спецификация йиғади - класс модуллараро чегарани бузмайди (қоида 6:
 * фақат майдон номи + қиймат олади, бирор repository'га тегмайди).
 * Услуб audit-log филтри (AuditLogService.page) нақшига мос.
 *
 * <p>Ҳар бўлак қиймат бўш бўлса {@code null} predicate қайтаради -
 * Spring Data комбинацияда буни «чеклов йўқ» деб ўтказиб юборади,
 * шунга чақирувчи томонда if-меҳнати керак эмас.
 *
 * @author Zafar
 */
public final class ListSpecs {

    /** Утилита класс - объект яратилмайди. */
    private ListSpecs() {
    }

    /** Сана майдони ≥ from (null - қуйидан чекланмаган). */
    public static <T> Specification<T> dateFrom(String field, LocalDate from) {
        return (root, query, cb) -> from == null ? null
                : cb.greaterThanOrEqualTo(root.get(field), from);
    }

    /** Сана майдони ≤ to - «Гача» куни ТЎЛИҚ киради (null - чекланмаган). */
    public static <T> Specification<T> dateTo(String field, LocalDate to) {
        return (root, query, cb) -> to == null ? null
                : cb.lessThanOrEqualTo(root.get(field), to);
    }

    /** Майдон қиймати айнан тенг (статус/контакт/тур); null - филтр йўқ. */
    public static <T> Specification<T> eq(String field, Object value) {
        return (root, query, cb) -> value == null ? null
                : cb.equal(root.get(field), value);
    }

    /**
     * Матн contains - катта-кичик фарқсиз, КИРИЛЛ ҳам (spec талаби):
     * намуна Java {@code toLowerCase()} билан, устун Postgres
     * {@code lower()} билан кичрайтирилади - иккиси ҳам кирилни тўғри
     * folds қилади (dev/тест базада текширилган), семантика ILIKE билан
     * бир хил. Бир нечта майдон OR билан (рақам ЁКИ изоҳ); null устун
     * қиймати мос келмайди (NULL LIKE → false) - хатосиз.
     */
    public static <T> Specification<T> textContains(String q, String... fields) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return null;
            }
            String pattern = "%" + q.strip().toLowerCase() + "%";
            List<Predicate> ors = new ArrayList<>(fields.length);
            for (String field : fields) {
                ors.add(cb.like(cb.lower(root.get(field)), pattern));
            }
            return cb.or(ors.toArray(Predicate[]::new));
        };
    }
}
