package com.averpo.erp.shared.repo;

import com.averpo.erp.shared.domain.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Тўлов усуллари репозиторийси - ташқарига фақат PaymentMethodService орқали.
 */
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {

    /** Созламалар экрани - ном тартибида (нофаоллар ҳам). */
    List<PaymentMethod> findAllByOrderByName();

    /** Ҳужжат формаси select'и - фақат фаоллар. */
    List<PaymentMethod> findByActiveTrueOrderByName();
}
