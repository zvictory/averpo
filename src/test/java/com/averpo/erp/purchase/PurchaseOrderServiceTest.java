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
import com.averpo.erp.ledger.repo.JournalEntryRepository;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.purchase.domain.Bill;
import com.averpo.erp.purchase.domain.BillLineType;
import com.averpo.erp.purchase.domain.PurchaseOrder;
import com.averpo.erp.purchase.domain.PurchaseOrderStatus;
import com.averpo.erp.purchase.service.BillService;
import com.averpo.erp.purchase.service.PurchaseOrderService;
import com.averpo.erp.purchase.service.PurchaseOrderService.LineData;
import com.averpo.erp.purchase.service.PurchaseOrderService.PurchaseOrderData;
import com.averpo.erp.purchase.web.BillForm;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PurchaseOrder тестлари (docs/modules/estimates-po.md «Тестлар» 4-банд
 * - Estimate тестларининг кўзгуси): GL'сизлик (journal_entry сони
 * ЎЗГАРМАЙДИ), OPEN→CLOSED оқими, айлантириш/linked ҳимоялари ва
 * prefill мослиги.
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PurchaseOrderServiceTest {

    /** Барча тест ҳужжатлар санаси. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 8);

    @Autowired PurchaseOrderService purchaseOrderService;
    @Autowired BillService billService;
    @Autowired ContactService contactService;
    @Autowired ItemService itemService;
    @Autowired WarehouseService warehouseService;
    @Autowired AccountService accountService;
    @Autowired JournalEntryRepository entryRepository;

    /** Тест таъминотчиси. */
    private Contact vendor;

    /** Буюртма қилинадиган товар (INVENTORY). */
    private Item item;

    /** Асосий омбор (seed) - айлантириш draft'ида керак. */
    private Warehouse warehouse;

    /** Chart + vendor + item + омбор тайёрланади (rollback тозалайди). */
    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        vendor = contactService.create(ContactType.VENDOR, new ContactData(
                "PO тест таъминотчиси", null, null, null, null, null,
                null, null, null, null, null));
        ItemService.DefaultAccounts defaults = itemService.defaultsFor(ItemType.INVENTORY);
        item = itemService.create(ItemType.INVENTORY, new ItemData(
                "PO тест товари", null, null, null, null, null,
                defaults.income(), null, null, defaults.expense(),
                defaults.inventoryAsset(), null));
        warehouse = warehouseService.all().stream()
                .filter(w -> "Асосий омбор".equals(w.getName()))
                .findFirst().orElseThrow();
    }

    /** Home валютадаги оддий буюртма маълумоти. */
    private PurchaseOrderData data(List<LineData> lines) {
        return new PurchaseOrderData(vendor.getId(), DATE, DATE.plusDays(14),
                null, null, "буюртма", false, lines);
    }

    /** Item сатри ясагич (ставкасиз). */
    private LineData line(BigDecimal qty, BigDecimal price) {
        return new LineData(item.getId(), qty, price, null, null, null);
    }

    /**
     * Arbitr-052 (043): BR-PO-001 валидация чегаралари - таъминотчисиз,
     * сатрсиз, миқдор 0/манфий, нарх манфий. (Финдинг: код тестда йўқ эди.)
     */
    /** Arbitr-087 (BR-PO-004): валюта контактдан derive + мослик гарови. */
    @Test
    void currency_derivedFromContact_mismatchRejected() {
        // Бўш currency - server USD контактдан ўзи олади
        Contact usdVendor = contactService.create(ContactType.VENDOR, new ContactData(
                "PO USD таъминотчиси", null, null, null, null, null,
                "USD", null, null, null, null));
        PurchaseOrder derived = purchaseOrderService.create(new PurchaseOrderData(
                usdVendor.getId(), DATE, DATE.plusDays(14), null, new BigDecimal("12600"),
                "буюртма", false, List.of(line(BigDecimal.ONE, new BigDecimal("100")))));
        assertThat(derived.getCurrency().getCode()).isEqualTo("USD");

        // Клиент қиймати контактга (home) зид - BR-PO-004 рад
        assertThatThrownBy(() -> purchaseOrderService.create(new PurchaseOrderData(
                vendor.getId(), DATE, DATE.plusDays(14), "USD", new BigDecimal("12600"),
                "x", false, List.of(line(BigDecimal.ONE, new BigDecimal("100"))))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PO-004"));
    }

    @Test
    void create_validationBoundaries_rejectedPo001() {
        // Таъминотчисиз
        assertPoRejected(new PurchaseOrderData(null, DATE, DATE.plusDays(14),
                null, null, "x", false,
                List.of(line(BigDecimal.ONE, new BigDecimal("100")))));
        // Камида битта сатр
        assertPoRejected(data(List.of()));
        // Миқдор 0 / манфий; нарх манфий
        assertPoRejected(data(List.of(line(BigDecimal.ZERO, new BigDecimal("100")))));
        assertPoRejected(data(List.of(line(new BigDecimal("-1"), new BigDecimal("100")))));
        assertPoRejected(data(List.of(line(BigDecimal.ONE, new BigDecimal("-1")))));
    }

    /** BR-PO-001 билан рад бўлишини тасдиқлайди (код айнан). */
    private void assertPoRejected(PurchaseOrderData data) {
        assertThatThrownBy(() -> purchaseOrderService.create(data))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PO-001"));
    }

    @Test
    void list_pagination_secondPageSlice_stableSort_statusFilter() {
        // Beruniy-perf1 2-босқич retrofit: size+1 буюртма - 2-саҳифада
        // биттагина қолади; саналар ҳар хил - тартиб детерминистик
        PurchaseOrder oldest = null;
        PurchaseOrder newest = null;
        for (int i = PurchaseOrderService.LIST_PAGE_SIZE; i >= 0; i--) {
            PurchaseOrder po = purchaseOrderService.create(new PurchaseOrderData(
                    vendor.getId(), DATE.minusDays(i), DATE.plusDays(14), null, null,
                    "буюртма", false, List.of(line(BigDecimal.ONE, new BigDecimal("1000")))));
            if (oldest == null) {
                oldest = po; // биринчи яратилгани энг эски санали
            }
            newest = po;
        }

        var page0 = purchaseOrderService.list(
                new PurchaseOrderService.ListFilter(null, null, null, null, null), 0);
        assertThat(page0.getContent()).hasSize(PurchaseOrderService.LIST_PAGE_SIZE);
        assertThat(page0.getTotalElements()).isEqualTo(PurchaseOrderService.LIST_PAGE_SIZE + 1);
        assertThat(page0.getTotalPages()).isEqualTo(2);
        // Барқарор тартиб: энг янги санали биринчи (аввалги ORDER BY)
        assertThat(page0.getContent().get(0).getId()).isEqualTo(newest.getId());

        var page1 = purchaseOrderService.list(
                new PurchaseOrderService.ListFilter(null, null, null, null, null), 1);
        assertThat(page1.getContent()).hasSize(1);
        assertThat(page1.getContent().get(0).getId()).isEqualTo(oldest.getId());
        assertThat(page1.hasNext()).isFalse();

        // Статус филтри ҳам саҳифаланади: ҳаммаси OPEN - жами size+1
        assertThat(purchaseOrderService.list(new PurchaseOrderService.ListFilter(
                        null, null, PurchaseOrderStatus.OPEN, null, null), 0).getTotalElements())
                .isEqualTo(PurchaseOrderService.LIST_PAGE_SIZE + 1);
        // CLOSED буюртма йўқ - бўш саҳифа
        assertThat(purchaseOrderService.list(new PurchaseOrderService.ListFilter(
                null, null, PurchaseOrderStatus.CLOSED, null, null), 0).getTotalElements()).isZero();
    }

    /** Spec 4-банд (1-кўзгу): бутун ҳаёт цикли GL'га ҲЕЧ НАРСА ёзмайди. */
    @Test
    void lifecycle_createUpdateStatusDelete_journalEntryCountUnchanged() {
        long entriesBefore = entryRepository.count();

        PurchaseOrder po = purchaseOrderService.create(data(List.of(
                line(new BigDecimal("10"), new BigDecimal("2500")))));
        assertThat(po.getPoNumber()).startsWith("PO-2026-");
        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.OPEN);
        assertThat(po.getTotal()).isEqualByComparingTo("25000");

        purchaseOrderService.update(po.getId(), data(List.of(
                line(new BigDecimal("5"), new BigDecimal("2500")))));
        assertThat(purchaseOrderService.getWithLines(po.getId()).getTotal())
                .isEqualByComparingTo("12500");

        purchaseOrderService.changeStatus(po.getId(), PurchaseOrderStatus.CLOSED);
        purchaseOrderService.delete(po.getId());

        // АСОСИЙ ASSERT (spec): journal_entry сони ўзгармади - GL'сиз ҳужжат
        assertThat(entryRepository.count()).isEqualTo(entriesBefore);
    }

    /** Spec 4-банд (2-кўзгу): OPEN→CLOSED оқими ва BR-PO-002 ҳимоялари. */
    @Test
    void statusFlow_openClosed_guards() {
        PurchaseOrder po = purchaseOrderService.create(data(List.of(
                line(BigDecimal.ONE, new BigDecimal("1000")))));

        purchaseOrderService.changeStatus(po.getId(), PurchaseOrderStatus.CLOSED);
        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.CLOSED);

        // CLOSED таҳрирланмайди ва айлантирилмайди (BR-PO-002)
        assertThatThrownBy(() -> purchaseOrderService.update(po.getId(),
                data(List.of(line(BigDecimal.ONE, new BigDecimal("2000"))))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PO-002"));
        assertThatThrownBy(() -> purchaseOrderService.requireConvertible(po.getId()))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PO-002"));

        // Linked'сиз CLOSED → OPEN қайта очилади
        purchaseOrderService.changeStatus(po.getId(), PurchaseOrderStatus.OPEN);
        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.OPEN);
    }

    /** Spec 4-банд (3-кўзгу): prefill мослиги ва айлантириш/linked ҳимоялари. */
    @Test
    void convert_prefillMatches_marksClosedLinked_thenGuards() {
        PurchaseOrder po = purchaseOrderService.create(data(List.of(
                line(new BigDecimal("4"), new BigDecimal("3000")))));

        // Prefill (BillController оқимидаги хаританинг ўзи): таъминотчи/
        // валюта/сатрлар (ITEM тури билан) айнан кўчади
        BillForm form = BillForm.fromPurchaseOrder(
                purchaseOrderService.requireConvertible(po.getId()), "UZS");
        assertThat(form.getPurchaseOrderId()).isEqualTo(po.getId().toString());
        assertThat(form.getVendorId()).isEqualTo(vendor.getId().toString());
        assertThat(form.getCurrency()).isEqualTo("UZS");
        assertThat(form.getLines()).hasSize(1);
        assertThat(form.getLines().get(0).getType()).isEqualTo("ITEM");
        assertThat(form.getLines().get(0).getItemId()).isEqualTo(item.getId().toString());
        assertThat(form.getLines().get(0).getQuantity()).isEqualTo("4");
        assertThat(form.getLines().get(0).getUnitPrice()).isEqualTo("3000");

        // «Фойдаланувчи сақлади»: оддий bill draft оқими (омборни формада
        // танлади), кейин mark
        Bill bill = billService.createDraft(new BillService.BillData(
                vendor.getId(), null, DATE, null, null, null, null,
                List.of(new BillService.LineData(BillLineType.ITEM, item.getId(),
                        warehouse.getId(), new BigDecimal("4"), new BigDecimal("3000"),
                        null, null, null))));
        purchaseOrderService.markConverted(po.getId(), bill.getId());

        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.CLOSED);
        assertThat(po.getBillId()).isEqualTo(bill.getId());
        // Bill кўришидаги «Буюртмадан» белгиси манбаи
        assertThat(purchaseOrderService.findByBillId(bill.getId())).contains(po);

        // Linked ўчирилмайди, қайта айлантирилмайди, қайта очилмайди
        assertThatThrownBy(() -> purchaseOrderService.delete(po.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PO-003"));
        assertThatThrownBy(() -> purchaseOrderService.markConverted(po.getId(),
                bill.getId()))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PO-003"));
        assertThatThrownBy(() -> purchaseOrderService.changeStatus(po.getId(),
                PurchaseOrderStatus.OPEN))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-PO-002"));
    }
}
