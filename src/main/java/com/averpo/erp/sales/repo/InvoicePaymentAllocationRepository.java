package com.averpo.erp.sales.repo;

import com.averpo.erp.sales.domain.InvoicePaymentAllocation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Тушум тақсимоти репозиторийси - фақат sales модули ичида.
 */
public interface InvoicePaymentAllocationRepository
        extends JpaRepository<InvoicePaymentAllocation, UUID> {

    /**
     * Тўловнинг тақсимотлари (reverse ва кўриш экрани учун).
     * invoice LOAD граф билан - шаблон invoice рақамини кўрсатади
     * (open-in-view=false, lazy proxy шаблонда портлайди).
     */
    @EntityGraph(attributePaths = {"invoice"}, type = EntityGraph.EntityGraphType.LOAD)
    List<InvoicePaymentAllocation> findByPaymentIdOrderByCreatedAtAsc(UUID paymentId);

    /** Invoice кўриш экрани: унга кетган тақсимотлар (payment граф билан). */
    @EntityGraph(attributePaths = {"payment"}, type = EntityGraph.EntityGraphType.LOAD)
    List<InvoicePaymentAllocation> findByInvoiceIdOrderByCreatedAtAsc(UUID invoiceId);

    /** BR-RCPT-011 guard: шу (тўлов, invoice) жуфти аллақачон борми. */
    boolean existsByPaymentIdAndInvoiceId(UUID paymentId, UUID invoiceId);
}
