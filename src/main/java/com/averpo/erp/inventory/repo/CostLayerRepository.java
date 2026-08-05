package com.averpo.erp.inventory.repo;

import com.averpo.erp.inventory.domain.CostLayer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * FIFO партиялари репозиторийси - фақат inventory модули ичида.
 */
public interface CostLayerRepository extends JpaRepository<CostLayer, UUID> {

    /**
     * Ейилмаган партиялар FIFO тартибида: received_date, кейин id
     * (UUIDv7 - вақт тартибини сақлайди). idx_cost_layer_next partial
     * index айнан шу қидирувга мос.
     */
    List<CostLayer> findByWarehouseIdAndItemIdAndExhaustedFalseOrderByReceivedDateAscIdAsc(
            UUID warehouseId, UUID itemId);

    /** Кирим ҳаракати яратган партия - reverseReceive айнан шуни ўчиради. */
    Optional<CostLayer> findBySourceMovementId(UUID sourceMovementId);
}
