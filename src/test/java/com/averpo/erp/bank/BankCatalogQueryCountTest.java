package com.averpo.erp.bank;

import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountType;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.testsupport.SqlCaptureInspector;
import com.averpo.erp.testsupport.WithMockRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OPT-009 регрессияси: уч банк экрани бир render'да каталогни қайта
 * сўрамайди - счёт рўйхати БИР марта олиниб барча subset/map'лар шундан
 * ясалади (TransferController.accountViewMaps нақши).
 *
 * <p>Кутилган account SELECT'лар (Hibernate орқали - жонли қолдиқнинг
 * хом jdbc сўрови инспекторда кўринмайди): форма GET'ларида 1 (аввал
 * postableAccounts + all = 2 эди), reconciliation рўйхатида 1 (аввал
 * 3). Контакт SELECT'лар банк формасида 2 (customer+vendor; аввал
 * allContacts() икки марта чақирилиб 4 эди). Option таркиби ва
 * тартиби (CHART_ORDER) айнан сақланганини model'дан текширамиз.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockRole(username = "admin")
class BankCatalogQueryCountTest {

    @Autowired WebApplicationContext context;

    /** Кутилмаларни мустақил ҳисоблаш учун (postableAccounts кўзгуси). */
    @Autowired AccountService accountService;

    /** Security filter chain уланган MockMvc (ScreenSmokeTest қолипи). */
    private MockMvc mockMvc;

    /** Chart юкланади (rollback тозалайди) ва MockMvc қурилади. */
    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /** GET'ни SQL ушлагич остида бажариб model + SQL'ларни қайтаради. */
    private MvcResult perform(String route) throws Exception {
        SqlCaptureInspector.start();
        return mockMvc.perform(get(route)).andExpect(status().isOk()).andReturn();
    }

    /** postableAccounts()'нинг BANK кесими - эски хулқ кутилмаси. */
    private List<UUID> expectedBankAccountIds() {
        return accountService.postableAccounts().stream()
                .filter(a -> a.getType() == AccountType.BANK)
                .map(Account::getId)
                .toList();
    }

    /** Model'даги Account рўйхатининг id тартиби. */
    @SuppressWarnings("unchecked")
    private static List<UUID> accountIds(MvcResult result, String attribute) {
        return ((List<Account>) result.getModelAndView().getModel().get(attribute))
                .stream().map(Account::getId).toList();
    }

    @Test
    void bankTransactionForm_singleCatalogRead_optionsIntact() throws Exception {
        MvcResult result = perform("/bank-transactions/new");
        List<String> captured = SqlCaptureInspector.stop();

        assertThat(SqlCaptureInspector.selectCount(captured, "account")).isEqualTo(1);
        assertThat(SqlCaptureInspector.selectCount(captured, "contact")).isEqualTo(2);

        // Банк select'и айнан postableAccounts() BANK кесими (тартиб ҳам)
        assertThat(accountIds(result, "bankAccounts"))
                .isEqualTo(expectedBankAccountIds())
                .isNotEmpty();
        // Сатр счётлари: BANK йўқ, нофаол эмас parent (postable=false)
        // жилдлар сақланган - select'да disabled group бўлиб қолади
        @SuppressWarnings("unchecked")
        List<Account> lineAccounts = (List<Account>)
                result.getModelAndView().getModel().get("lineAccounts");
        assertThat(lineAccounts).noneMatch(a -> a.getType() == AccountType.BANK);
        assertThat(lineAccounts).anyMatch(a -> !a.isPostable());
    }

    @Test
    void expenseForm_singleCatalogRead_optionsIntact() throws Exception {
        MvcResult result = perform("/expenses/new");
        List<String> captured = SqlCaptureInspector.stop();

        assertThat(SqlCaptureInspector.selectCount(captured, "account")).isEqualTo(1);
        assertThat(SqlCaptureInspector.selectCount(captured, "contact")).isEqualTo(2);
        assertThat(accountIds(result, "bankAccounts"))
                .isEqualTo(expectedBankAccountIds())
                .isNotEmpty();
    }

    @Test
    void reconciliationList_singleCatalogRead_mapsIntact() throws Exception {
        MvcResult result = perform("/reconciliation");
        List<String> captured = SqlCaptureInspector.stop();

        assertThat(SqlCaptureInspector.selectCount(captured, "account")).isEqualTo(1);

        // Ном/валюта хариталари бутун каталогни қоплайди; валютасиз счёт
        // home кодини олади (UI-005 хулқи сақланган)
        List<Account> all = accountService.all();
        Map<?, ?> names = (Map<?, ?>) result.getModelAndView().getModel().get("accountNames");
        Map<?, ?> currencies = (Map<?, ?>)
                result.getModelAndView().getModel().get("accountCurrencies");
        assertThat(names).hasSize(all.size());
        assertThat(currencies).hasSize(all.size());
        String home = (String) result.getModelAndView().getModel().get("homeCurrency");
        Account currencyless = all.stream()
                .filter(a -> a.getCurrency() == null).findFirst().orElseThrow();
        assertThat(currencies.get(currencyless.getId())).isEqualTo(home);
        assertThat(accountIds(result, "bankAccounts"))
                .isEqualTo(expectedBankAccountIds())
                .isNotEmpty();
    }
}
