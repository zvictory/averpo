package com.averpo.erp.item.repo;

import com.averpo.erp.item.domain.ItemCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Категориялар репозиторийси.
 */
public interface ItemCategoryRepository extends JpaRepository<ItemCategory, UUID> {

    /** Ном unique - валидация учун. */
    Optional<ItemCategory> findByName(String name);

    /** Дарахт қуриш учун - ном тартибида. */
    List<ItemCategory> findAllByOrderByName();
}
