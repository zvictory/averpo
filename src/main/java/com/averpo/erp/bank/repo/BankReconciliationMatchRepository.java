package com.averpo.erp.bank.repo;

import com.averpo.erp.bank.domain.BankReconciliationMatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reconciliation белгилари репозиторийси - фақат bank модули ичида.
 */
public interface BankReconciliationMatchRepository
        extends JpaRepository<BankReconciliationMatch, UUID> {

    /** Ҳужжатнинг белгиланган сатрлари (фарқ ҳисоби ва экран учун). */
    List<BankReconciliationMatch> findByReconciliationIdOrderByCreatedAtAsc(UUID reconciliationId);

    /** Toggle: шу reconciliation'да шу сатр белгиланганми. */
    Optional<BankReconciliationMatch> findByReconciliationIdAndJournalEntryLineId(
            UUID reconciliationId, UUID journalEntryLineId);

    /** BR-RCN-006 guard: сатр умуман (қайси ҳужжатда бўлса ҳам) белгиланганми. */
    boolean existsByJournalEntryLineId(UUID journalEntryLineId);

    /**
     * Жорий номзодлар КЕСИМИДА белгиланган GL сатр id'лари - candidates()
     * экрани учун БИТТА query (Beruniy-011: аввал ҳар номзодга алоҳида
     * exists кетарди). Sanjar-006: бутун тарих эмас, фақат берилган
     * id'лар текширилади - акс ҳолда 100 давр × 500 белги = 50 000 UUID
     * ҳар view'да хотирага келарди; membership фақат номзодлар учун
     * сўралгани сабабли натижа айнан аввалгидек.
     */
    @org.springframework.data.jpa.repository.Query(
            "select m.journalEntryLineId from BankReconciliationMatch m"
            + " where m.journalEntryLineId in :lineIds")
    java.util.Set<UUID> findMatchedLineIdsIn(
            @org.springframework.data.repository.query.Param("lineIds")
            java.util.Collection<UUID> lineIds);

    /** Бекор қилишда ҳужжат белгилари ўчади. */
    void deleteByReconciliationId(UUID reconciliationId);
}
