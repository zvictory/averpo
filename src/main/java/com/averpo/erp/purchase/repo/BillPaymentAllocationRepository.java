package com.averpo.erp.purchase.repo;

import com.averpo.erp.purchase.domain.BillPaymentAllocation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Тўлов тақсимоти репозиторийси - фақат purchase модули ичида.
 *
 * @author Zafar
 */
public interface BillPaymentAllocationRepository
        extends JpaRepository<BillPaymentAllocation, UUID> {

    /**
     * Тўловнинг тақсимотлари (reverse ва кўриш экрани учун).
     * bill LOAD граф билан - шаблон bill рақамини кўрсатади
     * (open-in-view=false, lazy proxy шаблонда портлайди).
     */
    @EntityGraph(attributePaths = {"bill"}, type = EntityGraph.EntityGraphType.LOAD)
    List<BillPaymentAllocation> findByPaymentIdOrderByCreatedAtAsc(UUID paymentId);

    /** Bill кўриш экрани: унга кетган тақсимотлар (payment граф билан). */
    @EntityGraph(attributePaths = {"payment"}, type = EntityGraph.EntityGraphType.LOAD)
    List<BillPaymentAllocation> findByBillIdOrderByCreatedAtAsc(UUID billId);

    /** BR-PAY-011 guard: шу (тўлов, bill) жуфти аллақачон борми. */
    boolean existsByPaymentIdAndBillId(UUID paymentId, UUID billId);
}
