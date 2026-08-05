package com.averpo.erp.shared.repo;

import com.averpo.erp.shared.domain.PaymentTerm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Тўлов шартлари каталоги репозиторийси.
 */
public interface PaymentTermRepository extends JpaRepository<PaymentTerm, UUID> {

    /** Контакт/ҳужжат формаларидаги select учун - фаоллар, кун тартибида. */
    List<PaymentTerm> findByActiveTrueOrderByDays();
}
