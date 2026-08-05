package com.averpo.erp.ledger.service;

import com.averpo.erp.ledger.domain.AccountType;
import com.averpo.erp.shared.service.TxnClassService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * P&amp;L by Class (docs/modules/class-tracking.md, QBO паритети):
 * оддий P&amp;L тузилмаси, лекин ҳар ишлатилган Йўналиш алоҳида устун
 * («Ота:Бола» тўлиқ ном, sub-class алоҳида - rollup ЙЎҚ, QBO услуби) +
 * «Кўрсатилмаган» (class'сиз сатрлар) + Жами.
 *
 * <p>ИНВАРИАНТ (spec асосий тести): устунлар йиғиндиси ҳар сатрда ва
 * Net Income'да айнан оддий P&amp;L'га тенг - class фақат тег,
 * суммаларни қайта тақсимламайди. ProfitAndLossService'га АТАЙЛАБ
 * тегилмаган (параллел иш) - SQL ўша, фақат class_id кесими қўшилган.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProfitAndLossByClassService {

    /** Битта счёт сатри: устун кесимидаги қийматлар + сатр жамиси. */
    public record Row(String name, String code, List<BigDecimal> cells, BigDecimal total) { }

    /** Бўлим: сатрлар + устун кесимидаги жамилар + умумий жами. */
    public record Section(List<Row> rows, List<BigDecimal> totals, BigDecimal total) { }

    /**
     * Тайёр ҳисобот. columns - устун сарлавҳалари (охиргиси ҳар доим
     * «Кўрсатилмаган» ЭМАС - у фақат ишлатилган бўлса киради; Жами
     * устуни рўйхатга кирмайди, шаблон алоҳида чиқаради). Ҳар
     * List&lt;BigDecimal&gt; columns билан бир хил тартибда.
     */
    public record Report(LocalDate from, LocalDate to, List<String> columns,
                         Section income, Section cogs,
                         List<BigDecimal> grossProfit, BigDecimal grossProfitTotal,
                         Section expenses,
                         List<BigDecimal> operatingIncome, BigDecimal operatingIncomeTotal,
                         Section otherIncome, Section otherExpenses,
                         List<BigDecimal> netOtherIncome, BigDecimal netOtherIncomeTotal,
                         List<BigDecimal> netIncome, BigDecimal netIncomeTotal) { }

    /** SQL агрегат учун JdbcClient (ProfitAndLossService қолипи). */
    private final JdbcClient jdbc;

    /** Устун сарлавҳалари учун id → «Ота:Бола» ном (shared каталог). */
    private final TxnClassService txnClassService;

    /** Ҳисоботни [from, to] даврига қуради (иккала чегара билан). */
    public Report build(LocalDate from, LocalDate to) {
        // Хом маълумот: счёт × class_id × нетто (Cr-Dt)
        record Cell(UUID accountId, String name, String code, AccountType type,
                    UUID classId, BigDecimal net) { }
        List<Cell> cells = new ArrayList<>();
        jdbc.sql("""
                SELECT a.id, a.name, a.code, a.type, a.classification, l.class_id,
                       COALESCE(SUM(COALESCE(l.credit_base_amount, 0)
                           - COALESCE(l.debit_base_amount, 0)), 0) AS net
                FROM journal_entry_line l
                JOIN journal_entry je ON je.id = l.entry_id
                JOIN account a ON a.id = l.account_id
                WHERE je.status IN ('POSTED', 'REVERSED')
                  AND je.entry_date >= :from AND je.entry_date <= :to
                  AND a.classification IN ('REVENUE', 'EXPENSE')
                GROUP BY a.id, a.name, a.code, a.type, a.classification, l.class_id
                """)
                .param("from", from)
                .param("to", to)
                .query(rs -> {
                    AccountType type = safeType(rs.getString("type"),
                            rs.getString("classification"));
                    boolean creditNormal = type == AccountType.INCOME
                            || type == AccountType.OTHER_INCOME;
                    BigDecimal net = rs.getBigDecimal("net");
                    cells.add(new Cell(rs.getObject("id", UUID.class),
                            rs.getString("name"), rs.getString("code"), type,
                            rs.getObject("class_id", UUID.class),
                            creditNormal ? net : net.negate()));
                });

        // Устунлар: даврда ишлатилган class'лар («Ота:Бола» ном тартибида),
        // class'сиз қиймат бўлса охирида «Кўрсатилмаган» (null калит)
        Map<UUID, String> names = txnClassService.namesById();
        TreeMap<String, UUID> usedByName = new TreeMap<>();
        boolean hasUnspecified = false;
        for (Cell cell : cells) {
            if (cell.classId() == null) {
                hasUnspecified = true;
            } else {
                usedByName.put(names.getOrDefault(cell.classId(),
                        cell.classId().toString()), cell.classId());
            }
        }
        List<UUID> columnIds = new ArrayList<>(usedByName.values());
        List<String> columns = new ArrayList<>(usedByName.keySet());
        if (hasUnspecified || columns.isEmpty()) {
            // Бўш ҳисоботда ҳам битта устун қолсин - шаблон синмайди
            columnIds.add(null);
            columns.add(null); // controller i18n номини қўяди
        }
        Map<UUID, Integer> columnIndex = new HashMap<>();
        for (int i = 0; i < columnIds.size(); i++) {
            columnIndex.put(columnIds.get(i), i);
        }

        // Счёт сатрлари: тур бўйича гуруҳ, ҳар сатрда устун қийматлари
        Map<AccountType, Map<String, Row>> byType = new HashMap<>();
        for (Cell cell : cells) {
            Map<String, Row> rows = byType.computeIfAbsent(cell.type(),
                    t -> new LinkedHashMap<>());
            Row row = rows.computeIfAbsent(cell.name(), n -> new Row(
                    cell.name(), cell.code(), zeroCells(columns.size()), BigDecimal.ZERO));
            int idx = columnIndex.get(cell.classId());
            row.cells().set(idx, row.cells().get(idx).add(cell.net()));
            rows.put(cell.name(), new Row(row.name(), row.code(), row.cells(),
                    row.total().add(cell.net())));
        }

        Section income = section(byType.get(AccountType.INCOME), columns.size());
        Section cogs = section(byType.get(AccountType.COST_OF_GOODS_SOLD), columns.size());
        Section expenses = section(byType.get(AccountType.EXPENSE), columns.size());
        Section otherIncome = section(byType.get(AccountType.OTHER_INCOME), columns.size());
        Section otherExpenses = section(byType.get(AccountType.OTHER_EXPENSE), columns.size());

        List<BigDecimal> grossProfit = minus(income.totals(), cogs.totals());
        List<BigDecimal> operatingIncome = minus(grossProfit, expenses.totals());
        List<BigDecimal> netOther = minus(otherIncome.totals(), otherExpenses.totals());
        List<BigDecimal> netIncome = plus(operatingIncome, netOther);

        return new Report(from, to, columns,
                income, cogs,
                grossProfit, income.total().subtract(cogs.total()),
                expenses,
                operatingIncome, income.total().subtract(cogs.total())
                        .subtract(expenses.total()),
                otherIncome, otherExpenses,
                netOther, otherIncome.total().subtract(otherExpenses.total()),
                netIncome, income.total().subtract(cogs.total())
                        .subtract(expenses.total())
                        .add(otherIncome.total().subtract(otherExpenses.total())));
    }

    /** Нотаниш тур classification бўйича асосий бўлимга тушади (P&L қолипи). */
    private static AccountType safeType(String typeName, String classification) {
        try {
            return AccountType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            return "REVENUE".equals(classification)
                    ? AccountType.INCOME : AccountType.EXPENSE;
        }
    }

    /** n та нол қиймат (ўзгарувчан рўйхат - сатр йиғиш пайтида тўлади). */
    private static List<BigDecimal> zeroCells(int n) {
        List<BigDecimal> cells = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            cells.add(BigDecimal.ZERO);
        }
        return cells;
    }

    /** Бўлим: ноль сатрлар яширилади, ном тартибида; устун жамилари. */
    private static Section section(Map<String, Row> rows, int columnCount) {
        List<Row> visible = rows == null ? List.of() : rows.values().stream()
                .filter(r -> r.total().signum() != 0
                        || r.cells().stream().anyMatch(c -> c.signum() != 0))
                .sorted(Comparator.comparing(Row::name))
                .toList();
        List<BigDecimal> totals = zeroCells(columnCount);
        BigDecimal total = BigDecimal.ZERO;
        for (Row row : visible) {
            for (int i = 0; i < columnCount; i++) {
                totals.set(i, totals.get(i).add(row.cells().get(i)));
            }
            total = total.add(row.total());
        }
        return new Section(visible, totals, total);
    }

    /** Элемент-ба-элемент айирма. */
    private static List<BigDecimal> minus(List<BigDecimal> a, List<BigDecimal> b) {
        List<BigDecimal> result = new ArrayList<>(a.size());
        for (int i = 0; i < a.size(); i++) {
            result.add(a.get(i).subtract(b.get(i)));
        }
        return result;
    }

    /** Элемент-ба-элемент йиғинди. */
    private static List<BigDecimal> plus(List<BigDecimal> a, List<BigDecimal> b) {
        List<BigDecimal> result = new ArrayList<>(a.size());
        for (int i = 0; i < a.size(); i++) {
            result.add(a.get(i).add(b.get(i)));
        }
        return result;
    }
}
