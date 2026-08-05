package com.averpo.erp.ledger.repo;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.averpo.erp.ledger.domain.EntryStatus;
import com.averpo.erp.ledger.domain.JournalEntry;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * GL ёзувлари репозиторийси - фақат ledger модули ичида (қоида 6:
 * ledger ҳеч кимга боғлиқ эмас, бошқалар бунга тегмайди).
 * JpaSpecificationExecutor - рўйхат филтри учун (Arbitr-068).
 */
public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<JournalEntry> {

    boolean existsByStatusIn(List<EntryStatus> statuses);

    /**
     * Idempotency guard учун: шу манба ҳужжат ҳозир GL'да фаол борми.
     * Сторно ҳисобга олинмайди (reversalOf IS NULL) - сторно атайлаб ўша
     * source'ни олади, лекин у ҳужжатни "қайта" ифодаламайди; reverse'дан
     * кейин ҳужжат қайта post қилиниши мумкин. Ҳақиқий кафолат DB'даги
     * ux_je_source_active partial unique index'да, бу текширув аниқ
     * хабар бериш учун.
     */
    boolean existsBySourceModuleAndSourceDocumentIdAndStatusInAndReversalOfIsNull(
            String sourceModule, UUID sourceDocumentId, List<EntryStatus> statuses);

    // reversedBy ҳам fetch қилинади - open-in-view=false, view'да lazy йўқ
    @EntityGraph(attributePaths = {"lines", "lines.account", "reversedBy"})
    Optional<JournalEntry> findWithLinesById(UUID id);

    /**
     * Манба ҳужжатнинг фаол GL ёзуви (сторно ҳисобга олинмайди) -
     * inventory adjustment каби автоматик проводкаларни манбасидан
     * топиш учун (тестлар ва кейинги кўриш экранлари).
     */
    @EntityGraph(attributePaths = {"lines", "lines.account"})
    Optional<JournalEntry> findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
            String sourceModule, UUID sourceDocumentId);

    /**
     * Манбанинг ЭНГ ОХИРГИ асл (reversalOf IS NULL) GL ёзуви (Arbitr-080).
     * Юқоридаги {@code findBy...}'дан фарқи: {@code findFirst} + тартиб,
     * шунда битта манбага бирдан кўп reversalOf=null ёзув тўпланганда ҳам
     * NonUniqueResultException (хом 500) отилмайди.
     *
     * <p><b>Нега кўп натижа мумкин:</b> {@code ux_je_source_active} partial
     * unique index фақат {@code status IN (DRAFT, POSTED) AND
     * reversal_of_id IS NULL}'ни қамрайди - REVERSED ёзув четда қолади.
     * Репост оқимида (масалан payroll BR-PYR-002: reverse→репост) битта
     * манбада REVERSED асл ва POSTED репост иккиси ҳам {@code reversalOf}
     * =null бўлади. Тартиб {@code createdAt DESC, id DESC} (UUIDv7 монотон,
     * tie-break) энг охиргисини - репост ҳолатида фаол POSTED ёзувни -
     * детерминистик танлайди. {@code by-source} redirect ва
     * {@code reverseBySource} иккови шу орқали ишлайди.
     */
    Optional<JournalEntry>
    findFirstBySourceModuleAndSourceDocumentIdAndReversalOfIsNullOrderByCreatedAtDescIdDesc(
            String sourceModule, UUID sourceDocumentId);

}
