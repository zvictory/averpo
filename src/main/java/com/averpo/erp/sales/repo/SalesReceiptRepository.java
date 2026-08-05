package com.averpo.erp.sales.repo;

import com.averpo.erp.sales.domain.SalesReceipt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Сотув чеклари репозиторийси - фақат sales модули ичида.
 * JpaSpecificationExecutor - рўйхат филтри учун (Arbitr-068).
 *
 * @author Zafar
 */
public interface SalesReceiptRepository extends JpaRepository<SalesReceipt, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<SalesReceipt> {

    /** Кўриш учун - сатрлари билан (open-in-view=false, lazy йўқ). */
    @EntityGraph(attributePaths = {"lines"}, type = EntityGraph.EntityGraphType.LOAD)
    Optional<SalesReceipt> findWithLinesById(UUID id);
}
