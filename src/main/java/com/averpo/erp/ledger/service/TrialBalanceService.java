package com.averpo.erp.ledger.service;

import com.averpo.erp.ledger.domain.AccountType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Айланма-қолдиқ ведомости (Trial Balance).
 *
 * <p>Entity граф эмас, тўғридан-тўғри SQL агрегат - journal_entry_line
 * энг катта жадвал, уни объектларга кўтариб ҳисоблаш бефойда.
 * POSTED билан бирга REVERSED entry'лар ҳам киради: сторно жуфти
 * иккиси ҳам GL'да туради ва нетто нолга тушади (ТЕМИР ҚОИДА №3'нинг
 * ҳисоботдаги оқибати).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TrialBalanceService {

    /**
     * Битта счёт бўйича сатр. Барча суммалар home валютада.
     *
     * @param accountId     счёт id'си - ҳисоботдан «Счёт амаллари»га
     *                      drill-down (spec T2) шу орқали боради
     * @param name          счёт номи (unique идентификатор)
     * @param code          ихтиёрий счёт рақами, бўлмаса null
     * @param typeName      account.type устунининг хом қиймати - enum'га
     *                      мажбурламаймиз: нотаниш қиймат (эски/кўчирилган
     *                      база) 500 бермасин
     * @param opening       давр бошидаги қолдиқ (мусбат - дебет)
     * @param debitTurnover давр ичидаги дебет айланма
     * @param creditTurnover давр ичидаги кредит айланма
     * @param closing       opening + debitTurnover - creditTurnover
     */
    public record Row(java.util.UUID accountId, String name, String code, String typeName,
                      BigDecimal opening, BigDecimal debitTurnover,
                      BigDecimal creditTurnover, BigDecimal closing) { }

    /** SQL агрегат учун JdbcClient - Hibernate'ни четлаб ўтади. */
    private final JdbcClient jdbc;

    /**
     * [from, to] даври: очилиш қолдиғи, Dt/Cr айланма, ёпилиш қолдиғи.
     * Тартиб - QBO услуби: счёт тури, кейин ном.
     */
    public List<Row> build(LocalDate from, LocalDate to) {
        List<Row> rows = jdbc.sql("""
                SELECT a.id, a.name, a.code, a.type,
                       COALESCE(SUM(CASE WHEN je.entry_date < :from
                           THEN COALESCE(l.debit_base_amount, 0) - COALESCE(l.credit_base_amount, 0)
                           ELSE 0 END), 0) AS opening,
                       COALESCE(SUM(CASE WHEN je.entry_date >= :from
                           THEN COALESCE(l.debit_base_amount, 0) ELSE 0 END), 0) AS debit_turnover,
                       COALESCE(SUM(CASE WHEN je.entry_date >= :from
                           THEN COALESCE(l.credit_base_amount, 0) ELSE 0 END), 0) AS credit_turnover
                FROM journal_entry_line l
                JOIN journal_entry je ON je.id = l.entry_id
                JOIN account a ON a.id = l.account_id
                WHERE je.status IN ('POSTED', 'REVERSED')
                  AND je.entry_date <= :to
                GROUP BY a.id, a.name, a.code, a.type
                """)
                .param("from", from)
                .param("to", to)
                .query((rs, i) -> {
                    BigDecimal opening = rs.getBigDecimal("opening");
                    BigDecimal dt = rs.getBigDecimal("debit_turnover");
                    BigDecimal ct = rs.getBigDecimal("credit_turnover");
                    return new Row(rs.getObject("id", java.util.UUID.class),
                            rs.getString("name"), rs.getString("code"),
                            rs.getString("type"),
                            opening, dt, ct, opening.add(dt).subtract(ct));
                })
                .list();
        // Тартиб Java'да: enum ordinal бўйича - SQL'да string тартиби нотўғри бўларди
        rows.sort(Comparator.comparingInt((Row r) -> safeOrdinal(r.typeName()))
                .thenComparing(Row::name));
        return rows;
    }

    /**
     * CoA экранидаги Balance устуни учун: ҳар счётнинг :asOf санасигача
     * бўлган хом қолдиғи (дебет - кредит, home валютада). Ишора
     * нормализацияси (пассивни кредит-мусбат кўрсатиш) чақирувчида -
     * бу ерда classification'га қарамаймиз.
     */
    public java.util.Map<java.util.UUID, BigDecimal> balancesByAccountId(LocalDate asOf) {
        java.util.Map<java.util.UUID, BigDecimal> result = new java.util.HashMap<>();
        jdbc.sql("""
                SELECT l.account_id,
                       COALESCE(SUM(COALESCE(l.debit_base_amount, 0)
                           - COALESCE(l.credit_base_amount, 0)), 0) AS balance
                FROM journal_entry_line l
                JOIN journal_entry je ON je.id = l.entry_id
                WHERE je.status IN ('POSTED', 'REVERSED')
                  AND je.entry_date <= :asOf
                GROUP BY l.account_id
                """)
                .param("asOf", asOf)
                .query(rs -> {
                    result.put(rs.getObject("account_id", java.util.UUID.class),
                            rs.getBigDecimal("balance"));
                });
        return result;
    }

    /** Нотаниш тур охирга тушади, 500 бермайди (хавфсиз парс). */
    private static int safeOrdinal(String typeName) {
        try {
            return AccountType.valueOf(typeName).ordinal();
        } catch (IllegalArgumentException e) {
            return Integer.MAX_VALUE;
        }
    }
}
