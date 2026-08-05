package com.averpo.erp.ledger;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.domain.AccountType;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.service.BalanceSheetService;
import com.averpo.erp.ledger.service.JournalEntryRequest;
import com.averpo.erp.ledger.service.JournalEntryRequest.Line;
import com.averpo.erp.ledger.service.PostingService;
import com.averpo.erp.purchase.service.BillPaymentService;
import com.averpo.erp.sales.service.InvoicePaymentService;
import com.averpo.erp.shared.domain.Money;
import com.averpo.erp.shared.service.CompanySettingsService;
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
 * Balance Sheet: ишоралар, RE/NI бўлиниши, молия йили, баланс текшируви.
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BalanceSheetServiceTest {

    /** Тестларда ишлатиладиган home валюта. */
    private static final String HOME = "UZS";

    /** Ҳисобот санаси - жорий молия йили ичида (FY боши январь). */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 5);

    @Autowired BalanceSheetService balanceSheetService;
    @Autowired PostingService postingService;
    @Autowired AccountRepository accountRepository;
    @Autowired CompanySettingsService settingsService;
    @Autowired ContactService contactService;
    @Autowired InvoicePaymentService invoicePaymentService;
    @Autowired BillPaymentService billPaymentService;
    @Autowired EntityManager em;

    /** Банк счёти (BANK - жорий актив). */
    private Account bank;

    /** Даромад счёти (INCOME - соф фойдага киради). */
    private Account sales;

    /** Кредиторлик счёти (ACCOUNTS_PAYABLE - жорий мажбурият). */
    private Account payable;

    /** Капитал счёти (OWNERS_EQUITY). */
    private Account equity;

    /** Тақсимланмаган фойда счёти (RETAINED_EARNINGS - алоҳида сатрга қўшилади). */
    private Account retained;

    /** Ҳар тест олдидан керакли счётларни яратади. */
    @BeforeEach
    void createAccounts() {
        bank = ensure("Банк ҳисобварағи", AccountDetailType.CHECKING);
        sales = ensure("Товар сотув даромади", AccountDetailType.SALES_OF_PRODUCT_INCOME);
        payable = ensure("Кредиторлик", AccountDetailType.ACCOUNTS_PAYABLE);
        equity = ensure("Таъсисчи капитали", AccountDetailType.OWNERS_EQUITY);
        retained = ensure("Тақсимланмаган фойда", AccountDetailType.RETAINED_EARNINGS);
    }

    /** Ном бўйича мавжуд счётни олади ёки яратади. */
    private Account ensure(String name, AccountDetailType detailType) {
        return accountRepository.findByName(name).orElseGet(() ->
                accountRepository.save(new Account(
                        name, detailType, null, null, null, true, null)));
    }

    /** Home валютада оддий икки сатрли JE post қилади. */
    private void post(LocalDate date, Account debit, Account credit, String amount) {
        postingService.createAndPost(JournalEntryRequest.manual(date, "BS тест", List.of(
                Line.debit(debit.getId(), Money.ofBase(new BigDecimal(amount), HOME), null),
                Line.credit(credit.getId(), Money.ofBase(new BigDecimal(amount), HOME), null))));
    }

    @Test
    void build_signsAndTotals_balanced() {
        // Банк 1 000 000 = AP 400 000 + капитал 600 000
        postingService.createAndPost(JournalEntryRequest.manual(DATE, "BS тест", List.of(
                Line.debit(bank.getId(), Money.ofBase(new BigDecimal("1000000"), HOME), null),
                Line.credit(payable.getId(), Money.ofBase(new BigDecimal("400000"), HOME), null),
                Line.credit(equity.getId(), Money.ofBase(new BigDecimal("600000"), HOME), null))));
        em.flush();

        BalanceSheetService.Report report = balanceSheetService.build(DATE);

        // Актив Dt-мусбат: банк BANK гуруҳида
        BalanceSheetService.Group bankGroup = group(report.currentAssetGroups(), AccountType.BANK);
        assertThat(row(bankGroup.rows(), "Банк ҳисобварағи").amount())
                .isEqualByComparingTo("1000000");

        // Мажбурият Cr-мусбат: AP ўз гуруҳида мусбат кўринади
        BalanceSheetService.Group apGroup =
                group(report.currentLiabilityGroups(), AccountType.ACCOUNTS_PAYABLE);
        assertThat(row(apGroup.rows(), "Кредиторлик").amount())
                .isEqualByComparingTo("400000");

        // Капитал Cr-мусбат
        assertThat(row(report.equityRows(), "Таъсисчи капитали").amount())
                .isEqualByComparingTo("600000");

        assertThat(report.totalAssets()).isEqualByComparingTo("1000000");
        assertThat(report.totalLiabilities()).isEqualByComparingTo("400000");
        assertThat(report.totalEquity()).isEqualByComparingTo("600000");
        assertThat(report.totalLiabilitiesAndEquity()).isEqualByComparingTo("1000000");
        assertThat(report.balanced()).isTrue();
    }

    @Test
    void build_currentYearProfit_inNetIncome() {
        post(DATE, bank, sales, "500000");
        em.flush();

        BalanceSheetService.Report report = balanceSheetService.build(DATE);

        // Жорий молия йили фойдаси Соф фойда сатрида, RE'га тегмайди
        assertThat(report.netIncome()).isEqualByComparingTo("500000");
        assertThat(report.retainedEarnings()).isEqualByComparingTo("0");
        assertThat(report.totalEquity()).isEqualByComparingTo("500000");
        assertThat(report.balanced()).isTrue();
    }

    @Test
    void build_priorYearProfit_mergedIntoRetainedEarnings() {
        // Ўтган йилги фойда + RE счётига қўлда ёзилган қолдиқ
        post(LocalDate.of(2025, 6, 30), bank, sales, "300000");
        post(DATE, bank, retained, "100000");
        em.flush();

        BalanceSheetService.Report report = balanceSheetService.build(DATE);

        // RE сатри = счёт қолдиғи 100 000 + ўтган йиллар фойдаси 300 000
        assertThat(report.retainedEarnings()).isEqualByComparingTo("400000");
        assertThat(report.netIncome()).isEqualByComparingTo("0");
        // RE счёти equityRows'да ТАКРОРЛАНМАЙДИ (битта сатрга бирлашган)
        assertThat(report.equityRows())
                .noneMatch(r -> r.name().equals("Тақсимланмаган фойда"));
        assertThat(report.balanced()).isTrue();
    }

    @Test
    void build_fiscalYearStartMonth_splitsBuckets() {
        // Молия йили июлдан бошланади
        var settings = settingsService.get();
        settingsService.update(settings.getName(), settings.homeCurrencyCode(),
                settings.getTimezone(), null, null, 7);

        post(LocalDate.of(2026, 6, 30), bank, sales, "200000"); // ўтган FY
        post(LocalDate.of(2026, 7, 2), bank, sales, "50000");   // жорий FY
        em.flush();

        BalanceSheetService.Report report = balanceSheetService.build(DATE);

        assertThat(report.netIncome()).isEqualByComparingTo("50000");
        assertThat(report.retainedEarnings()).isEqualByComparingTo("200000");
        assertThat(report.totalAssets()).isEqualByComparingTo("250000");
        assertThat(report.balanced()).isTrue();
    }

    @Test
    void build_reversedEntry_zeroRowsHidden() {
        var posted = postingService.createAndPost(JournalEntryRequest.manual(
                DATE, "BS тест", List.of(
                        Line.debit(bank.getId(), Money.ofBase(new BigDecimal("700000"), HOME), null),
                        Line.credit(sales.getId(), Money.ofBase(new BigDecimal("700000"), HOME), null))));
        postingService.reverse(posted.getId(), DATE, "сторно тест");
        em.flush();

        BalanceSheetService.Report report = balanceSheetService.build(DATE);

        // Сторно жуфти неттоси ноль - ноль сатрлар яширилади (QBO default)
        assertThat(report.currentAssetGroups()).isEmpty();
        assertThat(report.netIncome()).isEqualByComparingTo("0");
        assertThat(report.totalAssets()).isEqualByComparingTo("0");
        assertThat(report.balanced()).isTrue();
    }

    @Test
    void build_ias1SeparateLines_totalsPreserved() {
        // Komil-011/012: ТМЗ, гудвилл, номоддий - алоҳида моддалар (IAS 1.54)
        Account inventory = ensure("Товар захиралари BS", AccountDetailType.INVENTORY);
        Account goodwill = ensure("Гудвилл BS", AccountDetailType.GOODWILL);
        Account intangible = ensure("Номоддий актив BS", AccountDetailType.INTANGIBLE_ASSETS);
        Account transit = ensure("Транзит счёт BS", AccountDetailType.UNDEPOSITED_FUNDS);
        postingService.createAndPost(JournalEntryRequest.manual(DATE, "BS тест", List.of(
                Line.debit(inventory.getId(), Money.ofBase(new BigDecimal("300000"), HOME), null),
                Line.debit(goodwill.getId(), Money.ofBase(new BigDecimal("200000"), HOME), null),
                Line.debit(intangible.getId(), Money.ofBase(new BigDecimal("100000"), HOME), null),
                Line.debit(transit.getId(), Money.ofBase(new BigDecimal("50000"), HOME), null),
                Line.credit(equity.getId(), Money.ofBase(new BigDecimal("650000"), HOME), null))));
        em.flush();

        BalanceSheetService.Report report = balanceSheetService.build(DATE);

        // ТМЗ псевдо гуруҳда (titleKey detail'дан, type null - тур гуруҳларига аралашмайди)
        BalanceSheetService.Group inv = report.currentAssetGroups().stream()
                .filter(g -> "account.detail.INVENTORY".equals(g.titleKey()))
                .findFirst().orElseThrow();
        assertThat(inv.type()).isNull();
        assertThat(inv.total()).isEqualByComparingTo("300000");
        // ТМЗ бошқа жорий активлар гуруҳида ТАКРОРЛАНМАЙДИ
        assertThat(group(report.currentAssetGroups(), AccountType.OTHER_CURRENT_ASSET).rows())
                .noneMatch(r -> r.name().equals("Товар захиралари BS"));

        // Гудвилл ва номоддий «Бошқа активлар»дан ажралган
        assertThat(row(report.goodwill(), "Гудвилл BS").amount())
                .isEqualByComparingTo("200000");
        assertThat(row(report.intangibleAssets(), "Номоддий актив BS").amount())
                .isEqualByComparingTo("100000");
        assertThat(report.otherAssets()).noneMatch(r ->
                r.name().equals("Гудвилл BS") || r.name().equals("Номоддий актив BS"));

        // АСОСИЙ: бўлимлар йиғиндиси умумий жамига айнан тенг, тенглама сақланади
        assertThat(report.totalCurrentAssets().add(report.totalFixedAssets())
                .add(report.totalGoodwill()).add(report.totalIntangibleAssets())
                .add(report.totalOtherAssets()))
                .isEqualByComparingTo(report.totalAssets());
        assertThat(report.totalAssets()).isEqualByComparingTo("650000");
        assertThat(report.balanced()).isTrue();
    }

    @Test
    void build_unallocatedAdvances_reclassedForDisplay_equationHolds() {
        // Komil-005: тақсимланмаган аванслар кўрсатишда reclass - GL ўзгармайди.
        // Тизим счёти қидируви «ягона AR/AP detail» талаб қилади - default
        // chart юкланмайди (у @BeforeEach'даги AP билан дубль берарди),
        // фақат етишмаётган AR яратилади.
        ensure("Дебиторлик", AccountDetailType.ACCOUNTS_RECEIVABLE);
        post(DATE, bank, equity, "500000");
        Contact customer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "BS аванс мижози", null, null, null, null, null,
                null, null, null, null, null));
        Contact vendor = contactService.create(ContactType.VENDOR, new ContactData(
                "BS аванс таъминотчиси", null, null, null, null, null,
                null, null, null, null, null));
        // Тақсимотсиз тушум (мижоз аванси) ва тақсимотсиз тўлов (таъминотчи аванси)
        invoicePaymentService.create(new InvoicePaymentService.PaymentData(
                customer.getId(), DATE, bank.getId(), null, null,
                new BigDecimal("150000"), null, List.of()));
        billPaymentService.create(new BillPaymentService.PaymentData(
                vendor.getId(), DATE, bank.getId(), null, null,
                new BigDecimal("80000"), null, List.of()));
        em.flush();

        BalanceSheetService.Report report = balanceSheetService.build(DATE);

        // Мижоз аванси мажбуриятга, таъминотчи аванси активга ўтган
        assertThat(report.customerAdvances()).isEqualByComparingTo("150000");
        assertThat(report.vendorPrepayments()).isEqualByComparingTo("80000");
        // AR/AP қолдиқлари тўлиқ аванслардан иборат эди - гуруҳлар нолга
        // тушиб яширинади (кўрсатишда счёт сатри «тозаланган»)
        assertThat(report.currentAssetGroups())
                .noneMatch(g -> g.type() == AccountType.ACCOUNTS_RECEIVABLE);
        assertThat(report.currentLiabilityGroups())
                .noneMatch(g -> g.type() == AccountType.ACCOUNTS_PAYABLE);

        // АСОСИЙ: тенглама сақланади - иккала томонга бир хил сумма қўшилган
        // Банк: 500000 + 150000 - 80000 = 570000; активлар: 570000 + 80000
        assertThat(report.totalCurrentAssets()).isEqualByComparingTo("650000");
        assertThat(report.totalCurrentLiabilities()).isEqualByComparingTo("150000");
        assertThat(report.totalAssets()).isEqualByComparingTo("650000");
        assertThat(report.totalLiabilitiesAndEquity()).isEqualByComparingTo("650000");
        assertThat(report.balanced()).isTrue();
    }

    @Test
    void build_customerAdvance_fromGl_futureReverseRespectsAsOf() {
        // Komil-016: тўлов КЕЛАЖАКДА reverse қилинса, reverse'дан ОЛДИНги
        // as-of да аванс ҳали кўринади (GL as-of тарихий), reverse'дан
        // КЕЙИНги as-of да йўқолади. Домен unallocated (жорий ҳолат)
        // ёндашуви буни билмас эди - тўлов REVERSED бўлса дарҳол 0 берар,
        // GL хом AR эса кредит қоларди → AR манфий сатр, аванс йўқ.
        ensure("Дебиторлик", AccountDetailType.ACCOUNTS_RECEIVABLE);
        Contact customer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Future reverse мижоз", null, null, null, null, null,
                null, null, null, null, null));
        LocalDate receiptDate = LocalDate.of(2026, 7, 10);
        LocalDate reverseDate = LocalDate.of(2026, 8, 1);
        var payment = invoicePaymentService.create(new InvoicePaymentService.PaymentData(
                customer.getId(), receiptDate, bank.getId(), null, null,
                new BigDecimal("150000"), null, List.of()));
        invoicePaymentService.reverse(payment.getId(), reverseDate, "future reverse");
        em.flush();

        // reverse'дан ОЛДИН (2026-07-31): аванс ҳали кўринади
        assertThat(balanceSheetService.build(LocalDate.of(2026, 7, 31)).customerAdvances())
                .isEqualByComparingTo("150000");
        // reverse'дан КЕЙИН (2026-08-15): сторно as-of'га кирди, аванс йўқолди
        assertThat(balanceSheetService.build(LocalDate.of(2026, 8, 15)).customerAdvances())
                .isEqualByComparingTo("0");
    }

    @Test
    void build_unappliedCreditBalances_shownAsAdvances_fromGl() {
        // Komil-015: тақсимланмаган CreditMemo (Cr AR) ва VendorCredit (Dt AP)
        // қолдиқлари ҳам аванс netting яратади - GL contact кесимида
        // автоматик киради (домен unallocated'да умуман кўринмас эди). Бу
        // ерда уларнинг GL таъсири қўлда post қилинади: манба ҳужжат тури
        // аҳамиятсиз, BS фақат AR/AP contact қолдиғидан ўқийди.
        Account receivable = ensure("Дебиторлик", AccountDetailType.ACCOUNTS_RECEIVABLE);
        Contact customer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Unapplied CM мижози", null, null, null, null, null,
                null, null, null, null, null));
        Contact vendor = contactService.create(ContactType.VENDOR, new ContactData(
                "Unapplied VC таъминотчиси", null, null, null, null, null,
                null, null, null, null, null));
        // CreditMemo AR таъсири: Dr даромад / Cr AR (мижоз фойдасига кредит қолдиқ)
        postingService.createAndPost(JournalEntryRequest.manual(DATE, "unapplied CM каби", List.of(
                Line.debit(sales.getId(), Money.ofBase(new BigDecimal("100000"), HOME), null),
                new Line(receivable.getId(), null, Money.ofBase(new BigDecimal("100000"), HOME),
                        customer.getId(), null, null, null))));
        // VendorCredit AP таъсири: Dr AP (таъминотчи кредит қолдиқ) / Cr харажат
        postingService.createAndPost(JournalEntryRequest.manual(DATE, "unapplied VC каби", List.of(
                new Line(payable.getId(), Money.ofBase(new BigDecimal("60000"), HOME), null,
                        vendor.getId(), null, null, null),
                Line.credit(sales.getId(), Money.ofBase(new BigDecimal("60000"), HOME), null))));
        em.flush();

        BalanceSheetService.Report report = balanceSheetService.build(DATE);

        // Cr AR қолдиғи «Олинган аванслар»да, Dt AP қолдиғи «Берилган аванслар»да
        assertThat(report.customerAdvances()).isEqualByComparingTo("100000");
        assertThat(report.vendorPrepayments()).isEqualByComparingTo("60000");
        // AR/AP тўлиқ авансдан иборат - гуруҳлар нолга тушиб яширинади
        assertThat(report.currentAssetGroups())
                .noneMatch(g -> g.type() == AccountType.ACCOUNTS_RECEIVABLE);
        assertThat(report.currentLiabilityGroups())
                .noneMatch(g -> g.type() == AccountType.ACCOUNTS_PAYABLE);
        assertThat(report.balanced()).isTrue();
    }

    @Test
    void build_payrollAdvance_reclassedToAssets_creditStaysInLiability() {
        // Komil-020 (Arbitr-071): аванс тўланган, run ҳали POSTED эмас -
        // PAYROLL_CLEARING ходим кесимида нетто ДЕБЕТ. Аввал BS уни
        // мажбурият қаторини камайтириб (соф авансда МАНФИЙ қилиб)
        // кўрсатарди; энди актив томонда «Берилган аванслар (ходимларга)»
        // бўлиб чиқади, бошқа ходимнинг кредит (ҳисобланган) қолдиғи
        // мажбуриятда тўлиқ қолади - contact кесимида netting (IAS 1.32).
        Account clearing = ensure("Иш ҳақи бўйича мажбурият",
                AccountDetailType.PAYROLL_CLEARING);
        post(DATE, bank, equity, "500000");
        Contact advanced = contactService.create(ContactType.EMPLOYEE, new ContactData(
                "BS аванс ходими", null, null, null, null, null,
                null, null, null, null, null));
        Contact accrued = contactService.create(ContactType.EMPLOYEE, new ContactData(
                "BS ҳисобланган ходим", null, null, null, null, null,
                null, null, null, null, null));
        // Аванс тўловининг GL таъсири (payroll ADVANCE кўзгуси):
        // Dr clearing (ходим кесимида) / Cr банк
        postingService.createAndPost(JournalEntryRequest.manual(DATE, "аванс каби", List.of(
                new Line(clearing.getId(), Money.ofBase(new BigDecimal("120000"), HOME), null,
                        advanced.getId(), null, null, null),
                Line.credit(bank.getId(), Money.ofBase(new BigDecimal("120000"), HOME), null))));
        // Бошқа ходимга ҳисобланган (кредит) қолдиқ - мажбуриятда қолиши шарт
        postingService.createAndPost(JournalEntryRequest.manual(DATE, "ҳисоблаш каби", List.of(
                Line.debit(equity.getId(), Money.ofBase(new BigDecimal("200000"), HOME), null),
                new Line(clearing.getId(), null, Money.ofBase(new BigDecimal("200000"), HOME),
                        accrued.getId(), null, null, null))));
        em.flush();

        BalanceSheetService.Report report = balanceSheetService.build(DATE);

        // Аванс актив томонга reclass қилинган; кредит қолдиқ мажбуриятда тўлиқ
        assertThat(report.employeeAdvances()).isEqualByComparingTo("120000");
        BalanceSheetService.Group liab =
                group(report.currentLiabilityGroups(), AccountType.OTHER_CURRENT_LIABILITY);
        assertThat(row(liab.rows(), "Иш ҳақи бўйича мажбурият").amount())
                .isEqualByComparingTo("200000");
        // Мажбуриятда манфий сатр йўқ
        assertThat(report.currentLiabilityGroups())
                .allSatisfy(g -> assertThat(g.rows())
                        .noneMatch(r -> r.amount().signum() < 0));
        // Тенглама: банк 500000−120000=380000; активлар 380000+120000=500000;
        // мажбурият 200000 + капитал (500000−200000)=300000
        assertThat(report.totalCurrentAssets()).isEqualByComparingTo("500000");
        assertThat(report.totalCurrentLiabilities()).isEqualByComparingTo("200000");
        assertThat(report.totalLiabilitiesAndEquity()).isEqualByComparingTo("500000");
        assertThat(report.balanced()).isTrue();
    }

    /** Тур бўйича гуруҳни топади - бўлмаса дарров йиқилсин. */
    private static BalanceSheetService.Group group(
            List<BalanceSheetService.Group> groups, AccountType type) {
        return groups.stream().filter(g -> g.type() == type).findFirst().orElseThrow();
    }

    /** Ном бўйича сатрни топади - бўлмаса дарров йиқилсин. */
    private static BalanceSheetService.Row row(
            List<BalanceSheetService.Row> rows, String name) {
        return rows.stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
    }
}
