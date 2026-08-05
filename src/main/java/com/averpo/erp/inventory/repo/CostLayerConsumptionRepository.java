package com.averpo.erp.inventory.repo;

import com.averpo.erp.inventory.domain.CostLayerConsumption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * FIFO ейилиш изи репозиторийси - фақат inventory модули ичида.
 */
public interface CostLayerConsumptionRepository extends JpaRepository<CostLayerConsumption, UUID> {

    /** Битта чиқим ҳаракатининг ейилиш изи (audit/тест текшируви). */
    List<CostLayerConsumption> findByMovementIdOrderByCreatedAtAsc(UUID movementId);
}
