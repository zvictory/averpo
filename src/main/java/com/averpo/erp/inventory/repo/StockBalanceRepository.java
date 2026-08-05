package com.averpo.erp.inventory.repo;

import com.averpo.erp.inventory.domain.StockBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Қолдиқлар репозиторийси - фақат inventory модули ичида.
 *
 * @author Zafar
 */
public interface StockBalanceRepository extends JpaRepository<StockBalance, UUID> {

    /** Битта (омбор, item) қолдиғи - ҳаракатлар шу ёзувни янгилайди. */
    Optional<StockBalance> findByWarehouseIdAndItemId(UUID warehouseId, UUID itemId);

    /** Item'нинг барча омборлардаги қолдиқлари. */
    List<StockBalance> findByItemId(UUID itemId);

    /** Омбор кесимидаги қолдиқлар (қолдиқлар экрани). */
    List<StockBalance> findByWarehouseId(UUID warehouseId);
}
