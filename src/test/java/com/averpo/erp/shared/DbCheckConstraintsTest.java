package com.averpo.erp.shared;

import com.averpo.erp.ledger.service.AccountService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Changeset 051 DB инвариантлари тести (Arbitr-076): service гаровини
 * ЧЕТЛАБ хом SQL билан ёзилганда ҳам DB рад этишини тасдиқлайди
 * (PostingServiceTest.dbIndex_duplicateSource қолипи). Темир қоида 4
 * замини (home балансланиш) энди returns/чек оиласида ҳам DB
 * даражасида: exchange_rate > 0; payroll.md:93 ваъдаси: gross > 0.
 *
 * <p>Ҳар сценарий алоҳида тест методи: CHECK бузилиши Postgres
 * транзакциясини abort ҳолатига ўтказади - кейинги statement'лар шу
 * транзакцияда юрмайди, шунга бузувчи INSERT ҳар методда охирги амал.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DbCheckConstraintsTest {

    /** Хом INSERT'лар учун - service қатлами атайлаб четланади. */
    @Autowired
    private JdbcClient jdbcClient;

    /** sales_receipt FK учун банк счёти (default chart). */
    @Autowired
    private AccountService accountService;

    /** JPA (chart import) ёзувларини хом SELECT'дан олдин flush қилиш учун. */
    @PersistenceContext
    private EntityManager entityManager;

    /** 0 курсли сотув чеки - ck_sales_receipt_rate_positive рад этади. */
    @Test
    void salesReceipt_zeroExchangeRate_rejectedByDb() {
        accountService.importDefaultChart();
        // Chart JPA билан ёзилади - flush'сиз кейинги хом SELECT кўрмайди
        entityManager.flush();
        UUID customerId = insertContact("CUSTOMER", "Чек CHECK мижози");
        UUID bankId = jdbcClient.sql("SELECT id FROM account WHERE name = 'Банк ҳисобварағи'")
                .query(UUID.class).single();
        UUID uzsId = jdbcClient.sql("SELECT id FROM currency WHERE code = 'UZS'")
                .query(UUID.class).single();

        assertThatThrownBy(() -> jdbcClient.sql("INSERT INTO sales_receipt "
                        + "(id, sr_number, customer_id, bank_account_id, sr_date, "
                        + "currency_id, exchange_rate, status) "
                        + "VALUES (?, 'SR-CK-00001', ?, ?, DATE '2026-07-10', ?, 0, 'POSTED')")
                .params(UUID.randomUUID(), customerId, bankId, uzsId)
                .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_sales_receipt_rate_positive");
    }

    /** gross = 0 payroll сатри - ck_prl_gross_positive рад этади. */
    @Test
    void payrollRunLine_zeroGross_rejectedByDb() {
        UUID employeeId = insertContact("EMPLOYEE", "Payroll CHECK ходими");
        UUID runId = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO payroll_run (id, run_number, period, run_date, status) "
                        + "VALUES (?, 'PAYR-CK-00001', '2026-07', DATE '2026-07-10', 'DRAFT')")
                .param(runId).update();

        assertThatThrownBy(() -> jdbcClient.sql("INSERT INTO payroll_run_line "
                        + "(id, payroll_run_id, line_no, employee_id, gross, "
                        + "income_tax, pension, social_tax, net) "
                        + "VALUES (?, ?, 1, ?, 0, 0, 0, 0, 0)")
                .params(UUID.randomUUID(), runId, employeeId)
                .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_prl_gross_positive");
    }

    /** Минимал контакт сатри - FK'лар учун (FactoryResetServiceTest қолипи). */
    private UUID insertContact(String type, String displayName) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO contact (id, type, display_name) VALUES (?, ?, ?)")
                .params(id, type, displayName).update();
        return id;
    }
}
