package com.averpo.erp.sales.repo;

import com.averpo.erp.sales.domain.Estimate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Estimate репозиторийси - фақат sales модули ичида ишлатилади.
 * Рўйхат экрани саҳифаланган: findAll(Specification, Pageable) (тартиб
 * EstimateService.LIST_SORT'дан) - PERF-perf1 2-босқич + DEC-068
 * рўйхат филтри (аввалги findByStatus шунга алмашди).
 */
public interface EstimateRepository extends JpaRepository<Estimate, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Estimate> {

    /** Кўриш/таҳрир учун - сатрлар билан (open-in-view=false, InvoiceRepository нақши). */
    @EntityGraph(attributePaths = {"lines"}, type = EntityGraph.EntityGraphType.LOAD)
    Optional<Estimate> findWithLinesById(UUID id);

    /** Invoice кўришидаги «Estimate'дан» белгиси учун (linked манба). */
    Optional<Estimate> findByInvoiceId(UUID invoiceId);
}
