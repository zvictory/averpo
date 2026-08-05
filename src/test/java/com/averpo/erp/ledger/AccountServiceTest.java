package com.averpo.erp.ledger;

import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Счёт CRUD валидациялари: иерархия цикли, код дубликати, тизим тури дубликати.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountServiceTest {

    @Autowired AccountService accountService;
    @Autowired com.averpo.erp.ledger.repo.AccountRepository accountRepository;
    @Autowired com.averpo.erp.shared.service.CurrencyService currencyService;

    @Test
    void update_parentCycle_rejected() {
        // A → B иерархияси: A'ни B'га боғласак цикл бўлади
        Account a = accountService.create("Гуруҳ A", AccountDetailType.CHECKING,
                null, null, null, false, null);
        Account b = accountService.create("Счёт B", AccountDetailType.CHECKING,
                null, null, a.getId(), true, null);

        assertThatThrownBy(() -> accountService.update(a.getId(), a.getName(),
                a.getDetailType(), null, null, b.getId(), false, null, true))
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class)
                .hasMessageContaining("цикл");

        // Ўзига ота бўлиш ҳам шу ҳимояга киради
        assertThatThrownBy(() -> accountService.update(a.getId(), a.getName(),
                a.getDetailType(), null, null, a.getId(), false, null, true))
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class)
                .hasMessageContaining("цикл");
    }

    @Test
    void create_blankNameOrMissingDetailType_rejected() {
        // Tampered request (параметрсиз POST) controller'да NPE=500
        // бермай, service'даги BR-COA-009/008 билан қайтиши шарт
        assertThatThrownBy(() -> accountService.create("  ", AccountDetailType.CHECKING,
                null, null, null, true, null))
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class)
                .satisfies(e -> assertThat(
                        ((com.averpo.erp.shared.exception.BusinessRuleException) e).getCode())
                        .isEqualTo("BR-COA-009"));

        assertThatThrownBy(() -> accountService.create("Янги счёт", null,
                null, null, null, true, null))
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class)
                .satisfies(e -> assertThat(
                        ((com.averpo.erp.shared.exception.BusinessRuleException) e).getCode())
                        .isEqualTo("BR-COA-008"));
    }

    @Test
    void update_duplicateCode_rejected() {
        accountService.create("Касса-1", AccountDetailType.CASH_ON_HAND,
                "1010", null, null, true, null);
        Account other = accountService.create("Касса-2", AccountDetailType.CASH_ON_HAND,
                "1011", null, null, true, null);

        // 1011 счётига 1010 кодини бериш - банд, тушунарли хато
        assertThatThrownBy(() -> accountService.update(other.getId(), other.getName(),
                other.getDetailType(), "1010", null, null, true, null, true))
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class)
                .hasMessageContaining("1010");

        // Ўз кодини сақлаб қолиш - муаммосиз
        Account updated = accountService.update(other.getId(), other.getName(),
                other.getDetailType(), "1011", null, null, true, null, true);
        assertThat(updated.getCode()).isEqualTo("1011");
    }

    @Test
    void requireSystemAccount_notSingle_rejected() {
        // Иккита фаол postable счёт - detail type ноаниқ: findSystemAccount
        // empty беради, мажбурий вариант эса BR-LED-021 билан йиқилиши шарт
        accountService.create("Банк-1", AccountDetailType.CHECKING,
                null, null, null, true, null);
        accountService.create("Банк-2", AccountDetailType.CHECKING,
                null, null, null, true, null);

        assertThat(accountService.findSystemAccount(AccountDetailType.CHECKING))
                .isEmpty();
        assertThatThrownBy(() ->
                accountService.requireSystemAccount(AccountDetailType.CHECKING))
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class)
                .satisfies(e -> assertThat(
                        ((com.averpo.erp.shared.exception.BusinessRuleException) e).getCode())
                        .isEqualTo("BR-LED-021"));
    }

    @Test
    void create_secondActiveSystemDetailType_rejected() {
        // Arbitr-060 жонли ҳодиса ҳимояси: тизим турида (AP) фаол счёт
        // турганда иккинчисини яратиш BR-COA-010 билан рад, хабарда мавжуд ном
        accountService.create("060 AP биринчи", AccountDetailType.ACCOUNTS_PAYABLE,
                null, null, null, true, null);

        assertThatThrownBy(() -> accountService.create("060 AP иккинчи",
                AccountDetailType.ACCOUNTS_PAYABLE, null, null, null, true, null))
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class)
                .satisfies(e -> assertThat(
                        ((com.averpo.erp.shared.exception.BusinessRuleException) e).getCode())
                        .isEqualTo("BR-COA-010"))
                .hasMessageContaining("060 AP биринчи");
    }

    @Test
    void update_reactivateWhenAnotherActiveSystemAccount_rejected() {
        // Деактив тизим счётини бошқа фаол тургани ҳолда қайта активлаштириш
        // ҳам BR-COA-010 (карта: «деактивни активлаштиришда ҳам текширилади»)
        Account first = accountService.create("060 AP реактив-1",
                AccountDetailType.ACCOUNTS_PAYABLE, null, null, null, true, null);
        accountService.update(first.getId(), first.getName(), first.getDetailType(),
                null, null, null, true, null, false);

        // Тур бўшади - янгисини яратиш очиқ
        Account second = accountService.create("060 AP реактив-2",
                AccountDetailType.ACCOUNTS_PAYABLE, null, null, null, true, null);

        assertThatThrownBy(() -> accountService.update(first.getId(), first.getName(),
                first.getDetailType(), null, null, null, true, null, true))
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class)
                .satisfies(e -> assertThat(
                        ((com.averpo.erp.shared.exception.BusinessRuleException) e).getCode())
                        .isEqualTo("BR-COA-010"))
                .hasMessageContaining("060 AP реактив-2");

        // Фаол счётнинг ЎЗИНИ фаол ҳолда сақлаш дубликат саналмайди (selfId)
        Account saved = accountService.update(second.getId(), second.getName(),
                second.getDetailType(), null, null, null, true, null, true);
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void update_changeDetailTypeToOccupiedSystemType_rejected() {
        // Оддий счёт турини банд тизим турига алмаштириш ҳам шу ҳимоядан ўтади
        accountService.create("060 солиқ эгаси", AccountDetailType.SALES_TAX_PAYABLE,
                null, null, null, true, null);
        Account other = accountService.create("060 оддий мажбурият",
                AccountDetailType.OTHER_CURRENT_LIABILITIES, null, null, null, true, null);

        assertThatThrownBy(() -> accountService.update(other.getId(), other.getName(),
                AccountDetailType.SALES_TAX_PAYABLE, null, null, null, true, null, true))
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class)
                .satisfies(e -> assertThat(
                        ((com.averpo.erp.shared.exception.BusinessRuleException) e).getCode())
                        .isEqualTo("BR-COA-010"))
                .hasMessageContaining("060 солиқ эгаси");
    }

    @Test
    void create_secondPayrollExpenses_allowed() {
        // PAYROLL_EXPENSES истисно (Arbitr-060): payroll НОМ бўйича топади,
        // атайлаб иккита счёт бўлади - ҳимоя бу турни чекламайди
        accountService.create("060 иш ҳақи харажати", AccountDetailType.PAYROLL_EXPENSES,
                null, null, null, true, null);
        Account second = accountService.create("060 иш ҳақи солиқ харажати",
                AccountDetailType.PAYROLL_EXPENSES, null, null, null, true, null);

        assertThat(second.isActive()).isTrue();
    }

    @Test
    void requireSystemAccount_messages_distinguishMissingAndMultiple() {
        // Arbitr-060: BR-LED-021 хабари иккига ажралди. 0 та - «топилмади»
        // (chart юкланмаган янги база ҳолати)
        assertThatThrownBy(() ->
                accountService.requireSystemAccount(AccountDetailType.INVENTORY_CLEARING))
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class)
                .satisfies(e -> assertThat(
                        ((com.averpo.erp.shared.exception.BusinessRuleException) e).getCode())
                        .isEqualTo("BR-LED-021"))
                .hasMessageContaining("топилмади");

        // Legacy дубликат (жонли сервердаги ҳолат): BR-COA-010 ҳимоясини
        // четлаб репозиторийга тўғри ёзамиз - хабар номларни санаб,
        // деактив қилишни таклиф этиши шарт
        accountRepository.save(new Account("060 legacy AP-1",
                AccountDetailType.ACCOUNTS_PAYABLE, null, null, null, true, null));
        accountRepository.save(new Account("060 legacy AP-2",
                AccountDetailType.ACCOUNTS_PAYABLE, null, null, null, true, null));

        assertThatThrownBy(() ->
                accountService.requireSystemAccount(AccountDetailType.ACCOUNTS_PAYABLE))
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class)
                .satisfies(e -> assertThat(
                        ((com.averpo.erp.shared.exception.BusinessRuleException) e).getCode())
                        .isEqualTo("BR-LED-021"))
                .hasMessageContaining("бир нечта")
                .hasMessageContaining("060 legacy AP-1")
                .hasMessageContaining("060 legacy AP-2")
                .hasMessageContaining("деактив");
    }

    @Test
    void create_currencyOnNonCurrencyType_rejected() {
        // Arbitr-161: даромад/харажат каби турга валюта = BR-COA-011 рад
        // (валюта фақат банк/дебитор/кредитор/кредит карта турида)
        String currency = currencyService.active().get(0).getCode();
        assertThatThrownBy(() -> accountService.create("161 даромад валютали",
                AccountDetailType.SALES_OF_PRODUCT_INCOME, null, null, null, true, currency))
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class)
                .satisfies(e -> assertThat(
                        ((com.averpo.erp.shared.exception.BusinessRuleException) e).getCode())
                        .isEqualTo("BR-COA-011"));
    }

    @Test
    void create_currencyOnCurrencyType_allowed() {
        // Arbitr-161: валютага боғланган турга (банк) валюта берилади
        String currency = currencyService.active().get(0).getCode();
        Account bank = accountService.create("161 валютали банк",
                AccountDetailType.CHECKING, null, null, null, true, currency);
        assertThat(bank.getCurrency()).isNotNull();
        assertThat(bank.getCurrency().getCode()).isEqualTo(currency);
    }

    @Test
    void update_currencyOnNonCurrencyType_rejected() {
        // Arbitr-161: таҳрирда ҳам чекланади - мавжуд харажат счётига валюта
        // беришга уриниш BR-COA-011
        String currency = currencyService.active().get(0).getCode();
        Account expense = accountService.create("161 харажат",
                AccountDetailType.OFFICE_GENERAL_ADMINISTRATIVE_EXPENSES,
                null, null, null, true, null);
        assertThatThrownBy(() -> accountService.update(expense.getId(), expense.getName(),
                expense.getDetailType(), null, null, null, true, currency, true))
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class)
                .satisfies(e -> assertThat(
                        ((com.averpo.erp.shared.exception.BusinessRuleException) e).getCode())
                        .isEqualTo("BR-COA-011"));
    }
}
