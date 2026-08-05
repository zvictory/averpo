package com.averpo.erp.ledger;

import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.domain.EntryStatus;
import com.averpo.erp.ledger.domain.JournalEntry;
import com.averpo.erp.ledger.domain.JournalEntryLine;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.service.JournalEntryRequest;
import com.averpo.erp.ledger.service.JournalEntryRequest.Line;
import com.averpo.erp.ledger.service.PostingException;
import com.averpo.erp.ledger.service.PostingService;
import com.averpo.erp.shared.domain.Money;
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
 * Мажбурий тестлар рўйхати: docs/modules/ledger.md → «Тестлар».
 * Локал PostgreSQL'даги averpo_test базасида ишлайди,
 * ҳар тест rollback.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PostingServiceTest {

    /** Тестларда ишлатиладиган home валюта. */
    private static final String HOME = "UZS";

    /** Барча тест проводкалар санаси. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 5);

    @Autowired PostingService postingService;
    @Autowired AccountRepository accountRepository;
    @Autowired com.averpo.erp.ledger.repo.JournalEntryRepository entryRepository;

    /** Банк счёти (postable). */
    private Account bank;

    /** Даромад счёти (postable). */
    private Account sales;

    /** Гуруҳ счёти (postable=false) - 3-валидация тести учун. */
    private Account group;

    /** Ҳар тест олдидан керакли счётларни яратади (rollback тозалайди). */
    @BeforeEach
    void createAccounts() {
        bank = ensure("Банк ҳисобварағи", AccountDetailType.CHECKING, true);
        sales = ensure("Товар сотув даромади", AccountDetailType.SALES_OF_PRODUCT_INCOME, true);
        group = ensure("Пул маблағлари (гуруҳ)", AccountDetailType.CHECKING, false);
    }

    /** Ном бўйича мавжуд счётни олади ёки яратади. */
    private Account ensure(String name, AccountDetailType detailType, boolean postable) {
        return accountRepository.findByName(name).orElseGet(() ->
                accountRepository.save(new Account(
                        name, detailType, null, null, null, postable, null)));
    }

    /** Иккита home-валюта сатрли оддий request ясайди. */
    private JournalEntryRequest balanced(String debitAmount, String creditAmount) {
        return JournalEntryRequest.manual(DATE, "Тест проводка", List.of(
                Line.debit(bank.getId(), Money.ofBase(new BigDecimal(debitAmount), HOME), null),
                Line.credit(sales.getId(), Money.ofBase(new BigDecimal(creditAmount), HOME), null)));
    }

    @Test
    void post_balancedEntry_becomesPosted() {
        JournalEntry entry = postingService.createAndPost(balanced("1000000", "1000000"));

        assertThat(entry.getStatus()).isEqualTo(EntryStatus.POSTED);
        assertThat(entry.getPostedAt()).isNotNull();
        assertThat(entry.getEntryNumber()).startsWith("JE-2026-");

        // ТЕМИР ҚОИДА №7: debit == credit assert
        assertThat(sumDebitBase(entry)).isEqualByComparingTo(sumCreditBase(entry));
    }

    @Test
    void post_unbalancedEntry_throwsPostingException() {
        // Draft мувозанатсиз сақланади - баланс фақат post'да текширилади
        JournalEntry draft = postingService.createDraft(balanced("1000000", "900000"));
        assertThat(draft.getStatus()).isEqualTo(EntryStatus.DRAFT);

        assertThatThrownBy(() -> postingService.post(draft.getId()))
                .isInstanceOf(PostingException.class)
                .hasMessageContaining("Баланс");
    }

    @Test
    void journalList_pagination_secondPageSlice_stableSort() {
        // Beruniy-perf1 1-босқич: JE рўйхат оқими саҳифаланган - size+1
        // ёзувда 2-саҳифада биттагина қолади; тартиб аввалги ORDER BY'га
        // мос (сана, кейин рақам камайиши - рақамлар padded, string
        // тартиб сон тартибига тенг)
        JournalEntry first = postingService.createDraft(balanced("1000", "1000"));
        JournalEntry last = first;
        for (int i = 0; i < 25; i++) {
            last = postingService.createDraft(balanced("1000", "1000"));
        }
        var sort = org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("entryDate"),
                org.springframework.data.domain.Sort.Order.desc("entryNumber"));

        // Arbitr-068: рўйхат филтри Specification'га ўтди - тест ҳам шу
        // йўл билан (ListSpecs бўлаклари, эски derived query ўчирилган)
        var betweenSpec = org.springframework.data.jpa.domain.Specification.allOf(
                com.averpo.erp.shared.repo.ListSpecs.<JournalEntry>dateFrom("entryDate", DATE),
                com.averpo.erp.shared.repo.ListSpecs.dateTo("entryDate", DATE));
        var page0 = entryRepository.findAll(betweenSpec,
                org.springframework.data.domain.PageRequest.of(0, 25, sort));
        assertThat(page0.getContent()).hasSize(25);
        assertThat(page0.getTotalElements()).isEqualTo(26);
        assertThat(page0.getTotalPages()).isEqualTo(2);
        // Бир хил санада рақами каттаси (энг охирги яратилгани) биринчи
        assertThat(page0.getContent().get(0).getEntryNumber())
                .isEqualTo(last.getEntryNumber());

        var page1 = entryRepository.findAll(betweenSpec,
                org.springframework.data.domain.PageRequest.of(1, 25, sort));
        assertThat(page1.getContent()).hasSize(1);
        assertThat(page1.getContent().get(0).getEntryNumber())
                .isEqualTo(first.getEntryNumber());
    }

    @Test
    void deleteDraft_draftDeleted_postedRejected() {
        // DRAFT ўчади - GL lifecycle ўчириши ҳам service орқали (қоида №3)
        JournalEntry draft = postingService.createDraft(balanced("1000", "1000"));
        postingService.deleteDraft(draft.getId());
        assertThat(entryRepository.findById(draft.getId())).isEmpty();

        // POSTED ўзгармас - ўчиришга уриниш BR-LED-013 билан рад этилади
        JournalEntry posted = postingService.createAndPost(balanced("1000", "1000"));
        assertThatThrownBy(() -> postingService.deleteDraft(posted.getId()))
                .isInstanceOf(PostingException.class)
                .satisfies(e -> assertThat(
                        ((com.averpo.erp.shared.exception.BusinessRuleException) e).getCode())
                        .isEqualTo("BR-LED-013"));
        assertThat(entryRepository.findById(posted.getId())).isPresent();
    }

    @Test
    void post_nonPostableAccount_throwsPostingException() {
        JournalEntryRequest request = JournalEntryRequest.manual(DATE, "Гуруҳга проводка", List.of(
                Line.debit(group.getId(), Money.ofBase(new BigDecimal("500000"), HOME), null),
                Line.credit(sales.getId(), Money.ofBase(new BigDecimal("500000"), HOME), null)));

        assertThatThrownBy(() -> postingService.createAndPost(request))
                .isInstanceOf(PostingException.class)
                .hasMessageContaining("Гуруҳ");
    }

    @Test
    void reverse_createsOppositeEntry_andMarksOriginalReversed() {
        JournalEntry original = postingService.createAndPost(balanced("1000000", "1000000"));

        JournalEntry storno = postingService.reverse(
                original.getId(), DATE.plusDays(1), "хатони тузатиш");

        assertThat(original.getStatus()).isEqualTo(EntryStatus.REVERSED);
        assertThat(original.getReversedBy()).isEqualTo(storno);
        assertThat(storno.getStatus()).isEqualTo(EntryStatus.POSTED);
        assertThat(storno.getEntryDate()).isEqualTo(DATE.plusDays(1));

        // Суммалар тескари: асл дебет сатри сторнода кредит бўлади
        JournalEntryLine originalDebit = original.getLines().get(0);
        JournalEntryLine stornoFirst = storno.getLines().get(0);
        assertThat(originalDebit.getDebit()).isNotNull();
        assertThat(stornoFirst.getDebit()).isNull();
        assertThat(stornoFirst.getCredit().getBaseAmount())
                .isEqualByComparingTo(originalDebit.getDebit().getBaseAmount());

        // Сторно ҳам балансда
        assertThat(sumDebitBase(storno)).isEqualByComparingTo(sumCreditBase(storno));
    }

    @Test
    void postedEntry_isImmutable() {
        JournalEntry entry = postingService.createAndPost(balanced("1000000", "1000000"));
        Money money = Money.ofBase(new BigDecimal("1"), HOME);

        assertThatThrownBy(() ->
                entry.addLine(bank, money, null, null, null, null, null))
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class);
        assertThatThrownBy(() -> entry.updateHeader(DATE, "янги матн"))
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class);
        assertThatThrownBy(entry::clearLines)
                .isInstanceOf(com.averpo.erp.shared.exception.BusinessRuleException.class);
    }

    @Test
    void multiCurrencyLines_balanceInBaseCurrency() {
        // 100 USD * 12600 = 1 260 000 home валютада - home сатр билан балансланади
        Money usd = Money.of(new BigDecimal("100"), "USD", new BigDecimal("12600"));
        JournalEntryRequest request = JournalEntryRequest.manual(DATE, "Валюта тушум", List.of(
                Line.debit(bank.getId(), usd, null),
                Line.credit(sales.getId(), Money.ofBase(new BigDecimal("1260000"), HOME), null)));

        JournalEntry entry = postingService.createAndPost(request);

        assertThat(entry.getStatus()).isEqualTo(EntryStatus.POSTED);
        JournalEntryLine usdLine = entry.getLines().get(0);
        assertThat(usdLine.getDebit().getCurrency()).isEqualTo("USD");
        assertThat(usdLine.getDebit().getBaseAmount())
                .isEqualByComparingTo(new BigDecimal("1260000"));
        assertThat(sumDebitBase(entry)).isEqualByComparingTo(sumCreditBase(entry));
    }

    @Test
    void createDraft_lessThanTwoLines_throwsPostingException() {
        JournalEntryRequest request = JournalEntryRequest.manual(DATE, "Битта сатр", List.of(
                Line.debit(bank.getId(), Money.ofBase(BigDecimal.TEN, HOME), null)));

        assertThatThrownBy(() -> postingService.createDraft(request))
                .isInstanceOf(PostingException.class)
                .hasMessageContaining("2 сатр");
    }

    @Test
    void reverse_accountDeactivatedAfterPost_throwsPostingException() {
        // Post'дан кейин счёт нофаол қилинди - сторно ҳам ўтмаслиги шарт
        JournalEntry posted = postingService.createAndPost(balanced("1000000", "1000000"));
        bank.setActive(false);

        assertThatThrownBy(() ->
                postingService.reverse(posted.getId(), DATE.plusDays(1), "тест"))
                .isInstanceOf(PostingException.class)
                .hasMessageContaining("фаол эмас");
    }

    @Test
    void createDraft_duplicateSourceDocument_throwsPostingException() {
        // Idempotency: бир манба ҳужжат икки марта GL'га тушмайди
        java.util.UUID docId = java.util.UUID.randomUUID();
        JournalEntryRequest first = new JournalEntryRequest(DATE, "Манба ҳужжат",
                "SALES", docId, List.of(
                        Line.debit(bank.getId(), Money.ofBase(new BigDecimal("100"), HOME), null),
                        Line.credit(sales.getId(), Money.ofBase(new BigDecimal("100"), HOME), null)));
        postingService.createAndPost(first);

        assertThatThrownBy(() -> postingService.createDraft(first))
                .isInstanceOf(PostingException.class)
                .hasMessageContaining("аллақачон GL'да");
    }

    @Test
    void reverse_allowedDespiteSourceGuard() {
        // Guard сторнони тўсмаслиги шарт - reverse ички йўлдан ўтади
        java.util.UUID docId = java.util.UUID.randomUUID();
        JournalEntry posted = postingService.createAndPost(new JournalEntryRequest(
                DATE, "Сторно guard тести", "SALES", docId, List.of(
                        Line.debit(bank.getId(), Money.ofBase(new BigDecimal("200"), HOME), null),
                        Line.credit(sales.getId(), Money.ofBase(new BigDecimal("200"), HOME), null))));

        JournalEntry storno = postingService.reverse(posted.getId(), DATE, "тест");
        assertThat(storno.getSourceDocumentId()).isEqualTo(docId);
        assertThat(storno.getStatus()).isEqualTo(EntryStatus.POSTED);
    }

    @Test
    void createDraft_afterReversal_allowedAgain() {
        // Reverse'дан кейин ҳужжат GL'да фаол эмас (асл REVERSED, сторно
        // фақат из) - қайта post қилиниши мумкин (Bill/Invoice оқими:
        // post -> reverse -> таҳрир -> қайта post)
        java.util.UUID docId = java.util.UUID.randomUUID();
        JournalEntryRequest request = new JournalEntryRequest(DATE, "Қайта post тести",
                "SALES", docId, List.of(
                        Line.debit(bank.getId(), Money.ofBase(new BigDecimal("300"), HOME), null),
                        Line.credit(sales.getId(), Money.ofBase(new BigDecimal("300"), HOME), null)));
        JournalEntry posted = postingService.createAndPost(request);
        postingService.reverse(posted.getId(), DATE.plusDays(1), "хато тузатиш");

        JournalEntry again = postingService.createAndPost(request);
        assertThat(again.getStatus()).isEqualTo(EntryStatus.POSTED);
    }

    @Test
    void dbIndex_duplicateSource_rejectedEvenBypassingService() {
        // Parallel race симуляцияси: service guard'ни четлаб repository
        // орқали иккита entry ёзилса, иккинчисини ux_je_source_active
        // partial unique index DB даражасида йиқитиши шарт
        java.util.UUID docId = java.util.UUID.randomUUID();
        entryRepository.saveAndFlush(new JournalEntry(
                "JE-TEST-000001", DATE, "1-нусха", "SALES", docId));

        assertThatThrownBy(() -> entryRepository.saveAndFlush(new JournalEntry(
                "JE-TEST-000002", DATE, "2-нусха", "SALES", docId)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void post_accountDeactivatedAfterDraft_throwsPostingException() {
        // Draft яратилди, кейин счёт нофаол қилинди - post ўтмаслиги шарт
        JournalEntry draft = postingService.createDraft(balanced("1000000", "1000000"));
        bank.setActive(false);

        assertThatThrownBy(() -> postingService.post(draft.getId()))
                .isInstanceOf(PostingException.class)
                .hasMessageContaining("фаол эмас");
    }

    @Test
    void createDraft_homeCurrencyLineWithRate_throwsPostingException() {
        // Home валюта (UZS) сатрида курс 1 бўлмаса - Money.ofBase нотўғри
        // ишлатилган ёки фойдаланувчи адашган
        Money wrongRate = Money.of(new BigDecimal("1000"), HOME, new BigDecimal("2"));
        JournalEntryRequest request = JournalEntryRequest.manual(DATE, "Хато курс", List.of(
                Line.debit(bank.getId(), wrongRate, null),
                Line.credit(sales.getId(), Money.ofBase(new BigDecimal("2000"), HOME), null)));

        assertThatThrownBy(() -> postingService.createDraft(request))
                .isInstanceOf(PostingException.class)
                .hasMessageContaining("курс 1");
    }

    @Test
    void fxDifferencePattern_paymentAtDifferentRate_balancesWithFxLine() {
        // Курс фарқи қолипи (posting-rules.md, 4-босқич тасдиғи): ҳужжат
        // курси 12 600, тўлов курси 12 700 - 10 000 сўм фойда
        // EXCHANGE_GAIN_OR_LOSS кредитига тушиб entry балансланади.
        // Тўлиқ realized оқим Invoice/Payment билан 6-7-босқичда келади.
        Account fx = ensure("Валюта курси фарқи",
                AccountDetailType.EXCHANGE_GAIN_OR_LOSS, true);
        JournalEntryRequest request = JournalEntryRequest.manual(DATE,
                "Курс фарқи қолипи", List.of(
                Line.debit(bank.getId(),
                        Money.of(new BigDecimal("100"), "USD", new BigDecimal("12700")), null),
                Line.credit(sales.getId(),
                        Money.of(new BigDecimal("100"), "USD", new BigDecimal("12600")), null),
                Line.credit(fx.getId(),
                        Money.ofBase(new BigDecimal("10000"), HOME), null)));

        JournalEntry entry = postingService.createAndPost(request);

        assertThat(entry.getStatus()).isEqualTo(EntryStatus.POSTED);
        // ТЕМИР ҚОИДА №7: debit == credit assert (home валютада)
        assertThat(sumDebitBase(entry)).isEqualByComparingTo(sumCreditBase(entry));
        assertThat(sumDebitBase(entry)).isEqualByComparingTo(new BigDecimal("1270000"));
    }

    @Test
    void createDraft_unknownCurrency_wrappedAsBrLed011() {
        // CurrencyService BR-CUR-001 отади - ledger контекстида у
        // BR-LED-011 PostingException бўлиб чиқиши шарт, акс ҳолда
        // web қатламдаги форма catch'лари уни жойида кўрсата олмайди
        Money unknown = Money.of(new BigDecimal("100"), "XXX", new BigDecimal("5000"));
        JournalEntryRequest request = JournalEntryRequest.manual(DATE, "Номаълум валюта", List.of(
                Line.debit(bank.getId(), unknown, null),
                Line.credit(sales.getId(), Money.ofBase(new BigDecimal("500000"), HOME), null)));

        assertThatThrownBy(() -> postingService.createDraft(request))
                .isInstanceOf(PostingException.class)
                .hasMessageContaining("XXX")
                .satisfies(e -> assertThat(((PostingException) e).getCode())
                        .isEqualTo("BR-LED-011"));
    }

    @Test
    void createDraft_bothDebitAndCredit_throwsPostingException() {
        Money money = Money.ofBase(BigDecimal.TEN, HOME);
        JournalEntryRequest request = JournalEntryRequest.manual(DATE, "XOR бузилган", List.of(
                new Line(bank.getId(), money, money, null, null, null, null),
                Line.credit(sales.getId(), money, null)));

        assertThatThrownBy(() -> postingService.createDraft(request))
                .isInstanceOf(PostingException.class);
    }

    /** Entry'нинг барча дебет baseAmount йиғиндиси. */
    private static BigDecimal sumDebitBase(JournalEntry entry) {
        return entry.getLines().stream()
                .map(JournalEntryLine::getDebit)
                .filter(java.util.Objects::nonNull)
                .map(Money::getBaseAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Entry'нинг барча кредит baseAmount йиғиндиси. */
    private static BigDecimal sumCreditBase(JournalEntry entry) {
        return entry.getLines().stream()
                .map(JournalEntryLine::getCredit)
                .filter(java.util.Objects::nonNull)
                .map(Money::getBaseAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
