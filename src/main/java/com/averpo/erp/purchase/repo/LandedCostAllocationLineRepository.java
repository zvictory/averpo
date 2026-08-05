package com.averpo.erp.purchase.repo;

import com.averpo.erp.purchase.domain.LandedCostAllocationLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Landed cost тақсимот қаторлари репозиторийси - purchase модули ичида.
 */
public interface LandedCostAllocationLineRepository
        extends JpaRepository<LandedCostAllocationLine, UUID> {

    /** Ҳужжат қаторлари (кўриш экрани ва reverse учун). */
    List<LandedCostAllocationLine> findByAllocationIdOrderByCreatedAtAsc(UUID allocationId);

    /** Receipt'га берилган ҳолатдаги тақсимот борми (BR-BILL-012 гарови). */
    boolean existsByMovementIdAndAllocationStatus(
            UUID movementId, com.averpo.erp.purchase.domain.LandedCostAllocation.Status status);

    /**
     * Санагача ёзилган тақсимот қаторлари (inventory valuation порти) -
     * EntityGraph LOAD: allocation'нинг status/reversalDate'си
     * шаблонсиз, лекин lazy'сиз керак.
     */
    @org.springframework.data.jpa.repository.EntityGraph(
            attributePaths = "allocation",
            type = org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD)
    List<LandedCostAllocationLine> findByAllocationAllocationDateLessThanEqual(
            java.time.LocalDate date);
}
