package com.averpo.erp.sales.repo;

import com.averpo.erp.sales.domain.Invoice;
import com.averpo.erp.sales.domain.InvoiceStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Invoice репозиторийси - фақат sales модули ичида ишлатилади.
 * JpaSpecificationExecutor - рўйхат филтри учун (DEC-068): давр/
 * статус/мижоз/матн комбинациялари битта Specification'да, аввалги
 * findByStatus/findByCustomerId derived методлари шунга алмашди.
 */
public interface InvoiceRepository extends JpaRepository<Invoice, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Invoice> {

    /**
     * Кўриш/post учун - сатрлар билан (open-in-view=false, lazy йўқ).
     * Тур LOAD - default FETCH граф'да йўқ EAGER майдонларни (currency)
     * lazy proxy қилиб, шаблонда LazyInitialization отарди
     * (BillRepository'даги сабоқ).
     */
    @EntityGraph(attributePaths = {"lines"}, type = EntityGraph.EntityGraphType.LOAD)
    Optional<Invoice> findWithLinesById(UUID id);

    /** Тушум формаси ва credit limit: мижознинг очиқ invoice'лари. */
    List<Invoice> findByCustomerIdAndStatusAndBalanceDueGreaterThanOrderByInvoiceDateAsc(
            UUID customerId, InvoiceStatus status, java.math.BigDecimal zero);

    /** AR aging: барча очиқ invoice'лар (мижозлар бўйича гуруҳланади). */
    List<Invoice> findByStatusAndBalanceDueGreaterThan(
            InvoiceStatus status, java.math.BigDecimal zero);
}
