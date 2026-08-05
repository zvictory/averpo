package com.averpo.erp.purchase;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.inventory.domain.Warehouse;
import com.averpo.erp.inventory.service.WarehouseService;
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
import com.averpo.erp.purchase.domain.Bill;
import com.averpo.erp.purchase.domain.BillLineType;
import com.averpo.erp.purchase.domain.BillPayment;
import com.averpo.erp.purchase.domain.BillPaymentAllocation;
import com.averpo.erp.purchase.domain.PaymentStatus;
import com.averpo.erp.purchase.service.BillPaymentService;
import com.averpo.erp.purchase.service.BillPaymentService.AllocationData;
import com.averpo.erp.purchase.service.BillPaymentService.PaymentData;
import com.averpo.erp.purchase.service.BillService;
import com.averpo.erp.purchase.service.BillService.BillData;
import com.averpo.erp.purchase.service.BillService.LineData;
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
 * BillPayment тестлари: docs/modules/purchases.md → «Тестлар»
 * (3-туртки). Тўлов GL'и, allocation денормализацияси, realized курс
 * фарқи (ҳар allocation учун алоҳида JE) ва reverse шу ерда
 * текширилади (ТЕМИР ҚОИДА №7: debit == credit).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BillPaymentServiceTest {

    /** Барча тест ҳужжатлар санаси. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 6);

    @Autowired BillPaymentService paymentService;
    @Autowired BillService billService;
    @Autowired ContactService contactService;
    @Autowired ItemService itemService;
    @Autowired WarehouseService warehouseService;
    @Autowired AccountService accountService;
    @Autowired AccountRepository accountRepository;
    @Autowired JournalEntryRepository entryRepository;

    /** Тест vendor'и (home валюта). */
    private Contact vendor;

    /**
     * USD валютали vendor (Arbitr-087): bill валютаси контактдан келади -
     * USD bill ва унинг тўловлари шу vendor'га ёзилади (BR-PAY-009:
     * allocation фақат ўша vendor'нинг bill'ига).
     */
    private Contact usdVendor;

    /** Тест товари (INVENTORY). */
    private Item item;

    /** Асосий омбор (seed). */
    private Warehouse warehouse;

    /** Тўлов банк счёти (CHECKING, home валюта). */
    private UUID bankAccountId;

    /**
     * USD банк счёти (default chart) - Arbitr-070 дан бери чет валюта
     * тўлов фақат ўз валютасидаги счётдан чиқади (BR-PAY-002).
     */
    private UUID usdBankAccountId;

    /** Chart + vendor + item + омбор тайёрланади (rollback тозалайди). */
    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        vendor = contactService.create(ContactType.VENDOR, new ContactData(
                "Тўлов тест етказувчиси", null, null, null, null, null,
                null, null, null, null, null));
        usdVendor = contactService.create(ContactType.VENDOR, new ContactData(
                "Тўлов USD етказувчиси", null, null, null, null, null,
                "USD", null, null, null, null));
        ItemService.DefaultAccounts defaults = itemService.defaultsFor(ItemType.INVENTORY);
        item = itemService.create(ItemType.INVENTORY, new ItemData(
                "Тўлов тест товари", null, null, null, null, null,
                defaults.income(), null, null, defaults.expense(),
                defaults.inventoryAsset(), null));
        warehouse = warehouseService.all().stream()
                .filter(w -> "Асосий омбор".equals(w.getName()))
                .findFirst().orElseThrow();
        bankAccountId = accountRepository.findByName("Банк ҳисобварағи").orElseThrow().getId();
        usdBankAccountId = accountRepository.findByName("Валюта ҳисобварағи (USD)")
                .orElseThrow().getId();
    }

    /** POSTED bill ясайди: битта ITEM сатр (qty 1 × сумма). */
    private Bill postedBill(UUID vendorId, String currency, BigDecimal rate,
                            BigDecimal amount) {
        Bill draft = billService.createDraft(new BillData(vendorId, null, DATE, null,
                currency, rate, null, List.of(new LineData(BillLineType.ITEM,
                        item.getId(), warehouse.getId(), BigDecimal.ONE, amount,
                        null, null, null))));
        return billService.post(draft.getId());
    }

    /** Home валютадаги POSTED bill. */
    private Bill homeBill(BigDecimal amount) {
        return postedBill(vendor.getId(), null, null, amount);
    }

    /** Home валютадаги тўлов маълумоти. */
    private PaymentData homePayment(BigDecimal total, List<AllocationData> allocations) {
        return new PaymentData(vendor.getId(), DATE, bankAccountId, null, null,
                total, null, allocations);
    }

    /**
     * Arbitr-070 (Nargiza-001, InvoicePayment кўзгуси): банк счёти
     * валютаси тўлов валютасига тенг бўлиши шарт (BR-PAY-002) - акс
     * ҳолда UZS счётга USD Money сатр ёзилиб bankBalances() валюта
     * кесими бузиларди. Икки йўналиш ҳам рад, мос валюта ўтади ва GL
     * балансланган.
     */
    @Test
    void create_bankCurrencyMismatch_rejectedBothDirections_matchPasses() {
        UUID usdBank = accountRepository.findByName("Валюта ҳисобварағи (USD)")
                .orElseThrow().getId();

        // USD тўлов + UZS (home) банк - РАД
        assertThatThrownBy(() -> paymentService.create(new PaymentData(vendor.getId(),
                DATE, bankAccountId, "USD", new BigDecimal("12600"),
                new BigDecimal("100"), null, List.of())))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PAY-002"));

        // Home (UZS) тўлов + USD банк - тескари йўналиш ҳам РАД
        assertThatThrownBy(() -> paymentService.create(new PaymentData(vendor.getId(),
                DATE, usdBank, null, null, new BigDecimal("1000"), null, List.of())))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PAY-002"));

        // Мос валюта (USD тўлов + USD банк) - ЎТАДИ, GL балансланган
        BillPayment payment = paymentService.create(new PaymentData(vendor.getId(),
                DATE, usdBank, "USD", new BigDecimal("12600"),
                new BigDecimal("100"), null, List.of()));
        assertBalanced(glEntry(BillPaymentService.SOURCE_MODULE, payment.getId()));
    }

    /** Манба бўйича фаол GL ёзувини топади. */
    private JournalEntry glEntry(String module, UUID docId) {
        return entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                module, docId).orElseThrow();
    }

    /** Дебет base йиғиндиси - detail type филтри билан. */
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
        // Beruniy-perf1 2-босқич: size+1 тўлов (аллокациясиз аванс) -
        // 2-саҳифада биттагина қолади; саналар ҳар хил - тартиб детерминистик
        BillPayment oldest = null;
        BillPayment newest = null;
        for (int i = BillPaymentService.LIST_PAGE_SIZE; i >= 0; i--) {
            BillPayment payment = paymentService.create(new PaymentData(vendor.getId(),
                    DATE.minusDays(i), bankAccountId, null, null,
                    new BigDecimal("1000"), null, List.of()));
            if (oldest == null) {
                oldest = payment; // биринчи яратилгани энг эски санали
            }
            newest = payment;
        }

        var page0 = paymentService.list(
                new BillPaymentService.ListFilter(null, null, null, null, null), 0);
        assertThat(page0.getContent()).hasSize(BillPaymentService.LIST_PAGE_SIZE);
        assertThat(page0.getTotalElements()).isEqualTo(BillPaymentService.LIST_PAGE_SIZE + 1);
        assertThat(page0.getTotalPages()).isEqualTo(2);
        // Барқарор тартиб: энг янги санали биринчи (аввалги ORDER BY)
        assertThat(page0.getContent().get(0).getId()).isEqualTo(newest.getId());

        var page1 = paymentService.list(
                new BillPaymentService.ListFilter(null, null, null, null, null), 1);
        assertThat(page1.getContent()).hasSize(1);
        assertThat(page1.getContent().get(0).getId()).isEqualTo(oldest.getId());
        assertThat(page1.hasNext()).isFalse();
    }

    @Test
    void create_homePayment_glAndDenormalization_advanceStays() {
        Bill bill1 = homeBill(new BigDecimal("100000"));
        Bill bill2 = homeBill(new BigDecimal("60000"));

        // 150 000 тўлов: 100 000 bill1'га, 50 000 аванс
        BillPayment payment = paymentService.create(homePayment(new BigDecimal("150000"),
                List.of(new AllocationData(bill1.getId(), new BigDecimal("100000")))));

        assertThat(payment.getPaymentNumber()).startsWith("PAY-2026-");
        assertThat(payment.getStatus()).isEqualTo(BillPayment.Status.POSTED);
        assertThat(payment.getAllocatedAmount()).isEqualByComparingTo("100000");
        assertThat(payment.getUnallocatedAmount()).isEqualByComparingTo("50000");

        // GL: AP дебети ТЎЛИҚ суммага (аванс ҳам ичида), банк кредити
        JournalEntry entry = glEntry(BillPaymentService.SOURCE_MODULE, payment.getId());
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.POSTED);
        assertThat(baseOf(entry, "ACCOUNTS_PAYABLE", true)).isEqualByComparingTo("150000");
        assertThat(baseOf(entry, "CHECKING", false)).isEqualByComparingTo("150000");
        assertThat(entry.getLines()).allSatisfy(line ->
                assertThat(line.getContactId()).isEqualTo(vendor.getId()));

        // Bill1 денормализацияси: UNPAID → PAID
        assertThat(bill1.getPaidAmount()).isEqualByComparingTo("100000");
        assertThat(bill1.getBalanceDue()).isEqualByComparingTo("0");
        assertThat(bill1.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);

        // Аванс ишлатиш: 30 000 кейин bill2'га → PARTIAL
        paymentService.allocate(payment.getId(),
                List.of(new AllocationData(bill2.getId(), new BigDecimal("30000"))));
        assertThat(bill2.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIAL);
        assertThat(bill2.getBalanceDue()).isEqualByComparingTo("30000");
        assertThat(payment.getAllocatedAmount()).isEqualByComparingTo("130000");
        assertThat(payment.getUnallocatedAmount()).isEqualByComparingTo("20000");

        // Bill2 қолдиғи ЯНГИ тўлов билан ёпилади (бир bill - бир нечта тўлов)
        paymentService.create(homePayment(new BigDecimal("30000"),
                List.of(new AllocationData(bill2.getId(), new BigDecimal("30000")))));
        assertThat(bill2.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(bill2.getBalanceDue()).isEqualByComparingTo("0");

        // Home валютада курс фарқи ЙЎҚ - allocation JE ёзилмаган
        for (BillPaymentAllocation allocation : paymentService.allocationsOf(payment.getId())) {
            assertThat(entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                    BillPaymentService.ALLOCATION_SOURCE_MODULE, allocation.getId())).isEmpty();
        }
    }

    @Test
    void fx_gainAndLoss_separateJePerAllocation() {
        // Иккала bill 100 USD, курси 12 600 (AP base 1 260 000 дан)
        Bill bill1 = postedBill(usdVendor.getId(), "USD", new BigDecimal("12600"),
                new BigDecimal("100"));
        Bill bill2 = postedBill(usdVendor.getId(), "USD", new BigDecimal("12600"),
                new BigDecimal("100"));

        // Тўлов курси 12 700 - base'да кўпроқ тўладик: ЗАРАР 10 000
        BillPayment loss = paymentService.create(new PaymentData(usdVendor.getId(), DATE,
                usdBankAccountId, "USD", new BigDecimal("12700"), new BigDecimal("100"),
                null, List.of(new AllocationData(bill1.getId(), new BigDecimal("100")))));
        UUID lossAlloc = paymentService.allocationsOf(loss.getId()).get(0).getId();
        JournalEntry lossJe = glEntry(BillPaymentService.ALLOCATION_SOURCE_MODULE, lossAlloc);
        assertThat(baseOf(lossJe, "EXCHANGE_GAIN_OR_LOSS", true)).isEqualByComparingTo("10000");
        assertThat(baseOf(lossJe, "ACCOUNTS_PAYABLE", false)).isEqualByComparingTo("10000");
        assertBalanced(lossJe);

        // Тўлов курси 12 500 - base'да камроқ тўладик: ФОЙДА 10 000
        BillPayment gain = paymentService.create(new PaymentData(usdVendor.getId(), DATE,
                usdBankAccountId, "USD", new BigDecimal("12500"), new BigDecimal("100"),
                null, List.of(new AllocationData(bill2.getId(), new BigDecimal("100")))));
        UUID gainAlloc = paymentService.allocationsOf(gain.getId()).get(0).getId();
        JournalEntry gainJe = glEntry(BillPaymentService.ALLOCATION_SOURCE_MODULE, gainAlloc);
        assertThat(baseOf(gainJe, "ACCOUNTS_PAYABLE", true)).isEqualByComparingTo("10000");
        assertThat(baseOf(gainJe, "EXCHANGE_GAIN_OR_LOSS", false)).isEqualByComparingTo("10000");
        assertBalanced(gainJe);

        assertThat(bill1.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(bill2.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void fx_zeroDifference_noJe_advanceLaterAllocationComputesFx() {
        Bill bill = postedBill(usdVendor.getId(), "USD", new BigDecimal("12600"),
                new BigDecimal("100"));

        // Аванс тўлов (тақсимотсиз), курси bill билан бир хил
        BillPayment payment = paymentService.create(new PaymentData(usdVendor.getId(), DATE,
                usdBankAccountId, "USD", new BigDecimal("12600"), new BigDecimal("250"),
                null, null));
        assertThat(payment.getUnallocatedAmount()).isEqualByComparingTo("250");

        // Нол фарқ - JE ёзилмайди
        paymentService.allocate(payment.getId(),
                List.of(new AllocationData(bill.getId(), new BigDecimal("100"))));
        UUID zeroAlloc = paymentService.allocationsOf(payment.getId()).get(0).getId();
        assertThat(entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                BillPaymentService.ALLOCATION_SOURCE_MODULE, zeroAlloc)).isEmpty();
        assertThat(bill.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);

        // Кейинги аванс ишлатиш бошқа курсли bill'га - фойда 20 000
        Bill bill2 = postedBill(usdVendor.getId(), "USD", new BigDecimal("12800"),
                new BigDecimal("100"));
        paymentService.allocate(payment.getId(),
                List.of(new AllocationData(bill2.getId(), new BigDecimal("100"))));
        UUID fxAlloc = paymentService.allocationsOf(payment.getId()).get(1).getId();
        JournalEntry fxJe = glEntry(BillPaymentService.ALLOCATION_SOURCE_MODULE, fxAlloc);
        assertThat(baseOf(fxJe, "ACCOUNTS_PAYABLE", true)).isEqualByComparingTo("20000");
        assertThat(baseOf(fxJe, "EXCHANGE_GAIN_OR_LOSS", false)).isEqualByComparingTo("20000");
        assertThat(payment.getAllocatedAmount()).isEqualByComparingTo("200");
        assertThat(payment.getUnallocatedAmount()).isEqualByComparingTo("50");
    }

    @Test
    void reverse_releasesAllocations_restoresBill_stornosFxJe() {
        Bill bill = postedBill(usdVendor.getId(), "USD", new BigDecimal("12600"),
                new BigDecimal("100"));
        BillPayment payment = paymentService.create(new PaymentData(usdVendor.getId(), DATE,
                usdBankAccountId, "USD", new BigDecimal("12700"), new BigDecimal("100"),
                null, List.of(new AllocationData(bill.getId(), new BigDecimal("100")))));
        assertThat(bill.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        UUID allocationId = paymentService.allocationsOf(payment.getId()).get(0).getId();

        paymentService.reverse(payment.getId(), DATE, "хато тўлов");

        // Тўлов REVERSED, bill ҳолати тўлиқ қайтди
        assertThat(payment.getStatus()).isEqualTo(BillPayment.Status.REVERSED);
        assertThat(bill.getPaidAmount()).isEqualByComparingTo("0");
        assertThat(bill.getBalanceDue()).isEqualByComparingTo("100");
        assertThat(bill.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);

        // Тўловнинг ўз денормализацияси ҳам тикланди - REVERSED тўлов
        // аванс рўйхатида «тақсимланмаган пул бор» деб кўринмайди
        assertThat(payment.getAllocatedAmount()).isEqualByComparingTo("0");
        assertThat(payment.getUnallocatedAmount())
                .isEqualByComparingTo(payment.getTotalAmount());

        // Тўлов JE'си ҳам, курс фарқи JE'си ҳам сторно бўлди
        assertThat(glEntry(BillPaymentService.SOURCE_MODULE, payment.getId()).getStatus())
                .isEqualTo(EntryStatus.REVERSED);
        assertThat(glEntry(BillPaymentService.ALLOCATION_SOURCE_MODULE, allocationId).getStatus())
                .isEqualTo(EntryStatus.REVERSED);

        // Иккинчи reverse тақиқ (BR-PAY-007)
        assertThatThrownBy(() -> paymentService.reverse(payment.getId(), DATE, null))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PAY-007"));

        // REVERSED тўловдан allocation тақиқ (BR-PAY-013)
        assertThatThrownBy(() -> paymentService.allocate(payment.getId(),
                List.of(new AllocationData(bill.getId(), new BigDecimal("10")))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PAY-013"));

        // Бўшаган bill'га янги тўлов бемалол киради
        BillPayment again = paymentService.create(new PaymentData(usdVendor.getId(), DATE,
                usdBankAccountId, "USD", new BigDecimal("12600"), new BigDecimal("100"),
                null, List.of(new AllocationData(bill.getId(), new BigDecimal("100")))));
        assertThat(again.getStatus()).isEqualTo(BillPayment.Status.POSTED);
        assertThat(bill.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void headerValidation_guards() {
        // BR-PAY-010: vendor йўқ / нотўғри тип
        assertThatThrownBy(() -> paymentService.create(new PaymentData(null, DATE,
                bankAccountId, null, null, BigDecimal.ONE, null, null)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PAY-010"));
        Contact customer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Мижоз (vendor эмас)", null, null, null, null, null,
                null, null, null, null, null));
        assertThatThrownBy(() -> paymentService.create(new PaymentData(customer.getId(),
                DATE, bankAccountId, null, null, BigDecimal.ONE, null, null)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PAY-010"));

        // BR-PAY-008: сана йўқ
        assertThatThrownBy(() -> paymentService.create(new PaymentData(vendor.getId(),
                null, bankAccountId, null, null, BigDecimal.ONE, null, null)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PAY-008"));

        // BR-PAY-001: сумма мусбат эмас
        assertThatThrownBy(() -> paymentService.create(new PaymentData(vendor.getId(),
                DATE, bankAccountId, null, null, BigDecimal.ZERO, null, null)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PAY-001"));

        // BR-PAY-002: банк счёти йўқ / BANK туридан эмас
        assertThatThrownBy(() -> paymentService.create(new PaymentData(vendor.getId(),
                DATE, null, null, null, BigDecimal.ONE, null, null)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PAY-002"));
        UUID rent = accountRepository.findByName("Ижара").orElseThrow().getId();
        assertThatThrownBy(() -> paymentService.create(new PaymentData(vendor.getId(),
                DATE, rent, null, null, BigDecimal.ONE, null, null)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PAY-002"));

        // BR-PAY-012: чет валютада курссиз
        assertThatThrownBy(() -> paymentService.create(new PaymentData(vendor.getId(),
                DATE, bankAccountId, "USD", null, BigDecimal.ONE, null, null)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PAY-012"));
    }

    @Test
    void allocationValidation_guards() {
        Bill bill = homeBill(new BigDecimal("100"));

        // BR-PAY-003: DRAFT bill'га allocation
        Bill draft = billService.createDraft(new BillData(vendor.getId(), null, DATE,
                null, null, null, null, List.of(new LineData(BillLineType.ITEM,
                        item.getId(), warehouse.getId(), BigDecimal.ONE,
                        new BigDecimal("100"), null, null, null))));
        assertThatThrownBy(() -> paymentService.create(homePayment(new BigDecimal("100"),
                List.of(new AllocationData(draft.getId(), new BigDecimal("100"))))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PAY-003"));

        // BR-PAY-009: бошқа vendor'нинг bill'ига
        Contact other = contactService.create(ContactType.VENDOR, new ContactData(
                "Бошқа етказувчи", null, null, null, null, null,
                null, null, null, null, null));
        Bill otherBill = postedBill(other.getId(), null, null, new BigDecimal("100"));
        assertThatThrownBy(() -> paymentService.create(homePayment(new BigDecimal("100"),
                List.of(new AllocationData(otherBill.getId(), new BigDecimal("100"))))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PAY-009"));

        // BR-PAY-006: home тўлов - USD bill (тўлов ҳам USD vendor номидан -
        // контакт мос, валюта номос; Arbitr-087: USD bill фақат USD контактда)
        Bill usdBill = postedBill(usdVendor.getId(), "USD", new BigDecimal("12600"),
                new BigDecimal("100"));
        assertThatThrownBy(() -> paymentService.create(new PaymentData(usdVendor.getId(),
                DATE, bankAccountId, null, null, new BigDecimal("100"), null,
                List.of(new AllocationData(usdBill.getId(), new BigDecimal("100"))))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PAY-006"));

        // BR-PAY-004: bill қолдиғидан ошиқ
        assertThatThrownBy(() -> paymentService.create(homePayment(new BigDecimal("500"),
                List.of(new AllocationData(bill.getId(), new BigDecimal("200"))))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PAY-004"));

        // BR-PAY-005: йиғинди тўлов суммасидан ошиқ. ДИҚҚАТ: биринчи
        // allocation (billA'га) хатогача сақланиб улгуради - тест
        // транзакцияси ичида rollback йўқ, шунинг учун billA/billB
        // қуйида қайта ишлатилмайди (prod'да бутун tx қайтади)
        Bill billA = homeBill(new BigDecimal("100"));
        Bill billB = homeBill(new BigDecimal("100"));
        assertThatThrownBy(() -> paymentService.create(homePayment(new BigDecimal("150"),
                List.of(new AllocationData(billA.getId(), new BigDecimal("100")),
                        new AllocationData(billB.getId(), new BigDecimal("100"))))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PAY-005"));

        // BR-PAY-011: бир тўловдан бир bill'га иккинчи allocation
        Bill billC = homeBill(new BigDecimal("100"));
        BillPayment payment = paymentService.create(homePayment(new BigDecimal("100"),
                List.of(new AllocationData(billC.getId(), new BigDecimal("40")))));
        assertThatThrownBy(() -> paymentService.allocate(payment.getId(),
                List.of(new AllocationData(billC.getId(), new BigDecimal("40")))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PAY-011"));

        // BR-PAY-001: allocation суммаси мусбат эмас
        assertThatThrownBy(() -> paymentService.allocate(payment.getId(),
                List.of(new AllocationData(billC.getId(), BigDecimal.ZERO))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PAY-001"));
    }
}
