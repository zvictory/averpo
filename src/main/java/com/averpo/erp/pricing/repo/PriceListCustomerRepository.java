package com.averpo.erp.pricing.repo;

import com.averpo.erp.pricing.domain.PriceList;
import com.averpo.erp.pricing.domain.PriceListCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Мижоз бириктирувлари репозиторийси.
 *
 * @author Zafar
 */
public interface PriceListCustomerRepository
        extends JpaRepository<PriceListCustomer, UUID> {

    /** Мижознинг бириктируви (глобал unique) - кўчириш/ўчириш учун. */
    Optional<PriceListCustomer> findByCustomerId(UUID customerId);

    /**
     * Мижоз рўйхати валютаси билан БИТТА сўровда - resolvePrice йўли
     * (Beruniy-018): аввал бириктирув, lazy рўйхат ва EAGER валюта учта
     * алоҳида SELECT бўлиб келарди ва ҳар item lookup'ида такрорланарди.
     */
    @Query("""
            select pl from PriceListCustomer plc
            join plc.priceList pl
            join fetch pl.currency
            where plc.customerId = :customerId
            """)
    Optional<PriceList> findPriceListForResolve(@Param("customerId") UUID customerId);

    /** Рўйхатга бириктирилган мижозлар - экран учун. */
    List<PriceListCustomer> findByPriceListIdOrderByCreatedAtAsc(UUID priceListId);
}
