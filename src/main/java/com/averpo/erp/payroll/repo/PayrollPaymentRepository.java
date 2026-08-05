package com.averpo.erp.payroll.repo;

import com.averpo.erp.payroll.domain.PayrollPayment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Иш ҳақи тўлови репозиторийси - фақат payroll модули ичида.
 * Рўйхат экрани саҳифаланган: JpaRepository.findAll(Pageable) (тартиб
 * PayrollPaymentService.LIST_SORT'дан) - «туғилишда пагинация» (perf1).
 */
public interface PayrollPaymentRepository extends JpaRepository<PayrollPayment, UUID> {

    /** Кўриш/post учун - сатрлари билан (open-in-view=false, lazy йўқ). */
    @EntityGraph(attributePaths = {"lines"}, type = EntityGraph.EntityGraphType.LOAD)
    Optional<PayrollPayment> findWithLinesById(UUID id);
}
