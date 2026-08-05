package com.averpo.erp.sales;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
import com.averpo.erp.inventory.domain.StockMovement;
import com.averpo.erp.inventory.domain.Warehouse;
import com.averpo.erp.inventory.service.InventoryService;
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
import com.averpo.erp.purchase.service.LandedCostService;
import com.averpo.erp.sales.domain.Invoice;
import com.averpo.erp.sales.domain.InvoiceStatus;
import com.averpo.erp.sales.service.InvoiceService;
import com.averpo.erp.sales.service.InvoiceService.InvoiceData;
import com.averpo.erp.sales.service.InvoiceService.LineData;
import com.averpo.erp.shared.domain.InventoryValuationMethod;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.service.PaymentTermService;
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
 * Invoice ҳаёт цикли тестлари: docs/modules/sales.md → «Тестлар»
 * (2-туртки). GL posting-rules «Сотув» жадвалига мослиги, омбор
 * чиқими (COGS) ва reverseIssue аниқлиги шу ерда текширилади
 * (ТЕМИР ҚОИДА №7: debit == credit).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InvoiceServiceTest {

    /** Барча тест ҳужжатлар санаси. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 6);

    @Autowired InvoiceService invoiceService;
    @Autowired ContactService contactService;
    @Autowired ItemService itemService;
    @Autowired WarehouseService warehouseService;
    @Autowired InventoryService inventoryService;
    @Autowired LandedCostService landedCostService;
    @Autowired AccountService accountService;
    @Autowired AccountRepository accountRepository;
    @Autowired JournalEntryRepository entryRepository;
    @Autowired CompanySettingsService settingsService;
    @Autowired PaymentTermService paymentTermService;

    /** Тест мижози (home валюта). */
    private Contact customer;

    /**
     * USD валютали мижоз (DEC-087): ҳужжат валютаси контактдан
     * келади - чет валюта ҳужжатлари шу мижозга ёзилади.
     */
    private Contact usdCustomer;

    /** Омбордаги товар (INVENTORY). */
    private Item item;

    /** Хизмат item'и (SERVICE). */
    private Item service;

    /** Асосий омбор (seed). */
    private Warehouse warehouse;

    /** Chart + мижоз + item'лар + омбор тайёрланади (rollback тозалайди). */
    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        customer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Тест мижози", null, null, null, null, null,
                null, null, null, null, null));
        usdCustomer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Тест USD мижози", null, null, null, null, null,
                "USD", null, null, null, null));
        ItemService.DefaultAccounts invDefaults = itemService.defaultsFor(ItemType.INVENTORY);
        item = itemService.create(ItemType.INVENTORY, new ItemData(
                "Invoice тест товари", null, null, null, null, null,
                invDefaults.income(), null, null, invDefaults.expense(),
                invDefaults.inventoryAsset(), null));
        ItemService.DefaultAccounts svcDefaults = itemService.defaultsFor(ItemType.SERVICE);
        service = itemService.create(ItemType.SERVICE, new ItemData(
                "Ўрнатиш хизмати", null, null, null, null, null,
                svcDefaults.income(), null, null, svcDefaults.expense(),
                null, null));
        warehouse = warehouseService.all().stream()
                .filter(w -> "Асосий омбор".equals(w.getName()))
                .findFirst().orElseThrow();
    }

    /** Омборга дастлабки қолдиқ киритади. */
    private void seedStock(BigDecimal qty, BigDecimal unitCost) {
        inventoryService.receive(item.getId(), warehouse.getId(), qty, unitCost,
                DATE, "SEED", null, null);
    }

    /** INVENTORY товар сатри ясагич. */
    private LineData itemLine(BigDecimal qty, BigDecimal price) {
        return new LineData(item.getId(), warehouse.getId(), qty, price, null, null);
    }

    /** Хизмат сатри ясагич. */
    private LineData serviceLine(BigDecimal qty, BigDecimal price) {
        return new LineData(service.getId(), null, qty, price, null, null);
    }

    /** Home валютадаги оддий invoice маълумоти. */
    private InvoiceData homeInvoice(List<LineData> lines) {
        return new InvoiceData(customer.getId(), DATE, null, null, null, null, lines);
    }

    @org.junit.jupiter.api.Test
    void list_pagination_secondPageSlice_stableSort() {
        // PERF-perf1 1-босқич: size+1 ёзув - 2-саҳифада биттагина
        // қолади; саналар атайлаб ҳар хил - тартиб (янгидан эскига)
        // детерминистик текширилади
        Invoice oldest = null;
        Invoice newest = null;
        for (int i = InvoiceService.LIST_PAGE_SIZE; i >= 0; i--) {
            Invoice draft = invoiceService.createDraft(new InvoiceData(
                    customer.getId(), DATE.minusDays(i), null, null, null, null,
                    List.of(serviceLine(BigDecimal.ONE, new BigDecimal("1000")))));
            if (oldest == null) {
                oldest = draft; // биринчи яратилгани энг эски санали
            }
            newest = draft;
        }

        var page0 = invoiceService.list(
                new InvoiceService.ListFilter(null, null, null, null, null), 0);
        assertThat(page0.getContent()).hasSize(InvoiceService.LIST_PAGE_SIZE);
        assertThat(page0.getTotalElements())
                .isEqualTo(InvoiceService.LIST_PAGE_SIZE + 1);
        assertThat(page0.getTotalPages()).isEqualTo(2);
        // Барқарор тартиб: энг янги санали биринчи (аввалги ORDER BY)
        assertThat(page0.getContent().get(0).getId()).isEqualTo(newest.getId());

        var page1 = invoiceService.list(
                new InvoiceService.ListFilter(null, null, null, null, null), 1);
        assertThat(page1.getContent()).hasSize(1);
        assertThat(page1.getContent().get(0).getId()).isEqualTo(oldest.getId());
        assertThat(page1.hasNext()).isFalse();
    }

    /** Invoice'нинг фаол GL ёзувини топади. */
    private JournalEntry glEntry(Invoice invoice) {
        return entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                InvoiceService.SOURCE_MODULE, invoice.getId()).orElseThrow();
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

    /** Valuation методини созлайди (қулф очиқлигида). */
    private void valuation(InventoryValuationMethod method) {
        var settings = settingsService.get();
        settingsService.update(settings.getName(), settings.homeCurrencyCode(),
                settings.getTimezone(), method, settings.getClosingDate());
    }

    @Test
    void post_homeInvoice_glMatchesPostingRules_andInventoryOut() {
        seedStock(new BigDecimal("10"), new BigDecimal("1000"));
        Invoice invoice = invoiceService.createDraft(homeInvoice(List.of(
                itemLine(new BigDecimal("4"), new BigDecimal("2500")),
                serviceLine(new BigDecimal("2"), new BigDecimal("5000")))));
        assertThat(invoice.getInvoiceNumber()).startsWith("INV-2026-");
        assertThat(invoice.getTotal()).isEqualByComparingTo("20000");

        invoiceService.post(invoice.getId());

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.POSTED);
        JournalEntry entry = glEntry(invoice);
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.POSTED);

        // AR дебети = 20 000 (мижоз dimension билан), COGS 4 000,
        // INVENTORY кредити 4 000, даромад кредитлари 20 000
        assertThat(baseOf(entry, "ACCOUNTS_RECEIVABLE", true)).isEqualByComparingTo("20000");
        assertThat(baseOf(entry, "SUPPLIES_MATERIALS_COGS", true)).isEqualByComparingTo("4000");
        assertThat(baseOf(entry, "INVENTORY", false)).isEqualByComparingTo("4000");
        // Ҳар сатр даромади ўз счётига (spec: «ҳар сатр даромади тўғри
        // счётга»): товар 4×2500 маҳсулот даромадига, хизмат 2×5000
        // хизмат даромадига - умумий кредит ичида аралашиб кетмайди
        assertThat(baseOf(entry, "SALES_OF_PRODUCT_INCOME", false))
                .isEqualByComparingTo("10000");
        assertThat(baseOf(entry, "SERVICE_FEE_INCOME", false))
                .isEqualByComparingTo("10000");
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        UUID arContact = null;
        for (JournalEntryLine line : entry.getLines()) {
            if (line.getDebit() != null) {
                debit = debit.add(line.getDebit().getBaseAmount());
                if (line.getAccount().getDetailType().name().equals("ACCOUNTS_RECEIVABLE")) {
                    arContact = line.getContactId();
                }
            }
            if (line.getCredit() != null) {
                credit = credit.add(line.getCredit().getBaseAmount());
            }
        }
        // ТЕМИР ҚОИДА №7: debit == credit (20 000 + 4 000 = 24 000)
        assertThat(debit).isEqualByComparingTo(credit);
        assertThat(debit).isEqualByComparingTo("24000");
        assertThat(arContact).isEqualTo(customer.getId());

        // Омбордан 4 дона чиқди; SERVICE сатр омборга тегмади
        assertThat(inventoryService.quantityOnHand(item.getId(), warehouse.getId()))
                .isEqualByComparingTo("6");
        List<StockMovement> movements =
                inventoryService.byReference(InvoiceService.SOURCE_MODULE, invoice.getId());
        assertThat(movements).hasSize(1);
        assertThat(movements.get(0).getType().inbound()).isFalse();
    }

    @Test
    void post_serviceOnly_noInventoryTouch_noCogs() {
        Invoice invoice = invoiceService.post(invoiceService.createDraft(homeInvoice(
                List.of(serviceLine(BigDecimal.ONE, new BigDecimal("5000"))))).getId());

        JournalEntry entry = glEntry(invoice);
        // Фақат AR Dt / даромад Cr - COGS/INVENTORY сатрлари йўқ
        assertThat(entry.getLines()).hasSize(2);
        assertThat(baseOf(entry, "SUPPLIES_MATERIALS_COGS", true)).isEqualByComparingTo("0");
        assertThat(inventoryService.byReference(InvoiceService.SOURCE_MODULE,
                invoice.getId())).isEmpty();
    }

    @Test
    void post_foreignCurrency_convertsToBase() {
        seedStock(new BigDecimal("10"), new BigDecimal("1000"));
        Invoice invoice = invoiceService.post(invoiceService.createDraft(new InvoiceData(
                usdCustomer.getId(), DATE, null, "USD", new BigDecimal("12600"), null,
                List.of(itemLine(BigDecimal.ONE, new BigDecimal("10"))))).getId());

        // 10 USD × 12 600 = 126 000 сўм AR; COGS home'да 1 000
        assertThat(invoice.getTotalBase()).isEqualByComparingTo("126000");
        JournalEntry entry = glEntry(invoice);
        assertThat(baseOf(entry, "ACCOUNTS_RECEIVABLE", true)).isEqualByComparingTo("126000");
        assertThat(baseOf(entry, "SUPPLIES_MATERIALS_COGS", true)).isEqualByComparingTo("1000");
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
    void post_foreignMultiLine_pennyRounding_balancedAndPosts() {
        // PERF-007 + LOG-002: AR дебети (назорат сатри) битта
        // яхлитлашли target = round(0.06 × 12345.6789) = 740.7407,
        // даромад сатрлари largest-remainder билан шунга тақсимланади
        Invoice invoice = invoiceService.post(invoiceService.createDraft(new InvoiceData(
                usdCustomer.getId(), DATE, null, "USD", new BigDecimal("12345.6789"), null,
                List.of(serviceLine(BigDecimal.ONE, new BigDecimal("0.03")),
                        serviceLine(BigDecimal.ONE, new BigDecimal("0.03"))))).getId());

        JournalEntry entry = glEntry(invoice);
        BigDecimal debitBase = BigDecimal.ZERO;
        BigDecimal creditBase = BigDecimal.ZERO;
        for (JournalEntryLine line : entry.getLines()) {
            if (line.getDebit() != null) {
                debitBase = debitBase.add(line.getDebit().getBaseAmount());
            }
            if (line.getCredit() != null) {
                creditBase = creditBase.add(line.getCredit().getBaseAmount());
            }
        }
        // Кредит йиғинди == AR дебети == Invoice.totalBase == target
        assertThat(creditBase).isEqualByComparingTo("740.7407");
        assertThat(debitBase).isEqualByComparingTo(creditBase);
        assertThat(invoice.getTotalBase()).isEqualByComparingTo(creditBase);
        assertMoneyInvariant(entry);
    }

    @Test
    void post_foreignThreeLines_everyLineKeepsMoneyInvariant() {
        // LOG-002 сценарийси: 3 × 0.01 USD, rate 10012.345 - эски
        // «йиғинди» ечимида AR сатри BR-LED-003 дан 0.00015 га чиқиб,
        // тўғри киритилган invoice пост бўлмай қоларди
        Invoice invoice = invoiceService.post(invoiceService.createDraft(new InvoiceData(
                usdCustomer.getId(), DATE, null, "USD", new BigDecimal("10012.345"), null,
                List.of(serviceLine(BigDecimal.ONE, new BigDecimal("0.01")),
                        serviceLine(BigDecimal.ONE, new BigDecimal("0.01")),
                        serviceLine(BigDecimal.ONE, new BigDecimal("0.01"))))).getId());

        JournalEntry entry = glEntry(invoice);
        BigDecimal debitBase = BigDecimal.ZERO;
        BigDecimal creditBase = BigDecimal.ZERO;
        for (JournalEntryLine line : entry.getLines()) {
            if (line.getDebit() != null) {
                debitBase = debitBase.add(line.getDebit().getBaseAmount());
            }
            if (line.getCredit() != null) {
                creditBase = creditBase.add(line.getCredit().getBaseAmount());
            }
        }
        // target = round(0.03 × 10012.345) = 300.3704
        assertThat(debitBase).isEqualByComparingTo("300.3704");
        assertThat(creditBase).isEqualByComparingTo(debitBase);
        assertThat(invoice.getTotalBase()).isEqualByComparingTo(debitBase);
        // Ҳар сатр (AR ҳам!) Money инвариантидан ўтади
        assertMoneyInvariant(entry);
    }

    @Test
    void arAging_onlyCurrentDate_rejectsHistorical() {
        // BR-RPT-001 (IFRS-004): жорий balanceDue'дан ўқилади - ўтган
        // санага сўралса ҳисобот ёлғон гапирар эди
        LocalDate today = LocalDate.now(settingsService.zoneId());
        assertThatThrownBy(() -> invoiceService.arAging(today.minusDays(1)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RPT-001"));
        // Бугунга (ва null - бугун олинади) ишлайверади
        assertThat(invoiceService.arAging(today)).isNotNull();
        assertThat(invoiceService.arAging(null)).isNotNull();
    }

    @Test
    void post_insufficientStock_blocked() {
        seedStock(new BigDecimal("2"), new BigDecimal("1000"));
        Invoice invoice = invoiceService.createDraft(homeInvoice(
                List.of(itemLine(new BigDecimal("5"), new BigDecimal("2500")))));

        assertThatThrownBy(() -> invoiceService.post(invoice.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-SINV-004"));

        // Invoice DRAFT'да қолди, омбор тегилмаган
        assertThat(invoiceService.get(invoice.getId()).getStatus())
                .isEqualTo(InvoiceStatus.DRAFT);
        assertThat(inventoryService.quantityOnHand(item.getId(), warehouse.getId()))
                .isEqualByComparingTo("2");
    }

    @Test
    void reverse_avco_restoresStock() {
        seedStock(new BigDecimal("10"), new BigDecimal("1000"));
        Invoice invoice = invoiceService.post(invoiceService.createDraft(homeInvoice(
                List.of(itemLine(new BigDecimal("4"), new BigDecimal("2500"))))).getId());
        assertThat(inventoryService.quantityOnHand(item.getId(), warehouse.getId()))
                .isEqualByComparingTo("6");

        invoiceService.reverse(invoice.getId(), DATE, "хато");

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.REVERSED);
        assertThat(glEntry(invoice).getStatus()).isEqualTo(EntryStatus.REVERSED);
        assertThat(inventoryService.quantityOnHand(item.getId(), warehouse.getId()))
                .isEqualByComparingTo("10");
        // Қиймат ҳам асл ҳолида: 10 дона × 1 000
        InventoryService.IssueResult issued = inventoryService.issue(
                item.getId(), warehouse.getId(), new BigDecimal("10"),
                DATE, "TEST", null, null);
        assertThat(issued.totalCost()).isEqualByComparingTo("10000");
    }

    @Test
    void reverse_fifo_restoresConsumedLayersInOrder() {
        valuation(InventoryValuationMethod.FIFO);
        seedStock(new BigDecimal("5"), new BigDecimal("1000"));
        seedStock(new BigDecimal("5"), new BigDecimal("2000"));

        // 7 дона сотилди: 5×1000 + 2×2000 = 9 000 COGS
        Invoice invoice = invoiceService.post(invoiceService.createDraft(homeInvoice(
                List.of(itemLine(new BigDecimal("7"), new BigDecimal("3000"))))).getId());
        JournalEntry entry = glEntry(invoice);
        assertThat(baseOf(entry, "SUPPLIES_MATERIALS_COGS", true)).isEqualByComparingTo("9000");

        invoiceService.reverse(invoice.getId(), DATE, null);

        // Партиялар ўз жойига қайтди: FIFO тартиби сақланган -
        // яна 7 чиқарилса айнан 9 000, қолган 3 таси 2 000 дан
        InventoryService.IssueResult first = inventoryService.issue(
                item.getId(), warehouse.getId(), new BigDecimal("7"),
                DATE, "TEST", null, null);
        assertThat(first.totalCost()).isEqualByComparingTo("9000");
        InventoryService.IssueResult second = inventoryService.issue(
                item.getId(), warehouse.getId(), new BigDecimal("3"),
                DATE, "TEST", null, null);
        assertThat(second.totalCost()).isEqualByComparingTo("6000");
    }

    @Test
    void reverse_blockedWhenLayerCostChangedByLandedCost() {
        valuation(InventoryValuationMethod.FIFO);
        // BILL манбали кирим (landed cost фақат шуларга тақсимланади)
        StockMovement receipt = inventoryService.receive(item.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("1000"), DATE,
                "BILL", UUID.randomUUID(), null);

        Invoice invoice = invoiceService.post(invoiceService.createDraft(homeInvoice(
                List.of(itemLine(new BigDecimal("4"), new BigDecimal("2500"))))).getId());

        // Чиқимдан КЕЙИН партияга landed cost тушди - нархи 1 060 бўлди
        landedCostService.create(new LandedCostService.AllocationData(
                DATE, new BigDecimal("600"), null, List.of(receipt.getId())));

        // Энди 4×1 060 ≠ 4 000 (асл COGS) - қайтариш блокланади
        assertThatThrownBy(() -> invoiceService.reverse(invoice.getId(), DATE, null))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-INV-009"));
        assertThat(invoiceService.get(invoice.getId()).getStatus())
                .isEqualTo(InvoiceStatus.POSTED);
    }

    @Test
    void lifecycle_guards() {
        seedStock(BigDecimal.ONE, new BigDecimal("1000"));
        Invoice invoice = invoiceService.createDraft(homeInvoice(
                List.of(itemLine(BigDecimal.ONE, new BigDecimal("2500")))));

        // Draft'ни reverse қилиб бўлмайди
        assertThatThrownBy(() -> invoiceService.reverse(invoice.getId(), DATE, null))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-SINV-007"));

        invoiceService.post(invoice.getId());

        // POSTED'ни қайта post/ўчириш тақиқ
        assertThatThrownBy(() -> invoiceService.post(invoice.getId()))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-SINV-006"));
        assertThatThrownBy(() -> invoiceService.deleteDraft(invoice.getId()))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-SINV-006"));
    }

    /** DEC-087 (BR-SINV-011): валюта контактдан derive + мослик гарови. */
    @Test
    void currency_derivedFromContact_mismatchRejected() {
        // Бўш currency - server USD контактдан ўзи олади (ҳақиқат манбаи)
        Invoice derived = invoiceService.createDraft(new InvoiceData(
                usdCustomer.getId(), DATE, null, null, new BigDecimal("12600"), null,
                List.of(serviceLine(BigDecimal.ONE, new BigDecimal("10")))));
        assertThat(derived.getCurrency().getCode()).isEqualTo("USD");

        // Клиент қиймати контактга (home) зид - BR-SINV-011 рад
        assertThatThrownBy(() -> invoiceService.createDraft(new InvoiceData(
                customer.getId(), DATE, null, "USD", new BigDecimal("12600"), null,
                List.of(serviceLine(BigDecimal.ONE, new BigDecimal("10"))))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-SINV-011"));
    }

    @Test
    void validation_guards() {
        List<LineData> okLine = List.of(serviceLine(BigDecimal.ONE, new BigDecimal("1000")));

        // BR-SINV-001: customer йўқ / нотўғри тип
        assertThatThrownBy(() -> invoiceService.createDraft(new InvoiceData(null,
                DATE, null, null, null, null, okLine)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-SINV-001"));
        Contact vendor = contactService.create(ContactType.VENDOR, new ContactData(
                "Етказувчи (мижоз эмас)", null, null, null, null, null,
                null, null, null, null, null));
        assertThatThrownBy(() -> invoiceService.createDraft(new InvoiceData(vendor.getId(),
                DATE, null, null, null, null, okLine)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-SINV-001"));

        // BR-SINV-009: сана йўқ; BR-SINV-002: сатр йўқ
        assertThatThrownBy(() -> invoiceService.createDraft(new InvoiceData(customer.getId(),
                null, null, null, null, null, okLine)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-SINV-009"));
        assertThatThrownBy(() -> invoiceService.createDraft(homeInvoice(List.of())))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-SINV-002"));

        // BR-SINV-003: манфий миқдор
        assertThatThrownBy(() -> invoiceService.createDraft(homeInvoice(List.of(
                serviceLine(new BigDecimal("-1"), BigDecimal.ONE)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-SINV-003"));

        // BR-SINV-004: INVENTORY item'га омбор танланмаган
        assertThatThrownBy(() -> invoiceService.createDraft(homeInvoice(List.of(
                new LineData(item.getId(), null, BigDecimal.ONE, BigDecimal.ONE, null, null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-SINV-004"));

        // BR-SINV-005: даромад счёти ўрнида AP счёти
        UUID apAccount = accountRepository.findByName("Кредиторлик (AP)").orElseThrow().getId();
        assertThatThrownBy(() -> invoiceService.createDraft(homeInvoice(List.of(
                new LineData(service.getId(), null, BigDecimal.ONE, new BigDecimal("100"),
                        apAccount, null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-SINV-005"));

        // BR-SINV-008: чет валютада курссиз (контакт USD - валюта гарови ўтади)
        assertThatThrownBy(() -> invoiceService.createDraft(new InvoiceData(usdCustomer.getId(),
                DATE, null, "USD", null, null, okLine)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-SINV-008"));

        // BR-SINV-010: нофаол item
        ItemService.DefaultAccounts defaults = itemService.defaultsFor(ItemType.SERVICE);
        Item inactive = itemService.create(ItemType.SERVICE, new ItemData(
                "Нофаол хизмат", null, null, null, null, null,
                defaults.income(), null, null, defaults.expense(), null, null));
        itemService.update(inactive.getId(), new ItemData(
                "Нофаол хизмат", null, null, null, null, null,
                defaults.income(), null, null, defaults.expense(), null, null), false);
        assertThatThrownBy(() -> invoiceService.createDraft(homeInvoice(List.of(
                new LineData(inactive.getId(), null, BigDecimal.ONE,
                        new BigDecimal("100"), null, null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-SINV-010"));
    }

    @Test
    void dueDate_creditLimit_updateAndDeleteDraft() {
        // Net 30 шартли ва 10 000 лимитли мижоз
        UUID termId = paymentTermService.active().stream()
                .filter(t -> t.getDays() == 30)
                .findFirst().orElseThrow().getId();
        Contact limited = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Лимитли мижоз", null, null, null, null, null,
                null, termId, null, new BigDecimal("10000"), null));

        Invoice invoice = invoiceService.createDraft(new InvoiceData(limited.getId(),
                DATE, null, null, null, null,
                List.of(serviceLine(BigDecimal.ONE, new BigDecimal("15000")))));
        assertThat(invoice.getDueDate()).isEqualTo(DATE.plusDays(30));

        // Credit limit: 15 000 > 10 000 - огоҳлантиради, лекин ТЎСМАЙДИ.
        // additional мижоз валютасида (getTotal, home getTotalBase эмас) -
        // home мижозда rate=1 бўлгани учун қиймат бир хил (15 000)
        InvoiceService.CreditCheck before = invoiceService.creditCheck(
                limited.getId(), invoice.getTotal());
        assertThat(before.exceeded()).isTrue();
        assertThat(before.creditLimit()).isEqualByComparingTo("10000");
        invoiceService.post(invoice.getId());
        assertThat(invoiceService.get(invoice.getId()).getStatus())
                .isEqualTo(InvoiceStatus.POSTED);
        // Post'дан кейин очиқ AR лимитдан ошган - белги энди доимий
        InvoiceService.CreditCheck after = invoiceService.creditCheck(limited.getId(), null);
        assertThat(after.exceeded()).isTrue();
        assertThat(after.exposure()).isEqualByComparingTo("15000");

        // Update: сатрлар қайта терилади, жами янгиланади; кейин ўчириш
        Invoice draft = invoiceService.createDraft(new InvoiceData(limited.getId(),
                DATE, null, null, null, null,
                List.of(serviceLine(BigDecimal.ONE, new BigDecimal("1000")))));
        Invoice updated = invoiceService.updateDraft(draft.getId(), new InvoiceData(
                limited.getId(), DATE, DATE.plusDays(10), null, null, "янги изоҳ",
                List.of(serviceLine(new BigDecimal("2"), new BigDecimal("3000")))));
        assertThat(updated.getTotal()).isEqualByComparingTo("6000");
        assertThat(updated.getDueDate()).isEqualTo(DATE.plusDays(10));
        invoiceService.deleteDraft(draft.getId());
        assertThatThrownBy(() -> invoiceService.get(draft.getId()))
                .isInstanceOf(com.averpo.erp.shared.exception.NotFoundException.class);
    }

    @Test
    void creditCheck_customerCurrency_noHomeConversion() {
        // Чет валютали (USD) мижоз - home UZS'да USD курси 12600. Лимит
        // МИЖОЗ ВАЛЮТАСИДА (1000 USD): очиқ AR home'га айлантирилмайди
        // (акс ҳолда 500 USD × 12600 = 6.3M деб лимитдан «ошиб» кетарди -
        // DEC-160 бузилиши). Бу тест конверсия ЙЎҚлигини қулфлайди.
        Contact usdLimited = contactService.create(ContactType.CUSTOMER, new ContactData(
                "USD лимитли мижоз", null, null, null, null, null,
                "USD", null, null, new BigDecimal("1000"), null));
        // POSTED USD invoice: balanceDue = 500 USD, курс 12600
        Invoice usdInv = invoiceService.post(invoiceService.createDraft(new InvoiceData(
                usdLimited.getId(), DATE, null, "USD", new BigDecimal("12600"), null,
                List.of(serviceLine(BigDecimal.ONE, new BigDecimal("500"))))).getId());
        assertThat(usdInv.getBalanceDue()).isEqualByComparingTo("500");

        // exposure = 500 (МИЖОЗ валютаси, ×12600 ЭМАС) < лимит 1000 → ошмайди
        InvoiceService.CreditCheck check = invoiceService.creditCheck(usdLimited.getId(), null);
        assertThat(check.exposure()).isEqualByComparingTo("500");
        assertThat(check.creditLimit()).isEqualByComparingTo("1000");
        assertThat(check.exceeded()).isFalse();

        // additional 600 USD (мижоз валютаси) қўшилса: 500 + 600 = 1100 >
        // 1000 → лимит мижоз валютасида ишлайди (home конверсиясиз)
        InvoiceService.CreditCheck withNew = invoiceService.creditCheck(
                usdLimited.getId(), new BigDecimal("600"));
        assertThat(withNew.exposure()).isEqualByComparingTo("1100");
        assertThat(withNew.exceeded()).isTrue();
    }
}
