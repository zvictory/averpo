package com.averpo.erp.purchase.repo;

import com.averpo.erp.purchase.domain.VendorCredit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Таъминотчи кредит-ноталари репозиторийси - фақат purchase модули ичида.
 * JpaSpecificationExecutor - рўйхат филтри учун (DEC-068).
 */
public interface VendorCreditRepository extends JpaRepository<VendorCredit, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<VendorCredit> {

    /** Кўриш/пост учун - сатрлари билан (open-in-view=false, lazy йўқ). */
    @EntityGraph(attributePaths = {"lines"}, type = EntityGraph.EntityGraphType.LOAD)
    Optional<VendorCredit> findWithLinesById(UUID id);

    /** Bill кўришида «шу ҳужжатдан яратилган кредитлар» рўйхати. */
    List<VendorCredit> findByBillIdOrderByCreatedAtAsc(UUID billId);

    /**
     * Кумулятив қайтим гарови (BR-RET-006) учун: шу bill'га ҳавола
     * қилинган берилган ҳолатдаги кредитлар, сатрлари билан - аввалги
     * POSTED қайтимлар йиғиндиси шундан ҳисобланади (REVERSED
     * чақирилмайди - қайтими сторно билан бекор бўлган).
     */
    @EntityGraph(attributePaths = {"lines"}, type = EntityGraph.EntityGraphType.LOAD)
    List<VendorCredit> findWithLinesByBillIdAndStatus(UUID billId, VendorCredit.Status status);
}
