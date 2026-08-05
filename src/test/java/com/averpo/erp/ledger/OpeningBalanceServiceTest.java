package com.averpo.erp.ledger;

import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.domain.EntryStatus;
import com.averpo.erp.ledger.domain.JournalEntry;
import com.averpo.erp.ledger.domain.JournalEntryLine;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.service.OpeningBalanceService;
import com.averpo.erp.ledger.service.PostingException;
import com.averpo.erp.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Opening balance проводкалари (posting-rules.md, «Очилиш қолдиқлари»):
 * ТЕМИР ҚОИДА №7 - ҳар posting логикага debit == credit assert.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OpeningBalanceServiceTest {

    /** Тест home валютаси (CompanySettings default). */
    private static final String HOME = "UZS";

    /** Қолдиқ ҳолати санаси. */
    private static final LocalDate AS_OF = LocalDate.of(2026, 1, 1);

    @Autowired OpeningBalanceService openingBalanceService;
    @Autowired AccountRepository accountRepository;
    @Autowired com.averpo.erp.shared.service.CurrencyService currencyService;

    /** Home валютадаги банк счёти (актив). */
    private Account bank;

    /** Пассив счёт (кредит табиий томон тести). */
    private Account loan;

    /** Даромад счёти - BR-COA-005 рад этилиши тести учун. */
    private Account income;

    /** Opening Balance Equity тизим счёти. */
    private Account obe;

    /** Ҳар тест олдидан счётлар (rollback тозалайди). */
    @BeforeEach
    void createAccounts() {
        bank = ensure("OB тест банк", AccountDetailType.CHECKING);
        loan = ensure("OB тест кредит", AccountDetailType.LOAN_PAYABLE);
        income = ensure("OB тест даромад", AccountDetailType.SALES_OF_PRODUCT_INCOME);
        obe = ensure("OB тест OBE", AccountDetailType.OPENING_BALANCE_EQUITY);
    }

    /** Ном бўйича мавжуд счётни олади ёки яратади. */
    private Account ensure(String name, AccountDetailType detailType) {
        return accountRepository.findByName(name).orElseGet(() ->
                accountRepository.save(new Account(
                        name, detailType, null, null, null, true, null)));
    }

    @Test
    void enter_assetPositive_debitsAccountCreditsObe() {
        JournalEntry entry = openingBalanceService.enter(
                bank.getId(), new BigDecimal("5000000"), AS_OF, null);

        assertThat(entry.getStatus()).isEqualTo(EntryStatus.POSTED);
        assertThat(entry.getSourceModule()).isEqualTo(OpeningBalanceService.SOURCE_MODULE);
        assertThat(entry.getSourceDocumentId()).isEqualTo(bank.getId());

        JournalEntryLine first = entry.getLines().get(0);
        JournalEntryLine second = entry.getLines().get(1);
        // Актив мусбат: счёт дебети, OBE кредити
        assertThat(first.getAccount().getId()).isEqualTo(bank.getId());
        assertThat(first.getDebit().getBaseAmount()).isEqualByComparingTo("5000000");
        assertThat(second.getAccount().getId()).isEqualTo(obe.getId());
        assertThat(second.getCredit().getBaseAmount()).isEqualByComparingTo("5000000");
    }

    @Test
    void enter_liabilityPositive_creditsAccountDebitsObe() {
        JournalEntry entry = openingBalanceService.enter(
                loan.getId(), new BigDecimal("3000000"), AS_OF, null);

        JournalEntryLine first = entry.getLines().get(0);
        JournalEntryLine second = entry.getLines().get(1);
        // Пассив мусбат: OBE дебети, счёт кредити
        assertThat(first.getAccount().getId()).isEqualTo(obe.getId());
        assertThat(first.getDebit().getBaseAmount()).isEqualByComparingTo("3000000");
        assertThat(second.getAccount().getId()).isEqualTo(loan.getId());
        assertThat(second.getCredit().getBaseAmount()).isEqualByComparingTo("3000000");
    }

    @Test
    void enter_assetNegative_flipsSides() {
        // Манфий актив (overdraft): счёт кредит томонга тушади
        JournalEntry entry = openingBalanceService.enter(
                bank.getId(), new BigDecimal("-700000"), AS_OF, null);

        JournalEntryLine first = entry.getLines().get(0);
        assertThat(first.getAccount().getId()).isEqualTo(obe.getId());
        assertThat(first.getDebit().getBaseAmount()).isEqualByComparingTo("700000");
        assertThat(entry.getLines().get(1).getCredit().getBaseAmount())
                .isEqualByComparingTo("700000");
    }

    @Test
    void enter_incomeAccount_rejected() {
        assertThatThrownBy(() -> openingBalanceService.enter(
                income.getId(), BigDecimal.TEN, AS_OF, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("balance-sheet");
    }

    @Test
    void enter_twice_rejectedByIdempotency() {
        openingBalanceService.enter(bank.getId(), new BigDecimal("100"), AS_OF, null);

        // sourceDocumentId = account id - BR-LED-012 иккинчисини тўсади
        assertThatThrownBy(() -> openingBalanceService.enter(
                bank.getId(), new BigDecimal("200"), AS_OF, null))
                .isInstanceOf(PostingException.class)
                .hasMessageContaining("аллақачон GL'да");
    }

    @Test
    void enter_foreignCurrencyAccount_requiresRate_andConvertsToBase() {
        Account usdBank = accountRepository.findByName("OB тест USD банк").orElseGet(() ->
                accountRepository.save(new Account("OB тест USD банк",
                        AccountDetailType.CHECKING, null, null, null, true,
                        currencyService.require("USD"))));

        // Курссиз - BR-COA-007
        assertThatThrownBy(() -> openingBalanceService.enter(
                usdBank.getId(), new BigDecimal("100"), AS_OF, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("курс");

        JournalEntry entry = openingBalanceService.enter(
                usdBank.getId(), new BigDecimal("100"), AS_OF, new BigDecimal("12600"));

        JournalEntryLine accountLine = entry.getLines().get(0);
        JournalEntryLine obeLine = entry.getLines().get(1);
        // Счёт сатри ўз валютасида, OBE сатри home'да - baseAmount тенг
        assertThat(accountLine.getDebit().getCurrency()).isEqualTo("USD");
        assertThat(accountLine.getDebit().getBaseAmount()).isEqualByComparingTo("1260000");
        assertThat(obeLine.getCredit().getCurrency()).isEqualTo(HOME);
        assertThat(obeLine.getCredit().getBaseAmount()).isEqualByComparingTo("1260000");
    }

    @Test
    void enter_zeroAmount_rejected() {
        assertThatThrownBy(() -> openingBalanceService.enter(
                bank.getId(), BigDecimal.ZERO, AS_OF, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("нолдан фарқли");
    }
}
