package com.averpo.erp.inventory.repo;

import com.averpo.erp.inventory.domain.StockAdjustment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

/**
 * Ҳужжатли инвентаризация актлари репозиторийси - фақат inventory
 * модули ичида (қоида №6). JpaSpecificationExecutor рўйхат филтрлари
 * учун (омбор/сана оралиғи, ListSpecs нақши).
 *
 * @author Zafar
 */
public interface StockAdjustmentRepository
        extends JpaRepository<StockAdjustment, UUID>,
                JpaSpecificationExecutor<StockAdjustment> {

    /**
     * Кўриш экрани учун - сатрлар ва омбор битта сўровда (open-in-view=
     * false'да lazy шаблонда портлайди). warehouse EAGER бўлса ҳам lines
     * учун граф керак.
     */
    @EntityGraph(attributePaths = {"lines", "warehouse"}, type = EntityGraph.EntityGraphType.LOAD)
    Optional<StockAdjustment> findWithLinesById(UUID id);

    /** Ҳаракатлар филтрида «ҳужжат рақами» кесими учун (ADJ-2026-NNNNN). */
    Optional<StockAdjustment> findByAdjNumber(String adjNumber);
}
