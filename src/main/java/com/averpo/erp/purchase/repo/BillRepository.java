package com.averpo.erp.purchase.repo;

import com.averpo.erp.purchase.domain.Bill;
import com.averpo.erp.purchase.domain.BillStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Bill репозиторийси - фақат purchase модули ичида ишлатилади.
 * JpaSpecificationExecutor - рўйхат филтри учун (DEC-068): давр/
 * статус/vendor/матн комбинациялари битта Specification'да, аввалги
 * findByStatus/findByVendorId derived методлари шунга алмашди.
 */
public interface BillRepository extends JpaRepository<Bill, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Bill> {

    /**
     * Кўриш/post учун - сатрлар билан (open-in-view=false, lazy йўқ).
     * ДИҚҚАТ: тур LOAD - default FETCH граф'да йўқ EAGER majdonlarni
     * (currency) lazy proxy қилиб, шаблонда LazyInitialization отарди.
     */
    @EntityGraph(attributePaths = {"lines"}, type = EntityGraph.EntityGraphType.LOAD)
    Optional<Bill> findWithLinesById(UUID id);

    /** Vendor duplicate guard (BR-BILL-006): фаол статусларда шу рақам борми. */
    Optional<Bill> findByVendorIdAndVendorInvoiceNumberAndStatusIn(
            UUID vendorId, String vendorInvoiceNumber, List<BillStatus> statuses);

    /** Тўлов формаси: vendor'нинг очиқ (тўланмаган қолдиқли) bill'лари. */
    List<Bill> findByVendorIdAndStatusAndBalanceDueGreaterThanOrderByBillDateAsc(
            UUID vendorId, BillStatus status, java.math.BigDecimal zero);

    /** AP aging: барча очиқ bill'лар (vendor'лар бўйича гуруҳланади). */
    List<Bill> findByStatusAndBalanceDueGreaterThan(
            BillStatus status, java.math.BigDecimal zero);
}
