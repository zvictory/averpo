package com.averpo.erp.ledger;

import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.domain.EntryStatus;
import com.averpo.erp.ledger.domain.JournalEntry;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.service.JournalEntryRequest;
import com.averpo.erp.ledger.service.JournalEntryRequest.Line;
import com.averpo.erp.ledger.service.PostingException;
import com.averpo.erp.ledger.service.PostingService;
import com.averpo.erp.shared.domain.CompanySettings;
import com.averpo.erp.shared.domain.Money;
import com.averpo.erp.shared.service.CompanySettingsService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Period lock (BR-LED-020) тестлари: docs/modules/closing-date.md →
 * «Тестлар» рўйхати. Ёпилиш санаси CompanySettings'да, текширув
 * PostingService'нинг учта нуқтасида (createDraft, post, reverse).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ClosingDateLockTest {

    /** Тест ёпилиш санаси - 2026 йил июнь охири. */
    private static final LocalDate CLOSING = LocalDate.of(2026, 6, 30);

    /** Ёпиқ даврдаги сана. */
    private static final LocalDate CLOSED_DATE = LocalDate.of(2026, 6, 15);

    /** Очиқ даврдаги сана. */
    private static final LocalDate OPEN_DATE = LocalDate.of(2026, 7, 5);

    @Autowired PostingService postingService;
    @Autowired CompanySettingsService settingsService;
    @Autowired AccountRepository accountRepository;

    /** Дебет счёти. */
    private Account bank;

    /** Кредит счёти. */
    private Account sales;

    /** Ҳар тест олдидан керакли счётларни яратади (rollback тозалайди). */
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

    /** Ёпилиш санасини белгилайди - бошқа созламалар ўз ҳолича қолади. */
    private void lockPeriod(LocalDate closingDate) {
        CompanySettings settings = settingsService.get();
        settingsService.update(settings.getName(), settings.homeCurrencyCode(),
                settings.getTimezone(), null, closingDate);
    }

    /** Кўрсатилган санага оддий балансланган request ясайди. */
    private JournalEntryRequest balanced(LocalDate date) {
        BigDecimal amount = new BigDecimal("100000");
        return JournalEntryRequest.manual(date, "Period lock тести", List.of(
                Line.debit(bank.getId(), Money.ofBase(amount, "UZS"), null),
                Line.credit(sales.getId(), Money.ofBase(amount, "UZS"), null)));
    }

    @Test
    void createDraft_inClosedPeriod_rejected() {
        lockPeriod(CLOSING);

        // Ёпилиш санасидан олдинги сана ҳам, санасининг ўзи ҳам тақиқ
        for (LocalDate date : List.of(CLOSED_DATE, CLOSING)) {
            assertThatThrownBy(() -> postingService.createDraft(balanced(date)))
                    .isInstanceOf(PostingException.class)
                    .satisfies(e -> assertThat(((PostingException) e).getCode())
                            .isEqualTo("BR-LED-020"));
        }
    }

    @Test
    void post_draftCreatedBeforeLock_rejected() {
        // Draft очиқ пайтда яратилди, кейин ADMIN ёпилиш санасини қўйди -
        // post олдидан қайта текширув тутиши шарт
        JournalEntry draft = postingService.createDraft(balanced(CLOSED_DATE));
        lockPeriod(CLOSING);

        assertThatThrownBy(() -> postingService.post(draft.getId()))
                .isInstanceOf(PostingException.class)
                .satisfies(e -> assertThat(((PostingException) e).getCode())
                        .isEqualTo("BR-LED-020"));
    }

    @Test
    void reverse_stornoDateInClosedPeriod_rejected() {
        JournalEntry posted = postingService.createAndPost(balanced(OPEN_DATE));
        lockPeriod(CLOSING);

        assertThatThrownBy(() ->
                postingService.reverse(posted.getId(), CLOSED_DATE, "тест"))
                .isInstanceOf(PostingException.class)
                .satisfies(e -> assertThat(((PostingException) e).getCode())
                        .isEqualTo("BR-LED-020"));
    }

    @Test
    void reverse_originalInClosedPeriod_allowedWithOpenStornoDate() {
        // Ҳужжат ёпиқ даврда, лекин REVERSED статус балансни ўзгартирмайди
        // (TrialBalance POSTED+REVERSED'ни олади) - очиқ санали сторно рухсат
        JournalEntry posted = postingService.createAndPost(balanced(CLOSED_DATE));
        lockPeriod(CLOSING);

        JournalEntry storno = postingService.reverse(
                posted.getId(), LocalDate.of(2026, 7, 1), "ёпиқ даврни тузатиш");

        assertThat(storno.getStatus()).isEqualTo(EntryStatus.POSTED);
        assertThat(storno.getEntryDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(posted.getStatus()).isEqualTo(EntryStatus.REVERSED);
    }

    @Test
    void createAndPost_afterClosingDate_allowed() {
        lockPeriod(CLOSING);

        JournalEntry entry = postingService.createAndPost(balanced(OPEN_DATE));
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.POSTED);
    }

    @Test
    void noClosingDate_noLock() {
        // Ёпилиш санаси белгиланмаган (default) - ҳар қандай сана очиқ
        JournalEntry entry = postingService.createAndPost(
                balanced(LocalDate.of(2020, 1, 1)));
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.POSTED);
    }

    @Test
    void clearingClosingDate_unlocksPeriod() {
        lockPeriod(CLOSING);
        assertThatThrownBy(() -> postingService.createDraft(balanced(CLOSED_DATE)))
                .isInstanceOf(PostingException.class);

        // ADMIN қулфни олиб ташлади (null) - давр яна очиқ
        lockPeriod(null);
        JournalEntry entry = postingService.createAndPost(balanced(CLOSED_DATE));
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.POSTED);
    }
}
