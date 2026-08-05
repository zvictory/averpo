package com.averpo.erp.ledger;

import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.service.JournalEntryRequest;
import com.averpo.erp.ledger.service.JournalEntryRequest.Line;
import com.averpo.erp.ledger.service.PostingService;
import com.averpo.erp.ledger.service.TrialBalanceService;
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
 * Айланма-қолдиқ ведомости тестлари.
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TrialBalanceServiceTest {

    /** Тестларда ишлатиладиган home валюта. */
    private static final String HOME = "UZS";

    /** Барча тест проводкалар санаси. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 5);

    @Autowired TrialBalanceService trialBalanceService;
    @Autowired PostingService postingService;
    @Autowired AccountRepository accountRepository;
    @Autowired EntityManager em;

    /** Банк счёти. */
    private Account bank;

    /** Даромад счёти. */
    private Account sales;

    /** Ҳар тест олдидан керакли счётларни яратади. */
    @BeforeEach
    void createAccounts() {
        bank = ensure("Банк ҳисобварағи", AccountDetailType.CHECKING);
        sales = ensure("Товар сотув даромади", AccountDetailType.SALES_OF_PRODUCT_INCOME);
    }

    /** Ном бўйича мавжуд счётни олади ёки яратади. */
    private Account ensure(String name, AccountDetailType detailType) {
        return accountRepository.findByName(name).orElseGet(() ->
                accountRepository.save(new Account(
                        name, detailType, null, null, null, true, null)));
    }

    @Test
    void build_showsTurnoverAndClosing_balanced() {
        BigDecimal amount = new BigDecimal("1000000");
        postingService.createAndPost(JournalEntryRequest.manual(DATE, "Сотув", List.of(
                Line.debit(bank.getId(), Money.ofBase(amount, HOME), null),
                Line.credit(sales.getId(), Money.ofBase(amount, HOME), null))));

        // TrialBalance SQL билан ўқийди - Hibernate ўзгаришларини flush қиламиз
        em.flush();

        List<TrialBalanceService.Row> rows = trialBalanceService.build(
                DATE.withDayOfMonth(1), DATE.plusDays(1));

        TrialBalanceService.Row bankRow = row(rows, "Банк ҳисобварағи");
        assertThat(bankRow.debitTurnover()).isEqualByComparingTo(amount);
        assertThat(bankRow.closing()).isEqualByComparingTo(amount);

        TrialBalanceService.Row salesRow = row(rows, "Товар сотув даромади");
        assertThat(salesRow.creditTurnover()).isEqualByComparingTo(amount);
        assertThat(salesRow.closing()).isEqualByComparingTo(amount.negate());

        // Умумий Dt айланма == Cr айланма (ledger балансда)
        BigDecimal dt = rows.stream().map(TrialBalanceService.Row::debitTurnover)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ct = rows.stream().map(TrialBalanceService.Row::creditTurnover)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(dt).isEqualByComparingTo(ct);
    }

    @Test
    void build_reversedEntry_netsToZero() {
        BigDecimal amount = new BigDecimal("500000");
        var posted = postingService.createAndPost(JournalEntryRequest.manual(DATE, "Сотув", List.of(
                Line.debit(bank.getId(), Money.ofBase(amount, HOME), null),
                Line.credit(sales.getId(), Money.ofBase(amount, HOME), null))));
        postingService.reverse(posted.getId(), DATE, "сторно тест");
        em.flush();

        List<TrialBalanceService.Row> rows = trialBalanceService.build(
                DATE.withDayOfMonth(1), DATE.plusDays(1));

        // Асл + сторно - нетто ноль, лекин айланмада иккиси ҳам кўринади
        TrialBalanceService.Row bankRow = row(rows, "Банк ҳисобварағи");
        assertThat(bankRow.closing()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bankRow.debitTurnover()).isEqualByComparingTo(amount);
        assertThat(bankRow.creditTurnover()).isEqualByComparingTo(amount);
    }

    /** Ном бўйича сатрни топади - тестда бўлмаса дарров йиқилсин. */
    private static TrialBalanceService.Row row(List<TrialBalanceService.Row> rows, String name) {
        return rows.stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
    }
}
