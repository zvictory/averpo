package com.averpo.erp.ledger.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dashboard'нинг GL'га таянган карталари учун public ўқиш API'си
 * (docs/modules/reports.md «Dashboard»). Ledger жадвалларига SQL фақат
 * шу модулда - dashboard модули тайёр натижани олади (қоида №6).
 * Барча суммалар home валютада (банк қолдиғида қўшимча счёт валютаси).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LedgerDashboardService {

    /**
     * Бир ойлик P&L нуқтаси (график устуни).
     *
     * @param month   ой
     * @param income  даромад (REVENUE, Cr-Dt)
     * @param expense харажат (EXPENSE синфи тўлиқ: COGS ҳам, Dt-Cr)
     */
    public record MonthPl(YearMonth month, BigDecimal income, BigDecimal expense) { }

    /**
     * Бир ойлик пул оқими нуқтаси (cash flow графиги устуни).
     *
     * @param month   ой
     * @param inflow  BANK счётларга кирим (Dt base йиғиндиси)
     * @param outflow BANK счётлардан чиқим (Cr base йиғиндиси)
     */
    public record MonthCashFlow(YearMonth month, BigDecimal inflow,
                                BigDecimal outflow) { }

    /**
     * Битта банк счёти қолдиғи.
     *
     * @param currencyCode счёт валютаси; home бўлса null (amount == baseAmount)
     * @param amount       счёт валютасидаги қолдиқ (валюта мос сатрлардан)
     * @param baseAmount   home валютадаги қолдиқ
     */
    public record BankBalance(UUID accountId, String name, String currencyCode,
                              BigDecimal amount, BigDecimal baseAmount) { }

    /**
     * Харажат тақсимоти бўлаги (битта счёт).
     *
     * @param amount даврдаги нетто харажат (Dt-Cr, мусбат)
     */
    public record ExpenseSlice(UUID accountId, String name, BigDecimal amount) { }

    /** SQL агрегат учун JdbcClient - Hibernate'ни четлаб ўтади. */
    private final JdbcClient jdbc;

    /**
     * Охирги N ойнинг даромад/харажати (fromMonth..toMonth, иккиси
     * билан). Ҳаракат бўлмаган ойлар нол билан тўлдирилади - график
     * устунлари узилмасин.
     */
    public List<MonthPl> monthlyPl(YearMonth fromMonth, YearMonth toMonth) {
        Map<YearMonth, BigDecimal[]> byMonth = new HashMap<>();
        jdbc.sql("""
                SELECT date_trunc('month', je.entry_date)::date AS month,
                       COALESCE(SUM(CASE WHEN a.classification = 'REVENUE'
                           THEN COALESCE(l.credit_base_amount, 0) - COALESCE(l.debit_base_amount, 0)
                           ELSE 0 END), 0) AS income,
                       COALESCE(SUM(CASE WHEN a.classification = 'EXPENSE'
                           THEN COALESCE(l.debit_base_amount, 0) - COALESCE(l.credit_base_amount, 0)
                           ELSE 0 END), 0) AS expense
                FROM journal_entry_line l
                JOIN journal_entry je ON je.id = l.entry_id
                JOIN account a ON a.id = l.account_id
                WHERE je.status IN ('POSTED', 'REVERSED')
                  AND je.entry_date >= :from AND je.entry_date <= :to
                  AND a.classification IN ('REVENUE', 'EXPENSE')
                GROUP BY 1
                """)
                .param("from", fromMonth.atDay(1))
                .param("to", toMonth.atEndOfMonth())
                .query(rs -> {
                    LocalDate month = rs.getObject("month", LocalDate.class);
                    byMonth.put(YearMonth.from(month), new BigDecimal[]{
                            rs.getBigDecimal("income"), rs.getBigDecimal("expense")});
                });

        List<MonthPl> result = new ArrayList<>();
        for (YearMonth m = fromMonth; !m.isAfter(toMonth); m = m.plusMonths(1)) {
            BigDecimal[] sums = byMonth.get(m);
            result.add(new MonthPl(m,
                    sums == null ? BigDecimal.ZERO : sums[0],
                    sums == null ? BigDecimal.ZERO : sums[1]));
        }
        return result;
    }

    /**
     * Охирги N ойнинг пул оқими (fromMonth..toMonth, иккиси билан):
     * BANK тур счётлардаги GL ҳаракатлар ой кесимида - Dt base кирим,
     * Cr base чиқим (DEC-036). POSTED билан REVERSED бирга -
     * сторно жуфти иккала томонга тушиб нетто оқимни бузмайди
     * (monthlyPl конвенцияси). Ҳаракат бўлмаган ойлар нол билан
     * тўлдирилади - график устунлари узилмасин.
     */
    public List<MonthCashFlow> monthlyCashFlow(YearMonth fromMonth, YearMonth toMonth) {
        Map<YearMonth, BigDecimal[]> byMonth = new HashMap<>();
        jdbc.sql("""
                SELECT date_trunc('month', je.entry_date)::date AS month,
                       COALESCE(SUM(COALESCE(l.debit_base_amount, 0)), 0) AS inflow,
                       COALESCE(SUM(COALESCE(l.credit_base_amount, 0)), 0) AS outflow
                FROM journal_entry_line l
                JOIN journal_entry je ON je.id = l.entry_id
                JOIN account a ON a.id = l.account_id
                WHERE je.status IN ('POSTED', 'REVERSED')
                  AND je.entry_date >= :from AND je.entry_date <= :to
                  AND a.type = 'BANK'
                GROUP BY 1
                """)
                .param("from", fromMonth.atDay(1))
                .param("to", toMonth.atEndOfMonth())
                .query(rs -> {
                    LocalDate month = rs.getObject("month", LocalDate.class);
                    byMonth.put(YearMonth.from(month), new BigDecimal[]{
                            rs.getBigDecimal("inflow"), rs.getBigDecimal("outflow")});
                });

        List<MonthCashFlow> result = new ArrayList<>();
        for (YearMonth m = fromMonth; !m.isAfter(toMonth); m = m.plusMonths(1)) {
            BigDecimal[] sums = byMonth.get(m);
            result.add(new MonthCashFlow(m,
                    sums == null ? BigDecimal.ZERO : sums[0],
                    sums == null ? BigDecimal.ZERO : sums[1]));
        }
        return result;
    }

    /**
     * Фаол BANK счётлари қолдиқлари (ҳаракатсизлари ҳам, ноль билан).
     * Чет валютали счётда amount - валюта мос сатрлар йиғиндиси
     * (тарихий ёзувда бошқа валюта учраса фақат base'га киради,
     * AccountTransactionsService услуби); home счётда amount == base.
     */
    public List<BankBalance> bankBalances() {
        List<BankBalance> result = new ArrayList<>();
        jdbc.sql("""
                SELECT a.id, a.name, c.code AS currency_code,
                       COALESCE(SUM(COALESCE(l.debit_base_amount, 0)
                           - COALESCE(l.credit_base_amount, 0)), 0) AS base,
                       COALESCE(SUM(CASE WHEN c.code IS NOT NULL
                               AND (l.debit_currency = c.code OR l.credit_currency = c.code)
                           THEN COALESCE(l.debit_amount, 0) - COALESCE(l.credit_amount, 0)
                           ELSE 0 END), 0) AS cur_amount
                FROM account a
                LEFT JOIN currency c ON c.id = a.currency_id
                LEFT JOIN (
                    SELECT l.*
                    FROM journal_entry_line l
                    JOIN journal_entry je ON je.id = l.entry_id
                    WHERE je.status IN ('POSTED', 'REVERSED')
                ) l ON l.account_id = a.id
                WHERE a.type = 'BANK' AND a.active
                GROUP BY a.id, a.name, c.code
                ORDER BY a.name
                """)
                .query(rs -> {
                    String code = rs.getString("currency_code");
                    BigDecimal base = rs.getBigDecimal("base");
                    result.add(new BankBalance(rs.getObject("id", UUID.class),
                            rs.getString("name"), code,
                            code == null ? base : rs.getBigDecimal("cur_amount"),
                            base));
                });
        return result;
    }

    /**
     * Берилган счётнинг КОНТАКТ кесимидаги owed қолдиғи (Cr − Dt base)
     * asOf санагача (инклюзив), POSTED+REVERSED - subledger қолдиғи учун
     * (payroll clearing ведомости/prefill; келажакда бошқа контакт
     * кесимли счётлар). JdbcClient агрегат - хотирага entity ЮКЛАНМАЙДИ
     * (PERF-028: EPOCH register бутун тарихни объект қиларди).
     * Контактсиз сатрлар четда (map калити - contact_id).
     */
    public Map<UUID, BigDecimal> contactBalances(UUID accountId, LocalDate asOf) {
        Map<UUID, BigDecimal> owed = new HashMap<>();
        jdbc.sql("""
                SELECT l.contact_id AS cid,
                       COALESCE(SUM(COALESCE(l.credit_base_amount, 0)
                           - COALESCE(l.debit_base_amount, 0)), 0) AS owed
                FROM journal_entry_line l
                JOIN journal_entry je ON je.id = l.entry_id
                WHERE l.account_id = :accountId
                  AND l.contact_id IS NOT NULL
                  AND je.status IN ('POSTED', 'REVERSED')
                  AND je.entry_date <= :asOf
                GROUP BY l.contact_id
                """)
                .param("accountId", accountId)
                .param("asOf", asOf)
                .query(rs -> {
                    owed.put(rs.getObject("cid", UUID.class), rs.getBigDecimal("owed"));
                });
        return owed;
    }

    /**
     * Счётнинг контакт кесимида МАНБА МОДУЛ бўйича давр ичи [from, to]
     * нетто ҳаракати (Dt − Cr base), POSTED+REVERSED. Ведомостнинг
     * «даврда тўланган» устуни учун (source=PAYROLL_PAYMENT, DEC-047):
     * тўлов Dt − сторно Cr - шунда тўлов reverse қилинса ҳам жуфти нолга
     * тушиб инвариант (давр_охири = давр_боши + net − тўланган) сақланади.
     * Run кредитлари (бошқа source_module) четда қолади.
     */
    public Map<UUID, BigDecimal> contactSourceMovement(UUID accountId, LocalDate from,
                                                       LocalDate to, String sourceModule) {
        Map<UUID, BigDecimal> movement = new HashMap<>();
        jdbc.sql("""
                SELECT l.contact_id AS cid,
                       COALESCE(SUM(COALESCE(l.debit_base_amount, 0)
                           - COALESCE(l.credit_base_amount, 0)), 0) AS moved
                FROM journal_entry_line l
                JOIN journal_entry je ON je.id = l.entry_id
                WHERE l.account_id = :accountId
                  AND l.contact_id IS NOT NULL
                  AND je.status IN ('POSTED', 'REVERSED')
                  AND je.source_module = :sourceModule
                  AND je.entry_date >= :from AND je.entry_date <= :to
                GROUP BY l.contact_id
                """)
                .param("accountId", accountId)
                .param("from", from)
                .param("to", to)
                .param("sourceModule", sourceModule)
                .query(rs -> {
                    movement.put(rs.getObject("cid", UUID.class), rs.getBigDecimal("moved"));
                });
        return movement;
    }

    /**
     * Даврдаги харажатлар счёт кесимида (EXPENSE синфи тўлиқ), нетто
     * камайиш тартибида; ноль ва манфий нетто (қайтарилган харажат)
     * бўлаклар ташланади - улуш барлари фақат мусбатда маъноли.
     */
    public List<ExpenseSlice> expenseBreakdown(LocalDate from, LocalDate to) {
        List<ExpenseSlice> result = new ArrayList<>();
        jdbc.sql("""
                SELECT a.id, a.name,
                       COALESCE(SUM(COALESCE(l.debit_base_amount, 0)
                           - COALESCE(l.credit_base_amount, 0)), 0) AS amount
                FROM journal_entry_line l
                JOIN journal_entry je ON je.id = l.entry_id
                JOIN account a ON a.id = l.account_id
                WHERE je.status IN ('POSTED', 'REVERSED')
                  AND je.entry_date >= :from AND je.entry_date <= :to
                  AND a.classification = 'EXPENSE'
                GROUP BY a.id, a.name
                HAVING COALESCE(SUM(COALESCE(l.debit_base_amount, 0)
                    - COALESCE(l.credit_base_amount, 0)), 0) > 0
                ORDER BY amount DESC
                """)
                .param("from", from)
                .param("to", to)
                .query(rs -> {
                    result.add(new ExpenseSlice(rs.getObject("id", UUID.class),
                            rs.getString("name"), rs.getBigDecimal("amount")));
                });
        return result;
    }

    /**
     * Контактнинг ТАРИХИЙ (бутун ҳаёт) жами ҳисоб-фактура/bill қиймати
     * (GROSS) - контрол счёт (AR/AP) кесимида, битта манба модул бўйича,
     * нормал ишорада МУСБАТ: мижоз «Жами сотув» = INVOICE'нинг AR дебетлари
     * (Dt-Cr), таъминотчи «Жами харид» = BILL'нинг AP кредитлари (Cr-Dt).
     * Контакт кесими {@code journal_entry_line.contact_id}'дан
     * ({@link StatementService} изоҳи). POSTED+REVERSED - сторно қилинган
     * ҳужжат жуфти нолга тушади.
     *
     * <p>НЕГА контрол счёт, REVENUE/EXPENSE классификацияси ЭМАС: ҳисоб-
     * фактура/bill GROSS қиймати (солиқ + инвентар + харажат ҳаммаси) AR/AP
     * дебет/кредитига тушади; классификация бўйича олсак ИНВЕНТАР харид
     * (ASSET леги) «Жами харид»га кирмай, инвентар етказувчида кўрсаткич
     * чалғитувчи 0 бўларди (жонли smoke'да айнан шу кўринди). Контрол счёт
     * gross эса ҳар икки томонни ҳам қамрайди ва симметрик. Асосий восита
     * GL бўлгани учун ledger модулда (қоида №6); CASE ичида ишора
     * счётнинг нормал томонига мосланади.
     *
     * @param contactId    контакт id'си
     * @param detailType   контрол счёт detail_type: {@code
     *                     "ACCOUNTS_RECEIVABLE"} ёки {@code "ACCOUNTS_PAYABLE"}
     * @param sourceModule заряд манба модули: {@code "INVOICE"} ёки {@code "BILL"}
     * @return нормал ишорада мусбат жами (ҳаракат бўлмаса нол)
     */
    public BigDecimal contactBilledTotal(UUID contactId, String detailType, String sourceModule) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(
                        CASE WHEN a.detail_type = 'ACCOUNTS_RECEIVABLE'
                            THEN COALESCE(l.debit_base_amount, 0) - COALESCE(l.credit_base_amount, 0)
                            ELSE COALESCE(l.credit_base_amount, 0) - COALESCE(l.debit_base_amount, 0)
                        END), 0)
                FROM journal_entry_line l
                JOIN journal_entry je ON je.id = l.entry_id
                JOIN account a ON a.id = l.account_id
                WHERE l.contact_id = :cid
                  AND a.detail_type = :dt
                  AND je.source_module = :sm
                  AND je.status IN ('POSTED', 'REVERSED')
                """)
                .param("cid", contactId)
                .param("dt", detailType)
                .param("sm", sourceModule)
                .query(BigDecimal.class)
                .single();
    }

    /**
     * Контактнинг ОХИРГИ тўлов санаси (мижозда INVOICE_PAYMENT,
     * таъминотчида BILL_PAYMENT) ёки {@code null} - карточка «Охирги
     * тўлов» картаси учун. ФАҚАТ POSTED: сторно қилинган тўлов «охирги
     * тўлов» ҳисобланмайди (баланс инвариантидан фарқли - у ерда
     * REVERSED ҳам киради, лекин бу кўрсаткич факт тўловни билдиради).
     * Контакт кесими {@code contact_id}'дан (тўлов AR/AP леги контактни
     * ёзади). MAX(entry_date) агрегати - ҳаракат бўлмаса SQL NULL →
     * {@code null}.
     *
     * @param contactId    контакт id'си
     * @param sourceModule {@code "INVOICE_PAYMENT"} ёки {@code "BILL_PAYMENT"}
     * @return охирги тўлов санаси ёки {@code null}
     */
    public LocalDate lastPaymentDate(UUID contactId, String sourceModule) {
        return jdbc.sql("""
                SELECT MAX(je.entry_date)
                FROM journal_entry je
                JOIN journal_entry_line l ON l.entry_id = je.id
                WHERE l.contact_id = :cid
                  AND je.source_module = :sm
                  AND je.status = 'POSTED'
                """)
                .param("cid", contactId)
                .param("sm", sourceModule)
                .query(LocalDate.class)
                .optional()
                .orElse(null);
    }
}
