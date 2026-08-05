package com.averpo.erp.purchase.repo;

import com.averpo.erp.purchase.domain.BillPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * BillPayment репозиторийси - фақат purchase модули ичида ишлатилади.
 * Рўйхат экрани саҳифаланган: JpaRepository.findAll(Pageable) (тартиб
 * BillPaymentService.LIST_SORT'дан) - Beruniy-perf1 2-босқич.
 *
 * @author Zafar
 */
public interface BillPaymentRepository extends JpaRepository<BillPayment, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<BillPayment> {

    /** Vendor карточкаси/allocation формаси: шу vendor тўловлари. */
    List<BillPayment> findByVendorIdOrderByPaymentDateDescCreatedAtDesc(UUID vendorId);
}
