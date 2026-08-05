package com.averpo.erp.pricing.repo;

import com.averpo.erp.pricing.domain.PriceList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Нарх рўйхатлари репозиторийси.
 */
public interface PriceListRepository extends JpaRepository<PriceList, UUID> {

    /** Ном unique - валидация учун (BR-PL-001). */
    Optional<PriceList> findByName(String name);

    /** Default номзод (биттагина - partial unique) - алмашув/валидация учун. */
    Optional<PriceList> findByDefaultListTrue();

    /**
     * Default рўйхат валютаси билан БИТТА сўровда - resolvePrice йўли
     * (PERF-018): derived query'да EAGER валюта алоҳида SELECT
     * бўлиб келарди.
     */
    @Query("""
            select pl from PriceList pl
            join fetch pl.currency
            where pl.defaultList = true
            """)
    Optional<PriceList> findDefaultForResolve();

    /** Созламалар экрани учун - ном тартибида. */
    List<PriceList> findAllByOrderByName();
}
