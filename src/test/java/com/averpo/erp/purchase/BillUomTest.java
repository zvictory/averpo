package com.averpo.erp.purchase;

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
import com.averpo.erp.item.domain.Unit;
import com.averpo.erp.item.domain.UnitGroup;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.item.service.ItemService.ItemData;
import com.averpo.erp.item.service.UnitService;
import com.averpo.erp.ledger.domain.JournalEntry;
import com.averpo.erp.ledger.domain.JournalEntryLine;
import com.averpo.erp.ledger.repo.JournalEntryRepository;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.purchase.domain.Bill;
import com.averpo.erp.purchase.domain.BillLine;
import com.averpo.erp.purchase.domain.BillLineType;
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
 * Bill'да UoM интеграцияси (docs/modules/uom.md, 3-туртки): сатр
 * киритилган бирликда, омборга base миқдор (qty × factor snapshot),
 * GL сумма = сатр суммаси (ўзгармайди), аниқ reverse.
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BillUomTest {

    /** Барча тест ҳужжатлар санаси. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 7);

    @Autowired BillService billService;
    @Autowired UnitService unitService;
    @Autowired ItemService itemService;
    @Autowired ContactService contactService;
    @Autowired WarehouseService warehouseService;
    @Autowired InventoryService inventoryService;
    @Autowired AccountService accountService;
    @Autowired JournalEntryRepository entryRepository;

    /** Тест vendor'и. */
    private Contact vendor;

    /** «Оғирлик» гуруҳи. */
    private UnitGroup weight;

    /** Base бирлик - кг (омбор шунда юритилади). */
    private Unit kg;

    /** Кичик бирлик - гр (0.001 кг). */
    private Unit gram;

    /** Base'и кг бўлган INVENTORY item. */
    private Item item;

    /** Асосий омбор (seed). */
    private Warehouse warehouse;

    /** Chart + гуруҳ/бирликлар + item + vendor тайёрланади. */
    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        vendor = contactService.create(ContactType.VENDOR, new ContactData(
                "UoM тест етказувчиси", null, null, null, null, null,
                null, null, null, null, null));
        weight = unitService.createGroup("Оғирлик (bill тест)");
        kg = unitService.create("кг (bill тест)", weight.getId(), null, true);
        gram = unitService.create("гр (bill тест)", weight.getId(),
                new BigDecimal("0.001"), false);
        ItemService.DefaultAccounts defaults = itemService.defaultsFor(ItemType.INVENTORY);
        item = itemService.create(ItemType.INVENTORY, new ItemData(
                "UoM тест товари", null, null, kg.getId(), null, null,
                defaults.income(), null, null, defaults.expense(),
                defaults.inventoryAsset(), null, gram.getId(), null));
        warehouse = warehouseService.all().stream()
                .filter(w -> "Асосий омбор".equals(w.getName()))
                .findFirst().orElseThrow();
    }

    /** Бирликли битта ITEM сатрли bill маълумоти. */
    private BillData data(BigDecimal qty, BigDecimal price, UUID unitId) {
        return new BillData(vendor.getId(), null, DATE, null, null, null, null,
                List.of(new LineData(BillLineType.ITEM, item.getId(), warehouse.getId(),
                        qty, price, null, null, null, unitId, null)));
    }

    /** Фаол GL ёзувини топади. */
    private JournalEntry glEntry(UUID billId) {
        return entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                BillService.SOURCE_MODULE, billId).orElseThrow();
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
    void post_gramLine_receivesBaseKg_glUnchanged() {
        // 2500 гр × 4 = 10 000 (сумма киритилган бирликда)
        Bill bill = billService.post(billService.createDraft(
                data(new BigDecimal("2500"), new BigDecimal("4"), gram.getId())).getId());

        // Сатрда snapshot: киритилган бирлик ва factor'и
        BillLine line = billService.getWithLines(bill.getId()).getLines().get(0);
        assertThat(line.getUnitId()).isEqualTo(gram.getId());
        assertThat(line.getUnitFactor()).isEqualByComparingTo("0.001");
        assertThat(line.getAmount()).isEqualByComparingTo("10000");

        // Омборда BASE бирликда: 2.5 кг, қиймати 10 000 (GL билан тенг)
        assertThat(inventoryService.quantityOnHand(item.getId(), warehouse.getId()))
                .isEqualByComparingTo("2.5");
        List<StockMovement> movements =
                inventoryService.byReference(BillService.SOURCE_MODULE, bill.getId());
        assertThat(movements).hasSize(1);
        assertThat(movements.get(0).getQuantity()).isEqualByComparingTo("2.5");
        assertThat(movements.get(0).getTotalCost()).isEqualByComparingTo("10000");
        assertThat(debitBase(glEntry(bill.getId()), "INVENTORY"))
                .isEqualByComparingTo("10000");
        assertThat(bill.getBalanceDue()).isEqualByComparingTo("10000");
    }

    @Test
    void post_afterCatalogFactorChange_usesDraftSnapshot() {
        Bill draft = billService.createDraft(
                data(new BigDecimal("2500"), new BigDecimal("4"), gram.getId()));
        // Каталогда factor ўзгарди - draft'даги snapshot ўзгармаслиги керак
        unitService.update(gram.getId(), gram.getName(), true,
                weight.getId(), new BigDecimal("0.002"), false);

        Bill bill = billService.post(draft.getId());

        assertThat(billService.getWithLines(bill.getId()).getLines().get(0).getUnitFactor())
                .isEqualByComparingTo("0.001");
        assertThat(inventoryService.quantityOnHand(item.getId(), warehouse.getId()))
                .isEqualByComparingTo("2.5");
    }

    @Test
    void post_baseUnitSelected_sameAsLegacy() {
        // Base бирликнинг ўзи танланса - snapshot сақланмайди (factor 1)
        Bill bill = billService.post(billService.createDraft(
                data(new BigDecimal("3"), new BigDecimal("5000"), kg.getId())).getId());

        BillLine line = billService.getWithLines(bill.getId()).getLines().get(0);
        assertThat(line.getUnitFactor()).isNull();
        assertThat(inventoryService.quantityOnHand(item.getId(), warehouse.getId()))
                .isEqualByComparingTo("3");
    }

    @Test
    void reverse_restoresStock_exactly() {
        Bill bill = billService.post(billService.createDraft(
                data(new BigDecimal("2500"), new BigDecimal("4"), gram.getId())).getId());
        billService.reverse(bill.getId(), DATE, "UoM сторно тест");

        assertThat(inventoryService.quantityOnHand(item.getId(), warehouse.getId()))
                .isEqualByComparingTo("0");
    }

    @Test
    void unitValidation_guards() {
        // Бошқа гуруҳдаги (гуруҳсиз) бирлик - BR-UOM-006
        Unit dona = unitService.create("дона (bill тест)", null, null, false);
        assertThatThrownBy(() -> billService.createDraft(
                data(new BigDecimal("5"), new BigDecimal("100"), dona.getId())))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-UOM-006"));

        // Base миқдор нолга юмалоқланади - BR-BILL-003
        assertThatThrownBy(() -> billService.createDraft(
                data(new BigDecimal("0.0001"), new BigDecimal("100"), gram.getId())))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-BILL-003"));
    }
}
