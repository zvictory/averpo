package com.averpo.erp.sales.repo;

import com.averpo.erp.sales.domain.InvoicePayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * InvoicePayment (тушум) репозиторийси - фақат sales модули ичида.
 * Рўйхат экрани саҳифаланган: JpaRepository.findAll(Pageable) (тартиб
 * InvoicePaymentService.LIST_SORT'дан) - Beruniy-perf1 2-босқич.
 */
public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<InvoicePayment> {

    /** Мижоз карточкаси/allocation формаси: шу мижоз тўловлари. */
    List<InvoicePayment> findByCustomerIdOrderByPaymentDateDescCreatedAtDesc(UUID customerId);

    /**
     * Даврдаги POSTED тушумлар жамиси home валютада (сумма × курс) -
     * dashboard «охирги 30 кунда тўланган» картаси (Arbitr-036).
     * REVERSED тўлов қайтарилган пул - жамга кирмайди.
     */
    @org.springframework.data.jpa.repository.Query("""
            select coalesce(sum(p.totalAmount * p.exchangeRate), 0)
            from InvoicePayment p
            where p.status = com.averpo.erp.sales.domain.InvoicePayment.Status.POSTED
              and p.paymentDate >= :from and p.paymentDate <= :to
            """)
    java.math.BigDecimal sumPostedBaseBetween(
            @org.springframework.data.repository.query.Param("from") java.time.LocalDate from,
            @org.springframework.data.repository.query.Param("to") java.time.LocalDate to);
}
