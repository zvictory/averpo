package com.averpo.erp.bank.repo;

import com.averpo.erp.bank.domain.BankReconciliation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reconciliation репозиторийси - фақат bank модули ичида.
 */
public interface BankReconciliationRepository
        extends JpaRepository<BankReconciliation, UUID> {

    /** Рўйхат экрани - янгидан эскига. */

    /**
     * Opening учун: ЯНГИ кўчирма санасидан ОЛДИНГИ энг сўнгги COMPLETED
     * reconciliation (CHK-003: глобал энг сўнггисини олиш тартибсиз
     * бошлашда кейинги даврнинг closing'ини «ўғирлаб» келар эди).
     */
    Optional<BankReconciliation> findTopByAccountIdAndStatusAndStatementDateLessThanOrderByStatementDateDesc(
            UUID accountId, BankReconciliation.Status status, java.time.LocalDate statementDate);

    /** BR-RCN-008 guard: янги санадан КЕЙИНГИ COMPLETED давр борми. */
    boolean existsByAccountIdAndStatusAndStatementDateGreaterThan(
            UUID accountId, BankReconciliation.Status status, java.time.LocalDate statementDate);

    /** BR-RCN-003 guard: шу счёт ва санага reconciliation борми. */
    boolean existsByAccountIdAndStatementDate(UUID accountId, java.time.LocalDate statementDate);
}
