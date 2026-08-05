package com.averpo.erp.ledger;

import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Payroll (23а) seed счётлари default chart'да detail type орқали
 * топилиши - spec: docs/modules/payroll.md. Инвариант 6: тизим счётлари
 * detail type орқали resolve қилинади (findSystemAccount), шунинг учун
 * PAYROLL_CLEARING/PAYROLL_TAX_PAYABLE ягона, PAYROLL_EXPENSES иккита
 * postable (+ Arbitr-126 дан «Иш ҳақи харажатлари» гуруҳ отаси).
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PayrollAccountSeedTest {

    @Autowired AccountService accountService;
    @Autowired AccountRepository accountRepository;

    @Test
    void defaultChart_seedsFourPayrollAccounts_byDetailType() {
        accountService.importDefaultChart();

        // PAYROLL_EXPENSES - иккита postable seed (харажат + иш берувчи
        // солиқ харажати); Arbitr-126 дан бери турда учинчи ёзув ҳам бор -
        // «Иш ҳақи харажатлари» гуруҳ отаси (postable=false), payroll
        // ном+postable бўйича топгани учун у ҳисобга кирмайди
        assertThat(accountRepository.findByDetailType(AccountDetailType.PAYROLL_EXPENSES)
                .stream().filter(Account::isPostable))
                .extracting(Account::getName)
                .containsExactlyInAnyOrder("Иш ҳақи харажати", "Иш ҳақи солиқ харажати");
        assertThat(accountRepository.findByDetailType(AccountDetailType.PAYROLL_EXPENSES)
                .stream().filter(a -> !a.isPostable()))
                .extracting(Account::getName)
                .containsExactly("Иш ҳақи харажатлари");

        // PAYROLL_CLEARING - ягона (ходим кесими субледжери; systemManaged)
        assertThat(accountRepository.findByDetailType(AccountDetailType.PAYROLL_CLEARING))
                .extracting(Account::getName)
                .containsExactly("Иш ҳақи бўйича мажбурият");

        // PAYROLL_TAX_PAYABLE - ягона (жамланган ушланма солиқлар)
        assertThat(accountRepository.findByDetailType(AccountDetailType.PAYROLL_TAX_PAYABLE))
                .extracting(Account::getName)
                .containsExactly("Иш ҳақи солиқлари мажбурияти");
    }

    @Test
    void singleInstancePayrollAccounts_resolveAsSystemAccount() {
        // Ягона detail type'лар findSystemAccount орқали топилиши шарт
        // (23б posting ходим кесими clearing'ни шу орқали олади)
        accountService.importDefaultChart();
        assertThat(accountService.findSystemAccount(AccountDetailType.PAYROLL_CLEARING))
                .isPresent();
        assertThat(accountService.findSystemAccount(AccountDetailType.PAYROLL_TAX_PAYABLE))
                .isPresent();
    }

    @Test
    void payrollClearing_isSystemManaged_expensesAndTaxAreNot() {
        // 23а қарори (AccountDetailType.systemManaged JavaDoc): clearing тизим
        // счёти (тўлов фақат PayrollPayment), харажат/солиқ мажбурияти эмас
        assertThat(AccountDetailType.PAYROLL_CLEARING.systemManaged()).isTrue();
        assertThat(AccountDetailType.PAYROLL_TAX_PAYABLE.systemManaged()).isFalse();
        assertThat(AccountDetailType.PAYROLL_EXPENSES.systemManaged()).isFalse();
    }
}
