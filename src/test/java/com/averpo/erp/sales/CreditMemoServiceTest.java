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
import com.averpo.erp.sales.domain.CreditApplication;
import com.averpo.erp.sales.domain.CreditMemo;
import com.averpo.erp.sales.domain.Invoice;
import com.averpo.erp.sales.domain.RefundReceipt;
import com.averpo.erp.sales.service.CreditMemoService;
import com.averpo.erp.sales.service.CreditMemoService.CreditMemoData;
import com.averpo.erp.sales.service.CreditMemoService.LineData;
import com.averpo.erp.sales.service.InvoiceService;
import com.averpo.erp.sales.service.InvoiceService.InvoiceData;
import com.averpo.erp.sales.service.RefundReceiptService;
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
 * CreditMemo тестлари (docs/modules/returns.md «Тестлар» 1-4, 7-8
 * бандлари; 9-банд смок ScreenSmokeTest'да, VendorCredit бандлари
 * 15-турткида). Ҳар posting'да debit == credit (ТЕМИР ҚОИДА №7).
 *
 * @author Zafar
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CreditMemoServiceTest {

    /** Барча тест ҳужжатлар санаси. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 8);

    @Autowired CreditMemoService creditMemoService;
    @Autowired InvoiceService invoiceService;
    @Autowired RefundReceiptService refundReceiptService;
    @Autowired AccountRepository accountRepository;
    @Autowired InventoryService inventoryService;
    @Autowired WarehouseService warehouseService;
    @Autowired ContactService contactService;
    @Autowired ItemService itemService;
    @Autowired AccountService accountService;
    @Autowired JournalEntryRepository entryRepository;
    @Autowired CompanySettingsService settingsService;

    private Contact customer;

    /** USD валютали мижоз (Arbitr-087): чет валюта ҳужжатлари шунга ёзилади. */
    private Contact usdCustomer;

    private Item service;
    private Item invItem;
    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        customer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Қайтариш мижози", null, null, null, null, null,
                null, null, null, null, null));
        usdCustomer = contactService.create(ContactType.CUSTOMER, new ContactData(
                "Қайтариш USD мижози", null, null, null, null, null,
                "USD", null, null, null, null));
        ItemService.DefaultAccounts svc = itemService.defaultsFor(ItemType.SERVICE);
        service = itemService.create(ItemType.SERVICE, new ItemData(
                "Қайтариш хизмати", null, null, null, null, null,
                svc.income(), null, null, svc.expense(), null, null));
        ItemService.DefaultAccounts inv = itemService.defaultsFor(ItemType.INVENTORY);
        invItem = itemService.create(ItemType.INVENTORY, new ItemData(
                "Қайтариш товари", null, null, null, null, null,
                inv.income(), null, null, inv.expense(), inv.inventoryAsset(), null));
        warehouse = warehouseService.all().stream()
                .filter(w -> "Асосий омбор".equals(w.getName()))
                .findFirst().orElseThrow();
    }

    /** Манба бўйича фаол GL ёзуви. */
    private JournalEntry glEntry(String sourceModule, UUID docId) {
        return entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                sourceModule, docId).orElseThrow();
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

    /**
     * SERVICE сатрли invoice post қилади (10 000, солиқсиз). Контакт
     * валютадан танланади (Arbitr-087: ҳужжат валютаси контактдан) -
     * USD ҳужжат usdCustomer'га, home ҳужжат customer'га.
     */
    private Invoice postServiceInvoice(String currency, BigDecimal rate, String price) {
        UUID contactId = "USD".equals(currency) ? usdCustomer.getId() : customer.getId();
        InvoiceData data = new InvoiceData(contactId, DATE, null,
                currency, rate, null, false, List.of(new InvoiceService.LineData(
                        service.getId(), null, BigDecimal.ONE, new BigDecimal(price),
                        null, null)));
        return invoiceService.post(invoiceService.createDraft(data).getId());
    }

    /** SERVICE сатрли кредит-нота (home валютада). */
    private CreditMemo createServiceMemo(String price) {
        return creditMemoService.create(new CreditMemoData(customer.getId(), null,
                DATE, null, null, false, null, List.of(new LineData(
                        service.getId(), null, BigDecimal.ONE, new BigDecimal(price),
                        null, null, null, null, null))));
    }

    @Test
    void list_pagination_secondPageSlice_stableSort() {
        // Beruniy-perf1 2-босқич retrofit: size+1 кредит-нота - 2-саҳифада
        // биттагина қолади; саналар ҳар хил - тартиб детерминистик
        CreditMemo oldest = null;
        CreditMemo newest = null;
        for (int i = CreditMemoService.LIST_PAGE_SIZE; i >= 0; i--) {
            CreditMemo memo = creditMemoService.create(new CreditMemoData(customer.getId(),
                    null, DATE.minusDays(i), null, null, false, null,
                    List.of(new LineData(service.getId(), null, BigDecimal.ONE,
                            new BigDecimal("1000"), null, null, null, null, null))));
            if (oldest == null) {
                oldest = memo; // биринчи яратилгани энг эски санали
            }
            newest = memo;
        }

        var page0 = creditMemoService.list(
                new CreditMemoService.ListFilter(null, null, null, null, null), 0);
        assertThat(page0.getContent()).hasSize(CreditMemoService.LIST_PAGE_SIZE);
        assertThat(page0.getTotalElements()).isEqualTo(CreditMemoService.LIST_PAGE_SIZE + 1);
        assertThat(page0.getTotalPages()).isEqualTo(2);
        // Барқарор тартиб: энг янги санали биринчи (аввалги ORDER BY)
        assertThat(page0.getContent().get(0).getId()).isEqualTo(newest.getId());

        var page1 = creditMemoService.list(
                new CreditMemoService.ListFilter(null, null, null, null, null), 1);
        assertThat(page1.getContent()).hasSize(1);
        assertThat(page1.getContent().get(0).getId()).isEqualTo(oldest.getId());
        assertThat(page1.hasNext()).isFalse();
    }

    /** Spec 1-банд: Dr даромад / Cr AR, inventory сатрда IN + Dr INVENTORY / Cr COGS. */
    @Test
    void post_glMatchesPostingRules_inventoryReturns() {
        // Омборга 10 @ 800 кирган, 5 таси сотилган - қайтариш 2 та
        inventoryService.receive(invItem.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("800"), DATE, "SEED", null, null);
        Invoice original = invoiceService.post(invoiceService.createDraft(
                new InvoiceData(customer.getId(), DATE, null, null, null, null, false,
                        List.of(new InvoiceService.LineData(invItem.getId(),
                                warehouse.getId(), new BigDecimal("5"),
                                new BigDecimal("2000"), null, null)))).getId());

        CreditMemo memo = creditMemoService.create(new CreditMemoData(
                customer.getId(), original.getId(), DATE, null, null, false, null,
                List.of(new LineData(invItem.getId(), warehouse.getId(),
                        new BigDecimal("2"), new BigDecimal("2000"),
                        null, null, null, null, null))));

        assertThat(memo.getStatus()).isEqualTo(CreditMemo.Status.POSTED);
        assertThat(memo.getCmNumber()).startsWith("CM-2026-");
        assertThat(memo.getTotal()).isEqualByComparingTo("4000");

        JournalEntry entry = glEntry(CreditMemoService.SOURCE_MODULE, memo.getId());
        assertBalanced(entry);
        // Dr даромад (қайтади) / Cr AR (мижоз кредити)
        assertThat(baseOf(entry, "SALES_OF_PRODUCT_INCOME", true)).isEqualByComparingTo("4000");
        assertThat(baseOf(entry, "ACCOUNTS_RECEIVABLE", false)).isEqualByComparingTo("4000");
        // Inventory қайтими: Dr INVENTORY / Cr COGS - 2 × 800 = 1600
        assertThat(baseOf(entry, "INVENTORY", true)).isEqualByComparingTo("1600");
        assertThat(baseOf(entry, "SUPPLIES_MATERIALS_COGS", false)).isEqualByComparingTo("1600");
        // StockMovement IN ёзилган - қолдиқ 5 → 7
        assertThat(inventoryService.quantityOnHand(invItem.getId(), warehouse.getId()))
                .isEqualByComparingTo("7");
    }

    /** Spec 2-банд: ҳаволали - асл сотув таннархида; ҳаволасиз - жорий AVCO. */
    @Test
    void returnCost_linkedUsesOriginalSaleCost_unlinkedUsesCurrentAvco() {
        // 10 @ 800 кирим → 5 сотилди (таннарх 800) → 5 @ 1000 кирим:
        // жорий AVCO = (5×800 + 5×1000) / 10 = 900
        inventoryService.receive(invItem.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("800"), DATE, "SEED", null, null);
        Invoice original = invoiceService.post(invoiceService.createDraft(
                new InvoiceData(customer.getId(), DATE, null, null, null, null, false,
                        List.of(new InvoiceService.LineData(invItem.getId(),
                                warehouse.getId(), new BigDecimal("5"),
                                new BigDecimal("2000"), null, null)))).getId());
        inventoryService.receive(invItem.getId(), warehouse.getId(),
                new BigDecimal("5"), new BigDecimal("1000"), DATE, "SEED", null, null);

        // Ҳаволасиз БИРИНЧИ (кирими ўртачани ўзгартирмасидан аввал
        // текширилади): жорий сиёсат - AVCO ўртачаси = 900
        CreditMemo unlinked = creditMemoService.create(new CreditMemoData(
                customer.getId(), null, DATE, null, null, false, null,
                List.of(new LineData(invItem.getId(), warehouse.getId(),
                        BigDecimal.ONE, new BigDecimal("2000"),
                        null, null, null, null, null))));
        StockMovement unlinkedIn = inventoryService.byReference(
                CreditMemoService.SOURCE_MODULE, unlinked.getId()).get(0);
        assertThat(unlinkedIn.getUnitCost()).isEqualByComparingTo("900");

        // Ҳаволали: жорий ўртача ўзгарган бўлса ҳам асл сотув OUT
        // ҳаракатининг таннархи (800) олинади - марж бузилмайди
        CreditMemo linked = creditMemoService.create(new CreditMemoData(
                customer.getId(), original.getId(), DATE, null, null, false, null,
                List.of(new LineData(invItem.getId(), warehouse.getId(),
                        BigDecimal.ONE, new BigDecimal("2000"),
                        null, null, null, null, null))));
        StockMovement linkedIn = inventoryService.byReference(
                CreditMemoService.SOURCE_MODULE, linked.getId()).get(0);
        assertThat(linkedIn.getUnitCost()).isEqualByComparingTo("800");
    }

    /** Spec 3-банд: apply - balance/қолдиқ камаяди; BR-RET-003/004 гаровлари. */
    @Test
    void apply_reducesBalances_guardsOverAndCrossCurrency() {
        Invoice invoice = postServiceInvoice(null, null, "10000");
        CreditMemo memo = createServiceMemo("3000");

        creditMemoService.apply(memo.getId(), invoice.getId(), new BigDecimal("3000"));
        assertThat(invoice.getBalanceDue()).isEqualByComparingTo("7000");
        assertThat(creditMemoService.get(memo.getId()).getOpenBalance())
                .isEqualByComparingTo("0");

        // BR-RET-003: очиқ қолдиқдан ошиқча қўллаш рад
        Invoice second = postServiceInvoice(null, null, "5000");
        assertThatThrownBy(() -> creditMemoService.apply(memo.getId(), second.getId(),
                new BigDecimal("100")))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RET-003"));

        // Кросс-валюта қўллаш рад. Arbitr-087 дан кейин бир контактда икки
        // валютали ҳужжат структуравий имконсиз (валюта контактдан) - USD
        // кредит энди фақат USD контактда бўлади, бошқа контактнинг UZS
        // invoice'ига қўллашда контакт гарови (BR-RET-005) аввал ушлайди;
        // BR-RET-004 тарихий (087 дан аввалги) маълумот учун ҳимоя қатлами
        CreditMemo usdMemo = creditMemoService.create(new CreditMemoData(
                usdCustomer.getId(), null, DATE, "USD", new BigDecimal("12600"),
                false, null, List.of(new LineData(service.getId(), null,
                        BigDecimal.ONE, new BigDecimal("100"),
                        null, null, null, null, null))));
        assertThatThrownBy(() -> creditMemoService.apply(usdMemo.getId(), second.getId(),
                new BigDecimal("50")))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RET-005"));
    }

    /** Arbitr-087 (BR-RET-008): валюта контактдан derive + мослик гарови. */
    @Test
    void currency_derivedFromContact_mismatchRejected() {
        // Бўш currency - server USD контактдан ўзи олади
        CreditMemo derived = creditMemoService.create(new CreditMemoData(
                usdCustomer.getId(), null, DATE, null, new BigDecimal("12600"),
                false, null, List.of(new LineData(service.getId(), null,
                        BigDecimal.ONE, new BigDecimal("100"),
                        null, null, null, null, null))));
        assertThat(derived.getCurrency().getCode()).isEqualTo("USD");

        // Клиент қиймати контактга (home) зид - BR-RET-008 рад
        assertThatThrownBy(() -> creditMemoService.create(new CreditMemoData(
                customer.getId(), null, DATE, "USD", new BigDecimal("12600"),
                false, null, List.of(new LineData(service.getId(), null,
                        BigDecimal.ONE, new BigDecimal("100"),
                        null, null, null, null, null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RET-008"));
    }

    /**
     * Arbitr-052 (007): create сатр валидация чегаралари - миқдор мусбат,
     * нарх манфий эмас (BR-RET-001), INVENTORY сатрида омбор шарт
     * (BR-RET-002). Аввал CM create чегаралари умуман қопланмаган эди -
     * бузилиш «камида битта тест» талабини айланиб ўтарди.
     */
    @Test
    void create_lineBoundaries_rejectedRet001And002() {
        // BR-RET-001: миқдор 0, миқдор манфий, нарх манфий - ҳар бири рад
        assertCmCreateRejected("BR-RET-001", new LineData(service.getId(), null,
                BigDecimal.ZERO, new BigDecimal("100"), null, null, null, null, null));
        assertCmCreateRejected("BR-RET-001", new LineData(service.getId(), null,
                new BigDecimal("-1"), new BigDecimal("100"), null, null, null, null, null));
        assertCmCreateRejected("BR-RET-001", new LineData(service.getId(), null,
                BigDecimal.ONE, new BigDecimal("-5"), null, null, null, null, null));
        // BR-RET-002: INVENTORY item сатрида омбор танланмаса рад
        assertCmCreateRejected("BR-RET-002", new LineData(invItem.getId(), null,
                BigDecimal.ONE, new BigDecimal("100"), null, null, null, null, null));
    }

    /** CM create'ни битта сатр билан чақириб, кутилган BR кодини тасдиқлайди. */
    private void assertCmCreateRejected(String code, LineData line) {
        assertThatThrownBy(() -> creditMemoService.create(new CreditMemoData(
                customer.getId(), null, DATE, null, null, false, null, List.of(line))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo(code));
    }

    /**
     * Arbitr-069 (Komil-019): BR-RET-006 КУМУЛЯТИВ - аввалги POSTED
     * қайтимлар ҳисобга киради, ҳовуз CM+RR умумий (иккиси ҳам invoice
     * қайтими): 10 доналик сотувга RR 6 дона POSTED бўлгач CM 6 дона
     * РАД (12 > 10), лимитга айнан тенг қисман қайтим (4) ЎТАДИ,
     * REVERSED қайтим эса ҳисобдан чиқади. Аввал фақат жорий ҳужжат
     * текширилиб COGS икки марта камаярди (IAS 2.34).
     */
    @Test
    void create_cumulativeQuantities_rrAndCmSharedPool() {
        inventoryService.receive(invItem.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("800"), DATE, "SEED", null, null);
        Invoice original = invoiceService.post(invoiceService.createDraft(
                new InvoiceData(customer.getId(), DATE, null, null, null, null, false,
                        List.of(new InvoiceService.LineData(invItem.getId(),
                                warehouse.getId(), new BigDecimal("10"),
                                new BigDecimal("2000"), null, null)))).getId());
        UUID bankAccountId = accountRepository.findByName("Банк ҳисобварағи")
                .orElseThrow().getId();

        // RR 6 дона POSTED - ҳовузнинг чек томони
        RefundReceipt refund = refundReceiptService.create(
                new RefundReceiptService.RefundReceiptData(customer.getId(),
                        original.getId(), bankAccountId, DATE, null, null, false, null,
                        List.of(new RefundReceiptService.LineData(invItem.getId(),
                                warehouse.getId(), new BigDecimal("6"),
                                new BigDecimal("2000"), null, null, null, null, null))));
        assertBalanced(glEntry(RefundReceiptService.SOURCE_MODULE, refund.getId()));

        // CM 6 дона РАД - кумулятив 6 + 6 = 12 > 10
        assertThatThrownBy(() -> createLinkedMemo(original, "6"))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RET-006"));

        // Лимитга айнан тенг: 6 + 4 = 10 - ЎТАДИ, GL балансланган
        CreditMemo partial = createLinkedMemo(original, "4");
        assertBalanced(glEntry(CreditMemoService.SOURCE_MODULE, partial.getId()));

        // Тўлиқ қайтарилган - энди 1 дона ҳам сиғмайди
        assertThatThrownBy(() -> createLinkedMemo(original, "1"))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RET-006"));

        // REVERSED ҳисобдан чиқади: 4 доналик CM сторно бўлгач жой очилади
        creditMemoService.reverse(partial.getId(), DATE, "кумулятив тест");
        CreditMemo again = createLinkedMemo(original, "4");
        assertBalanced(glEntry(CreditMemoService.SOURCE_MODULE, again.getId()));
    }

    /** Ҳаволали inventory кредит-нотаси (кумулятив тест ёрдамчиси). */
    private CreditMemo createLinkedMemo(Invoice original, String qty) {
        return creditMemoService.create(new CreditMemoData(customer.getId(),
                original.getId(), DATE, null, null, false, null,
                List.of(new LineData(invItem.getId(), warehouse.getId(),
                        new BigDecimal(qty), new BigDecimal("2000"),
                        null, null, null, null, null))));
    }

    /**
     * Arbitr-069 (Asrorxoja-013): асл ҳужжат СТАТУСИ текширилади - DRAFT
     * ҳам, REVERSED ҳам «асл ҳужжат» бўла олмайди. Аввал create фақат
     * мавжудлик + контактни текширарди (apply POSTED текшируви билан
     * асимметрия).
     */
    @Test
    void create_originalInvoiceMustBePosted_draftAndReversedRejected() {
        // DRAFT invoice - GL'да акс этмаган, унга қайтим боғлаб бўлмайди
        Invoice draft = invoiceService.createDraft(new InvoiceData(customer.getId(),
                DATE, null, null, null, null, false,
                List.of(new InvoiceService.LineData(service.getId(), null,
                        BigDecimal.ONE, new BigDecimal("10000"), null, null))));
        assertLinkedServiceMemoRejected(draft);

        // REVERSED invoice - сотув бекор бўлган, қайтим ҳам рад
        Invoice reversed = postServiceInvoice(null, null, "10000");
        invoiceService.reverse(reversed.getId(), DATE, "статус тест");
        assertLinkedServiceMemoRejected(reversed);
    }

    /** POSTED бўлмаган асл invoice'га ҳаволали CM create BR-RET-006 рад. */
    private void assertLinkedServiceMemoRejected(Invoice original) {
        assertThatThrownBy(() -> creditMemoService.create(new CreditMemoData(
                customer.getId(), original.getId(), DATE, null, null, false, null,
                List.of(new LineData(service.getId(), null, BigDecimal.ONE,
                        new BigDecimal("1000"), null, null, null, null, null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RET-006"));
    }

    /** Spec 4-банд: apply FX - курслар фарқли бўлса алоҳида JE, нолда JE йўқ. */
    @Test
    void applyFx_differentRates_separateJe_zeroDiffNoJe() {
        // USD invoice курс 12600, USD кредит курс 12700 - фарқ 100×100=10000
        Invoice usdInvoice = postServiceInvoice("USD", new BigDecimal("12600"), "500");
        CreditMemo usdMemo = creditMemoService.create(new CreditMemoData(
                usdCustomer.getId(), null, DATE, "USD", new BigDecimal("12700"),
                false, null, List.of(new LineData(service.getId(), null,
                        BigDecimal.ONE, new BigDecimal("200"),
                        null, null, null, null, null))));
        CreditApplication application = creditMemoService.apply(
                usdMemo.getId(), usdInvoice.getId(), new BigDecimal("100"));

        JournalEntry fxEntry = glEntry(CreditMemoService.APPLICATION_SOURCE_MODULE,
                application.getId());
        assertBalanced(fxEntry);
        // Фарқ мусбат (кредит курси юқори) - фойда: AR Dt / gain Cr
        assertThat(baseOf(fxEntry, "ACCOUNTS_RECEIVABLE", true)).isEqualByComparingTo("10000");
        assertThat(baseOf(fxEntry, "EXCHANGE_GAIN_OR_LOSS", false)).isEqualByComparingTo("10000");

        // Нол фарқ: бир хил курсда JE ёзилмайди
        CreditMemo sameRate = creditMemoService.create(new CreditMemoData(
                usdCustomer.getId(), null, DATE, "USD", new BigDecimal("12600"),
                false, null, List.of(new LineData(service.getId(), null,
                        BigDecimal.ONE, new BigDecimal("50"),
                        null, null, null, null, null))));
        CreditApplication zeroDiff = creditMemoService.apply(
                sameRate.getId(), usdInvoice.getId(), new BigDecimal("50"));
        assertThat(entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                CreditMemoService.APPLICATION_SOURCE_MODULE, zeroDiff.getId())).isEmpty();
    }

    /**
     * Arbitr-050 / Беруний-031: эски (ёпилган) даврдаги кредитни янги очиқ
     * даврдаги invoice'га қўллаш ЎТАДИ - realized FX JE ҳужжат санаси эмас,
     * ҚЎЛЛАШ (бугун) санасида ёзилади. Акс ҳолда FX JE cmDate'да (ёпиқ
     * давр) яратилиб BR-LED-020 блокига урар ва бутун apply тўхтарди.
     */
    @Test
    void applyFx_closedCreditPeriod_postsWithApplicationDateNotDocDate() {
        LocalDate today = LocalDate.now(settingsService.zoneId());
        LocalDate creditDate = today.minusMonths(2);   // кейин ёпиладиган давр
        LocalDate closing = today.minusMonths(1);      // давр ёпилиш чегараси

        // USD invoice (курс 12700) очиқ санада, USD кредит (12600) эски санада -
        // курслар фарқли, шунда FX JE албатта ёзилади
        Invoice usdInvoice = invoiceService.post(invoiceService.createDraft(
                new InvoiceData(usdCustomer.getId(), today, null, "USD",
                        new BigDecimal("12700"), null, false,
                        List.of(new InvoiceService.LineData(service.getId(), null,
                                BigDecimal.ONE, new BigDecimal("500"), null, null)))).getId());
        CreditMemo usdMemo = creditMemoService.create(new CreditMemoData(
                usdCustomer.getId(), null, creditDate, "USD", new BigDecimal("12600"),
                false, null, List.of(new LineData(service.getId(), null,
                        BigDecimal.ONE, new BigDecimal("200"),
                        null, null, null, null, null))));

        // Ҳар икки ҳужжат POST бўлгач давр ёпилади (creditDate энди ёпиқ)
        var s = settingsService.get();
        settingsService.update(s.getName(), s.homeCurrencyCode(), s.getTimezone(), null, closing);

        // Эски кодда бу қатор BR-LED-020 (ёпиқ давр) билан отарди - энди ўтади
        CreditApplication application = creditMemoService.apply(
                usdMemo.getId(), usdInvoice.getId(), new BigDecimal("100"));

        JournalEntry fxEntry = glEntry(CreditMemoService.APPLICATION_SOURCE_MODULE,
                application.getId());
        assertBalanced(fxEntry);
        // ЯДРО: FX JE санаси = қўллаш куни (бугун), кредит санаси ЭМАС; ёпилишдан кейин
        assertThat(fxEntry.getEntryDate()).isEqualTo(today);
        assertThat(fxEntry.getEntryDate()).isAfter(closing);
        // Фарқ: 100 × (12600 − 12700) = −10000 - зарар: AR Cr / gain Dt
        assertThat(baseOf(fxEntry, "ACCOUNTS_RECEIVABLE", false)).isEqualByComparingTo("10000");
        assertThat(baseOf(fxEntry, "EXCHANGE_GAIN_OR_LOSS", true)).isEqualByComparingTo("10000");
    }

    /** Spec 7-банд: қўлланган кредит reverse рад; unapply'дан кейин ўтади. */
    @Test
    void reverse_rejectedWhenApplied_allowedAfterUnapply() {
        Invoice invoice = postServiceInvoice(null, null, "10000");
        CreditMemo memo = createServiceMemo("3000");
        CreditApplication application = creditMemoService.apply(
                memo.getId(), invoice.getId(), new BigDecimal("3000"));

        assertThatThrownBy(() -> creditMemoService.reverse(memo.getId(), DATE, null))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RET-007"));

        creditMemoService.unapply(application.getId(), DATE);
        // Unapply денормализацияларни тиклайди
        assertThat(invoice.getBalanceDue()).isEqualByComparingTo("10000");
        assertThat(creditMemoService.get(memo.getId()).getOpenBalance())
                .isEqualByComparingTo("3000");

        CreditMemo reversed = creditMemoService.reverse(memo.getId(), DATE, "тест");
        assertThat(reversed.getStatus()).isEqualTo(CreditMemo.Status.REVERSED);
    }

    /** Spec 8-банд: reverse - тўлиқ GL сторно + омбор қайтими бекор бўлади. */
    @Test
    void reverse_stornosGl_andReturnsStock() {
        inventoryService.receive(invItem.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("800"), DATE, "SEED", null, null);
        CreditMemo memo = creditMemoService.create(new CreditMemoData(
                customer.getId(), null, DATE, null, null, false, null,
                List.of(new LineData(invItem.getId(), warehouse.getId(),
                        new BigDecimal("2"), new BigDecimal("2000"),
                        null, null, null, null, null))));
        assertThat(inventoryService.quantityOnHand(invItem.getId(), warehouse.getId()))
                .isEqualByComparingTo("12");

        creditMemoService.reverse(memo.getId(), DATE, "қайтариш бекор");

        assertThat(glEntry(CreditMemoService.SOURCE_MODULE, memo.getId()).getStatus())
                .isEqualTo(EntryStatus.REVERSED);
        // Омбор кирими тескари қайтарилди - қолдиқ аслига тушди
        assertThat(inventoryService.quantityOnHand(invItem.getId(), warehouse.getId()))
                .isEqualByComparingTo("10");
        assertThat(creditMemoService.get(memo.getId()).getStatus())
                .isEqualTo(CreditMemo.Status.REVERSED);
    }
}
