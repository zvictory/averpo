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
import com.averpo.erp.item.domain.Unit;
import com.averpo.erp.item.domain.UnitGroup;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.item.service.ItemService.ItemData;
import com.averpo.erp.item.service.UnitService;
import com.averpo.erp.ledger.domain.JournalEntry;
import com.averpo.erp.ledger.domain.JournalEntryLine;
import com.averpo.erp.ledger.repo.JournalEntryRepository;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.sales.domain.Invoice;
import com.averpo.erp.sales.domain.InvoiceLine;
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
 * Invoice'да UoM интеграцияси (docs/modules/uom.md, 4-туртки,
 * BillUomTest кўзгуси): сатр киритилган бирликда, омбордан чиқим base
 * миқдорда (qty × factor snapshot), COGS valuation'дан ўзгармай келади,
 * SERVICE сатрда бирлик фақат ҳужжатда (омборга тегмайди).
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InvoiceUomTest {

    /** Барча тест ҳужжатлар санаси. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 7);

    @Autowired InvoiceService invoiceService;
    @Autowired UnitService unitService;
    @Autowired ItemService itemService;
    @Autowired ContactService contactService;
    @Autowired WarehouseService warehouseService;
    @Autowired InventoryService inventoryService;
    @Autowired AccountService accountService;
    @Autowired JournalEntryRepository entryRepository;

    /** Тест мижози. */
    private Contact customer;

    /** «Оғирлик» гуруҳи. */
    private UnitGroup weight;

    /** Base бирлик - кг. */
    private Unit kg;

    /** Кичик бирлик - гр (0.001 кг). */
    private Unit gram;

    /** Base'и кг, сотув бирлиги гр бўлган INVENTORY item. */
    private Item item;

    /** Асосий омбор (seed). */
    private Warehouse warehouse;

    /** Chart + гуруҳ/бирликлар + item (10 кг захира) + мижоз тайёрланади. */
    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        customer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "UoM тест мижози", null, null, null, null, null,
                null, null, null, null, null));
        weight = unitService.createGroup("Оғирлик (invoice тест)");
        kg = unitService.create("кг (invoice тест)", weight.getId(), null, true);
        gram = unitService.create("гр (invoice тест)", weight.getId(),
                new BigDecimal("0.001"), false);
        ItemService.DefaultAccounts defaults = itemService.defaultsFor(ItemType.INVENTORY);
        item = itemService.create(ItemType.INVENTORY, new ItemData(
                "UoM сотув товари", null, null, kg.getId(), null, null,
                defaults.income(), null, null, defaults.expense(),
                defaults.inventoryAsset(), null, null, gram.getId()));
        warehouse = warehouseService.all().stream()
                .filter(w -> "Асосий омбор".equals(w.getName()))
                .findFirst().orElseThrow();
        // Захира BASE бирликда: 10 кг × 4 000
        inventoryService.receive(item.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("4000"), DATE, "TEST", null, null);
    }

    /** Бирликли битта сатрли invoice маълумоти. */
    private InvoiceData data(UUID itemId, BigDecimal qty, BigDecimal price, UUID unitId) {
        return new InvoiceData(customer.getId(), DATE, null, null, null, null,
                List.of(new LineData(itemId, warehouse.getId(), qty, price,
                        null, null, unitId)));
    }

    /** Фаол GL ёзувини топади. */
    private JournalEntry glEntry(UUID invoiceId) {
        return entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                InvoiceService.SOURCE_MODULE, invoiceId).orElseThrow();
    }

    /** Detail type бўйича дебет base йиғиндиси. */
    private BigDecimal debitBase(JournalEntry entry, String detailType) {
        BigDecimal sum = BigDecimal.ZERO;
        for (JournalEntryLine line : entry.getLines()) {
            if (line.getDebit() != null
                    && line.getAccount().getDetailType().name().equals(detailType)) {
                sum = sum.add(line.getDebit().getBaseAmount());
            }
        }
        return sum;
    }

    @Test
    void post_gramLine_issuesBaseKg_cogsFromValuation() {
        // 2500 гр × 6 = 15 000 даромад; чиқим 2.5 кг × 4 000 = 10 000 COGS
        Invoice invoice = invoiceService.post(invoiceService.createDraft(
                data(item.getId(), new BigDecimal("2500"), new BigDecimal("6"),
                        gram.getId())).getId());

        InvoiceLine line = invoiceService.getWithLines(invoice.getId()).getLines().get(0);
        assertThat(line.getUnitId()).isEqualTo(gram.getId());
        assertThat(line.getUnitFactor()).isEqualByComparingTo("0.001");
        assertThat(line.getAmount()).isEqualByComparingTo("15000");

        assertThat(inventoryService.quantityOnHand(item.getId(), warehouse.getId()))
                .isEqualByComparingTo("7.5");
        JournalEntry entry = glEntry(invoice.getId());
        assertThat(debitBase(entry, "ACCOUNTS_RECEIVABLE")).isEqualByComparingTo("15000");
        assertThat(debitBase(entry, "SUPPLIES_MATERIALS_COGS")).isEqualByComparingTo("10000");
    }

    @Test
    void reverse_restoresStock_exactly() {
        Invoice invoice = invoiceService.post(invoiceService.createDraft(
                data(item.getId(), new BigDecimal("2500"), new BigDecimal("6"),
                        gram.getId())).getId());
        invoiceService.reverse(invoice.getId(), DATE, "UoM сторно тест");

        assertThat(inventoryService.quantityOnHand(item.getId(), warehouse.getId()))
                .isEqualByComparingTo("10");
    }

    @Test
    void post_serviceItem_unitIsDocumentOnly() {
        // SERVICE item: соат base, кун = 8 соат - омборга тегмайди
        UnitGroup time = unitService.createGroup("Вақт (invoice тест)");
        Unit hour = unitService.create("соат (invoice тест)", time.getId(), null, true);
        Unit day = unitService.create("кун (invoice тест)", time.getId(),
                new BigDecimal("8"), false);
        ItemService.DefaultAccounts defaults = itemService.defaultsFor(ItemType.SERVICE);
        Item service = itemService.create(ItemType.SERVICE, new ItemData(
                "UoM хизмати", null, null, hour.getId(), null, null,
                defaults.income(), null, null, defaults.expense(),
                null, null, null, day.getId()));

        Invoice invoice = invoiceService.post(invoiceService.createDraft(
                new InvoiceData(customer.getId(), DATE, null, null, null, null,
                        List.of(new LineData(service.getId(), null,
                                new BigDecimal("2"), new BigDecimal("100000"),
                                null, null, day.getId())))).getId());

        InvoiceLine line = invoiceService.getWithLines(invoice.getId()).getLines().get(0);
        assertThat(line.getUnitFactor()).isEqualByComparingTo("8");
        assertThat(line.getAmount()).isEqualByComparingTo("200000");
        assertThat(debitBase(glEntry(invoice.getId()), "ACCOUNTS_RECEIVABLE"))
                .isEqualByComparingTo("200000");
    }

    @Test
    void unitValidation_guards() {
        // Бошқа гуруҳдаги (гуруҳсиз) бирлик - BR-UOM-006
        Unit dona = unitService.create("дона (invoice тест)", null, null, false);
        assertThatThrownBy(() -> invoiceService.createDraft(
                data(item.getId(), new BigDecimal("5"), new BigDecimal("100"), dona.getId())))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-UOM-006"));

        // ITEM сатрида base миқдор нолга юмалоқланади - BR-SINV-003
        assertThatThrownBy(() -> invoiceService.createDraft(
                data(item.getId(), new BigDecimal("0.0001"), new BigDecimal("100"),
                        gram.getId())))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-SINV-003"));
    }
}
