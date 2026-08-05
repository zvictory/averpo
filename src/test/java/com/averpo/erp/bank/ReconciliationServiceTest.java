package com.averpo.erp.bank;

import com.averpo.erp.bank.domain.BankReconciliation;
import com.averpo.erp.bank.domain.BankTransaction;
import com.averpo.erp.bank.service.BankTransactionService;
import com.averpo.erp.bank.service.BankTransactionService.LineData;
import com.averpo.erp.bank.service.BankTransactionService.TransferData;
import com.averpo.erp.bank.service.BankTransactionService.TxnData;
import com.averpo.erp.bank.service.ReconciliationService;
import com.averpo.erp.bank.service.ReconciliationService.Candidate;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.testsupport.SqlCaptureInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reconciliation тестлари: docs/modules/banking.md → «Тестлар»
 * (3-туртки). QBO Reconcile оқими - opening занжири, белгилаш/ечиш,
 * фарқ 0 гарови, глобал unique, бекор қилиш.
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReconciliationServiceTest {

    /** Барча тест ҳужжатлар санаси. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 6);

    @Autowired ReconciliationService reconciliationService;
    @Autowired BankTransactionService bankService;
    @Autowired AccountService accountService;
    @Autowired AccountRepository accountRepository;

    /** Сессия кэшини тозалаб query-count'ни аниқ ўлчаш учун. */
    @Autowired jakarta.persistence.EntityManager em;

    /** Солиштириладиган банк счёти. */
    private UUID bank;

    /** Иккинчи банк (бошқа счёт сатри тести учун). */
    private UUID cash;

    /** Deposit манбаси. */
    private UUID undeposited;

    /** Expense счёти. */
    private UUID rent;

    /** Chart + счётлар тайёрланади (rollback тозалайди). */
    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        bank = accountRepository.findByName("Банк ҳисобварағи").orElseThrow().getId();
        cash = accountRepository.findByName("Касса").orElseThrow().getId();
        undeposited = accountRepository.findByName("Тушумлар транзити").orElseThrow().getId();
        rent = accountRepository.findByName("Ижара").orElseThrow().getId();
    }

    /** Банкка кирим транзакцияси ясайди. */
    private BankTransaction depositToBank(String amount) {
        return bankService.deposit(new TxnData(bank, DATE, null, null, null,
                List.of(new LineData(undeposited, new BigDecimal(amount), null, null))));
    }

    /** Банкдан чиқим транзакцияси ясайди. */
    private BankTransaction expenseFromBank(String amount) {
        return bankService.expense(new TxnData(bank, DATE, null, null, null,
                List.of(new LineData(rent, new BigDecimal(amount), null, null))));
    }

    @Test
    void fullFlow_markAll_zeroDifference_completed_openingChains() {
        // Банкда иккита ҳаракат: +100 000 ва -30 000 → кўчирма 70 000
        depositToBank("100000");
        expenseFromBank("30000");

        BankReconciliation first = reconciliationService.start(bank, DATE,
                new BigDecimal("70000"), null);
        assertThat(first.getOpeningBalance()).isEqualByComparingTo("0");
        assertThat(first.getStatus()).isEqualTo(BankReconciliation.Status.IN_PROGRESS);

        // Номзодлар: банкдаги иккита GL сатри, ишорали суммалар билан
        List<Candidate> candidates = reconciliationService.candidates(first.getId());
        assertThat(candidates).hasSize(2);
        assertThat(candidates.stream()
                .map(c -> c.line().signedAmount())
                .map(BigDecimal::stripTrailingZeros))
                .containsExactlyInAnyOrder(new BigDecimal("100000").stripTrailingZeros(),
                        new BigDecimal("-30000").stripTrailingZeros());

        // Белгиланмагунча фарқ = 70 000; ҳаммаси белгилангач 0
        assertThat(reconciliationService.difference(first.getId()))
                .isEqualByComparingTo("70000");
        for (Candidate candidate : candidates) {
            reconciliationService.toggle(first.getId(), candidate.line().lineId());
        }
        assertThat(reconciliationService.difference(first.getId())).isEqualByComparingTo("0");

        reconciliationService.complete(first.getId());
        assertThat(first.getStatus()).isEqualTo(BankReconciliation.Status.COMPLETED);
        assertThat(first.getCompletedAt()).isNotNull();

        // Иккинчи давр: opening автоматик 70 000; янги чиқим 20 000 → 50 000
        expenseFromBank("20000");
        BankReconciliation second = reconciliationService.start(bank,
                DATE.plusMonths(1), new BigDecimal("50000"), null);
        assertThat(second.getOpeningBalance()).isEqualByComparingTo("70000");

        // Аввал белгиланганлар энди «бошқа ҳужжатда банд» - фақат
        // янги сатр очиқ
        List<Candidate> secondCandidates = reconciliationService.candidates(second.getId());
        assertThat(secondCandidates).hasSize(3);
        assertThat(secondCandidates.stream().filter(Candidate::matchedElsewhere)).hasSize(2);
        Candidate open = secondCandidates.stream()
                .filter(c -> !c.matchedElsewhere()).findFirst().orElseThrow();
        reconciliationService.toggle(second.getId(), open.line().lineId());
        reconciliationService.complete(second.getId());
        assertThat(second.getStatus()).isEqualTo(BankReconciliation.Status.COMPLETED);
    }

    @Test
    void start_backdated_afterCompletedPeriods_rejected() {
        // Zumrad-003 сценарийси: Фев/Март COMPLETED, Январь орқага
        // бошланса opening Мартнинг closing'ини «ўғирлаб» келар эди -
        // энди BR-RCN-008 очиқ рад этади
        depositToBank("100000");
        BankReconciliation feb = reconciliationService.start(bank, DATE,
                new BigDecimal("100000"), null);
        for (Candidate candidate : reconciliationService.candidates(feb.getId())) {
            reconciliationService.toggle(feb.getId(), candidate.line().lineId());
        }
        reconciliationService.complete(feb.getId());

        expenseFromBank("20000");
        BankReconciliation mar = reconciliationService.start(bank,
                DATE.plusMonths(1), new BigDecimal("80000"), null);
        for (Candidate candidate : reconciliationService.candidates(mar.getId())) {
            if (!candidate.matchedElsewhere()) {
                reconciliationService.toggle(mar.getId(), candidate.line().lineId());
            }
        }
        reconciliationService.complete(mar.getId());

        assertThatThrownBy(() -> reconciliationService.start(bank,
                DATE.minusMonths(1), new BigDecimal("50000"), null))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCN-008"));

        // Кетма-кет (олдинга) оқим ишлайверади: opening = Март closing'и
        BankReconciliation apr = reconciliationService.start(bank,
                DATE.plusMonths(2), new BigDecimal("80000"), null);
        assertThat(apr.getOpeningBalance()).isEqualByComparingTo("80000");
    }

    @Test
    void toggle_unmarks_andDifferenceRestores() {
        depositToBank("50000");
        BankReconciliation recon = reconciliationService.start(bank, DATE,
                new BigDecimal("50000"), null);
        UUID lineId = reconciliationService.candidates(recon.getId())
                .get(0).line().lineId();

        reconciliationService.toggle(recon.getId(), lineId);
        assertThat(reconciliationService.difference(recon.getId())).isEqualByComparingTo("0");

        // Иккинчи toggle - ечади, фарқ қайтади
        reconciliationService.toggle(recon.getId(), lineId);
        assertThat(reconciliationService.difference(recon.getId()))
                .isEqualByComparingTo("50000");
        assertThat(reconciliationService.candidates(recon.getId())
                .get(0).matchedHere()).isFalse();
    }

    @Test
    void complete_blockedWhenDifferenceNotZero() {
        depositToBank("50000");
        BankReconciliation recon = reconciliationService.start(bank, DATE,
                new BigDecimal("60000"), null);
        reconciliationService.toggle(recon.getId(),
                reconciliationService.candidates(recon.getId()).get(0).line().lineId());

        // 60 000 − 0 − 50 000 = 10 000 ≠ 0
        assertThatThrownBy(() -> reconciliationService.complete(recon.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCN-005"));
        assertThat(recon.getStatus()).isEqualTo(BankReconciliation.Status.IN_PROGRESS);
    }

    @Test
    void cancel_freesSlotAndLines() {
        depositToBank("50000");
        BankReconciliation recon = reconciliationService.start(bank, DATE,
                new BigDecimal("50000"), null);
        UUID lineId = reconciliationService.candidates(recon.getId())
                .get(0).line().lineId();
        reconciliationService.toggle(recon.getId(), lineId);

        reconciliationService.cancel(recon.getId());

        // Ҳужжат ўчди, (счёт, сана) ўрни ва сатр бўшади
        assertThatThrownBy(() -> reconciliationService.get(recon.getId()))
                .isInstanceOf(com.averpo.erp.shared.exception.NotFoundException.class);
        BankReconciliation again = reconciliationService.start(bank, DATE,
                new BigDecimal("50000"), null);
        reconciliationService.toggle(again.getId(), lineId);
        assertThat(reconciliationService.difference(again.getId())).isEqualByComparingTo("0");
    }

    @Test
    void validation_guards() {
        depositToBank("50000");

        // BR-RCN-001: BANK туридан эмас / танланмаган
        assertThatThrownBy(() -> reconciliationService.start(rent, DATE,
                BigDecimal.ONE, null))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCN-001"));
        assertThatThrownBy(() -> reconciliationService.start(null, DATE,
                BigDecimal.ONE, null))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCN-001"));

        // BR-RCN-002: сана/қолдиқ йўқ
        assertThatThrownBy(() -> reconciliationService.start(bank, null,
                BigDecimal.ONE, null))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCN-002"));
        assertThatThrownBy(() -> reconciliationService.start(bank, DATE, null, null))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCN-002"));

        BankReconciliation recon = reconciliationService.start(bank, DATE,
                new BigDecimal("50000"), null);

        // BR-RCN-003: ўша счёт ва санага иккинчиси
        assertThatThrownBy(() -> reconciliationService.start(bank, DATE,
                BigDecimal.ONE, null))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCN-003"));

        // BR-RCN-007: бошқа счёт (касса) сатри белгиланмайди
        BankTransaction cashTxn = bankService.deposit(new TxnData(cash, DATE, null,
                null, null, List.of(new LineData(undeposited, BigDecimal.ONE, null, null))));
        UUID cashLine = reconciliationService.candidates(
                reconciliationService.start(cash, DATE, BigDecimal.ONE, null).getId())
                .stream().filter(c -> c.line().signedAmount().signum() > 0)
                .findFirst().orElseThrow().line().lineId();
        assertThatThrownBy(() -> reconciliationService.toggle(recon.getId(), cashLine))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCN-007"));

        // BR-RCN-006: сатр бошқа reconciliation'да банд
        UUID bankLine = reconciliationService.candidates(recon.getId())
                .get(0).line().lineId();
        reconciliationService.toggle(recon.getId(), bankLine);
        BankReconciliation other = reconciliationService.start(bank,
                DATE.plusMonths(1), new BigDecimal("50000"), null);
        assertThatThrownBy(() -> reconciliationService.toggle(other.getId(), bankLine))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCN-006"));

        // BR-RCN-004: якунлангандан кейин белгилаш/бекор қилиш тақиқ
        reconciliationService.complete(recon.getId());
        assertThatThrownBy(() -> reconciliationService.toggle(recon.getId(), bankLine))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCN-004"));
        assertThatThrownBy(() -> reconciliationService.cancel(recon.getId()))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCN-004"));
    }

    /**
     * Sanjar-006: view() бутун экранни битта read-only транзакцияда
     * йиғади - аввал controller get/candidates/difference'ни алоҳида
     * чақириб жами 9 SELECT берарди (reconciliation ×3, счёт ×2, match
     * рўйхати ×2, бутун тарих matched-ids, GL сатрлари). Энди:
     * reconciliation 1, счёт 1, match рўйхати 1 + кесимли matched-in 1,
     * номзод GL сатрлари 1 = 5. Мазмун эски accessor'лар қайтарадигани
     * билан айнан бир хил.
     */
    @Test
    void view_singleTransaction_fiveSelects_matchesLegacyAccessors() {
        depositToBank("100000");
        expenseFromBank("30000");
        BankReconciliation recon = reconciliationService.start(bank, DATE,
                new BigDecimal("70000"), null);
        reconciliationService.toggle(recon.getId(),
                reconciliationService.candidates(recon.getId()).get(0).line().lineId());

        em.flush();
        em.clear(); // сессия кэши тозаланади - ҳар ўқиш ҳақиқий SELECT бўлсин
        SqlCaptureInspector.start();
        ReconciliationService.ReconciliationView view;
        List<String> captured;
        try {
            view = reconciliationService.view(recon.getId());
        } finally {
            captured = SqlCaptureInspector.stop();
        }

        assertThat(SqlCaptureInspector.selectCount(captured, "bank_reconciliation"))
                .isEqualTo(1);
        assertThat(SqlCaptureInspector.selectCount(captured, "account")).isEqualTo(1);
        assertThat(SqlCaptureInspector.selectCount(captured, "bank_reconciliation_match"))
                .isEqualTo(2);
        assertThat(SqlCaptureInspector.selectCount(captured, "journal_entry_line"))
                .isEqualTo(1);

        // Мазмун регрессияси: эски accessor'лар билан айнан бир хил
        assertThat(view.accountName()).isEqualTo("Банк ҳисобварағи");
        assertThat(view.accountCurrency()).isNull(); // home счёт - валюта майдони бўш
        assertThat(view.candidates()).hasSize(2);
        assertThat(view.candidates().stream().filter(Candidate::matchedHere)).hasSize(1);
        assertThat(view.difference())
                .isEqualByComparingTo(reconciliationService.difference(recon.getId()));
    }

    @Test
    void reversedPair_bothMarkable_netZero() {
        // Транзакция + сторноси: кўчирмада иккиси ҳам туради,
        // белгилангач неттоси нол (BR-RCN-007 изоҳи)
        BankTransaction txn = depositToBank("40000");
        bankService.reverse(txn.getId(), DATE, "хато");
        depositToBank("90000");

        BankReconciliation recon = reconciliationService.start(bank, DATE,
                new BigDecimal("90000"), null);
        // 3 та GL ҳужжатидан банкда 3 сатр: +40 000 (REVERSED), -40 000
        // (сторно), +90 000
        List<Candidate> candidates = reconciliationService.candidates(recon.getId());
        assertThat(candidates).hasSize(3);
        for (Candidate candidate : candidates) {
            reconciliationService.toggle(recon.getId(), candidate.line().lineId());
        }
        assertThat(reconciliationService.difference(recon.getId())).isEqualByComparingTo("0");
        reconciliationService.complete(recon.getId());
        assertThat(recon.getStatus()).isEqualTo(BankReconciliation.Status.COMPLETED);
    }
}
