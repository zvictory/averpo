package com.averpo.erp.ledger;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.domain.EntryStatus;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.service.AccountTransactionsService;
import com.averpo.erp.ledger.service.AccountTransactionsService.Register;
import com.averpo.erp.ledger.service.AccountTransactionsService.Row;
import com.averpo.erp.ledger.service.JournalEntryRequest;
import com.averpo.erp.ledger.service.JournalEntryRequest.Line;
import com.averpo.erp.ledger.service.PostingService;
import com.averpo.erp.shared.domain.Money;
import com.averpo.erp.shared.exception.NotFoundException;
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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Счёт амаллари (register, spec T1) тестлари.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountTransactionsServiceTest {

    /** Тестларда ишлатиладиган home валюта. */
    private static final String HOME = "UZS";

    /** Асосий тест санаси - давр филтри шу атрофда қурилади. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 5);

    @Autowired AccountTransactionsService transactionsService;
    @Autowired PostingService postingService;
    @Autowired AccountRepository accountRepository;
    @Autowired ContactService contactService;
    @Autowired EntityManager em;

    /** Register қараладиган счёт. */
    private Account bank;

    /** Қарши счёт - унинг сатрлари register'га кирмаслиги керак. */
    private Account sales;

    /** Учинчи счёт - bank'сиз проводкалар учун. */
    private Account cash;

    /** Ҳар тест олдидан керакли счётларни яратади. */
    @BeforeEach
    void createAccounts() {
        bank = ensure("Банк ҳисобварағи", AccountDetailType.CHECKING);
        sales = ensure("Товар сотув даромади", AccountDetailType.SALES_OF_PRODUCT_INCOME);
        cash = ensure("Касса", AccountDetailType.CHECKING);
    }

    /** Ном бўйича мавжуд счётни олади ёки яратади. */
    private Account ensure(String name, AccountDetailType detailType) {
        return accountRepository.findByName(name).orElseGet(() ->
                accountRepository.save(new Account(
                        name, detailType, null, null, null, true, null)));
    }

    /** Home валютада оддий икки сатрли проводка post қилади. */
    private com.averpo.erp.ledger.domain.JournalEntry post(
            LocalDate date, Account debit, Account credit, String amount) {
        return postingService.createAndPost(JournalEntryRequest.manual(date, "тест", List.of(
                Line.debit(debit.getId(), Money.ofBase(new BigDecimal(amount), HOME), null),
                Line.credit(credit.getId(), Money.ofBase(new BigDecimal(amount), HOME), null))));
    }

    @Test
    void register_onlyRequestedAccountLines_withRunningBalance() {
        var entryA = post(DATE, bank, sales, "1000000");
        post(DATE.plusDays(1), cash, sales, "300000"); // bank қатнашмайди
        var entryC = post(DATE.plusDays(2), cash, bank, "400000");

        Register register = transactionsService.register(
                bank.getId(), DATE.withDayOfMonth(1), DATE.plusDays(10));

        // Фақат bank қатнашган сатрлар: A'нинг дебети, C'нинг кредити.
        // Қарши счёт сатрлари (sales/cash) ва bank'сиз entry кирмайди.
        assertThat(register.rows()).hasSize(2);
        Row first = register.rows().get(0);
        Row second = register.rows().get(1);
        assertThat(first.entryId()).isEqualTo(entryA.getId());
        assertThat(first.entryNumber()).isEqualTo(entryA.getEntryNumber());
        assertThat(first.debit().getBaseAmount()).isEqualByComparingTo("1000000");
        assertThat(first.credit()).isNull();
        assertThat(second.entryId()).isEqualTo(entryC.getId());
        assertThat(second.debit()).isNull();
        assertThat(second.credit().getBaseAmount()).isEqualByComparingTo("400000");

        // Жорий қолдиқ сатрма-сатр: 0 → 1 000 000 → 600 000
        assertThat(register.opening()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(first.balance()).isEqualByComparingTo("1000000");
        assertThat(second.balance()).isEqualByComparingTo("600000");
        assertThat(register.closing()).isEqualByComparingTo("600000");
    }

    @Test
    void register_rowsSortedByDate_notByInsertionOrder() {
        // Атайлаб сана тартибисиз post қилинади
        post(DATE.plusDays(5), bank, sales, "100");
        post(DATE.plusDays(1), bank, sales, "200");
        post(DATE.plusDays(3), bank, sales, "400");

        Register register = transactionsService.register(
                bank.getId(), DATE, DATE.plusDays(10));

        assertThat(register.rows()).extracting(Row::entryDate).containsExactly(
                DATE.plusDays(1), DATE.plusDays(3), DATE.plusDays(5));
        // Қолдиқ ҳам айнан сана тартибида йиғилади
        assertThat(register.rows().get(0).balance()).isEqualByComparingTo("200");
        assertThat(register.rows().get(1).balance()).isEqualByComparingTo("600");
        assertThat(register.rows().get(2).balance()).isEqualByComparingTo("700");
    }

    @Test
    void register_periodFilter_beforeIntoOpening_afterExcluded() {
        post(DATE.minusDays(10), bank, sales, "500000"); // давргача - opening'га
        var inPeriod = post(DATE, bank, sales, "200000");
        post(DATE.plusDays(20), bank, sales, "100000"); // даврдан кейин - умуман йўқ

        Register register = transactionsService.register(
                bank.getId(), DATE.minusDays(2), DATE.plusDays(2));

        assertThat(register.opening()).isEqualByComparingTo("500000");
        assertThat(register.rows()).hasSize(1);
        assertThat(register.rows().get(0).entryId()).isEqualTo(inPeriod.getId());
        assertThat(register.closing()).isEqualByComparingTo("700000");
    }

    @Test
    void register_reversedPair_bothVisible_netZero() {
        var posted = post(DATE, bank, sales, "800000");
        postingService.reverse(posted.getId(), DATE, "сторно тест");

        Register register = transactionsService.register(
                bank.getId(), DATE.minusDays(1), DATE.plusDays(1));

        // Асл (REVERSED) ва сторно (POSTED) иккиси ҳам тарихда кўринади
        assertThat(register.rows()).hasSize(2);
        assertThat(register.rows().get(0).status()).isEqualTo(EntryStatus.REVERSED);
        assertThat(register.rows().get(1).status()).isEqualTo(EntryStatus.POSTED);
        assertThat(register.closing()).isEqualByComparingTo(register.opening());
    }

    /**
     * DEC-105б: register саҳифаланганда жорий қолдиқ саҳифалараро
     * УЗЛУКСИЗ - кейинги саҳифа биринчи сатри олдинги саҳифа охирги
     * сатрининг айнан давоми («саҳифагача йиғинди» aggregate'и opening
     * билан қўшилади). Давр opening/closing ҳар саҳифада бир хил ДАВР
     * қиймати; page метадатаси (жами/саҳифалар сони) тўғри.
     */
    @Test
    void registerPage_balanceContinuesAcrossPages() {
        post(DATE.minusDays(5), bank, sales, "500"); // давргача - opening'га
        // 7 сатр, ҳажм 3 → 3 саҳифа; кумулятив: 600,800,1100,1500,2000,2600,3300
        for (int i = 1; i <= 7; i++) {
            post(DATE.plusDays(i), bank, sales, String.valueOf(i * 100));
        }

        var p0 = transactionsService.registerPage(
                bank.getId(), DATE, DATE.plusDays(10), 0, 3);
        var p1 = transactionsService.registerPage(
                bank.getId(), DATE, DATE.plusDays(10), 1, 3);
        var p2 = transactionsService.registerPage(
                bank.getId(), DATE, DATE.plusDays(10), 2, 3);

        // Метадата: жами 7 сатр, 3 саҳифа; rows = page content айнан ўзи
        assertThat(p0.page().getTotalElements()).isEqualTo(7);
        assertThat(p0.page().getTotalPages()).isEqualTo(3);
        assertThat(p0.register().rows()).hasSize(3).isEqualTo(p0.page().getContent());

        // 1-саҳифа: opening'дан бошлаб сатрма-сатр
        assertThat(p0.register().opening()).isEqualByComparingTo("500");
        assertThat(p0.register().rows().get(0).balance()).isEqualByComparingTo("600");
        assertThat(p0.register().rows().get(2).balance()).isEqualByComparingTo("1100");

        // УЗЛУКСИЗЛИК: 2-саҳифа биринчи сатри = 1-саҳифа охиргиси + ўз дебети
        Row lastOfP0 = p0.register().rows().get(2);
        Row firstOfP1 = p1.register().rows().get(0);
        assertThat(firstOfP1.balance()).isEqualByComparingTo(
                lastOfP0.balance().add(firstOfP1.debit().getBaseAmount()));
        assertThat(firstOfP1.balance()).isEqualByComparingTo("1500");
        assertThat(p1.register().rows().get(2).balance()).isEqualByComparingTo("2600");

        // 3-саҳифа (охирги, битта сатр) ҳам давомли; давр closing'ига етади
        assertThat(p2.register().rows()).hasSize(1);
        assertThat(p2.register().rows().get(0).balance()).isEqualByComparingTo("3300");

        // Давр opening/closing ҳар саҳифада бир хил (ДАВР қиймати, саҳифаники эмас)
        for (var paged : List.of(p0, p1, p2)) {
            assertThat(paged.register().opening()).isEqualByComparingTo("500");
            assertThat(paged.register().closing()).isEqualByComparingTo("3300");
        }

        // Саҳифасиз (делегат) вариант тўлиқ рўйхатни аввалгидек беради
        Register full = transactionsService.register(bank.getId(), DATE, DATE.plusDays(10));
        assertThat(full.rows()).hasSize(7);
        assertThat(full.closing()).isEqualByComparingTo("3300");
    }

    @Test
    void register_unknownAccount_throwsNotFound() {
        assertThatThrownBy(() -> transactionsService.register(
                UUID.randomUUID(), DATE, DATE.plusDays(1)))
                .isInstanceOf(NotFoundException.class);
    }

    /**
     * DEC-044: контакт номи ContactService'сиз, хом SQL name-map билан
     * ечилади (ledger contact модулига боғланмайди - ТЕМИР ҚОИДА №6). Контакт
     * dimension'ли JE post қилинади → register сатрида contactId → ном
     * {@code contactNames} орқали ечилиб «кўринишда сақланади».
     *
     * <p>ЭСЛАТМА: contactNames() JdbcClient (native) ишлатади - Hibernate
     * auto-flush'ни триггер қилмайди, шунинг учун ўқишдан олдин em.flush()
     * (акс ҳолда бир транзакциядаги контакт SQL'га кўринмайди).
     */
    @Test
    void contactNames_resolvesShownRowIds_viaSqlNameMap() {
        Contact customer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Register контрагенти", null, null, null, null, null,
                null, null, null, null, null));
        // bank дебет сатрига контакт dimension'и билан home JE
        postingService.createAndPost(JournalEntryRequest.manual(DATE, "тест", List.of(
                new Line(bank.getId(), Money.ofBase(new BigDecimal("500000"), HOME), null,
                        customer.getId(), null, null, null),
                Line.credit(sales.getId(), Money.ofBase(new BigDecimal("500000"), HOME), null))));
        em.flush();

        Register register = transactionsService.register(
                bank.getId(), DATE.withDayOfMonth(1), DATE.plusDays(10));
        List<UUID> ids = register.rows().stream().map(Row::contactId)
                .filter(Objects::nonNull).distinct().toList();

        // Саҳифа сатрида контакт id'си бор ва номи SQL name-map'дан ечилади
        assertThat(ids).contains(customer.getId());
        Map<UUID, String> names = transactionsService.contactNames(ids);
        assertThat(names).containsEntry(customer.getId(), "Register контрагенти");
        // Бўш вход - бўш харита (саҳифада контакт сатри бўлмаса SQL юрмайди)
        assertThat(transactionsService.contactNames(List.of())).isEmpty();
    }
}
