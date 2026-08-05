package com.averpo.erp.purchase.repo;

import com.averpo.erp.purchase.domain.PurchaseOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * PurchaseOrder репозиторийси - фақат purchase модули ичида.
 * Рўйхат экрани саҳифаланган: findAll(Specification, Pageable) (тартиб
 * PurchaseOrderService.LIST_SORT'дан) - Beruniy-perf1 2-босқич +
 * Arbitr-068 рўйхат филтри (аввалги findByStatus шунга алмашди).
 */
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<PurchaseOrder> {

    /** Кўриш/таҳрир учун - сатрлар билан (open-in-view=false, BillRepository нақши). */
    @EntityGraph(attributePaths = {"lines"}, type = EntityGraph.EntityGraphType.LOAD)
    Optional<PurchaseOrder> findWithLinesById(UUID id);

    /** Bill кўришидаги «Буюртмадан» белгиси учун (linked манба). */
    Optional<PurchaseOrder> findByBillId(UUID billId);
}
