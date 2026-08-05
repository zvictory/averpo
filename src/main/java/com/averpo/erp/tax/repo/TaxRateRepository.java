package com.averpo.erp.tax.repo;

import com.averpo.erp.tax.domain.TaxRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ҚҚС ставкалари репозиторийси - фақат tax модули ичида.
 *
 * @author Zafar
 */
public interface TaxRateRepository extends JpaRepository<TaxRate, UUID> {

    /** Созламалар экрани учун - код тартибида. */
    List<TaxRate> findAllByOrderByCode();

    /** Ҳужжат формаси select'и учун - фаоллар, код тартибида. */
    List<TaxRate> findByActiveTrueOrderByCode();

    /** BR-TAX-001: код бандлигини текшириш (DB unique ҳам бор). */
    Optional<TaxRate> findByCode(String code);
}
