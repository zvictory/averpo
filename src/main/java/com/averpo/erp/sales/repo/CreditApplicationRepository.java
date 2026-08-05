package com.averpo.erp.sales.repo;

import com.averpo.erp.sales.domain.CreditApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Кредит қўллашлари репозиторийси - фақат sales модули ичида.
 */
public interface CreditApplicationRepository extends JpaRepository<CreditApplication, UUID> {

    /** Кредит кўриш экрани - қўлланганлар рўйхати. */
    List<CreditApplication> findByCreditMemoIdOrderByCreatedAtAsc(UUID creditMemoId);

    /** Invoice кўриш экрани - унга қўлланган кредитлар. */
    List<CreditApplication> findByInvoiceIdOrderByCreatedAtAsc(UUID invoiceId);

    /** BR-RET-003: бир (кредит, invoice) жуфтига биттагина қўллаш. */
    boolean existsByCreditMemoIdAndInvoiceId(UUID creditMemoId, UUID invoiceId);
}
