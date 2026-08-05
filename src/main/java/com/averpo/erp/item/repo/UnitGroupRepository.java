package com.averpo.erp.item.repo;

import com.averpo.erp.item.domain.UnitGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * UoM гуруҳлари репозиторийси (docs/modules/uom.md).
 */
public interface UnitGroupRepository extends JpaRepository<UnitGroup, UUID> {

    /** Ном unique - валидация учун (BR-UOM-001). */
    Optional<UnitGroup> findByName(String name);

    /** Созламалар экрани учун - ном тартибида. */
    List<UnitGroup> findAllByOrderByName();
}
