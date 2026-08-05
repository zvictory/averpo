package com.averpo.erp.sales;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.domain.ItemType;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.item.service.ItemService.ItemData;
import com.averpo.erp.ledger.domain.EntryStatus;
import com.averpo.erp.ledger.domain.JournalEntry;
import com.averpo.erp.ledger.domain.JournalEntryLine;
import com.averpo.erp.ledger.repo.AccountRepository;
import com.averpo.erp.ledger.repo.JournalEntryRepository;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.sales.domain.Invoice;
import com.averpo.erp.sales.domain.InvoicePayment;
import com.averpo.erp.sales.domain.InvoicePaymentAllocation;
import com.averpo.erp.sales.domain.InvoicePaymentStatus;
import com.averpo.erp.sales.service.InvoicePaymentService;
import com.averpo.erp.sales.service.InvoicePaymentService.AllocationData;
import com.averpo.erp.sales.service.InvoicePaymentService.PaymentData;
import com.averpo.erp.sales.service.InvoiceService;
import com.averpo.erp.sales.service.InvoiceService.InvoiceData;
import com.averpo.erp.sales.service.InvoiceService.LineData;
import com.averpo.erp.shared.exception.BusinessRuleException;
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
 * InvoicePayment (тушум) тестлари: docs/modules/sales.md → «Тестлар»
 * (3-туртки). GL, allocation денормализацияси, realized курс фарқи
 * (йўналиши AP томонга ТЕСКАРИ) ва reverse шу ерда текширилади.
 * Invoice'лар SERVICE сатрли - омбор шарт эмас.
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InvoicePaymentServiceTest {

    /** Барча тест ҳужжатлар санаси. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 6);

    @Autowired InvoicePaymentService paymentService;
    @Autowired InvoiceService invoiceService;
    @Autowired ContactService contactService;
    @Autowired ItemService itemService;
    @Autowired AccountService accountService;
    @Autowired AccountRepository accountRepository;
    @Autowired JournalEntryRepository entryRepository;

    /** Тест мижози (home валюта). */
    private Contact customer;

    /**
     * USD валютали мижоз (Arbitr-087): invoice валютаси контактдан келади -
     * USD invoice ва унинг тўловлари шу мижозга ёзилади (BR-RCPT-009:
     * allocation фақат ўша мижознинг invoice'ига).
     */
    private Contact usdCustomer;

    /** Хизмат item'и (омборсиз invoice учун). */
    private Item service;

    /** Қабул банк счёти (CHECKING, home валюта). */
    private UUID bankAccountId;

    /**
     * USD банк счёти (default chart) - Arbitr-070 дан бери чет валюта
     * тўлов фақат ўз валютасидаги счётга тушади (BR-RCPT-002).
     */
    private UUID usdBankAccountId;

    /** Chart + мижоз + хизмат item тайёрланади (rollback тозалайди). */
    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        customer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Тушум тест мижози", null, null, null, null, null,
                null, null, null, null, null));
        usdCustomer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Тушум USD мижози", null, null, null, null, null,
                "USD", null, null, null, null));
        ItemService.DefaultAccounts defaults = itemService.defaultsFor(ItemType.SERVICE);
        service = itemService.create(ItemType.SERVICE, new ItemData(
                "Тушум тест хизмати", null, null, null, null, null,
                defaults.income(), null, null, defaults.expense(), null, null));
        bankAccountId = accountRepository.findByName("Банк ҳисобварағи").orElseThrow().getId();
        usdBankAccountId = accountRepository.findByName("Валюта ҳисобварағи (USD)")
                .orElseThrow().getId();
    }

    /** POSTED invoice ясайди: битта SERVICE сатр (qty 1 × сумма). */
    private Invoice postedInvoice(UUID customerId, String currency, BigDecimal rate,
                                  BigDecimal amount) {
        Invoice draft = invoiceService.createDraft(new InvoiceData(customerId, DATE,
                null, currency, rate, null, List.of(new LineData(service.getId(),
                        null, BigDecimal.ONE, amount, null, null))));
        return invoiceService.post(draft.getId());
    }

    /** Home валютадаги POSTED invoice. */
    private Invoice homeInvoice(BigDecimal amount) {
        return postedInvoice(customer.getId(), null, null, amount);
    }

    /** Home валютадаги тушум маълумоти. */
    private PaymentData homePayment(BigDecimal total, List<AllocationData> allocations) {
        return new PaymentData(customer.getId(), DATE, bankAccountId, null, null,
                total, null, allocations);
    }

    /** Манба бўйича фаол GL ёзувини топади. */
    private JournalEntry glEntry(String module, UUID docId) {
        return entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                module, docId).orElseThrow();
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

    /** ТЕМИР ҚОИДА №7: entry ичида жами debit base == credit base. */
    private void assertBalanced(JournalEntry entry) {
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
    }

    @Test
    void list_pagination_secondPageSlice_stableSort() {
        // Beruniy-perf1 2-босқич: size+1 тушум (аллокациясиз аванс) -
        // 2-саҳифада биттагина қолади; саналар ҳар хил - тартиб детерминистик
        InvoicePayment oldest = null;
        InvoicePayment newest = null;
        for (int i = InvoicePaymentService.LIST_PAGE_SIZE; i >= 0; i--) {
            InvoicePayment payment = paymentService.create(new PaymentData(customer.getId(),
                    DATE.minusDays(i), bankAccountId, null, null,
                    new BigDecimal("1000"), null, List.of()));
            if (oldest == null) {
                oldest = payment; // биринчи яратилгани энг эски санали
            }
            newest = payment;
        }

        var page0 = paymentService.list(
                new InvoicePaymentService.ListFilter(null, null, null, null, null), 0);
        assertThat(page0.getContent()).hasSize(InvoicePaymentService.LIST_PAGE_SIZE);
        assertThat(page0.getTotalElements()).isEqualTo(InvoicePaymentService.LIST_PAGE_SIZE + 1);
        assertThat(page0.getTotalPages()).isEqualTo(2);
        // Барқарор тартиб: энг янги санали биринчи (аввалги ORDER BY)
        assertThat(page0.getContent().get(0).getId()).isEqualTo(newest.getId());

        var page1 = paymentService.list(
                new InvoicePaymentService.ListFilter(null, null, null, null, null), 1);
        assertThat(page1.getContent()).hasSize(1);
        assertThat(page1.getContent().get(0).getId()).isEqualTo(oldest.getId());
        assertThat(page1.hasNext()).isFalse();
    }

    @Test
    void create_homePayment_glAndDenormalization_advanceStays() {
        Invoice invoice1 = homeInvoice(new BigDecimal("100000"));
        Invoice invoice2 = homeInvoice(new BigDecimal("60000"));

        // 150 000 тушум: 100 000 invoice1'га, 50 000 аванс
        InvoicePayment payment = paymentService.create(homePayment(new BigDecimal("150000"),
                List.of(new AllocationData(invoice1.getId(), new BigDecimal("100000")))));

        assertThat(payment.getReceiptNumber()).startsWith("RCPT-2026-");
        assertThat(payment.getStatus()).isEqualTo(InvoicePayment.Status.POSTED);
        assertThat(payment.getAllocatedAmount()).isEqualByComparingTo("100000");
        assertThat(payment.getUnallocatedAmount()).isEqualByComparingTo("50000");

        // GL: банк Dt ТЎЛИҚ суммага (аванс ҳам ичида), AR Cr
        JournalEntry entry = glEntry(InvoicePaymentService.SOURCE_MODULE, payment.getId());
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.POSTED);
        assertThat(baseOf(entry, "CHECKING", true)).isEqualByComparingTo("150000");
        assertThat(baseOf(entry, "ACCOUNTS_RECEIVABLE", false)).isEqualByComparingTo("150000");
        assertThat(entry.getLines()).allSatisfy(line ->
                assertThat(line.getContactId()).isEqualTo(customer.getId()));

        // Invoice1: UNPAID → PAID; аванс кейин invoice2'га - PARTIAL
        assertThat(invoice1.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
        assertThat(invoice1.getBalanceDue()).isEqualByComparingTo("0");
        paymentService.allocate(payment.getId(),
                List.of(new AllocationData(invoice2.getId(), new BigDecimal("30000"))));
        assertThat(invoice2.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PARTIAL);
        assertThat(invoice2.getBalanceDue()).isEqualByComparingTo("30000");
        assertThat(payment.getAllocatedAmount()).isEqualByComparingTo("130000");
        assertThat(payment.getUnallocatedAmount()).isEqualByComparingTo("20000");

        // Home валютада курс фарқи ЙЎҚ - allocation JE ёзилмаган
        for (InvoicePaymentAllocation allocation : paymentService.allocationsOf(payment.getId())) {
            assertThat(entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                    InvoicePaymentService.ALLOCATION_SOURCE_MODULE, allocation.getId())).isEmpty();
        }
    }

    @Test
    void create_depositToUndepositedFunds_allowed() {
        Invoice invoice = homeInvoice(new BigDecimal("50000"));
        UUID undeposited = accountRepository.findByName("Тушумлар транзити")
                .orElseThrow().getId();

        InvoicePayment payment = paymentService.create(new PaymentData(customer.getId(),
                DATE, undeposited, null, null, new BigDecimal("50000"), null,
                List.of(new AllocationData(invoice.getId(), new BigDecimal("50000")))));

        JournalEntry entry = glEntry(InvoicePaymentService.SOURCE_MODULE, payment.getId());
        assertThat(baseOf(entry, "UNDEPOSITED_FUNDS", true)).isEqualByComparingTo("50000");
        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
    }

    /**
     * Arbitr-070 (Nargiza-001): BANK счёт валютаси тўлов валютасига тенг
     * бўлиши шарт (BR-RCPT-002) - акс ҳолда UZS счётга USD Money сатр
     * ёзилиб bankBalances() валюта кесими бузиларди. Икки йўналиш ҳам
     * рад, мос валюта ўтади ва GL балансланган.
     */
    @Test
    void create_bankCurrencyMismatch_rejectedBothDirections_matchPasses() {
        UUID usdBank = accountRepository.findByName("Валюта ҳисобварағи (USD)")
                .orElseThrow().getId();

        // USD тўлов + UZS (home) банк - РАД
        assertThatThrownBy(() -> paymentService.create(new PaymentData(customer.getId(),
                DATE, bankAccountId, "USD", new BigDecimal("12600"),
                new BigDecimal("100"), null, List.of())))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCPT-002"));

        // Home (UZS) тўлов + USD банк - тескари йўналиш ҳам РАД
        assertThatThrownBy(() -> paymentService.create(new PaymentData(customer.getId(),
                DATE, usdBank, null, null, new BigDecimal("1000"), null, List.of())))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCPT-002"));

        // Мос валюта (USD тўлов + USD банк) - ЎТАДИ, GL балансланган
        InvoicePayment payment = paymentService.create(new PaymentData(customer.getId(),
                DATE, usdBank, "USD", new BigDecimal("12600"),
                new BigDecimal("100"), null, List.of()));
        assertBalanced(glEntry(InvoicePaymentService.SOURCE_MODULE, payment.getId()));
    }

    /**
     * Arbitr-070 истисноси: UNDEPOSITED_FUNDS клиринг чўнтагига чет валюта
     * тўлов ТУШАДИ (QBO'да ҳам foreign payment undeposited'га боради) -
     * валюта гарови унга қўлланмайди, акс ҳолда чет валюта тўловни
     * undeposited орқали қабул қилиш оқими бутунлай синарди.
     */
    @Test
    void create_foreignCurrencyToUndeposited_allowed() {
        UUID undeposited = accountRepository.findByName("Тушумлар транзити")
                .orElseThrow().getId();

        InvoicePayment payment = paymentService.create(new PaymentData(customer.getId(),
                DATE, undeposited, "USD", new BigDecimal("12600"),
                new BigDecimal("100"), null, List.of()));

        JournalEntry entry = glEntry(InvoicePaymentService.SOURCE_MODULE, payment.getId());
        assertBalanced(entry);
        assertThat(baseOf(entry, "UNDEPOSITED_FUNDS", true)).isEqualByComparingTo("1260000");
    }

    @Test
    void fx_gainAndLoss_directionOppositeToAp() {
        // Иккала invoice 100 USD, курси 12 600 (AR base 1 260 000 дан)
        Invoice invoice1 = postedInvoice(usdCustomer.getId(), "USD",
                new BigDecimal("12600"), new BigDecimal("100"));
        Invoice invoice2 = postedInvoice(usdCustomer.getId(), "USD",
                new BigDecimal("12600"), new BigDecimal("100"));

        // Тўлов курси 12 700 - base'да КЎПРОҚ тушум олдик: ФОЙДА 10 000
        // (AP томонда бу зарар бўлар эди - йўналиш тескари)
        InvoicePayment gain = paymentService.create(new PaymentData(usdCustomer.getId(), DATE,
                usdBankAccountId, "USD", new BigDecimal("12700"), new BigDecimal("100"),
                null, List.of(new AllocationData(invoice1.getId(), new BigDecimal("100")))));
        UUID gainAlloc = paymentService.allocationsOf(gain.getId()).get(0).getId();
        JournalEntry gainJe = glEntry(InvoicePaymentService.ALLOCATION_SOURCE_MODULE, gainAlloc);
        assertThat(baseOf(gainJe, "ACCOUNTS_RECEIVABLE", true)).isEqualByComparingTo("10000");
        assertThat(baseOf(gainJe, "EXCHANGE_GAIN_OR_LOSS", false)).isEqualByComparingTo("10000");
        assertBalanced(gainJe);

        // Тўлов курси 12 500 - base'да камроқ: ЗАРАР 10 000
        InvoicePayment loss = paymentService.create(new PaymentData(usdCustomer.getId(), DATE,
                usdBankAccountId, "USD", new BigDecimal("12500"), new BigDecimal("100"),
                null, List.of(new AllocationData(invoice2.getId(), new BigDecimal("100")))));
        UUID lossAlloc = paymentService.allocationsOf(loss.getId()).get(0).getId();
        JournalEntry lossJe = glEntry(InvoicePaymentService.ALLOCATION_SOURCE_MODULE, lossAlloc);
        assertThat(baseOf(lossJe, "EXCHANGE_GAIN_OR_LOSS", true)).isEqualByComparingTo("10000");
        assertThat(baseOf(lossJe, "ACCOUNTS_RECEIVABLE", false)).isEqualByComparingTo("10000");
        assertBalanced(lossJe);

        assertThat(invoice1.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
        assertThat(invoice2.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
    }

    @Test
    void fx_zeroDifference_noJe_advanceLaterAllocationComputesFx() {
        Invoice invoice = postedInvoice(usdCustomer.getId(), "USD",
                new BigDecimal("12600"), new BigDecimal("100"));

        // Аванс тушум (тақсимотсиз), курси invoice билан бир хил
        InvoicePayment payment = paymentService.create(new PaymentData(usdCustomer.getId(),
                DATE, usdBankAccountId, "USD", new BigDecimal("12600"),
                new BigDecimal("250"), null, null));
        assertThat(payment.getUnallocatedAmount()).isEqualByComparingTo("250");

        // Нол фарқ - JE ёзилмайди
        paymentService.allocate(payment.getId(),
                List.of(new AllocationData(invoice.getId(), new BigDecimal("100"))));
        UUID zeroAlloc = paymentService.allocationsOf(payment.getId()).get(0).getId();
        assertThat(entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                InvoicePaymentService.ALLOCATION_SOURCE_MODULE, zeroAlloc)).isEmpty();

        // Кейинги аванс ишлатиш пастроқ курсли invoice'га - фойда 20 000
        // (тўлов 12 600 > invoice 12 400)
        Invoice cheaper = postedInvoice(usdCustomer.getId(), "USD",
                new BigDecimal("12400"), new BigDecimal("100"));
        paymentService.allocate(payment.getId(),
                List.of(new AllocationData(cheaper.getId(), new BigDecimal("100"))));
        UUID fxAlloc = paymentService.allocationsOf(payment.getId()).get(1).getId();
        JournalEntry fxJe = glEntry(InvoicePaymentService.ALLOCATION_SOURCE_MODULE, fxAlloc);
        assertThat(baseOf(fxJe, "ACCOUNTS_RECEIVABLE", true)).isEqualByComparingTo("20000");
        assertThat(baseOf(fxJe, "EXCHANGE_GAIN_OR_LOSS", false)).isEqualByComparingTo("20000");
        assertThat(payment.getUnallocatedAmount()).isEqualByComparingTo("50");
    }

    @Test
    void paidTotal_windowBoundary_andReversedExcluded() {
        // Arbitr-036 dashboard: 30 кун ойнаси чегараси - ичидагиси
        // киради, ташқаридагиси йўқ; REVERSED тушум жамга кирмайди
        paymentService.create(new PaymentData(customer.getId(), DATE, bankAccountId,
                null, null, new BigDecimal("60000"), null, List.of()));
        InvoicePayment older = paymentService.create(new PaymentData(customer.getId(),
                DATE.minusDays(31), bankAccountId, null, null,
                new BigDecimal("30000"), null, List.of()));

        // 30 кунлик ойна: фақат янгиси (эскиси 31 кун олдин - ташқарида)
        assertThat(invoiceService.paidTotal(DATE.minusDays(30), DATE))
                .isEqualByComparingTo("60000");
        // Кенг ойна иккисини ҳам олади
        assertThat(invoiceService.paidTotal(DATE.minusDays(40), DATE))
                .isEqualByComparingTo("90000");

        // Reverse қилинган тушум - қайтарилган пул, жамдан чиқади
        paymentService.reverse(older.getId(), DATE, "тест сторно");
        assertThat(invoiceService.paidTotal(DATE.minusDays(40), DATE))
                .isEqualByComparingTo("60000");
    }

    @Test
    void reverse_releasesAllocations_restoresInvoice_stornosFxJe() {
        Invoice invoice = postedInvoice(usdCustomer.getId(), "USD",
                new BigDecimal("12600"), new BigDecimal("100"));
        InvoicePayment payment = paymentService.create(new PaymentData(usdCustomer.getId(),
                DATE, usdBankAccountId, "USD", new BigDecimal("12700"), new BigDecimal("100"),
                null, List.of(new AllocationData(invoice.getId(), new BigDecimal("100")))));
        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
        UUID allocationId = paymentService.allocationsOf(payment.getId()).get(0).getId();

        paymentService.reverse(payment.getId(), DATE, "хато тушум");

        // Тушум REVERSED, invoice ҳолати тўлиқ қайтди
        assertThat(payment.getStatus()).isEqualTo(InvoicePayment.Status.REVERSED);
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("0");
        assertThat(invoice.getBalanceDue()).isEqualByComparingTo("100");
        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.UNPAID);
        // Beruniy-008: тўловнинг ЎЗ денормализацияси ҳам тикланади
        assertThat(payment.getAllocatedAmount()).isEqualByComparingTo("0");
        assertThat(payment.getUnallocatedAmount()).isEqualByComparingTo("100");

        // Тушум JE'си ҳам, курс фарқи JE'си ҳам сторно бўлди
        assertThat(glEntry(InvoicePaymentService.SOURCE_MODULE, payment.getId()).getStatus())
                .isEqualTo(EntryStatus.REVERSED);
        assertThat(glEntry(InvoicePaymentService.ALLOCATION_SOURCE_MODULE, allocationId)
                .getStatus()).isEqualTo(EntryStatus.REVERSED);

        // Иккинчи reverse (BR-RCPT-007) ва REVERSED'дан allocation (BR-RCPT-013) тақиқ
        assertThatThrownBy(() -> paymentService.reverse(payment.getId(), DATE, null))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCPT-007"));
        assertThatThrownBy(() -> paymentService.allocate(payment.getId(),
                List.of(new AllocationData(invoice.getId(), new BigDecimal("10")))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCPT-013"));

        // Бўшаган invoice'га янги тушум бемалол киради
        InvoicePayment again = paymentService.create(new PaymentData(usdCustomer.getId(),
                DATE, usdBankAccountId, "USD", new BigDecimal("12600"), new BigDecimal("100"),
                null, List.of(new AllocationData(invoice.getId(), new BigDecimal("100")))));
        assertThat(again.getStatus()).isEqualTo(InvoicePayment.Status.POSTED);
        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
    }

    @Test
    void headerValidation_guards() {
        // BR-RCPT-010: customer йўқ / нотўғри тип
        assertThatThrownBy(() -> paymentService.create(new PaymentData(null, DATE,
                bankAccountId, null, null, BigDecimal.ONE, null, null)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCPT-010"));
        Contact vendor = contactService.create(ContactType.VENDOR, new ContactData(
                "Етказувчи (мижоз эмас)", null, null, null, null, null,
                null, null, null, null, null));
        assertThatThrownBy(() -> paymentService.create(new PaymentData(vendor.getId(),
                DATE, bankAccountId, null, null, BigDecimal.ONE, null, null)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCPT-010"));

        // BR-RCPT-008: сана йўқ; BR-RCPT-001: сумма мусбат эмас
        assertThatThrownBy(() -> paymentService.create(new PaymentData(customer.getId(),
                null, bankAccountId, null, null, BigDecimal.ONE, null, null)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCPT-008"));
        assertThatThrownBy(() -> paymentService.create(new PaymentData(customer.getId(),
                DATE, bankAccountId, null, null, BigDecimal.ZERO, null, null)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCPT-001"));

        // BR-RCPT-002: қабул счёти йўқ / EXPENSE туридан
        assertThatThrownBy(() -> paymentService.create(new PaymentData(customer.getId(),
                DATE, null, null, null, BigDecimal.ONE, null, null)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCPT-002"));
        UUID rent = accountRepository.findByName("Ижара").orElseThrow().getId();
        assertThatThrownBy(() -> paymentService.create(new PaymentData(customer.getId(),
                DATE, rent, null, null, BigDecimal.ONE, null, null)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCPT-002"));

        // BR-RCPT-012: чет валютада курссиз
        assertThatThrownBy(() -> paymentService.create(new PaymentData(customer.getId(),
                DATE, bankAccountId, "USD", null, BigDecimal.ONE, null, null)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCPT-012"));
    }

    @Test
    void allocationValidation_guards() {
        Invoice invoice = homeInvoice(new BigDecimal("100"));

        // BR-RCPT-003: DRAFT invoice'га allocation
        Invoice draft = invoiceService.createDraft(new InvoiceData(customer.getId(),
                DATE, null, null, null, null, List.of(new LineData(service.getId(),
                        null, BigDecimal.ONE, new BigDecimal("100"), null, null))));
        assertThatThrownBy(() -> paymentService.create(homePayment(new BigDecimal("100"),
                List.of(new AllocationData(draft.getId(), new BigDecimal("100"))))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCPT-003"));

        // BR-RCPT-009: бошқа мижознинг invoice'ига
        Contact other = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Бошқа мижоз", null, null, null, null, null,
                null, null, null, null, null));
        Invoice otherInvoice = postedInvoice(other.getId(), null, null, new BigDecimal("100"));
        assertThatThrownBy(() -> paymentService.create(homePayment(new BigDecimal("100"),
                List.of(new AllocationData(otherInvoice.getId(), new BigDecimal("100"))))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCPT-009"));

        // BR-RCPT-006: home тушум - USD invoice (тўлов ҳам USD мижоз номидан -
        // контакт мос, валюта номос; Arbitr-087: USD invoice фақат USD контактда)
        Invoice usdInvoice = postedInvoice(usdCustomer.getId(), "USD",
                new BigDecimal("12600"), new BigDecimal("100"));
        assertThatThrownBy(() -> paymentService.create(new PaymentData(usdCustomer.getId(),
                DATE, bankAccountId, null, null, new BigDecimal("100"), null,
                List.of(new AllocationData(usdInvoice.getId(), new BigDecimal("100"))))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCPT-006"));

        // BR-RCPT-004: invoice қолдиғидан ошиқ
        assertThatThrownBy(() -> paymentService.create(homePayment(new BigDecimal("500"),
                List.of(new AllocationData(invoice.getId(), new BigDecimal("200"))))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCPT-004"));

        // BR-RCPT-005: йиғинди тўлов суммасидан ошиқ. ДИҚҚАТ: биринчи
        // allocation хатогача сақланиб улгуради (тест tx ичида rollback
        // йўқ) - invA/invB қуйида қайта ишлатилмайди
        Invoice invA = homeInvoice(new BigDecimal("100"));
        Invoice invB = homeInvoice(new BigDecimal("100"));
        assertThatThrownBy(() -> paymentService.create(homePayment(new BigDecimal("150"),
                List.of(new AllocationData(invA.getId(), new BigDecimal("100")),
                        new AllocationData(invB.getId(), new BigDecimal("100"))))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCPT-005"));

        // BR-RCPT-011: бир тушумдан бир invoice'га иккинчи allocation
        Invoice invC = homeInvoice(new BigDecimal("100"));
        InvoicePayment payment = paymentService.create(homePayment(new BigDecimal("100"),
                List.of(new AllocationData(invC.getId(), new BigDecimal("40")))));
        assertThatThrownBy(() -> paymentService.allocate(payment.getId(),
                List.of(new AllocationData(invC.getId(), new BigDecimal("40")))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCPT-011"));

        // BR-RCPT-001: allocation суммаси мусбат эмас
        assertThatThrownBy(() -> paymentService.allocate(payment.getId(),
                List.of(new AllocationData(invC.getId(), BigDecimal.ZERO))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RCPT-001"));
    }
}
