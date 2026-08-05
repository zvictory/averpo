package com.averpo.erp.ledger.service;

import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.domain.AccountType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Profit &amp; Loss - QBO тузилмасида (docs/modules/reports.md):
 * Даромад → COGS → Ялпи фойда → Харажатлар → Операцион фойда →
 * Бошқа даромад/харажат → Солиққача фойда → Солиқ харажати →
 * Соф фойда (солиқ сатри - IAS 1.82(b), IFRS-010).
 *
 * <p>Trial Balance услуби: JdbcClient SQL агрегат, POSTED+REVERSED,
 * барча суммалар home валютада. Ишора: даромад Cr-Dt, харажат Dt-Cr -
 * мусбат сон нормал оқим.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProfitAndLossService {

    /**
     * Битта счёт сатри. Суммаси home валютада, ишораси
     * нормализацияланган (даромад Cr-Dt, харажат Dt-Cr).
     *
     * @param accountId drill-down («Счёт амаллари») учун счёт id'си
     * @param name      счёт номи (unique идентификатор)
     * @param code      ихтиёрий счёт рақами, бўлмаса null
     * @param amount    даврдаги нетто айланма
     */
    public record Row(UUID accountId, String name, String code, BigDecimal amount) { }

    /**
     * Ҳисобот бўлими (Даромад, COGS, Харажатлар...).
     *
     * @param rows  ноль бўлмаган сатрлар, ном тартибида
     * @param total бўлим жамиси
     */
    public record Section(List<Row> rows, BigDecimal total) { }

    /**
     * Тайёр ҳисобот - шаблон тўғридан-тўғри render қилади.
     * Арифметика QBO'ники.
     *
     * @param from давр боши (шу кун билан)
     * @param to давр охири (шу кун билан)
     * @param income Даромад (INCOME)
     * @param cogs Сотилган товар таннархи (COST_OF_GOODS_SOLD)
     * @param grossProfit Ялпи фойда = Даромад - COGS
     * @param expenses Харажатлар (EXPENSE)
     * @param operatingIncome Операцион фойда = Ялпи - Харажатлар
     * @param otherIncome Бошқа даромад (OTHER_INCOME)
     * @param otherExpenses Бошқа харажат (OTHER_EXPENSE)
     * @param netOtherIncome Бошқа фойда (нетто) = Бошқа даромад - Бошқа харажат
     * @param profitBeforeTax Солиққача фойда = Операцион + Бошқа (нетто) -
     *                        IAS 1.82(b) талаби учун оралиқ сатр (IFRS-010)
     * @param taxExpense Солиқ харажати (EXPENSE ичидаги TAXES_PAID detail) -
     *                   операцион харажатлардан ажратиб кўрсатилади,
     *                   GL/detail type ўзгармайди
     * @param netIncome Соф фойда = Солиққача фойда - Солиқ харажати
     */
    public record Report(LocalDate from, LocalDate to,
                         Section income, Section cogs, BigDecimal grossProfit,
                         Section expenses, BigDecimal operatingIncome,
                         Section otherIncome, Section otherExpenses,
                         BigDecimal netOtherIncome, BigDecimal profitBeforeTax,
                         Section taxExpense, BigDecimal netIncome) { }

    /** SQL агрегат учун JdbcClient - Hibernate'ни четлаб ўтади. */
    private final JdbcClient jdbc;

    /** Ҳисоботни [from, to] даврига қуради (иккала чегара билан). */
    public Report build(LocalDate from, LocalDate to) {
        // Хом нетто (Cr-Dt) тур кесимида - даромад учун тайёр ишора,
        // харажат сатрларида кейин negate қилинади
        Map<AccountType, List<Row>> byType = new EnumMap<>(AccountType.class);
        // Солиқ харажати (TAXES_PAID) алоҳида бўлимга ажратилади -
        // IAS 1.82(b) кўрсатиш талаби, GL/detail type ўзгармайди (IFRS-010)
        List<Row> taxRows = new ArrayList<>();
        jdbc.sql("""
                SELECT a.id, a.name, a.code, a.type, a.classification, a.detail_type,
                       COALESCE(SUM(COALESCE(l.credit_base_amount, 0)
                           - COALESCE(l.debit_base_amount, 0)), 0) AS net
                FROM journal_entry_line l
                JOIN journal_entry je ON je.id = l.entry_id
                JOIN account a ON a.id = l.account_id
                WHERE je.status IN ('POSTED', 'REVERSED')
                  AND je.entry_date >= :from AND je.entry_date <= :to
                  AND a.classification IN ('REVENUE', 'EXPENSE')
                GROUP BY a.id, a.name, a.code, a.type, a.classification, a.detail_type
                """)
                .param("from", from)
                .param("to", to)
                .query(rs -> {
                    AccountType type = safeType(rs.getString("type"),
                            rs.getString("classification"));
                    boolean creditNormal = type == AccountType.INCOME
                            || type == AccountType.OTHER_INCOME;
                    BigDecimal net = rs.getBigDecimal("net");
                    Row row = new Row(rs.getObject("id", UUID.class),
                            rs.getString("name"), rs.getString("code"),
                            creditNormal ? net : net.negate());
                    if (type == AccountType.EXPENSE && AccountDetailType.TAXES_PAID
                            .name().equals(rs.getString("detail_type"))) {
                        taxRows.add(row);
                        return;
                    }
                    byType.computeIfAbsent(type, t -> new ArrayList<>()).add(row);
                });

        Section income = section(byType, AccountType.INCOME);
        Section cogs = section(byType, AccountType.COST_OF_GOODS_SOLD);
        Section expenses = section(byType, AccountType.EXPENSE);
        Section otherIncome = section(byType, AccountType.OTHER_INCOME);
        Section otherExpenses = section(byType, AccountType.OTHER_EXPENSE);
        Section taxExpense = section(taxRows);

        BigDecimal grossProfit = income.total().subtract(cogs.total());
        BigDecimal operatingIncome = grossProfit.subtract(expenses.total());
        BigDecimal netOtherIncome = otherIncome.total().subtract(otherExpenses.total());
        BigDecimal profitBeforeTax = operatingIncome.add(netOtherIncome);
        BigDecimal netIncome = profitBeforeTax.subtract(taxExpense.total());

        return new Report(from, to, income, cogs, grossProfit,
                expenses, operatingIncome, otherIncome, otherExpenses,
                netOtherIncome, profitBeforeTax, taxExpense, netIncome);
    }

    /**
     * Нотаниш тур (эски/кўчирилган база) 500 бермасин: classification'и
     * бўйича асосий бўлимга тушади - сумма йўқолмайди.
     */
    private static AccountType safeType(String typeName, String classification) {
        try {
            return AccountType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            return "REVENUE".equals(classification)
                    ? AccountType.INCOME : AccountType.EXPENSE;
        }
    }

    /** Бир турдаги бўлим: ноль сатрлар яширилади, ном тартибида. */
    private static Section section(Map<AccountType, List<Row>> byType, AccountType type) {
        return section(byType.getOrDefault(type, List.of()));
    }

    /** Тайёр сатрлардан бўлим: ноль сатрлар яширилади, ном тартибида. */
    private static Section section(List<Row> raw) {
        List<Row> rows = raw.stream()
                .filter(r -> r.amount().signum() != 0)
                .sorted(Comparator.comparing(Row::name))
                .toList();
        BigDecimal total = rows.stream().map(Row::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Section(rows, total);
    }
}
