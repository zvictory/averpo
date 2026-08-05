package com.averpo.erp.bank;

import com.averpo.erp.bank.domain.BankTransaction;
import com.averpo.erp.bank.service.BankTransactionService;
import com.averpo.erp.bank.service.BankTransactionService.LineData;
import com.averpo.erp.bank.service.BankTransactionService.TransferData;
import com.averpo.erp.bank.service.BankTransactionService.TxnData;
import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.ledger.domain.EntryStatus;
import com.averpo.erp.ledger.domain.JournalEntry;
import com.averpo.erp.ledger.domain.JournalEntryLine;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.repo.JournalEntryRepository;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.ExchangeRateService;
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
 * Банк транзакциялари тестлари: docs/modules/banking.md → «Тестлар»
 * (2-туртки). GL posting-rules «Банк» жадвалига мослиги ва конверсия
 * FX механикаси шу ерда текширилади (ТЕМИР ҚОИДА №7: debit == credit).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BankTransactionServiceTest {

    /** Барча тест ҳужжатлар санаси. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 6);

    @Autowired BankTransactionService bankService;
    @Autowired ContactService contactService;
    @Autowired AccountService accountService;
    @Autowired AccountRepository accountRepository;
    @Autowired JournalEntryRepository entryRepository;
    @Autowired ExchangeRateService exchangeRateService;
    @Autowired com.averpo.erp.shared.service.PaymentMethodService paymentMethodService;

    /** Home валютали банк счёти (CHECKING). */
    private UUID bank;

    /** USD валютали банк счёти. */
    private UUID usdBank;

    /** Касса (CASH_ON_HAND - BANK тури). */
    private UUID cash;

    /** Тушумлар транзити (UNDEPOSITED_FUNDS) - deposit манбаси. */
    private UUID undeposited;

    /** Ижара счёти - expense сатри учун. */
    private UUID rent;

    /** Заём (LOAN_PAYABLE) - тизим эмас, BANK эмас BS счёти транзфер тести учун. */
    private UUID loan;

    /** Chart + счётлар тайёрланади (rollback тозалайди). */
    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        bank = accountRepository.findByName("Банк ҳисобварағи").orElseThrow().getId();
        usdBank = accountRepository.findByName("Валюта ҳисобварағи (USD)").orElseThrow().getId();
        cash = accountRepository.findByName("Касса").orElseThrow().getId();
        undeposited = accountRepository.findByName("Тушумлар транзити").orElseThrow().getId();
        rent = accountRepository.findByName("Ижара").orElseThrow().getId();
        // Default chart'да заём счёти йўқ - транзфер кўлами тести учун яратилади
        loan = accountService.create("Қисқа муддатли кредит",
                com.averpo.erp.ledger.domain.AccountDetailType.LOAN_PAYABLE,
                null, null, null, true, null).getId();
    }

    /** Манба бўйича фаол GL ёзувини топади. */
    private JournalEntry glEntry(BankTransaction txn) {
        return entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                BankTransactionService.SOURCE_MODULE, txn.getId()).orElseThrow();
    }

    /** Detail type бўйича дебет/кредит base йиғиндиси. */
    private BigDecimal baseOf(JournalEntry entry, String detailType, boolean debit) {
        BigDecimal sum = BigDecimal.ZERO;
        for (JournalEntryLine line : entry.getLines()) {
            var money = debit ? line.getDebit() : line.getCredit();
            if (money != null && line.getAccount().getDetailType().name().equals(detailType)) {
                sum = sum.add(money.getBaseAmount());
            }
        }
        return sum;
    }

    /** Entry'нинг home'даги дебет/кредит жамлари тенглигини текширади. */
    private void assertBalanced(JournalEntry entry, String expectedTotal) {
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (JournalEntryLine line : entry.getLines()) {
            if (line.getDebit() != null) {
                debit = debit.add(line.getDebit().getBaseAmount());
            }
            if (line.getCredit() != null) {
                credit = credit.add(line.getCredit().getBaseAmount());
            }
        }
        assertThat(debit).isEqualByComparingTo(credit);
        assertThat(debit).isEqualByComparingTo(expectedTotal);
    }

    @Test
    void deposit_multiLine_glMatchesPostingRules() {
        Contact payer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Депозит мижози", null, null, null, null, null,
                null, null, null, null, null));
        // Икки манбадан битта deposit: транзитдан 50 000 + ижарадан қайтим 30 000
        BankTransaction txn = bankService.deposit(new TxnData(bank, DATE, null, null,
                "кунлик тушум", List.of(
                        new LineData(undeposited, new BigDecimal("50000"), payer.getId(), null),
                        new LineData(rent, new BigDecimal("30000"), null, "ижара қайтими"))));

        assertThat(txn.getTxnNumber()).startsWith("BT-2026-");
        assertThat(txn.getStatus()).isEqualTo(BankTransaction.Status.POSTED);
        assertThat(txn.getTotal()).isEqualByComparingTo("80000");

        JournalEntry entry = glEntry(txn);
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.POSTED);
        assertBalanced(entry, "80000");
        // Банк Dt жами / манбалар Cr (сатр контакти dimension'да)
        assertThat(baseOf(entry, "CHECKING", true)).isEqualByComparingTo("80000");
        assertThat(baseOf(entry, "UNDEPOSITED_FUNDS", false)).isEqualByComparingTo("50000");
        assertThat(entry.getLines().stream()
                .filter(l -> l.getCredit() != null && payer.getId().equals(l.getContactId()))
                .count()).isEqualTo(1);
    }

    /** Ҳар GL сатрининг Money инварианти (BR-LED-003): |base − amount × rate| ≤ 0.0001. */
    private void assertMoneyInvariant(JournalEntry entry) {
        for (JournalEntryLine line : entry.getLines()) {
            for (var money : new com.averpo.erp.shared.domain.Money[]{
                    line.getDebit(), line.getCredit()}) {
                if (money != null) {
                    BigDecimal expected = money.getAmount().multiply(money.getExchangeRate());
                    assertThat(money.getBaseAmount().subtract(expected).abs())
                            .isLessThanOrEqualTo(new BigDecimal("0.0001"));
                }
            }
        }
    }

    @Test
    void expense_foreignMultiLine_pennyRounding_balancedAndPosts() {
        // PERF-009 + LOG-002: банк томони (назорат сатри) битта
        // яхлитлашли target = round(0.06 × 12345.6789) = 740.7407, сатр
        // base'лари largest-remainder билан шунга тақсимланади
        BankTransaction txn = bankService.expense(new TxnData(usdBank, DATE,
                new BigDecimal("12345.6789"), null, null, List.of(
                        new LineData(rent, new BigDecimal("0.03"), null, null),
                        new LineData(rent, new BigDecimal("0.03"), null, null))));

        JournalEntry entry = glEntry(txn);
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.POSTED);
        assertBalanced(entry, "740.7407");
        // Entity totalBase ҳам худди шу target'да (ҳисоб битта жойда)
        assertThat(txn.getTotalBase()).isEqualByComparingTo("740.7407");
        assertMoneyInvariant(entry);
    }

    @Test
    void expense_foreignThreeLines_everyLineKeepsMoneyInvariant() {
        // LOG-002 сценарийси: 3 × 0.01 USD, rate 10012.345 - эски
        // «йиғинди» ечимида банк сатри BR-LED-003 дан 0.00015 га чиқиб,
        // тўғри киритилган транзакция пост бўлмай қоларди
        BankTransaction txn = bankService.expense(new TxnData(usdBank, DATE,
                new BigDecimal("10012.345"), null, null, List.of(
                        new LineData(rent, new BigDecimal("0.01"), null, null),
                        new LineData(rent, new BigDecimal("0.01"), null, null),
                        new LineData(rent, new BigDecimal("0.01"), null, null))));

        JournalEntry entry = glEntry(txn);
        // target = round(0.03 × 10012.345) = 300.3704
        assertBalanced(entry, "300.3704");
        assertThat(txn.getTotalBase()).isEqualByComparingTo("300.3704");
        // Ҳар сатр (банк томони ҳам!) Money инвариантидан ўтади
        assertMoneyInvariant(entry);
    }

    @Test
    void expense_glMatchesPostingRules() {
        Contact landlord = contactService.create(ContactType.VENDOR, new ContactData(
                "Ижарачи", null, null, null, null, null,
                null, null, null, null, null));
        BankTransaction txn = bankService.expense(new TxnData(bank, DATE, null,
                landlord.getId(), null, List.of(
                        new LineData(rent, new BigDecimal("40000"), null, "июль ижараси"))));

        JournalEntry entry = glEntry(txn);
        assertBalanced(entry, "40000");
        // Харажат Dt / банк Cr
        assertThat(baseOf(entry, "CHECKING", false)).isEqualByComparingTo("40000");
        // Банк сатрида header контакт (payee) dimension'да
        assertThat(entry.getLines().stream()
                .filter(l -> l.getCredit() != null)
                .allMatch(l -> landlord.getId().equals(l.getContactId()))).isTrue();
    }

    @Test
    void transfer_sameCurrency_noFxLine() {
        BankTransaction txn = bankService.transfer(new TransferData(bank, cash, DATE,
                new BigDecimal("100000"), null, null, null, null));

        JournalEntry entry = glEntry(txn);
        // Иккита сатр: касса Dt / банк Cr, FX сатр ЙЎҚ
        assertThat(entry.getLines()).hasSize(2);
        assertBalanced(entry, "100000");
        assertThat(baseOf(entry, "CASH_ON_HAND", true)).isEqualByComparingTo("100000");
        assertThat(baseOf(entry, "CHECKING", false)).isEqualByComparingTo("100000");
    }

    @Test
    void transfer_toNonBankBalanceSheetAccount_works() {
        // BR-TXF-001 (DEC-022): транзфер BANK эмас, ҳар қандай Balance
        // Sheet счёти орасида. loan = LOAN_PAYABLE (Мажбурият) - заёмни
        // банкдан ёпиш (spec'даги «заёмни ёпиш» ҳолати). Аввал бу тест
        // UNDEPOSITED_FUNDS билан эди - у энди тизим назорат счёти
        // сифатида BR-TXF-002 билан рад этилади (IFRS-008).
        BankTransaction txn = bankService.transfer(new TransferData(bank, loan, DATE,
                new BigDecimal("75000"), null, null, null, "заём тўлови"));

        JournalEntry entry = glEntry(txn);
        assertThat(entry.getLines()).hasSize(2);
        assertBalanced(entry, "75000");
        // Манзил (LOAN_PAYABLE) Dt - мажбурият камаяди / манба (CHECKING) Cr
        assertThat(baseOf(entry, "LOAN_PAYABLE", true)).isEqualByComparingTo("75000");
        assertThat(baseOf(entry, "CHECKING", false)).isEqualByComparingTo("75000");
    }

    @Test
    void linedTransaction_systemControlAccountLine_rejectedBt010() {
        // QA-012 (BR-TXF-002 кўзгуси): deposit/expense сатрида ҳам
        // тизим назорат счёти рад - GL subledger'сиз ўзгармасин
        UUID inventory = accountRepository
                .findByName("Товар-моддий заҳиралар").orElseThrow().getId();
        UUID ar = accountRepository.findByName("Дебиторлик (AR)").orElseThrow().getId();

        // INVENTORY сатрли кирим рад (BR-BT-010)
        assertThatThrownBy(() -> bankService.deposit(new TxnData(bank, DATE, null,
                null, null, List.of(new LineData(inventory, new BigDecimal("100000"),
                        null, null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BT-010"));

        // AR сатрли чиқим ҳам рад
        assertThatThrownBy(() -> bankService.expense(new TxnData(bank, DATE, null,
                null, null, List.of(new LineData(ar, new BigDecimal("50000"),
                        null, null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BT-010"));
    }

    @Test
    void expense_withPaymentMethodAndRefNo_savedAndBalanced() {
        // DEC-033: тўлов усули (seed каталогдан) ва ҳужжат рақами
        // сақланади, JE аввалгидек балансда - проводка ўзгармаган
        UUID method = paymentMethodService.all().stream()
                .filter(m -> "Нақд".equals(m.getName())).findFirst().orElseThrow().getId();
        BankTransaction txn = bankService.expense(new TxnData(bank, DATE, null,
                null, "офис харажати", List.of(
                        new LineData(rent, new BigDecimal("40000"), null, null)),
                method, "ЧЕК-00123"));

        assertThat(txn.getPaymentMethodId()).isEqualTo(method);
        assertThat(txn.getRefNo()).isEqualTo("ЧЕК-00123");
        JournalEntry entry = glEntry(txn);
        assertBalanced(entry, "40000"); // ТЕМИР ҚОИДА №7: debit == credit
        assertThat(baseOf(entry, "CHECKING", false)).isEqualByComparingTo("40000");

        // Каталогда йўқ усул - NotFound (BR кодисиз, DEC-033 кўлами)
        assertThatThrownBy(() -> bankService.expense(new TxnData(bank, DATE, null,
                null, null, List.of(new LineData(rent, BigDecimal.ONE, null, null)),
                UUID.randomUUID(), null)))
                .isInstanceOf(com.averpo.erp.shared.exception.NotFoundException.class);
    }

    @Test
    void expense_salesTaxPayableLine_allowed_vatPaymentStaysOpen() {
        // SALES_TAX_PAYABLE systemManaged ЭМАС (AccountDetailType JavaDoc):
        // алоҳида tax payment оқими йўқ - ҚҚС тўлови айнан чиқим орқали.
        // Бу тест шу йўл очиқлигини қотиради (BR-BT-010 уни ёпмасин).
        UUID vat = accountRepository.findByName("ҚҚС тўланадиган").orElseThrow().getId();
        BankTransaction txn = bankService.expense(new TxnData(bank, DATE, null,
                null, "ҚҚС тўлови", List.of(new LineData(vat, new BigDecimal("120000"),
                        null, null))));

        JournalEntry entry = glEntry(txn);
        assertBalanced(entry, "120000");
        // ҚҚС мажбурияти Dt (камаяди) / банк Cr
        assertThat(baseOf(entry, "SALES_TAX_PAYABLE", true)).isEqualByComparingTo("120000");
    }

    @Test
    void transfer_systemControlAccount_rejectedTxf002() {
        // IFRS-008: тизим назорат счёти (systemManaged) - GL'га ёзув фақат
        // ўз subledger хизмати орқали. Қўлда транзфер GL'ни ўзгартириб,
        // StockMovement/aging'ни ўзгартирмасди - мувофиқлик бузиларди.
        UUID inventory = accountRepository
                .findByName("Товар-моддий заҳиралар").orElseThrow().getId();
        UUID ar = accountRepository.findByName("Дебиторлик (AR)").orElseThrow().getId();

        // Манзил тизим счёти: банк → INVENTORY рад
        assertThatThrownBy(() -> bankService.transfer(new TransferData(bank, inventory, DATE,
                new BigDecimal("100000"), null, null, null, null)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-TXF-002"));

        // Манба тизим счёти: AR → банк ҳам рад (иккала йўналиш ҳимояланган)
        assertThatThrownBy(() -> bankService.transfer(new TransferData(ar, bank, DATE,
                new BigDecimal("100000"), null, null, null, null)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-TXF-002"));
    }

    @Test
    void transfer_payrollClearing_rejectedTxf002() {
        // Payroll (23а): PAYROLL_CLEARING systemManaged - ҳисобланган иш ҳақи
        // фақат PayrollPayment орқали тўланади. Қўлда транзфер (банк → clearing)
        // ходим кесими субледжерини GL'дан ажратар эди - BR-TXF-002 рад.
        UUID payrollClearing = accountRepository
                .findByName("Иш ҳақи бўйича мажбурият").orElseThrow().getId();
        assertThatThrownBy(() -> bankService.transfer(new TransferData(bank, payrollClearing, DATE,
                new BigDecimal("100000"), null, null, null, null)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-TXF-002"));
    }

    @Test
    void expense_payrollClearingLine_rejectedBt010() {
        // Payroll (23а): PAYROLL_CLEARING кирим/чиқим сатрида ҳам танланмайди
        // (BR-BT-010) - тўлов фақат PayrollPayment орқали (banking.md изоҳи).
        UUID payrollClearing = accountRepository
                .findByName("Иш ҳақи бўйича мажбурият").orElseThrow().getId();
        assertThatThrownBy(() -> bankService.expense(new TxnData(bank, DATE, null,
                null, "Тест", List.of(new LineData(payrollClearing,
                        new BigDecimal("100000"), null, null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BT-010"));
    }

    @Test
    void transfer_toNonBalanceSheetAccount_rejectedTxf001() {
        // rent = Ижара (EXPENSE, P&L) - Balance Sheet эмас, транзфер рад.
        // Income/Expense счётга ўтказма QBO'да ҳам йўқ (у deposit/expense).
        assertThatThrownBy(() -> bankService.transfer(new TransferData(bank, rent, DATE,
                BigDecimal.ONE, null, null, null, null)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-TXF-001"));
    }

    @Test
    void transfer_singleForeign_upsertsRateToCatalog() {
        // Т3 (DEC-022): бир томони уй валютаси (UZS→USD), курс 12700
        // берилди - ўша курс каталогга ҳужжат санаси билан ёзилиши шарт.
        bankService.transfer(new TransferData(bank, usdBank, DATE,
                new BigDecimal("1270000"), null,
                new BigDecimal("100"), new BigDecimal("12700"), null));

        assertThat(exchangeRateService.rateFor("USD", DATE))
                .hasValueSatisfying(r -> assertThat(r).isEqualByComparingTo("12700"));
    }

    @Test
    void transfer_conversion_gainAndLoss() {
        // Зарар: 1 270 000 сўм бердик, 100 USD × 12 600 = 1 260 000 олдик
        BankTransaction loss = bankService.transfer(new TransferData(bank, usdBank, DATE,
                new BigDecimal("1270000"), null,
                new BigDecimal("100"), new BigDecimal("12600"), null));
        JournalEntry lossJe = glEntry(loss);
        assertBalanced(lossJe, "1270000");
        assertThat(baseOf(lossJe, "EXCHANGE_GAIN_OR_LOSS", true)).isEqualByComparingTo("10000");

        // Фойда: 1 250 000 сўм бердик, ўша 100 USD (1 260 000) олдик
        BankTransaction gain = bankService.transfer(new TransferData(bank, usdBank, DATE,
                new BigDecimal("1250000"), null,
                new BigDecimal("100"), new BigDecimal("12600"), null));
        JournalEntry gainJe = glEntry(gain);
        assertBalanced(gainJe, "1260000");
        assertThat(baseOf(gainJe, "EXCHANGE_GAIN_OR_LOSS", false)).isEqualByComparingTo("10000");

        // Айнан тенг: 1 260 000 ↔ 100 USD - FX сатр йўқ
        BankTransaction even = bankService.transfer(new TransferData(bank, usdBank, DATE,
                new BigDecimal("1260000"), null,
                new BigDecimal("100"), new BigDecimal("12600"), null));
        assertThat(glEntry(even).getLines()).hasSize(2);
    }

    @Test
    void transfers_onlyTransferType_newestFirst() {
        // PERF-020: рўйхат бутун журнални тортмайди - deposit
        // аралашмайди, тартиб умумий рўйхатдек (сана, кейин created_at)
        bankService.deposit(new TxnData(bank, DATE, null, null, null,
                List.of(new LineData(rent, new BigDecimal("5000"), null, null))));
        BankTransaction older = bankService.transfer(new TransferData(bank, cash,
                DATE.minusDays(1), new BigDecimal("10000"), null, null, null, null));
        BankTransaction newer = bankService.transfer(new TransferData(bank, cash,
                DATE, new BigDecimal("20000"), null, null, null, null));

        List<BankTransaction> transfers = bankService.transfers(
                new BankTransactionService.ListFilter(null, null, null, null, null), 0).getContent();
        assertThat(transfers).allMatch(t ->
                t.getType() == com.averpo.erp.bank.domain.BankTransactionType.TRANSFER);
        assertThat(transfers).extracting(BankTransaction::getId)
                .containsExactly(newer.getId(), older.getId());
        // JOIN FETCH: валюта дарҳол келади (home банкда каталог UZS'и)
        assertThat(transfers.get(0).getCurrency()).isNotNull();
    }

    @Test
    void transfers_pagination_secondPageSlice() {
        // DEC-105 3-босқич: transfers Page<>'га ўтди - size+1 ўтказма,
        // 2-саҳифада биттагина қолади (JOIN FETCH count сўровини бузмайди)
        for (int i = 0; i <= BankTransactionService.LIST_PAGE_SIZE; i++) {
            bankService.transfer(new TransferData(bank, cash,
                    DATE.minusDays(i), new BigDecimal("1000"), null, null, null, null));
        }
        var page0 = bankService.transfers(
                new BankTransactionService.ListFilter(null, null, null, null, null), 0);
        assertThat(page0.getContent()).hasSize(BankTransactionService.LIST_PAGE_SIZE);
        assertThat(page0.getTotalElements())
                .isEqualTo(BankTransactionService.LIST_PAGE_SIZE + 1);
        assertThat(page0.getTotalPages()).isEqualTo(2);
        var page1 = bankService.transfers(
                new BankTransactionService.ListFilter(null, null, null, null, null), 1);
        assertThat(page1.getContent()).hasSize(1);
        assertThat(page1.hasNext()).isFalse();
    }

    @Test
    void list_pagination_secondPageSlice_stableSort() {
        // PERF-perf1 2-босқич: size+1 ёзув - 2-саҳифада биттагина
        // қолади; саналар ҳар хил - тартиб детерминистик текширилади
        BankTransaction oldest = null;
        BankTransaction newest = null;
        for (int i = BankTransactionService.LIST_PAGE_SIZE; i >= 0; i--) {
            BankTransaction txn = bankService.deposit(new TxnData(bank, DATE.minusDays(i),
                    null, null, null,
                    List.of(new LineData(rent, new BigDecimal("1000"), null, null))));
            if (oldest == null) {
                oldest = txn; // биринчи яратилгани энг эски санали
            }
            newest = txn;
        }

        var page0 = bankService.list(
                new BankTransactionService.ListFilter(null, null, null, null, null), 0);
        assertThat(page0.getContent()).hasSize(BankTransactionService.LIST_PAGE_SIZE);
        assertThat(page0.getTotalElements()).isEqualTo(BankTransactionService.LIST_PAGE_SIZE + 1);
        assertThat(page0.getTotalPages()).isEqualTo(2);
        // Барқарор тартиб: энг янги санали биринчи (аввалги ORDER BY)
        assertThat(page0.getContent().get(0).getId()).isEqualTo(newest.getId());

        var page1 = bankService.list(
                new BankTransactionService.ListFilter(null, null, null, null, null), 1);
        assertThat(page1.getContent()).hasSize(1);
        assertThat(page1.getContent().get(0).getId()).isEqualTo(oldest.getId());
        assertThat(page1.hasNext()).isFalse();
    }

    @Test
    void expenses_pagination_secondPageSlice_onlyExpenseType() {
        // PERF-perf1 2-босқич: /expenses фақат EXPENSE турини саҳифалайди;
        // аралашга битта deposit ҳам яратилади - у чиқмаслиги шарт
        bankService.deposit(new TxnData(bank, DATE, null, null, null,
                List.of(new LineData(rent, new BigDecimal("5000"), null, null))));
        BankTransaction oldest = null;
        BankTransaction newest = null;
        for (int i = BankTransactionService.LIST_PAGE_SIZE; i >= 0; i--) {
            BankTransaction txn = bankService.expense(new TxnData(bank, DATE.minusDays(i),
                    null, null, null,
                    List.of(new LineData(rent, new BigDecimal("1000"), null, null))));
            if (oldest == null) {
                oldest = txn;
            }
            newest = txn;
        }

        var page0 = bankService.expenses(
                new BankTransactionService.ListFilter(null, null, null, null, null), 0);
        assertThat(page0.getContent()).hasSize(BankTransactionService.LIST_PAGE_SIZE);
        // Deposit аралашмайди - жами айнан EXPENSE size+1
        assertThat(page0.getTotalElements()).isEqualTo(BankTransactionService.LIST_PAGE_SIZE + 1);
        assertThat(page0.getContent()).allMatch(t ->
                t.getType() == com.averpo.erp.bank.domain.BankTransactionType.EXPENSE);
        // JOIN FETCH: валюта дарҳол келади (N+1 йўқ)
        assertThat(page0.getContent().get(0).getCurrency()).isNotNull();
        assertThat(page0.getContent().get(0).getId()).isEqualTo(newest.getId());

        var page1 = bankService.expenses(
                new BankTransactionService.ListFilter(null, null, null, null, null), 1);
        assertThat(page1.getContent()).hasSize(1);
        assertThat(page1.getContent().get(0).getId()).isEqualTo(oldest.getId());
        assertThat(page1.hasNext()).isFalse();
    }

    @Test
    void expenses_filter_byDateStatusPayeeText() {
        // UX-009 (DEC-068): давр/статус/payee/матн ҳар бири кесади;
        // transfers'дан фарқ - payee (contactId) филтри (чиқимда Олувчи бор)
        UUID payeeA = contactService.create(ContactType.VENDOR, new ContactData(
                "Олувчи А 009", null, null, null, null, null,
                null, null, null, null, null)).getId();
        UUID payeeB = contactService.create(ContactType.VENDOR, new ContactData(
                "Олувчи Б 009", null, null, null, null, null,
                null, null, null, null, null)).getId();
        // payeeA - эски сана, memo «Синов ижара»
        BankTransaction oldA = bankService.expense(new TxnData(bank, DATE.minusDays(10),
                null, payeeA, "Синов ижара",
                List.of(new LineData(rent, new BigDecimal("1000"), null, null))));
        // payeeB - янги сана, memo «бошқа»
        BankTransaction newB = bankService.expense(new TxnData(bank, DATE,
                null, payeeB, "бошқа тўлов",
                List.of(new LineData(rent, new BigDecimal("2000"), null, null))));
        // Аралашга битта deposit - ҳеч бир филтрда чиқмайди (type=EXPENSE)
        bankService.deposit(new TxnData(bank, DATE, null, null, null,
                List.of(new LineData(rent, new BigDecimal("500"), null, null))));

        // Филтрсиз: янгидан эскига, deposit аралашмайди
        assertThat(bankService.expenses(new BankTransactionService.ListFilter(
                null, null, null, null, null), 0).getContent())
                .extracting(BankTransaction::getId).containsExactly(newB.getId(), oldA.getId());
        // Давр: эски кун атрофи → oldA
        assertThat(bankService.expenses(new BankTransactionService.ListFilter(
                DATE.minusDays(12), DATE.minusDays(8), null, null, null), 0).getContent())
                .extracting(BankTransaction::getId).containsExactly(oldA.getId());
        // Payee: payeeB → newB
        assertThat(bankService.expenses(new BankTransactionService.ListFilter(
                null, null, null, payeeB, null), 0).getContent())
                .extracting(BankTransaction::getId).containsExactly(newB.getId());
        // Матн: «ижара» → oldA (memo)
        assertThat(bankService.expenses(new BankTransactionService.ListFilter(
                null, null, null, null, "ижара"), 0).getContent())
                .extracting(BankTransaction::getId).containsExactly(oldA.getId());
        // Статус: oldA'ни reverse → REVERSED фақат oldA, POSTED фақат newB
        bankService.reverse(oldA.getId(), DATE, "тест сторно");
        assertThat(bankService.expenses(new BankTransactionService.ListFilter(
                null, null, BankTransaction.Status.REVERSED, null, null), 0).getContent())
                .extracting(BankTransaction::getId).containsExactly(oldA.getId());
        assertThat(bankService.expenses(new BankTransactionService.ListFilter(
                null, null, BankTransaction.Status.POSTED, null, null), 0).getContent())
                .extracting(BankTransaction::getId).containsExactly(newB.getId());
    }

    @Test
    void expenses_filter_textCyrillicCaseInsensitive() {
        // list-filters.md МАЖБУРИЙ: кирилл q катта-кичик фарқсиз (ILIKE)
        BankTransaction txn = bankService.expense(new TxnData(bank, DATE, null, null,
                "Синов Тўлови",
                List.of(new LineData(rent, new BigDecimal("1000"), null, null))));
        for (String q : List.of("синов", "СИНОВ", "Синов", "тўлов")) {
            assertThat(bankService.expenses(new BankTransactionService.ListFilter(
                    null, null, null, null, q), 0).getContent())
                    .as("q=%s", q)
                    .extracting(BankTransaction::getId).contains(txn.getId());
        }
        // Мос келмаган матн - бўш
        assertThat(bankService.expenses(new BankTransactionService.ListFilter(
                null, null, null, null, "мавжудэмас"), 0).getContent()).isEmpty();
    }

    @Test
    void expenses_filter_paginationPreservesFilter() {
        // Филтр + саҳифалаш бирга: payeeA'га size+1 чиқим, бошқа payee'га
        // қўшимча - иккала саҳифада ҳам фақат payeeA қолади (филтр сақланади)
        UUID payeeA = contactService.create(ContactType.VENDOR, new ContactData(
                "Олувчи Пейж 009", null, null, null, null, null,
                null, null, null, null, null)).getId();
        UUID payeeB = contactService.create(ContactType.VENDOR, new ContactData(
                "Олувчи Бошқа 009", null, null, null, null, null,
                null, null, null, null, null)).getId();
        bankService.expense(new TxnData(bank, DATE, null, payeeB, "аралаш",
                List.of(new LineData(rent, new BigDecimal("1000"), null, null))));
        for (int i = 0; i <= BankTransactionService.LIST_PAGE_SIZE; i++) {
            bankService.expense(new TxnData(bank, DATE.minusDays(i), null, payeeA, "A тўлов",
                    List.of(new LineData(rent, new BigDecimal("1000"), null, null))));
        }
        var filter = new BankTransactionService.ListFilter(null, null, null, payeeA, null);
        var page0 = bankService.expenses(filter, 0);
        assertThat(page0.getContent()).hasSize(BankTransactionService.LIST_PAGE_SIZE);
        assertThat(page0.getTotalElements()).isEqualTo(BankTransactionService.LIST_PAGE_SIZE + 1);
        assertThat(page0.getContent()).allMatch(t -> payeeA.equals(t.getContactId()));
        var page1 = bankService.expenses(filter, 1);
        assertThat(page1.getContent()).hasSize(1);
        assertThat(page1.getContent().get(0).getContactId()).isEqualTo(payeeA);
        assertThat(page1.hasNext()).isFalse();
    }

    @Test
    void foreignBank_currencyFromAccount_rateRequired() {
        // Курссиз - BR-BT-008
        assertThatThrownBy(() -> bankService.expense(new TxnData(usdBank, DATE, null,
                null, null, List.of(new LineData(rent, BigDecimal.TEN, null, null)))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BT-008"));

        // Курс билан: 10 USD × 12 600 = 126 000 base
        BankTransaction txn = bankService.expense(new TxnData(usdBank, DATE,
                new BigDecimal("12600"), null, null,
                List.of(new LineData(rent, BigDecimal.TEN, null, null))));
        assertThat(txn.getCurrency().getCode()).isEqualTo("USD");
        assertThat(txn.getTotalBase()).isEqualByComparingTo("126000");
        assertBalanced(glEntry(txn), "126000");
    }

    @Test
    void reverse_stornosGl_andGuards() {
        BankTransaction txn = bankService.transfer(new TransferData(bank, cash, DATE,
                new BigDecimal("50000"), null, null, null, null));

        bankService.reverse(txn.getId(), DATE, "хато ўтказма");

        assertThat(txn.getStatus()).isEqualTo(BankTransaction.Status.REVERSED);
        assertThat(glEntry(txn).getStatus()).isEqualTo(EntryStatus.REVERSED);
        // Иккинчи reverse тақиқ
        assertThatThrownBy(() -> bankService.reverse(txn.getId(), DATE, null))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BT-007"));
    }

    @Test
    void validation_guards() {
        List<LineData> okLine = List.of(new LineData(rent, BigDecimal.ONE, null, null));

        // BR-BT-002: банк счёти эмас (Ижара - EXPENSE)
        assertThatThrownBy(() -> bankService.deposit(new TxnData(rent, DATE, null,
                null, null, okLine)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BT-002"));
        assertThatThrownBy(() -> bankService.deposit(new TxnData(null, DATE, null,
                null, null, okLine)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BT-002"));

        // BR-BT-006: сана йўқ; BR-BT-003: сатр йўқ
        assertThatThrownBy(() -> bankService.deposit(new TxnData(bank, null, null,
                null, null, okLine)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BT-006"));
        assertThatThrownBy(() -> bankService.deposit(new TxnData(bank, DATE, null,
                null, null, List.of())))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BT-003"));

        // BR-BT-001: нол сумма
        assertThatThrownBy(() -> bankService.deposit(new TxnData(bank, DATE, null,
                null, null, List.of(new LineData(rent, BigDecimal.ZERO, null, null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BT-001"));

        // BR-BT-004: сатр счёти банкнинг ўзи / танланмаган
        assertThatThrownBy(() -> bankService.deposit(new TxnData(bank, DATE, null,
                null, null, List.of(new LineData(bank, BigDecimal.ONE, null, null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BT-004"));
        assertThatThrownBy(() -> bankService.deposit(new TxnData(bank, DATE, null,
                null, null, List.of(new LineData(null, BigDecimal.ONE, null, null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BT-004"));

        // BR-BT-005: манба == манзил
        assertThatThrownBy(() -> bankService.transfer(new TransferData(bank, bank, DATE,
                BigDecimal.ONE, null, null, null, null)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BT-005"));
    }
}
