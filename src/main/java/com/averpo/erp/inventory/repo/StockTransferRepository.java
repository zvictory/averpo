package com.averpo.erp.inventory.repo;

import com.averpo.erp.inventory.domain.StockTransfer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

/**
 * Ҳужжатли омборлараро кўчириш актлари репозиторийси - фақат inventory
 * модули ичида (қоида №6). JpaSpecificationExecutor рўйхат филтрлари
 * учун (омбор/сана оралиғи, ListSpecs нақши).
 */
public interface StockTransferRepository
        extends JpaRepository<StockTransfer, UUID>,
                JpaSpecificationExecutor<StockTransfer> {

    /**
     * Кўриш экрани учун - сатрлар ва иккала омбор битта сўровда
     * (open-in-view=false лозими).
     */
    @EntityGraph(attributePaths = {"lines", "fromWarehouse", "toWarehouse"},
            type = EntityGraph.EntityGraphType.LOAD)
    Optional<StockTransfer> findWithLinesById(UUID id);

    /** Ҳаракатлар филтрида «ҳужжат рақами» кесими учун (WTR-2026-NNNNN). */
    Optional<StockTransfer> findByWtrNumber(String wtrNumber);
}
