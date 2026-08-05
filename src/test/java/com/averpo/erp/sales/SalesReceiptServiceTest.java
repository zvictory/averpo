package com.averpo.erp.sales;

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
import com.averpo.erp.sales.domain.SalesReceipt;
import com.averpo.erp.sales.service.SalesReceiptService;
import com.averpo.erp.sales.service.SalesReceiptService.LineData;
import com.averpo.erp.sales.service.SalesReceiptService.SalesReceiptData;
import com.averpo.erp.shared.domain.TxnClass;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.service.TxnClassService;
import com.averpo.erp.tax.service.TaxRateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SalesReceipt (сотув чеки, 24-банд) тестлари - posting-rules «Сотув
 * чеки». Invoice'нинг AR'сиз кўзгуси: Dr банк/касса gross / Cr даромад +
 * ҚҚС, ITEM сатрда омбордан чиқим + Dr COGS / Cr INVENTORY. Ҳар
 * posting'да debit == credit (ТЕМИР ҚОИДА №7).
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SalesReceiptServiceTest {

    /** Барча тест ҳужжатлар санаси. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 8);

    @Autowired SalesReceiptService salesReceiptService;
    @Autowired InventoryService inventoryService;
    @Autowired WarehouseService warehouseService;
    @Autowired ContactService contactService;
    @Autowired ItemService itemService;
    @Autowired AccountService accountService;
    @Autowired AccountRepository accountRepository;
    @Autowired JournalEntryRepository entryRepository;
    @Autowired TxnClassService txnClassService;
    @Autowired TaxRateService taxRateService;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired jakarta.persistence.EntityManagerFactory emf;

    private Contact customer;

    /** USD валютали мижоз (Arbitr-087): чет валюта чеклари шунга ёзилади. */
    private Contact usdCustomer;

    private Item invItem;
    private Warehouse warehouse;

    /** Default chart'даги банк счёти (CHECKING, home валюта). */
    private UUID bankAccountId;

    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        customer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Сотув чеки мижози", null, null, null, null, null,
                null, null, null, null, null));
        usdCustomer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Сотув чеки USD мижози", null, null, null, null, null,
                "USD", null, null, null, null));
        ItemService.DefaultAccounts inv = itemService.defaultsFor(ItemType.INVENTORY);
        invItem = itemService.create(ItemType.INVENTORY, new ItemData(
                "Чекда сотиладиган товар", null, null, null, null, null,
                inv.income(), null, null, inv.expense(), inv.inventoryAsset(), null));
        warehouse = warehouseService.all().stream()
                .filter(w -> "Асосий омбор".equals(w.getName()))
                .findFirst().orElseThrow();
        bankAccountId = accountRepository.findByName("Банк ҳисобварағи").orElseThrow().getId();
    }

    /** Манба бўйича фаол GL ёзуви. */
    private JournalEntry glEntry(UUID docId) {
        return entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                SalesReceiptService.SOURCE_MODULE, docId).orElseThrow();
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

    /** Detail type бўйича биринчи сатр. */
    private JournalEntryLine lineOf(JournalEntry entry, String detailType) {
        return entry.getLines().stream()
                .filter(l -> l.getAccount().getDetailType().name().equals(detailType))
                .findFirst().orElseThrow();
    }

    /** ТЕМИР ҚОИДА №7: home'да дебет == кредит. */
    private void assertBalanced(JournalEntry entry) {
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (JournalEntryLine line : entry.getLines()) {
            if (line.getDebit() != null) debit = debit.add(line.getDebit().getBaseAmount());
            if (line.getCredit() != null) credit = credit.add(line.getCredit().getBaseAmount());
        }
        assertThat(debit).isEqualByComparingTo(credit);
    }

    /** ITEM сатрли чек (home валютада), ихтиёрий class билан. */
    private SalesReceipt createItemReceipt(String qty, String price, UUID classId) {
        return salesReceiptService.create(new SalesReceiptData(customer.getId(),
                bankAccountId, DATE, null, null, false, null,
                List.of(new LineData(invItem.getId(), warehouse.getId(),
                        new BigDecimal(qty), new BigDecimal(price),
                        null, null, null, null, classId))));
    }

    /**
     * Spec 1-банд: post debit == credit (home) + Dr банк / Cr даромад
     * (AR умуман қатнашмайди) + ITEM сатрда OUT ва Dr COGS / Cr INVENTORY.
     */
    @Test
    void post_debitsBank_creditsRevenue_issuesStock() {
        // Омборга 10 @ 800 кирган - сотув жорий AVCO (800) да чиқади
        inventoryService.receive(invItem.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("800"), DATE, "SEED", null, null);

        SalesReceipt receipt = createItemReceipt("2", "2000", null);

        assertThat(receipt.getStatus()).isEqualTo(SalesReceipt.Status.POSTED);
        assertThat(receipt.getSrNumber()).startsWith("SR-2026-");
        assertThat(receipt.getTotal()).isEqualByComparingTo("4000");

        JournalEntry entry = glEntry(receipt.getId());
        assertBalanced(entry);
        // Dr банк / Cr даромад - пул дарҳол тушади, AR йўқ
        assertThat(baseOf(entry, "CHECKING", true)).isEqualByComparingTo("4000");
        assertThat(baseOf(entry, "SALES_OF_PRODUCT_INCOME", false)).isEqualByComparingTo("4000");
        assertThat(baseOf(entry, "ACCOUNTS_RECEIVABLE", true)).isEqualByComparingTo("0");
        assertThat(baseOf(entry, "ACCOUNTS_RECEIVABLE", false)).isEqualByComparingTo("0");
        // Сотув таннархи: Dr COGS / Cr INVENTORY - 2 × 800 = 1600
        assertThat(baseOf(entry, "SUPPLIES_MATERIALS_COGS", true)).isEqualByComparingTo("1600");
        assertThat(baseOf(entry, "INVENTORY", false)).isEqualByComparingTo("1600");
        // Омбордан чиқди: 10 → 8
        assertThat(inventoryService.quantityOnHand(invItem.getId(), warehouse.getId()))
                .isEqualByComparingTo("8");
    }

    /**
     * Spec 3-банд: тўлов счёти валютаси ҳужжат валютасига тенг бўлиши шарт
     * (BR-SR-002). Arbitr-087 дан кейин ҳужжат валютаси мижозники - қоида
     * энди «мижоз валютасига мос тўлов счёти» маъносини беради: USD мижоз
     * чеки UZS (home) банк счётига ёзилмайди.
     */
    @Test
    void create_bankCurrencyMismatch_rejectedSr002() {
        assertThatThrownBy(() -> salesReceiptService.create(new SalesReceiptData(
                usdCustomer.getId(), bankAccountId, DATE, "USD",
                new BigDecimal("12600"), false, null,
                List.of(new LineData(invItem.getId(), warehouse.getId(),
                        BigDecimal.ONE, new BigDecimal("100"),
                        null, null, null, null, null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-SR-002"));
    }

    /** Arbitr-087 (BR-SR-004): валюта контактдан derive + мослик гарови. */
    @Test
    void currency_derivedFromContact_mismatchRejected() {
        // home мижозга USD чек - валюта мижозники бўлиши шарт
        assertThatThrownBy(() -> salesReceiptService.create(new SalesReceiptData(
                customer.getId(), bankAccountId, DATE, "USD",
                new BigDecimal("12600"), false, null,
                List.of(new LineData(invItem.getId(), warehouse.getId(),
                        BigDecimal.ONE, new BigDecimal("100"),
                        null, null, null, null, null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-SR-004"));

        // Бўш currency - server USD мижоздан ўзи олади (USD банкка ёзилади)
        inventoryService.receive(invItem.getId(), warehouse.getId(),
                new BigDecimal("5"), new BigDecimal("800"), DATE, "SEED-087", null, null);
        UUID usdBank = accountRepository.findByName("Валюта ҳисобварағи (USD)")
                .orElseThrow().getId();
        SalesReceipt derived = salesReceiptService.create(new SalesReceiptData(
                usdCustomer.getId(), usdBank, DATE, null, new BigDecimal("12600"),
                false, null,
                List.of(new LineData(invItem.getId(), warehouse.getId(),
                        BigDecimal.ONE, new BigDecimal("100"),
                        null, null, null, null, null))));
        assertThat(derived.getCurrency().getCode()).isEqualTo("USD");
    }

    /** Spec 4-банд: reverse - GL сторно + товар омборга АЙНАН қайтади. */
    @Test
    void reverse_stornosGl_andReturnsStock() {
        inventoryService.receive(invItem.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("800"), DATE, "SEED", null, null);
        SalesReceipt receipt = createItemReceipt("2", "2000", null);
        assertThat(inventoryService.quantityOnHand(invItem.getId(), warehouse.getId()))
                .isEqualByComparingTo("8");

        salesReceiptService.reverse(receipt.getId(), DATE, "сотув бекор");

        assertThat(glEntry(receipt.getId()).getStatus()).isEqualTo(EntryStatus.REVERSED);
        // Омбордан чиқим тескари қайтарилди - қолдиқ аслига тушди
        assertThat(inventoryService.quantityOnHand(invItem.getId(), warehouse.getId()))
                .isEqualByComparingTo("10");
        assertThat(salesReceiptService.get(receipt.getId()).getStatus())
                .isEqualTo(SalesReceipt.Status.REVERSED);
    }

    /**
     * Spec 5-банд: class даромад ва COGS легларига кўчади, банк легида
     * (назорат сатри) class ЙЎҚ (invoice кўзгуси, class-tracking.md).
     */
    @Test
    void class_flowsToRevenueAndCogs_notBankLeg() {
        inventoryService.receive(invItem.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("800"), DATE, "SEED", null, null);
        TxnClass filial = txnClassService.create("Чек филиали", null);

        SalesReceipt receipt = createItemReceipt("2", "2000", filial.getId());

        JournalEntry entry = glEntry(receipt.getId());
        assertBalanced(entry);
        // Даромад ва COGS сатрдан class олади
        assertThat(lineOf(entry, "SALES_OF_PRODUCT_INCOME").getClassId())
                .isEqualTo(filial.getId());
        assertThat(lineOf(entry, "SUPPLIES_MATERIALS_COGS").getClassId())
                .isEqualTo(filial.getId());
        // Банк леги (назорат) class'сиз
        assertThat(lineOf(entry, "CHECKING").getClassId()).isNull();
    }

    /** Бўш сатр рад: камида битта сатр шарт (BR-SR-001). */
    @Test
    void create_noLines_rejectedSr001() {
        assertThatThrownBy(() -> salesReceiptService.create(new SalesReceiptData(
                customer.getId(), bankAccountId, DATE, null, null, false, null, List.of())))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-SR-001"));
    }

    /**
     * Arbitr-052 (008): сатр чегаралари - qty 0/манфий, нарх манфий,
     * INVENTORY item warehouse'сиз - ҳаммаси BR-SR-001.
     */
    @Test
    void create_lineBoundaries_rejectedSr001() {
        assertLineRejected(new BigDecimal("0"), new BigDecimal("100"), warehouse.getId());
        assertLineRejected(new BigDecimal("-1"), new BigDecimal("100"), warehouse.getId());
        assertLineRejected(BigDecimal.ONE, new BigDecimal("-1"), warehouse.getId());
        assertLineRejected(BigDecimal.ONE, new BigDecimal("100"), null); // omborsiz
    }

    /** Битта сатрли чек рад бўлиши (BR-SR-001) - чегара ёрдамчиси. */
    private void assertLineRejected(BigDecimal qty, BigDecimal price, UUID warehouseId) {
        assertThatThrownBy(() -> salesReceiptService.create(new SalesReceiptData(
                customer.getId(), bankAccountId, DATE, null, null, false, null,
                List.of(new LineData(invItem.getId(), warehouseId, qty, price,
                        null, null, null, null, null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-SR-001"));
    }

    /**
     * Arbitr-052 (044): ҚҚС леги - Cr SALES_TAX_PAYABLE ставка кесимида
     * жамланган, entry balanced (солиқсиз happy-path'дан ташқари тармоқ).
     */
    @Test
    void post_withTax_creditsSalesTaxPayable_balanced() {
        inventoryService.receive(invItem.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("800"), DATE, "SEED", null, null);
        UUID qqs12 = taxRateService.all().stream()
                .filter(r -> "QQS12".equals(r.getCode())).findFirst().orElseThrow().getId();
        // qty 1 × 1000 (exclusive) → net 1000, ҚҚС 120, gross 1120
        SalesReceipt receipt = salesReceiptService.create(new SalesReceiptData(
                customer.getId(), bankAccountId, DATE, null, null, false, null,
                List.of(new LineData(invItem.getId(), warehouse.getId(),
                        BigDecimal.ONE, new BigDecimal("1000"),
                        null, null, qqs12, null, null))));

        assertThat(receipt.getTotal()).isEqualByComparingTo("1120");
        JournalEntry entry = glEntry(receipt.getId());
        assertBalanced(entry);
        assertThat(baseOf(entry, "CHECKING", true)).isEqualByComparingTo("1120");
        assertThat(baseOf(entry, "SALES_OF_PRODUCT_INCOME", false)).isEqualByComparingTo("1000");
        assertThat(baseOf(entry, "SALES_TAX_PAYABLE", false)).isEqualByComparingTo("120");
        assertThat(baseOf(entry, "SUPPLIES_MATERIALS_COGS", true)).isEqualByComparingTo("800");
        assertThat(baseOf(entry, "INVENTORY", false)).isEqualByComparingTo("800");
    }

    /**
     * Arbitr-052 (044): чет валюта (USD-doc + USD-bank) - MoneyAllocation
     * penny rounding тармоғи, home base'да balanced (BR-LED-006).
     */
    @Test
    void post_foreignCurrency_usdBank_balanced() {
        inventoryService.receive(invItem.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("800"), DATE, "SEED", null, null);
        UUID usdBank = accountRepository.findByName("Валюта ҳисобварағи (USD)")
                .orElseThrow().getId();
        // USD мижоз + USD банк (валюта мос - BR-SR-002 ўтади), курс 12600
        SalesReceipt receipt = salesReceiptService.create(new SalesReceiptData(
                usdCustomer.getId(), usdBank, DATE, "USD", new BigDecimal("12600"), false, null,
                List.of(new LineData(invItem.getId(), warehouse.getId(),
                        BigDecimal.ONE, new BigDecimal("100"),
                        null, null, null, null, null))));

        assertThat(receipt.getTotal()).isEqualByComparingTo("100"); // USD
        JournalEntry entry = glEntry(receipt.getId());
        assertBalanced(entry); // home base балансланиши penny rounding'дан кейин ҳам сақланади
        // Банк (USD, CHECKING detail type) дебети home base = 100 × 12600 = 1260000
        assertThat(baseOf(entry, "CHECKING", true)).isEqualByComparingTo("1260000");
        assertThat(baseOf(entry, "SALES_OF_PRODUCT_INCOME", false)).isEqualByComparingTo("1260000");
        // COGS home таннархда (100 USD сотилди, лекин таннарх 800 сўм/дона)
        assertThat(baseOf(entry, "SUPPLIES_MATERIALS_COGS", true)).isEqualByComparingTo("800");
    }

    /**
     * Arbitr-076 (Dilnoza-003): unit_price NUMERIC(24,12) - invoice_line
     * кўзгуси. 5+ хона касрли нарх тўлиқ аниқликда сақланади (яхлитлаш
     * фақат кўрсатишда - Fmt; сақланадиган қиймат тўлиқ).
     */
    @Test
    void create_highPrecisionUnitPrice_storedExactly() {
        inventoryService.receive(invItem.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("800"), DATE, "SEED", null, null);
        BigDecimal price = new BigDecimal("2000.123456789012");

        SalesReceipt receipt = salesReceiptService.create(new SalesReceiptData(
                customer.getId(), bankAccountId, DATE, null, null, false, null,
                List.of(new LineData(invItem.getId(), warehouse.getId(),
                        BigDecimal.ONE, price, null, null, null, null, null))));

        // DB'га тушириб контекстни тозалаймиз - қиймат ҳақиқатан устундан
        // қайта ўқилсин (19,4 бўлганда бу ерда 2000.1235 бўлиб қоларди)
        em.flush();
        em.clear();
        SalesReceipt reloaded = salesReceiptService.get(receipt.getId());
        assertThat(reloaded.getLines().get(0).getUnitPrice()).isEqualByComparingTo(price);
    }

    /**
     * Beruniy-035: сатр-циклда item/даромад счёти/омбор ва postGl'даги asset
     * счёти қайта-қайта ўқилмайди (батч Map). N+1 бўлса ҳар қўшимча сатр
     * item+account+warehouse+asset = 4 та қўшимча SELECT берарди; батчда
     * қўшимча сатр фақат inventory issue + JE легини қўшади. Шунинг учун
     * бир хил item такрорланган узунроқ чек сатр бошига КАМ қўшимча query
     * қилади (Statistics қолипи - InventoryAdjustTransferTest услуби).
     */
    @Test
    void create_batchesLineLookups_noNPlus1() {
        // Битта item кўп марта такрорланади - N+1 бўлса ҳар такрор алоҳида
        // item/счёт/омбор ўқирди; катта қолдиқ (сотув етарли)
        inventoryService.receive(invItem.getId(), warehouse.getId(),
                new BigDecimal("1000"), new BigDecimal("10"), DATE, "SEED", null, null);

        long few = countStatementsForCreate(3);
        long many = countStatementsForCreate(9);

        // 6 та қўшимча сатрга тўғри келган ўртача қўшимча query. Ҳар қўшимча
        // сатр омбордан чиқим (issue) + JE легларини қилади - бу N+1 ЭМАС,
        // сатрнинг ҳақиқий домен иши (ўлчанган ~12). N+1 бўлса устига ҳар
        // сатр item+даромад счёти+омбор+postGl asset = 4 қўшимча SELECT
        // қўшиларди (~16). Чегара 14 иккисини ажратади ва lookup регрессиясини
        // (validate/postGl'да қайта get()) ушлайди.
        long perExtraLine = (many - few) / 6;
        assertThat(perExtraLine)
                .as("сатр-цикл item/счёт/омбор lookup'лари батчланган (N+1 йўқ)")
                .isLessThanOrEqualTo(14);
    }

    /** Берилган сонли (бир хил item) сатрли чек яратишдаги JDBC statement сони. */
    private long countStatementsForCreate(int lineCount) {
        List<LineData> lines = new ArrayList<>();
        for (int i = 0; i < lineCount; i++) {
            lines.add(new LineData(invItem.getId(), warehouse.getId(),
                    BigDecimal.ONE, new BigDecimal("100"),
                    null, null, null, null, null));
        }
        em.flush(); // олдинги create'нинг кечиктирилган ёзувлари шу ўлчовга кирмасин
        var stats = emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        boolean wasEnabled = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        stats.clear();
        try {
            salesReceiptService.create(new SalesReceiptData(customer.getId(),
                    bankAccountId, DATE, null, null, false, null, lines));
            em.flush(); // create ичидаги барча query (markPosted UPDATE ҳам) ҳисобга кирсин
            return stats.getPrepareStatementCount();
        } finally {
            stats.setStatisticsEnabled(wasEnabled);
        }
    }
}
