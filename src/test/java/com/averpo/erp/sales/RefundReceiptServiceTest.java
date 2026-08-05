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
import com.averpo.erp.sales.domain.CreditMemo;
import com.averpo.erp.sales.domain.Invoice;
import com.averpo.erp.sales.domain.RefundReceipt;
import com.averpo.erp.sales.service.CreditMemoService;
import com.averpo.erp.sales.service.InvoiceService;
import com.averpo.erp.sales.service.RefundReceiptService;
import com.averpo.erp.sales.service.RefundReceiptService.LineData;
import com.averpo.erp.sales.service.RefundReceiptService.RefundReceiptData;
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
 * RefundReceipt тестлари (docs/modules/returns.md «Тестлар» 5 ва 8
 * бандларининг RR қисми; 9-банд смок ScreenSmokeTest'да). CreditMemo
 * кўзгуси - фарқи фақат Cr банк экани тестда ҳам кўринади. Ҳар
 * posting'да debit == credit (ТЕМИР ҚОИДА №7).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RefundReceiptServiceTest {

    /** Барча тест ҳужжатлар санаси. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 8);

    @Autowired RefundReceiptService refundReceiptService;
    @Autowired CreditMemoService creditMemoService;
    @Autowired InvoiceService invoiceService;
    @Autowired InventoryService inventoryService;
    @Autowired WarehouseService warehouseService;
    @Autowired ContactService contactService;
    @Autowired ItemService itemService;
    @Autowired AccountService accountService;
    @Autowired AccountRepository accountRepository;
    @Autowired JournalEntryRepository entryRepository;

    private Contact customer;

    /** USD валютали мижоз (DEC-087): чет валюта ҳужжатлари шунга ёзилади. */
    private Contact usdCustomer;

    private Item invItem;
    private Warehouse warehouse;

    /** Default chart'даги банк счёти (CHECKING, home валюта). */
    private UUID bankAccountId;

    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        customer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Пул қайтариладиган мижоз", null, null, null, null, null,
                null, null, null, null, null));
        usdCustomer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Пул қайтариладиган USD мижоз", null, null, null, null, null,
                "USD", null, null, null, null));
        ItemService.DefaultAccounts inv = itemService.defaultsFor(ItemType.INVENTORY);
        invItem = itemService.create(ItemType.INVENTORY, new ItemData(
                "Пулга қайтадиган товар", null, null, null, null, null,
                inv.income(), null, null, inv.expense(), inv.inventoryAsset(), null));
        warehouse = warehouseService.all().stream()
                .filter(w -> "Асосий омбор".equals(w.getName()))
                .findFirst().orElseThrow();
        bankAccountId = accountRepository.findByName("Банк ҳисобварағи").orElseThrow().getId();
    }

    /** Манба бўйича фаол GL ёзуви. */
    private JournalEntry glEntry(UUID docId) {
        return entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                RefundReceiptService.SOURCE_MODULE, docId).orElseThrow();
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

    /** ITEM сатрли чек (home валютада, банк счётига). */
    private RefundReceipt createItemReceipt(String qty, String price) {
        return refundReceiptService.create(new RefundReceiptData(customer.getId(), null,
                bankAccountId, DATE, null, null, false, null,
                List.of(new LineData(invItem.getId(), warehouse.getId(),
                        new BigDecimal(qty), new BigDecimal(price),
                        null, null, null, null, null))));
    }

    /**
     * DEC-069 (IFRS-019) - карта сценарийси айнан: 10 доналик
     * invoice'га CM 6 дона POSTED бўлгач RR 6 дона РАД (кумулятив
     * 12 > 10 - ҳовуз CM+RR умумий), лимитга айнан тенг 4 дона ЎТАДИ,
     * кейин яна 1 дона ҳам сиғмайди. Аввал ҳар ҳужжат алоҳида
     * текширилиб қайтим таннархи икки марта ёзиларди.
     */
    @Test
    void create_cumulativeQuantities_priorCreditMemoCounted() {
        inventoryService.receive(invItem.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("800"), DATE, "SEED", null, null);
        Invoice original = invoiceService.post(invoiceService.createDraft(
                new InvoiceService.InvoiceData(customer.getId(), DATE, null, null,
                        null, null, false, List.of(new InvoiceService.LineData(
                                invItem.getId(), warehouse.getId(), new BigDecimal("10"),
                                new BigDecimal("2000"), null, null)))).getId());

        // CM 6 дона POSTED - ҳовузнинг кредит томони
        CreditMemo memo = creditMemoService.create(new CreditMemoService.CreditMemoData(
                customer.getId(), original.getId(), DATE, null, null, false, null,
                List.of(new CreditMemoService.LineData(invItem.getId(), warehouse.getId(),
                        new BigDecimal("6"), new BigDecimal("2000"),
                        null, null, null, null, null))));
        assertBalanced(entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                CreditMemoService.SOURCE_MODULE, memo.getId()).orElseThrow());

        // RR 6 дона РАД - кумулятив 6 + 6 = 12 > 10
        assertThatThrownBy(() -> createLinkedReceipt(original, "6"))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RET-006"));

        // Лимитга айнан тенг: 6 + 4 = 10 - ЎТАДИ, GL балансланган
        RefundReceipt partial = createLinkedReceipt(original, "4");
        assertBalanced(glEntry(partial.getId()));

        // Тўлиқ қайтарилган - энди 1 дона ҳам сиғмайди
        assertThatThrownBy(() -> createLinkedReceipt(original, "1"))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RET-006"));
    }

    /** Ҳаволали inventory чеки (кумулятив тест ёрдамчиси). */
    private RefundReceipt createLinkedReceipt(Invoice original, String qty) {
        return refundReceiptService.create(new RefundReceiptData(customer.getId(),
                original.getId(), bankAccountId, DATE, null, null, false, null,
                List.of(new LineData(invItem.getId(), warehouse.getId(),
                        new BigDecimal(qty), new BigDecimal("2000"),
                        null, null, null, null, null))));
    }

    /**
     * Spec 5-банд: Dr даромад / Cr БАНК (AR умуман қатнашмайди) +
     * inventory сатрда IN ва Dr INVENTORY / Cr COGS.
     */
    @Test
    void post_creditsBank_arUntouched() {
        // Омборга 10 @ 800 кирган - қайтим жорий AVCO (800) да киради
        inventoryService.receive(invItem.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("800"), DATE, "SEED", null, null);

        RefundReceipt receipt = createItemReceipt("2", "2000");

        assertThat(receipt.getStatus()).isEqualTo(RefundReceipt.Status.POSTED);
        assertThat(receipt.getRrNumber()).startsWith("RR-2026-");
        assertThat(receipt.getTotal()).isEqualByComparingTo("4000");

        JournalEntry entry = glEntry(receipt.getId());
        assertBalanced(entry);
        // Dr даромад (қайтади) / Cr банк - пул дарҳол чиқади
        assertThat(baseOf(entry, "SALES_OF_PRODUCT_INCOME", true)).isEqualByComparingTo("4000");
        assertThat(baseOf(entry, "CHECKING", false)).isEqualByComparingTo("4000");
        // AR умуман қатнашмайди - на дебет, на кредит
        assertThat(baseOf(entry, "ACCOUNTS_RECEIVABLE", true)).isEqualByComparingTo("0");
        assertThat(baseOf(entry, "ACCOUNTS_RECEIVABLE", false)).isEqualByComparingTo("0");
        // Inventory қайтими: Dr INVENTORY / Cr COGS - 2 × 800 = 1600
        assertThat(baseOf(entry, "INVENTORY", true)).isEqualByComparingTo("1600");
        assertThat(baseOf(entry, "SUPPLIES_MATERIALS_COGS", false)).isEqualByComparingTo("1600");
        // Омборга кирди: 10 → 12
        assertThat(inventoryService.quantityOnHand(invItem.getId(), warehouse.getId()))
                .isEqualByComparingTo("12");
    }

    /** Пул счёти валютаси ҳужжат валютасига тенг бўлиши шарт (BR-RET-001). */
    @Test
    void create_bankCurrencyMismatch_rejected() {
        // UZS (home) банк счётига USD ҳужжат ёзиб бўлмайди (контакт USD -
        // DEC-087 валюта гарови ўтади, банк мослиги ушлайди)
        assertThatThrownBy(() -> refundReceiptService.create(new RefundReceiptData(
                usdCustomer.getId(), null, bankAccountId, DATE, "USD",
                new BigDecimal("12600"), false, null,
                List.of(new LineData(invItem.getId(), warehouse.getId(),
                        BigDecimal.ONE, new BigDecimal("100"),
                        null, null, null, null, null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RET-001"));
    }

    /** DEC-087 (BR-RET-008): RR валютаси контактга мос бўлмаса рад. */
    @Test
    void currency_mismatchRejected() {
        // home контактга USD ҳужжат - валюта контактдан келиши шарт
        assertThatThrownBy(() -> refundReceiptService.create(new RefundReceiptData(
                customer.getId(), null, bankAccountId, DATE, "USD",
                new BigDecimal("12600"), false, null,
                List.of(new LineData(invItem.getId(), warehouse.getId(),
                        BigDecimal.ONE, new BigDecimal("100"),
                        null, null, null, null, null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RET-008"));
    }

    /**
     * DEC-052 (007): create сатр валидация чегаралари - миқдор мусбат,
     * нарх манфий эмас (BR-RET-001), INVENTORY сатрида омбор шарт
     * (BR-RET-002). Аввал фақат тўлов счёти валютаси текширилар эди.
     */
    @Test
    void create_lineBoundaries_rejectedRet001And002() {
        // BR-RET-001: миқдор 0, миқдор манфий, нарх манфий - ҳар бири рад
        assertRrCreateRejected("BR-RET-001", new LineData(invItem.getId(), warehouse.getId(),
                BigDecimal.ZERO, new BigDecimal("100"), null, null, null, null, null));
        assertRrCreateRejected("BR-RET-001", new LineData(invItem.getId(), warehouse.getId(),
                new BigDecimal("-1"), new BigDecimal("100"), null, null, null, null, null));
        assertRrCreateRejected("BR-RET-001", new LineData(invItem.getId(), warehouse.getId(),
                BigDecimal.ONE, new BigDecimal("-5"), null, null, null, null, null));
        // BR-RET-002: INVENTORY item сатрида омбор танланмаса рад
        assertRrCreateRejected("BR-RET-002", new LineData(invItem.getId(), null,
                BigDecimal.ONE, new BigDecimal("100"), null, null, null, null, null));
    }

    /** RR create'ни битта сатр билан чақириб, кутилган BR кодини тасдиқлайди. */
    private void assertRrCreateRejected(String code, LineData line) {
        assertThatThrownBy(() -> refundReceiptService.create(new RefundReceiptData(
                customer.getId(), null, bankAccountId, DATE, null, null, false, null,
                List.of(line))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo(code));
    }

    /** Spec 8-банд (RR): reverse - тўлиқ GL сторно + омбор кирими бекор бўлади. */
    @Test
    void reverse_stornosGl_andReturnsStock() {
        inventoryService.receive(invItem.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("800"), DATE, "SEED", null, null);
        RefundReceipt receipt = createItemReceipt("2", "2000");
        assertThat(inventoryService.quantityOnHand(invItem.getId(), warehouse.getId()))
                .isEqualByComparingTo("12");

        refundReceiptService.reverse(receipt.getId(), DATE, "қайтариш бекор");

        assertThat(glEntry(receipt.getId()).getStatus()).isEqualTo(EntryStatus.REVERSED);
        // Омбор кирими тескари қайтарилди - қолдиқ аслига тушди
        assertThat(inventoryService.quantityOnHand(invItem.getId(), warehouse.getId()))
                .isEqualByComparingTo("10");
        assertThat(refundReceiptService.get(receipt.getId()).getStatus())
                .isEqualTo(RefundReceipt.Status.REVERSED);
    }
}
