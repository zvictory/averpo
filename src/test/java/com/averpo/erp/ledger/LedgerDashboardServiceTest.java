package com.averpo.erp.ledger;

import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.service.JournalEntryRequest;
import com.averpo.erp.ledger.service.JournalEntryRequest.Line;
import com.averpo.erp.ledger.service.LedgerDashboardService;
import com.averpo.erp.ledger.service.PostingService;
import com.averpo.erp.shared.domain.Money;
import com.averpo.erp.shared.service.CurrencyService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dashboard GL кесимлари: ойлик P&L, банк қолдиқлари, харажат тақсимоти.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LedgerDashboardServiceTest {

    /** Тестларда ишлатиладиган home валюта. */
    private static final String HOME = "UZS";

    /** Асосий тест ойи (июль 2026). */
    private static final YearMonth JULY = YearMonth.of(2026, 7);

    @Autowired LedgerDashboardService dashboardService;
    @Autowired PostingService postingService;
    @Autowired AccountRepository accountRepository;
    @Autowired CurrencyService currencyService;
    @Autowired EntityManager em;

    /** Home валютали банк счёти. */
    private Account bank;

    /** Даромад счёти. */
    private Account sales;

    /** Харажат счёти (ижара). */
    private Account rent;

    /** Иккинчи харажат счёти (банк харажатлари). */
    private Account fees;

    /** Ҳар тест олдидан керакли счётларни яратади. */
    @BeforeEach
    void createAccounts() {
        bank = ensure("Банк ҳисобварағи", AccountDetailType.CHECKING, null);
        sales = ensure("Товар сотув даромади", AccountDetailType.SALES_OF_PRODUCT_INCOME, null);
        rent = ensure("Ижара харажати", AccountDetailType.RENT_OR_LEASE_OF_BUILDINGS, null);
        fees = ensure("Банк харажатлари", AccountDetailType.BANK_CHARGES, null);
    }

    /** Ном бўйича мавжуд счётни олади ёки яратади. */
    private Account ensure(String name, AccountDetailType detailType,
                           com.averpo.erp.shared.domain.Currency currency) {
        return accountRepository.findByName(name).orElseGet(() ->
                accountRepository.save(new Account(
                        name, detailType, null, null, null, true, currency)));
    }

    /** Home валютада оддий икки сатрли JE post қилади. */
    private void post(LocalDate date, Account debit, Account credit, String amount) {
        postingService.createAndPost(JournalEntryRequest.manual(date, "Dash тест", List.of(
                Line.debit(debit.getId(), Money.ofBase(new BigDecimal(amount), HOME), null),
                Line.credit(credit.getId(), Money.ofBase(new BigDecimal(amount), HOME), null))));
    }

    @Test
    void monthlyPl_bucketsAndZeroFill() {
        post(LocalDate.of(2026, 5, 10), bank, sales, "500000");   // май: даромад
        post(LocalDate.of(2026, 7, 5), bank, sales, "1000000");   // июль: даромад
        post(LocalDate.of(2026, 7, 6), rent, bank, "300000");     // июль: харажат
        em.flush();

        List<LedgerDashboardService.MonthPl> months =
                dashboardService.monthlyPl(JULY.minusMonths(5), JULY);

        assertThat(months).hasSize(6);
        assertThat(months.get(0).month()).isEqualTo(YearMonth.of(2026, 2));
        assertThat(months.get(5).month()).isEqualTo(JULY);

        LedgerDashboardService.MonthPl may = months.get(3);
        assertThat(may.income()).isEqualByComparingTo("500000");
        assertThat(may.expense()).isEqualByComparingTo("0");

        // Ҳаракатсиз ой нол билан тўлдирилади (график узилмасин)
        LedgerDashboardService.MonthPl june = months.get(4);
        assertThat(june.income()).isEqualByComparingTo("0");

        LedgerDashboardService.MonthPl july = months.get(5);
        assertThat(july.income()).isEqualByComparingTo("1000000");
        assertThat(july.expense()).isEqualByComparingTo("300000");
    }

    @Test
    void monthlyCashFlow_inflowOutflowSides_emptyMonthZero() {
        // DEC-036: BANK счёт Dt - кирим, Cr - чиқим; бўш ой нол билан
        post(LocalDate.of(2026, 5, 10), bank, sales, "250000");   // май: кирим
        post(LocalDate.of(2026, 7, 5), bank, sales, "1000000");   // июль: кирим
        post(LocalDate.of(2026, 7, 6), rent, bank, "400000");     // июль: чиқим
        em.flush();

        List<LedgerDashboardService.MonthCashFlow> flows =
                dashboardService.monthlyCashFlow(JULY.minusMonths(2), JULY);

        assertThat(flows).hasSize(3);
        // Май: фақат кирим томони
        assertThat(flows.get(0).month()).isEqualTo(YearMonth.of(2026, 5));
        assertThat(flows.get(0).inflow()).isEqualByComparingTo("250000");
        assertThat(flows.get(0).outflow()).isEqualByComparingTo("0");
        // Июнь: ҳаракатсиз ой нол билан тўлдирилади (график узилмасин)
        assertThat(flows.get(1).month()).isEqualTo(YearMonth.of(2026, 6));
        assertThat(flows.get(1).inflow()).isEqualByComparingTo("0");
        assertThat(flows.get(1).outflow()).isEqualByComparingTo("0");
        // Июль: кирим Dt томонда, чиқим Cr томонда - алмашиб кетмаган
        assertThat(flows.get(2).inflow()).isEqualByComparingTo("1000000");
        assertThat(flows.get(2).outflow()).isEqualByComparingTo("400000");
    }

    @Test
    void bankBalances_homeAndForeign() {
        Account usdBank = ensure("USD банк", AccountDetailType.SAVINGS,
                currencyService.require("USD"));
        post(LocalDate.of(2026, 7, 5), bank, sales, "700000");
        // Чет валютали банк: 100 USD, курс 12 000 - base 1 200 000
        postingService.createAndPost(JournalEntryRequest.manual(
                LocalDate.of(2026, 7, 5), "Dash тест", List.of(
                        Line.debit(usdBank.getId(),
                                Money.of(new BigDecimal("100"), "USD", new BigDecimal("12000")), null),
                        Line.credit(sales.getId(),
                                Money.ofBase(new BigDecimal("1200000"), HOME), null))));
        em.flush();

        List<LedgerDashboardService.BankBalance> balances = dashboardService.bankBalances();

        LedgerDashboardService.BankBalance home = balance(balances, "Банк ҳисобварағи");
        assertThat(home.currencyCode()).isNull();
        assertThat(home.amount()).isEqualByComparingTo("700000");
        assertThat(home.baseAmount()).isEqualByComparingTo("700000");

        LedgerDashboardService.BankBalance usd = balance(balances, "USD банк");
        assertThat(usd.currencyCode()).isEqualTo("USD");
        assertThat(usd.amount()).isEqualByComparingTo("100");
        assertThat(usd.baseAmount()).isEqualByComparingTo("1200000");
    }

    @Test
    void bankBalances_inactiveExcluded_zeroIncluded() {
        Account idle = ensure("Ҳаракатсиз банк", AccountDetailType.MONEY_MARKET, null);
        Account inactive = ensure("Ёпилган банк", AccountDetailType.TRUST_ACCOUNTS, null);
        inactive.setActive(false);
        em.flush();

        List<LedgerDashboardService.BankBalance> balances = dashboardService.bankBalances();

        // Ҳаракатсиз фаол счёт ноль билан кўринади (QBO услуби)
        assertThat(balance(balances, "Ҳаракатсиз банк").baseAmount())
                .isEqualByComparingTo("0");
        assertThat(balances).noneMatch(b -> b.name().equals("Ёпилган банк"));
    }

    @Test
    void expenseBreakdown_orderedPositiveOnly() {
        post(LocalDate.of(2026, 7, 3), rent, bank, "100000");
        post(LocalDate.of(2026, 7, 4), fees, bank, "40000");
        // Қайтарилган харажат (нетто манфий) - тақсимотга кирмайди
        Account refund = ensure("Қайтган харажат", AccountDetailType.TRAVEL, null);
        post(LocalDate.of(2026, 7, 4), bank, refund, "5000");
        // Давр ташқарисидаги харажат ҳам кирмайди
        post(LocalDate.of(2026, 6, 20), rent, bank, "999000");
        em.flush();

        List<LedgerDashboardService.ExpenseSlice> slices = dashboardService
                .expenseBreakdown(JULY.atDay(1), JULY.atEndOfMonth());

        assertThat(slices).extracting(LedgerDashboardService.ExpenseSlice::name)
                .containsExactly("Ижара харажати", "Банк харажатлари");
        assertThat(slices.get(0).amount()).isEqualByComparingTo("100000");
        assertThat(slices.get(1).amount()).isEqualByComparingTo("40000");
    }

    /** Ном бўйича банк қолдиғини топади - бўлмаса дарров йиқилсин. */
    private static LedgerDashboardService.BankBalance balance(
            List<LedgerDashboardService.BankBalance> balances, String name) {
        return balances.stream().filter(b -> b.name().equals(name)).findFirst().orElseThrow();
    }
}
