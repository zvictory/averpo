package com.averpo.erp.ledger;

import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.service.JournalEntryRequest;
import com.averpo.erp.ledger.service.JournalEntryRequest.Line;
import com.averpo.erp.ledger.service.PostingService;
import com.averpo.erp.ledger.service.ProfitAndLossService;
import com.averpo.erp.shared.domain.Money;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P&L: бўлим жойлашуви, QBO арифметикаси, давр фильтри, ноль сатрлар.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProfitAndLossServiceTest {

    /** Тестларда ишлатиладиган home валюта. */
    private static final String HOME = "UZS";

    /** Барча асосий тест проводкалар санаси. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 5);

    /** Ҳисобот даври боши (шу ой). */
    private static final LocalDate FROM = LocalDate.of(2026, 7, 1);

    /** Ҳисобот даври охири (шу ой). */
    private static final LocalDate TO = LocalDate.of(2026, 7, 31);

    @Autowired ProfitAndLossService profitAndLossService;
    @Autowired PostingService postingService;
    @Autowired AccountRepository accountRepository;
    @Autowired EntityManager em;

    /** Банк счёти - қарши томон учун. */
    private Account bank;

    /** Даромад счёти (INCOME). */
    private Account sales;

    /** Чегирма счёти (INCOME, contra - Dt қолдиқли). */
    private Account discounts;

    /** Таннарх счёти (COST_OF_GOODS_SOLD). */
    private Account cogs;

    /** Inventory актив счёти - COGS қарши томони. */
    private Account inventory;

    /** Операцион харажат счёти (EXPENSE). */
    private Account rent;

    /** Бошқа даромад счёти (OTHER_INCOME). */
    private Account interest;

    /** Бошқа харажат счёти (OTHER_EXPENSE). */
    private Account fx;

    /** Ҳар тест олдидан керакли счётларни яратади. */
    @BeforeEach
    void createAccounts() {
        bank = ensure("Банк ҳисобварағи", AccountDetailType.CHECKING);
        sales = ensure("Товар сотув даромади", AccountDetailType.SALES_OF_PRODUCT_INCOME);
        discounts = ensure("Берилган чегирмалар", AccountDetailType.DISCOUNTS_REFUNDS_GIVEN);
        cogs = ensure("Сотилган товар таннархи", AccountDetailType.SUPPLIES_MATERIALS_COGS);
        inventory = ensure("Товар захиралари", AccountDetailType.INVENTORY);
        rent = ensure("Ижара харажати", AccountDetailType.RENT_OR_LEASE_OF_BUILDINGS);
        interest = ensure("Фоиз даромади", AccountDetailType.INTEREST_EARNED);
        fx = ensure("Валюта курси фарқи", AccountDetailType.EXCHANGE_GAIN_OR_LOSS);
    }

    /** Ном бўйича мавжуд счётни олади ёки яратади. */
    private Account ensure(String name, AccountDetailType detailType) {
        return accountRepository.findByName(name).orElseGet(() ->
                accountRepository.save(new Account(
                        name, detailType, null, null, null, true, null)));
    }

    /** Home валютада оддий икки сатрли JE post қилади. */
    private void post(LocalDate date, Account debit, Account credit, String amount) {
        postingService.createAndPost(JournalEntryRequest.manual(date, "P&L тест", List.of(
                Line.debit(debit.getId(), Money.ofBase(new BigDecimal(amount), HOME), null),
                Line.credit(credit.getId(), Money.ofBase(new BigDecimal(amount), HOME), null))));
    }

    @Test
    void build_sectionsAndArithmetic_qboStructure() {
        post(DATE, bank, sales, "1000000");      // даромад
        post(DATE, discounts, bank, "50000");    // contra-даромад (Dt)
        post(DATE, cogs, inventory, "400000");   // таннарх
        post(DATE, rent, bank, "100000");        // операцион харажат
        post(DATE, bank, interest, "50000");     // бошқа даромад
        post(DATE, fx, bank, "20000");           // бошқа харажат
        em.flush();

        ProfitAndLossService.Report report = profitAndLossService.build(FROM, TO);

        // Даромад бўлими: сотув мусбат, чегирма манфий (contra)
        assertThat(row(report.income().rows(), "Товар сотув даромади").amount())
                .isEqualByComparingTo("1000000");
        assertThat(row(report.income().rows(), "Берилган чегирмалар").amount())
                .isEqualByComparingTo("-50000");
        assertThat(report.income().total()).isEqualByComparingTo("950000");

        // Таннарх ва Ялпи фойда
        assertThat(report.cogs().total()).isEqualByComparingTo("400000");
        assertThat(report.grossProfit()).isEqualByComparingTo("550000");

        // Харажатлар ва Операцион фойда
        assertThat(report.expenses().total()).isEqualByComparingTo("100000");
        assertThat(report.operatingIncome()).isEqualByComparingTo("450000");

        // Бошқа даромад/харажат ва Соф фойда
        assertThat(report.otherIncome().total()).isEqualByComparingTo("50000");
        assertThat(report.otherExpenses().total()).isEqualByComparingTo("20000");
        assertThat(report.netOtherIncome()).isEqualByComparingTo("30000");
        assertThat(report.netIncome()).isEqualByComparingTo("480000");
    }

    @Test
    void build_periodFilter_excludesOutside() {
        post(DATE, bank, sales, "300000");                  // давр ичида
        post(LocalDate.of(2026, 9, 10), bank, sales, "999000"); // даврдан ташқарида
        em.flush();

        ProfitAndLossService.Report report = profitAndLossService.build(FROM, TO);

        assertThat(report.income().total()).isEqualByComparingTo("300000");
        assertThat(report.netIncome()).isEqualByComparingTo("300000");
    }

    @Test
    void build_reversedEntry_zeroRowsHidden() {
        var posted = postingService.createAndPost(JournalEntryRequest.manual(
                DATE, "P&L тест", List.of(
                        Line.debit(bank.getId(), Money.ofBase(new BigDecimal("100000"), HOME), null),
                        Line.credit(sales.getId(), Money.ofBase(new BigDecimal("100000"), HOME), null))));
        postingService.reverse(posted.getId(), DATE, "сторно тест");
        em.flush();

        ProfitAndLossService.Report report = profitAndLossService.build(FROM, TO);

        // Сторно жуфти неттоси ноль - сатр яширилади, ҳамма жами ноль
        assertThat(report.income().rows()).isEmpty();
        assertThat(report.netIncome()).isEqualByComparingTo("0");
    }

    @Test
    void build_taxExpense_separateSection_netPreserved() {
        // IFRS-010 (IAS 1.82(b)): TAXES_PAID операцион харажатлардан ажралади
        Account tax = ensure("Даромад солиғи харажати", AccountDetailType.TAXES_PAID);
        post(DATE, bank, sales, "1000000");   // даромад
        post(DATE, rent, bank, "200000");     // операцион харажат
        post(DATE, tax, bank, "120000");      // солиқ харажати
        em.flush();

        ProfitAndLossService.Report report = profitAndLossService.build(FROM, TO);

        // Солиқ операцион харажатлар бўлимида ТАКРОРЛАНМАЙДИ
        assertThat(report.expenses().rows())
                .noneMatch(r -> r.name().equals("Даромад солиғи харажати"));
        assertThat(report.expenses().total()).isEqualByComparingTo("200000");
        assertThat(row(report.taxExpense().rows(), "Даромад солиғи харажати").amount())
                .isEqualByComparingTo("120000");
        assertThat(report.taxExpense().total()).isEqualByComparingTo("120000");

        // Тартиб арифметикаси: Солиққача 800 000 → Солиқ 120 000 → Соф 680 000
        assertThat(report.profitBeforeTax()).isEqualByComparingTo("800000");
        assertThat(report.netIncome()).isEqualByComparingTo("680000");
        // АСОСИЙ: ажратиш неттони ўзгартирмайди - Солиққача - Солиқ == Соф
        assertThat(report.profitBeforeTax().subtract(report.taxExpense().total()))
                .isEqualByComparingTo(report.netIncome());
    }

    @Test
    void build_noTaxActivity_profitBeforeTaxEqualsNet() {
        post(DATE, bank, sales, "400000");
        em.flush();

        ProfitAndLossService.Report report = profitAndLossService.build(FROM, TO);

        // Солиқ айланмаси йўқ - бўлим бўш, Солиққача == Соф (шаблон
        // оралиқ сатрларни яширади)
        assertThat(report.taxExpense().rows()).isEmpty();
        assertThat(report.profitBeforeTax()).isEqualByComparingTo(report.netIncome());
        assertThat(report.netIncome()).isEqualByComparingTo("400000");
    }

    /** Ном бўйича сатрни топади - бўлмаса дарров йиқилсин. */
    private static ProfitAndLossService.Row row(
            List<ProfitAndLossService.Row> rows, String name) {
        return rows.stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
    }
}
