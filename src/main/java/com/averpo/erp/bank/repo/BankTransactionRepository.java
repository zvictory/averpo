package com.averpo.erp.bank.repo;

import com.averpo.erp.bank.domain.BankTransaction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Банк транзакциялари репозиторийси - фақат bank модули ичида.
 * JpaSpecificationExecutor - рўйхат/ўтказмалар филтри учун (Arbitr-068):
 * transfers'нинг эски List findByTypeWithCurrency методи Specification +
 * fetch бўлагига алмашди (Beruniy-020 N+1 ҳимояси сақланган).
 *
 * @author Zafar
 */
public interface BankTransactionRepository extends JpaRepository<BankTransaction, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<BankTransaction> {

    /**
     * Кўриш учун - сатрлар билан (open-in-view=false, lazy йўқ).
     * Тур LOAD - default FETCH граф'да йўқ EAGER майдонларни (currency)
     * lazy proxy қилиб, шаблонда LazyInitialization отарди
     * (BillRepository'даги сабоқ).
     */
    @EntityGraph(attributePaths = {"lines"}, type = EntityGraph.EntityGraphType.LOAD)
    Optional<BankTransaction> findWithLinesById(UUID id);

    /** Битта банк счётининг транзакциялари (счёт кесимидаги кўриниш). */
    List<BankTransaction> findByBankAccountIdOrderByTxnDateDescCreatedAtDesc(UUID bankAccountId);
}
