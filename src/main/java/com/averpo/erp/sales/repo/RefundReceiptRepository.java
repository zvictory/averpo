package com.averpo.erp.sales.repo;

import com.averpo.erp.sales.domain.RefundReceipt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Пул қайтариш чеклари репозиторийси - фақат sales модули ичида.
 * JpaSpecificationExecutor - рўйхат филтри учун (Arbitr-068).
 *
 * @author Zafar
 */
public interface RefundReceiptRepository extends JpaRepository<RefundReceipt, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<RefundReceipt> {

    /** Кўриш учун - сатрлари билан (open-in-view=false, lazy йўқ). */
    @EntityGraph(attributePaths = {"lines"}, type = EntityGraph.EntityGraphType.LOAD)
    Optional<RefundReceipt> findWithLinesById(UUID id);

    /**
     * Кумулятив қайтим гарови (BR-RET-006) учун: шу invoice'га ҳавола
     * қилинган берилган ҳолатдаги чеклар, сатрлари билан - CM билан
     * битта ҳовузда ҳисобланади (иккиси ҳам invoice қайтими).
     */
    @EntityGraph(attributePaths = {"lines"}, type = EntityGraph.EntityGraphType.LOAD)
    List<RefundReceipt> findWithLinesByInvoiceIdAndStatus(UUID invoiceId, RefundReceipt.Status status);
}
