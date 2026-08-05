package com.averpo.erp.purchase.repo;

import com.averpo.erp.purchase.domain.VendorCreditApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Таъминотчи кредити қўллашлари репозиторийси - фақат purchase модули ичида.
 */
public interface VendorCreditApplicationRepository
        extends JpaRepository<VendorCreditApplication, UUID> {

    /** Кредитнинг қўллашлари - кўриш экрани ва reverse текшируви учун. */
    List<VendorCreditApplication> findByVendorCreditIdOrderByCreatedAtAsc(UUID vendorCreditId);

    /** Bill'га қўлланган кредитлар - bill кўриш экрани учун. */
    List<VendorCreditApplication> findByBillIdOrderByCreatedAtAsc(UUID billId);

    /** Дубль-жуфт гарови (BR-RET-003) - DB unique қўшимча ҳимоя. */
    boolean existsByVendorCreditIdAndBillId(UUID vendorCreditId, UUID billId);
}
