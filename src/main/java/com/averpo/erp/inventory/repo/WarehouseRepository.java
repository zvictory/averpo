package com.averpo.erp.inventory.repo;

import com.averpo.erp.inventory.domain.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Омборлар репозиторийси - фақат inventory модули ичида ишлатилади.
 * JpaSpecificationExecutor - каталог рўйхати филтри учун (DEC-068).
 */
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Warehouse> {

    /** Ном unique текшируви учун (BR-WH-001). */
    Optional<Warehouse> findByName(String name);

    /** Код unique текшируви учун (BR-WH-002). */
    Optional<Warehouse> findByCode(String code);

    /** Формалардаги select учун фаол омборлар. */
    List<Warehouse> findByActiveTrueOrderByName();

    /** Рўйхат экрани - ҳаммаси, ном тартибида. */
    List<Warehouse> findAllByOrderByName();
}
