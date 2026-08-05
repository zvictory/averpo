package com.averpo.erp.ledger.repo;

import com.averpo.erp.ledger.domain.EntryStatus;
import com.averpo.erp.ledger.domain.JournalEntryLine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Проводка сатрлари репозиторийси - фақат ledger модули ичида.
 *
 * <p>Ҳозирча ягона истеъмолчиси - счёт амаллари (register) экрани:
 * сатрлар entry орқали эмас, тўғридан-тўғри счёт кесимида керак
 * бўлгани учун JournalEntryRepository'дан алоҳида.
 */
public interface JournalEntryLineRepository extends JpaRepository<JournalEntryLine, UUID> {

    /**
     * Битта счётнинг [from, to] даврдаги сатрлари - register тартибида.
     *
     * <p>Тартиб детерминистик бўлиши шарт, чунки жорий қолдиқ шу
     * кетма-кетликда ҳисобланади: entry_date → posted_at (бир кун
     * ичидаги хронология) → entry id (UUIDv7, posted_at тенг бўлса) →
     * line_no. {@code join fetch} - open-in-view=false, entry
     * майдонлари (рақам, сана, статус) view'гача lazy қолмасин.
     *
     * @param statuses қайси статуслар GL'да ҳисобланади - чақирувчи
     *                 беради (одатда POSTED + REVERSED, DRAFT ҳеч қачон)
     */
    @Query("""
            select l from JournalEntryLine l
            join fetch l.entry e
            where l.account.id = :accountId
              and e.status in :statuses
              and e.entryDate between :from and :to
            order by e.entryDate, e.postedAt, e.id, l.lineNo
            """)
    List<JournalEntryLine> findRegisterLines(@Param("accountId") UUID accountId,
                                             @Param("statuses") Collection<EntryStatus> statuses,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    /**
     * {@link #findRegisterLines} нинг саҳифаланган варианти (DEC-105б):
     * тартиб айнан ўша - жорий қолдиқ саҳифама-саҳифа узлуксиз давом
     * этиши учун ҳар икки query бир хил детерминистик кетма-кетликка
     * таянади. {@code countQuery} қўлда берилади: {@code join fetch}'дан
     * Spring Data count'ни ўзи ясай олмайди.
     */
    @Query(value = """
            select l from JournalEntryLine l
            join fetch l.entry e
            where l.account.id = :accountId
              and e.status in :statuses
              and e.entryDate between :from and :to
            order by e.entryDate, e.postedAt, e.id, l.lineNo
            """,
            countQuery = """
            select count(l) from JournalEntryLine l
            where l.account.id = :accountId
              and l.entry.status in :statuses
              and l.entry.entryDate between :from and :to
            """)
    Page<JournalEntryLine> findRegisterLines(@Param("accountId") UUID accountId,
                                             @Param("statuses") Collection<EntryStatus> statuses,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to,
                                             Pageable pageable);

    /**
     * Регистр тартибидаги ДАСТЛАБКИ {@code rowLimit} сатрнинг home
     * йиғиндиси: sum(debitBase - creditBase) - «саҳифагача йиғинди»
     * (DEC-105б). Саҳифа очилиш қолдиғи давр opening'ига шу қиймат
     * қўшилиб топилади - олдинги саҳифалар сатрлари Java'га юкланмайди.
     *
     * <p>Native, чунки JPQL субсўровда LIMIT йўқ. Ички SELECT'даги
     * ORDER BY {@link #findRegisterLines} тартибининг айнан ўзи
     * (entry_date, posted_at, e.id, line_no) - иккала query бир хил
     * кесимни кўриши шарт, акс ҳолда қолдиқ «сакраб» кетади.
     *
     * @param statuses  статус НОМЛАРИ (native query enum'ни ўзи
     *                  айлантирмайди - чақирувчи name() беради)
     * @param rowLimit  нечта сатр йиғилади (одатда page * size)
     * @return йиғинди ёки кесим бўш бўлса {@code null} - чақирувчи
     *         нолга айлантиради
     */
    @Query(value = """
            SELECT sum(coalesce(p.debit_base_amount, 0) - coalesce(p.credit_base_amount, 0))
            FROM (SELECT l.debit_base_amount, l.credit_base_amount
                  FROM journal_entry_line l
                  JOIN journal_entry e ON e.id = l.entry_id
                  WHERE l.account_id = :accountId
                    AND e.status IN (:statuses)
                    AND e.entry_date BETWEEN :from AND :to
                  ORDER BY e.entry_date, e.posted_at, e.id, l.line_no
                  LIMIT :rowLimit) p
            """, nativeQuery = true)
    BigDecimal sumBaseFirstRegisterRows(@Param("accountId") UUID accountId,
                                        @Param("statuses") Collection<String> statuses,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to,
                                        @Param("rowLimit") long rowLimit);

    /**
     * Счётнинг [from, to] давр ичидаги home айланма йиғиндиси:
     * sum(debitBase - creditBase). Давр closing'и охирги саҳифагача
     * бормай ҳисоблансин учун (DEC-105б): closing = opening + шу.
     *
     * @return йиғинди ёки даврда сатр бўлмаса {@code null}
     */
    @Query("""
            select sum(coalesce(l.debit.baseAmount, 0) - coalesce(l.credit.baseAmount, 0))
            from JournalEntryLine l
            where l.account.id = :accountId
              and l.entry.status in :statuses
              and l.entry.entryDate between :from and :to
            """)
    BigDecimal sumBaseBetween(@Param("accountId") UUID accountId,
                              @Param("statuses") Collection<EntryStatus> statuses,
                              @Param("from") LocalDate from,
                              @Param("to") LocalDate to);

    /**
     * Счётнинг {@code from} санасигача (қатъий кичик) home валютадаги
     * хом қолдиғи: sum(debitBase - creditBase). Давр бошидаги очилиш
     * қолдиғи учун - register сатрларини даврдан ташқарида юкламай,
     * битта агрегат билан олинади.
     *
     * @return signed қолдиқ (мусбат - дебет) ёки давргача сатр
     *         бўлмаса {@code null} - чақирувчи нолга айлантиради
     */
    @Query("""
            select sum(coalesce(l.debit.baseAmount, 0) - coalesce(l.credit.baseAmount, 0))
            from JournalEntryLine l
            where l.account.id = :accountId
              and l.entry.status in :statuses
              and l.entry.entryDate < :from
            """)
    BigDecimal sumBaseBefore(@Param("accountId") UUID accountId,
                             @Param("statuses") Collection<EntryStatus> statuses,
                             @Param("from") LocalDate from);
}
