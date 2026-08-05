package com.averpo.erp.purchase;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.contact.service.ContactService.ContactData;
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
import com.averpo.erp.purchase.domain.Bill;
import com.averpo.erp.purchase.domain.BillLineType;
import com.averpo.erp.purchase.domain.BillStatus;
import com.averpo.erp.purchase.service.BillService;
import com.averpo.erp.purchase.service.BillService.BillData;
import com.averpo.erp.purchase.service.BillService.LineData;
import com.averpo.erp.shared.domain.InventoryValuationMethod;
import com.averpo.erp.shared.exception.BusinessRuleException;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bill ҳаёт цикли тестлари: docs/modules/purchases.md → «Тестлар»
 * (2-туртки). GL posting-rules «Харид» жадвалига мослиги ва омбор
 * интеграцияси шу ерда текширилади (ТЕМИР ҚОИДА №7: debit == credit).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BillServiceTest {

    /** Барча тест ҳужжатлар санаси. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 6);

    @Autowired BillService billService;
    @Autowired ContactService contactService;
    @Autowired ItemService itemService;
    @Autowired WarehouseService warehouseService;
    @Autowired InventoryService inventoryService;
    @Autowired AccountService accountService;
    @Autowired AccountRepository accountRepository;
    @Autowired JournalEntryRepository entryRepository;
    @Autowired CompanySettingsService settingsService;

    /** Тест vendor'и (home валюта). */
    private Contact vendor;

    /**
     * USD валютали vendor (DEC-087): ҳужжат валютаси контактдан
     * келади - чет валюта bill'лари шу vendor'га ёзилади.
     */
    private Contact usdVendor;

    /** Тест товари (INVENTORY). */
    private Item item;

    /** Асосий омбор (seed). */
    private Warehouse warehouse;

    /** Ижара счёти - EXPENSE сатр тестлари учун. */
    private UUID rentAccountId;

    /** Chart + vendor + item + омбор тайёрланади (rollback тозалайди). */
    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        vendor = contactService.create(ContactType.VENDOR, new ContactData(
                "Тест етказувчи", null, null, null, null, null,
                null, null, null, null, null));
        usdVendor = contactService.create(ContactType.VENDOR, new ContactData(
                "Тест USD етказувчи", null, null, null, null, null,
                "USD", null, null, null, null));
        ItemService.DefaultAccounts defaults = itemService.defaultsFor(ItemType.INVENTORY);
        item = itemService.create(ItemType.INVENTORY, new ItemData(
                "Bill тест товари", null, null, null, null, null,
                defaults.income(), null, null, defaults.expense(),
                defaults.inventoryAsset(), null));
        warehouse = warehouseService.all().stream()
                .filter(w -> "Асосий омбор".equals(w.getName()))
                .findFirst().orElseThrow();
        rentAccountId = accountRepository.findByName("Ижара").orElseThrow().getId();
    }

    /** ITEM сатри ясагич. */
    private LineData itemLine(BigDecimal qty, BigDecimal price) {
        return new LineData(BillLineType.ITEM, item.getId(), warehouse.getId(),
                qty, price, null, null, null);
    }

    /** Home валютадаги оддий bill маълумоти. */
    private BillData homeBill(String vendorInvoiceNumber, List<LineData> lines) {
        return new BillData(vendor.getId(), vendorInvoiceNumber, DATE, null,
                null, null, null, lines);
    }

    @org.junit.jupiter.api.Test
    void list_pagination_secondPageSlice_stableSort() {
        // PERF-perf1 1-босқич: size+1 ёзув - 2-саҳифада биттагина
        // қолади; саналар ҳар хил - тартиб детерминистик текширилади
        Bill oldest = null;
        Bill newest = null;
        for (int i = BillService.LIST_PAGE_SIZE; i >= 0; i--) {
            Bill draft = billService.createDraft(new BillData(
                    vendor.getId(), null, DATE.minusDays(i), null, null, null, null,
                    List.of(new LineData(BillLineType.EXPENSE, null, null, null, null,
                            rentAccountId, new BigDecimal("1000"), null))));
            if (oldest == null) {
                oldest = draft; // биринчи яратилгани энг эски санали
            }
            newest = draft;
        }

        var page0 = billService.list(
                new BillService.ListFilter(null, null, null, null, null), 0);
        assertThat(page0.getContent()).hasSize(BillService.LIST_PAGE_SIZE);
        assertThat(page0.getTotalElements()).isEqualTo(BillService.LIST_PAGE_SIZE + 1);
        assertThat(page0.getTotalPages()).isEqualTo(2);
        // Барқарор тартиб: энг янги санали биринчи (аввалги ORDER BY)
        assertThat(page0.getContent().get(0).getId()).isEqualTo(newest.getId());

        var page1 = billService.list(
                new BillService.ListFilter(null, null, null, null, null), 1);
        assertThat(page1.getContent()).hasSize(1);
        assertThat(page1.getContent().get(0).getId()).isEqualTo(oldest.getId());
        assertThat(page1.hasNext()).isFalse();
    }

    /** Bill'нинг фаол GL ёзувини топади. */
    private JournalEntry glEntry(Bill bill) {
        return entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                BillService.SOURCE_MODULE, bill.getId()).orElseThrow();
    }

    @Test
    void post_homeBill_glMatchesPostingRules_andInventoryIn() {
        Bill bill = billService.createDraft(homeBill("VH-1", List.of(
                itemLine(new BigDecimal("10"), new BigDecimal("10000")),
                new LineData(BillLineType.EXPENSE, null, null, null, null,
                        rentAccountId, new BigDecimal("50000"), "офис ижараси"))));
        assertThat(bill.getBillNumber()).startsWith("BILL-2026-");
        assertThat(bill.getTotal()).isEqualByComparingTo("150000");

        billService.post(bill.getId());

        assertThat(bill.getStatus()).isEqualTo(BillStatus.POSTED);
        JournalEntry entry = glEntry(bill);
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.POSTED);
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        String itemDebitDetail = null;
        BigDecimal itemDebitBase = null;
        UUID expenseDebitAccount = null;
        BigDecimal expenseDebitBase = null;
        String apDetail = null;
        UUID apContact = null;
        for (JournalEntryLine line : entry.getLines()) {
            if (line.getDebit() != null) {
                debit = debit.add(line.getDebit().getBaseAmount());
                // ITEM сатрда item dimension бор, EXPENSE сатрда йўқ -
                // шу белги орқали debit сатрлар счёт бўйича ажратилади
                if (line.getItemId() != null) {
                    itemDebitDetail = line.getAccount().getDetailType().name();
                    itemDebitBase = line.getDebit().getBaseAmount();
                } else {
                    expenseDebitAccount = line.getAccount().getId();
                    expenseDebitBase = line.getDebit().getBaseAmount();
                }
            }
            if (line.getCredit() != null) {
                credit = credit.add(line.getCredit().getBaseAmount());
                apDetail = line.getAccount().getDetailType().name();
                apContact = line.getContactId();
            }
        }
        // ТЕМИР ҚОИДА №7 + posting-rules: жами AP кредити = 150 000
        assertThat(debit).isEqualByComparingTo(credit);
        assertThat(credit).isEqualByComparingTo("150000");
        // posting-rules «Харид»: ҳар debit сатр ўз счётига тушади -
        // ITEM → item'нинг INVENTORY asset счёти, EXPENSE → танланган счёт
        assertThat(itemDebitDetail).isEqualTo("INVENTORY");
        assertThat(itemDebitBase).isEqualByComparingTo("100000");
        assertThat(expenseDebitAccount).isEqualTo(rentAccountId);
        assertThat(expenseDebitBase).isEqualByComparingTo("50000");
        assertThat(apDetail).isEqualTo("ACCOUNTS_PAYABLE");
        assertThat(apContact).isEqualTo(vendor.getId());

        // Омборга 10 дона 10 000 дан кирди
        assertThat(inventoryService.quantityOnHand(item.getId(), warehouse.getId()))
                .isEqualByComparingTo("10");
    }

    @Test
    void post_landedCostLine_debitsInventoryClearing() {
        // LANDED_COST сатри INVENTORY_CLEARING'га Dt тушади (posting-rules
        // «Харид»), кейин тақсимот операцияси receipt'ларга ёяди
        Bill bill = billService.post(billService.createDraft(homeBill(null, List.of(
                new LineData(BillLineType.LANDED_COST, null, null, null, null,
                        null, new BigDecimal("20000"), "ташиш хизмати")))).getId());

        JournalEntry entry = glEntry(bill);
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.POSTED);
        JournalEntryLine debitLine = entry.getLines().stream()
                .filter(l -> l.getDebit() != null).findFirst().orElseThrow();
        JournalEntryLine creditLine = entry.getLines().stream()
                .filter(l -> l.getCredit() != null).findFirst().orElseThrow();
        // ТЕМИР ҚОИДА №7: debit == credit
        assertThat(debitLine.getDebit().getBaseAmount())
                .isEqualByComparingTo(creditLine.getCredit().getBaseAmount());
        assertThat(debitLine.getDebit().getBaseAmount()).isEqualByComparingTo("20000");
        assertThat(debitLine.getAccount().getDetailType().name())
                .isEqualTo("INVENTORY_CLEARING");
        assertThat(creditLine.getAccount().getDetailType().name())
                .isEqualTo("ACCOUNTS_PAYABLE");
    }

    @Test
    void post_foreignCurrency_convertsToBase() {
        BillData data = new BillData(usdVendor.getId(), null, DATE, null,
                "USD", new BigDecimal("12600"), null,
                List.of(itemLine(new BigDecimal("10"), new BigDecimal("10"))));
        Bill bill = billService.post(billService.createDraft(data).getId());

        // 100 USD × 12 600 = 1 260 000 сўм
        assertThat(bill.getTotal()).isEqualByComparingTo("100");
        assertThat(bill.getTotalBase()).isEqualByComparingTo("1260000");

        JournalEntry entry = glEntry(bill);
        BigDecimal creditBase = entry.getLines().stream()
                .filter(l -> l.getCredit() != null)
                .map(l -> l.getCredit().getBaseAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(creditBase).isEqualByComparingTo("1260000");

        // Омборга home қийматда кирди: 10 дона × 126 000
        InventoryService.IssueResult issued = inventoryService.issue(
                item.getId(), warehouse.getId(), new BigDecimal("10"),
                DATE, "TEST", null, null);
        assertThat(issued.totalCost()).isEqualByComparingTo("1260000");
    }

    /** EXPENSE сатри ясагич (penny rounding тестлари учун). */
    private LineData expenseLine(String amount) {
        return new LineData(BillLineType.EXPENSE, null, null, null, null,
                rentAccountId, new BigDecimal(amount), null);
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
        // PERF-001 + LOG-002: AP кредити (назорат сатри) битта
        // яхлитлашли target = round(0.06 × 12345.6789) = 740.7407, дебет
        // сатрлар largest-remainder билан шунга тақсимланади
        BillData data = new BillData(usdVendor.getId(), null, DATE, null,
                "USD", new BigDecimal("12345.6789"), null,
                List.of(expenseLine("0.03"), expenseLine("0.03")));
        Bill bill = billService.post(billService.createDraft(data).getId());

        JournalEntry entry = glEntry(bill);
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
        // Дебет йиғиндиси == AP кредити == Bill.totalBase == target
        assertThat(creditBase).isEqualByComparingTo("740.7407");
        assertThat(debitBase).isEqualByComparingTo(creditBase);
        assertThat(bill.getTotalBase()).isEqualByComparingTo(creditBase);
        assertMoneyInvariant(entry);
    }

    @Test
    void post_foreignThreeLines_everyLineKeepsMoneyInvariant() {
        // LOG-002 сценарийси: ҳар сатр exact base'и 100.12345
        // (яхлитлаш хатоси максимал) - эски «йиғинди» ечимида AP сатри
        // BR-LED-003 дан 0.00015 га чиқиб, ҳужжат пост бўлмай қоларди
        BillData data = new BillData(usdVendor.getId(), null, DATE, null,
                "USD", new BigDecimal("10012.345"), null,
                List.of(expenseLine("0.01"), expenseLine("0.01"), expenseLine("0.01")));
        Bill bill = billService.post(billService.createDraft(data).getId());

        JournalEntry entry = glEntry(bill);
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
        // target = round(0.03 × 10012.345) = 300.3704; баланс ва totalBase мос
        assertThat(creditBase).isEqualByComparingTo("300.3704");
        assertThat(debitBase).isEqualByComparingTo(creditBase);
        assertThat(bill.getTotalBase()).isEqualByComparingTo(creditBase);
        // Ҳар сатр (AP ҳам!) Money инвариантидан ўтади
        assertMoneyInvariant(entry);
    }

    @Test
    void vendorInvoiceNumber_duplicateGuard_freedAfterReversal() {
        Bill first = billService.createDraft(homeBill("INV-77",
                List.of(itemLine(BigDecimal.ONE, new BigDecimal("1000")))));

        // Ўша vendor + ўша рақам - тақиқ (draft ҳолатда ҳам)
        assertThatThrownBy(() -> billService.createDraft(homeBill("INV-77",
                List.of(itemLine(BigDecimal.ONE, new BigDecimal("1000"))))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BILL-006"));

        // Бошқа vendor'га ўша рақам - муаммосиз
        Contact other = contactService.create(ContactType.VENDOR, new ContactData(
                "Бошқа етказувчи", null, null, null, null, null,
                null, null, null, null, null));
        billService.createDraft(new BillData(other.getId(), "INV-77", DATE, null,
                null, null, null, List.of(itemLine(BigDecimal.ONE, new BigDecimal("500")))));

        // Post + reverse'дан кейин рақам бўшайди
        billService.post(first.getId());
        billService.reverse(first.getId(), DATE, "тест");
        Bill again = billService.createDraft(homeBill("INV-77",
                List.of(itemLine(BigDecimal.ONE, new BigDecimal("1000")))));
        assertThat(again.getVendorInvoiceNumber()).isEqualTo("INV-77");
    }

    @Test
    void reverse_avco_returnsInventoryAtOriginalCost() {
        // 1-bill 10 дона 1 000 дан, 2-bill 10 дона 2 000 дан - ўртача 1 500
        Bill bill1 = billService.post(billService.createDraft(homeBill(null,
                List.of(itemLine(new BigDecimal("10"), new BigDecimal("1000"))))).getId());
        Bill bill2 = billService.post(billService.createDraft(homeBill(null,
                List.of(itemLine(new BigDecimal("10"), new BigDecimal("2000"))))).getId());
        assertThat(inventoryService.quantityOnHand(item.getId(), warehouse.getId()))
                .isEqualByComparingTo("20");

        billService.reverse(bill2.getId(), DATE, "хато киритилган");

        // 2-bill айнан ўз нархида (2 000) қайтди - ўртача 1 000 га тушади,
        // GL сторноси билан омбор қиймати мос қолади
        assertThat(bill2.getStatus()).isEqualTo(BillStatus.REVERSED);
        assertThat(inventoryService.quantityOnHand(item.getId(), warehouse.getId()))
                .isEqualByComparingTo("10");
        InventoryService.IssueResult remaining = inventoryService.issue(
                item.getId(), warehouse.getId(), new BigDecimal("10"),
                DATE, "TEST", null, null);
        assertThat(remaining.totalCost()).isEqualByComparingTo("10000");

        // GL: асл entry REVERSED бўлди
        JournalEntry entry = glEntry(bill2);
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.REVERSED);
        // 1-bill тегилмаган
        assertThat(bill1.getStatus()).isEqualTo(BillStatus.POSTED);
    }

    @Test
    void reverse_blockedWhenStockConsumed() {
        settingsChange(InventoryValuationMethod.FIFO);
        Bill bill = billService.post(billService.createDraft(homeBill(null,
                List.of(itemLine(new BigDecimal("10"), new BigDecimal("1000"))))).getId());

        // 3 дона сотилди - партия қисман ейилган
        inventoryService.issue(item.getId(), warehouse.getId(),
                new BigDecimal("3"), DATE, "TEST", null, null);

        assertThatThrownBy(() -> billService.reverse(bill.getId(), DATE, null))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BILL-010"));
    }

    @Test
    void apAging_onlyCurrentDate_rejectsHistorical() {
        // BR-RPT-001 (IFRS-004): жорий balanceDue'дан ўқилади - ўтган
        // санага сўралса ҳисобот ёлғон гапирар эди
        LocalDate today = LocalDate.now(settingsService.zoneId());
        assertThatThrownBy(() -> billService.apAging(today.minusDays(1)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RPT-001"));
        // Бугунга (ва null - бугун олинади) ишлайверади
        assertThat(billService.apAging(today)).isNotNull();
        assertThat(billService.apAging(null)).isNotNull();
    }

    @Test
    void lifecycle_guards() {
        Bill bill = billService.createDraft(homeBill(null,
                List.of(itemLine(BigDecimal.ONE, new BigDecimal("1000")))));

        // Draft'ни reverse қилиб бўлмайди
        assertThatThrownBy(() -> billService.reverse(bill.getId(), DATE, null))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BILL-008"));

        billService.post(bill.getId());

        // POSTED'ни қайта post/таҳрир/ўчириш тақиқ
        assertThatThrownBy(() -> billService.post(bill.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BILL-007"));
        assertThatThrownBy(() -> billService.deleteDraft(bill.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BILL-007"));
    }

    /** DEC-087 (BR-BILL-013): валюта контактдан derive + мослик гарови. */
    @Test
    void currency_derivedFromContact_mismatchRejected() {
        // Бўш currency - server USD vendor'дан ўзи олади (ҳақиқат манбаи)
        Bill derived = billService.createDraft(new BillData(usdVendor.getId(), null,
                DATE, null, null, new BigDecimal("12600"), null,
                List.of(itemLine(BigDecimal.ONE, new BigDecimal("10")))));
        assertThat(derived.getCurrency().getCode()).isEqualTo("USD");

        // Клиент қиймати контактга (home) зид - BR-BILL-013 рад
        assertThatThrownBy(() -> billService.createDraft(new BillData(vendor.getId(), null,
                DATE, null, "USD", new BigDecimal("12600"), null,
                List.of(itemLine(BigDecimal.ONE, new BigDecimal("10"))))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BILL-013"));
    }

    @Test
    void validation_guards() {
        List<LineData> okLine = List.of(itemLine(BigDecimal.ONE, new BigDecimal("1000")));

        // Vendor йўқ / нотўғри тип
        assertThatThrownBy(() -> billService.createDraft(new BillData(null, null,
                DATE, null, null, null, null, okLine)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BILL-001"));
        Contact customer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Мижоз (vendor эмас)", null, null, null, null, null,
                null, null, null, null, null));
        assertThatThrownBy(() -> billService.createDraft(new BillData(customer.getId(),
                null, DATE, null, null, null, null, okLine)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BILL-001"));

        // Сана/сатрлар/ITEM талаблари
        assertThatThrownBy(() -> billService.createDraft(new BillData(vendor.getId(),
                null, null, null, null, null, null, okLine)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BILL-011"));
        assertThatThrownBy(() -> billService.createDraft(homeBill(null, List.of())))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BILL-002"));
        assertThatThrownBy(() -> billService.createDraft(homeBill(null, List.of(
                new LineData(BillLineType.ITEM, item.getId(), null,
                        BigDecimal.ONE, BigDecimal.ONE, null, null, null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BILL-004"));
        assertThatThrownBy(() -> billService.createDraft(homeBill(null, List.of(
                itemLine(new BigDecimal("-1"), BigDecimal.ONE)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BILL-003"));

        // EXPENSE сатрида AP счёти (EXPENSE туркуми эмас)
        UUID apAccount = accountRepository.findByName("Кредиторлик (AP)").orElseThrow().getId();
        assertThatThrownBy(() -> billService.createDraft(homeBill(null, List.of(
                new LineData(BillLineType.EXPENSE, null, null, null, null,
                        apAccount, new BigDecimal("100"), null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BILL-005"));

        // Чет валютада курссиз (контакт USD - валюта гарови ўтади)
        assertThatThrownBy(() -> billService.createDraft(new BillData(usdVendor.getId(),
                null, DATE, null, "USD", null, null, okLine)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BILL-009"));

        // Home валютада 1 дан фарқли курс - тақиқ (spec: «home'да курс 1»)
        assertThatThrownBy(() -> billService.createDraft(new BillData(vendor.getId(),
                null, DATE, null, null, new BigDecimal("2"), null, okLine)))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-BILL-009"));
    }

    @Test
    void dueDate_fromVendorPaymentTerm_updateAndDeleteDraft() {
        // Net 30 шартли vendor - due date автоматик
        UUID termId = paymentTermService.active().stream()
                .filter(t -> t.getDays() == 30)
                .findFirst().orElseThrow().getId();
        Contact termVendor = contactService.create(ContactType.VENDOR, new ContactData(
                "Шартли етказувчи", null, null, null, null, null,
                null, termId, null, null, null));

        Bill bill = billService.createDraft(new BillData(termVendor.getId(), null,
                DATE, null, null, null, null,
                List.of(itemLine(BigDecimal.ONE, new BigDecimal("1000")))));
        assertThat(bill.getDueDate()).isEqualTo(DATE.plusDays(30));

        // Update: сатрлар қайта терилади, жами янгиланади
        Bill updated = billService.updateDraft(bill.getId(), new BillData(
                termVendor.getId(), null, DATE, DATE.plusDays(10), null, null, "янги изоҳ",
                List.of(itemLine(new BigDecimal("2"), new BigDecimal("3000")))));
        assertThat(updated.getTotal()).isEqualByComparingTo("6000");
        assertThat(updated.getDueDate()).isEqualTo(DATE.plusDays(10));
        assertThat(updated.getLines()).hasSize(1);

        // Draft ўчирилади
        billService.deleteDraft(bill.getId());
        assertThatThrownBy(() -> billService.get(bill.getId()))
                .isInstanceOf(com.averpo.erp.shared.exception.NotFoundException.class);
    }

    @Autowired com.averpo.erp.shared.service.PaymentTermService paymentTermService;

    /** Valuation методини созлайди (қулф очиқлигида). */
    private void settingsChange(InventoryValuationMethod method) {
        var settings = settingsService.get();
        settingsService.update(settings.getName(), settings.homeCurrencyCode(),
                settings.getTimezone(), method, settings.getClosingDate());
    }
}
