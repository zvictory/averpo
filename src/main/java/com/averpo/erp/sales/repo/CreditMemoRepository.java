package com.averpo.erp.sales.repo;

import com.averpo.erp.sales.domain.CreditMemo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Кредит-ноталар репозиторийси - фақат sales модули ичида.
 * Рўйхат экрани саҳифаланган: findAll(Specification, Pageable) (тартиб
 * CreditMemoService.LIST_SORT'дан) - Beruniy-perf1 2-босқич +
 * Arbitr-068 рўйхат филтри.
 *
 * @author Zafar
 */
public interface CreditMemoRepository extends JpaRepository<CreditMemo, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<CreditMemo> {

    /** Кўриш/пост учун - сатрлари билан (open-in-view=false, lazy йўқ). */
    @EntityGraph(attributePaths = {"lines"}, type = EntityGraph.EntityGraphType.LOAD)
    Optional<CreditMemo> findWithLinesById(UUID id);

    /** Invoice кўришида «шу ҳужжатдан яратилган кредитлар» рўйхати. */
    List<CreditMemo> findByInvoiceIdOrderByCreatedAtAsc(UUID invoiceId);

    /**
     * Кумулятив қайтим гарови (BR-RET-006) учун: шу invoice'га ҳавола
     * қилинган берилган ҳолатдаги кредит-ноталар, сатрлари билан -
     * аввалги POSTED қайтимлар йиғиндиси шундан ҳисобланади (REVERSED
     * чақирилмайди - унинг қайтими сторно билан бекор бўлган).
     */
    @EntityGraph(attributePaths = {"lines"}, type = EntityGraph.EntityGraphType.LOAD)
    List<CreditMemo> findWithLinesByInvoiceIdAndStatus(UUID invoiceId, CreditMemo.Status status);
}
