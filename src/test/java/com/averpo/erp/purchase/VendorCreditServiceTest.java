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
import com.averpo.erp.ledger.repo.JournalEntryRepository;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.purchase.domain.Bill;
import com.averpo.erp.purchase.domain.BillLineType;
import com.averpo.erp.purchase.domain.VendorCredit;
import com.averpo.erp.purchase.domain.VendorCreditApplication;
import com.averpo.erp.purchase.service.BillService;
import com.averpo.erp.purchase.service.BillService.BillData;
import com.averpo.erp.purchase.service.VendorCreditService;
import com.averpo.erp.purchase.service.VendorCreditService.LineData;
import com.averpo.erp.purchase.service.VendorCreditService.VendorCreditData;
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
 * VendorCredit тестлари (docs/modules/returns.md «Тестлар» 6 ва 8
 * бандларининг VC қисми + фарқ сатри; 9-банд смок ScreenSmokeTest'да).
 * Apply/FX/BR-RET-007 ҳам шу ерда - ҳар posting логикага тест шарт
 * (ТЕМИР ҚОИДА №7), FX JE ҳам posting. Ҳар JE'да debit == credit.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VendorCreditServiceTest {

    /** Барча тест ҳужжатлар санаси. */
    private static final LocalDate DATE = LocalDate.of(2026, 7, 8);

    @Autowired VendorCreditService vendorCreditService;
    @Autowired BillService billService;
    @Autowired InventoryService inventoryService;
    @Autowired WarehouseService warehouseService;
    @Autowired ContactService contactService;
    @Autowired ItemService itemService;
    @Autowired AccountService accountService;
    @Autowired JournalEntryRepository entryRepository;
    @Autowired CompanySettingsService settingsService;
    @Autowired com.averpo.erp.tax.service.TaxRateService taxRateService;

    private Contact vendor;

    /** USD валютали vendor (Arbitr-087): чет валюта ҳужжатлари шунга ёзилади. */
    private Contact usdVendor;

    private Item invItem;
    private Warehouse warehouse;

    /** Хизмат харажати default счёти (EXPENSE туркуми) - EXPENSE сатр учун. */
    private UUID expenseAccountId;

    @BeforeEach
    void setUp() {
        accountService.importDefaultChart();
        vendor = contactService.create(ContactType.VENDOR, new ContactData(
                "Қайтариш таъминотчиси", null, null, null, null, null,
                null, null, null, null, null));
        usdVendor = contactService.create(ContactType.VENDOR, new ContactData(
                "Қайтариш USD таъминотчиси", null, null, null, null, null,
                "USD", null, null, null, null));
        ItemService.DefaultAccounts inv = itemService.defaultsFor(ItemType.INVENTORY);
        invItem = itemService.create(ItemType.INVENTORY, new ItemData(
                "Қайтариладиган товар", null, null, null, null, null,
                inv.income(), null, null, inv.expense(), inv.inventoryAsset(), null));
        warehouse = warehouseService.all().stream()
                .filter(w -> "Асосий омбор".equals(w.getName()))
                .findFirst().orElseThrow();
        expenseAccountId = itemService.defaultsFor(ItemType.SERVICE).expense();
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
     * Валютага мос контакт (Arbitr-087: ҳужжат валютаси контактдан) -
     * USD ҳужжатлар usdVendor'га, home ҳужжатлар vendor'га ёзилади.
     */
    private UUID vendorFor(String currency) {
        return "USD".equals(currency) ? usdVendor.getId() : vendor.getId();
    }

    /** EXPENSE сатрли POSTED bill (қўллаш тестлари учун). */
    private Bill postExpenseBill(String currency, BigDecimal rate, String amount) {
        BillData data = new BillData(vendorFor(currency), null, DATE, null, currency, rate,
                null, List.of(new BillService.LineData(BillLineType.EXPENSE, null, null,
                        null, null, expenseAccountId, new BigDecimal(amount), null)));
        return billService.post(billService.createDraft(data).getId());
    }

    /** EXPENSE сатрли кредит-нота. */
    private VendorCredit createExpenseCredit(String currency, BigDecimal rate, String amount) {
        return vendorCreditService.create(new VendorCreditData(vendorFor(currency), null,
                DATE, currency, rate, false, null, List.of(new LineData(
                        BillLineType.EXPENSE, null, null, null, null,
                        expenseAccountId, new BigDecimal(amount), null,
                        null, null, null, null))));
    }

    /**
     * Arbitr-069 - bill томони кўзгуси: BR-RET-006 кумулятив (аввалги
     * POSTED VC'лар йиғиндиси ҳисобга киради - акс ҳолда 10 доналик
     * харидга иккита VC билан омбордан 20 дона чиқарилар эди) + асл
     * bill POSTED бўлиши шарт (REVERSED рад).
     */
    @Test
    void create_cumulativeQuantities_andPostedBillRequired() {
        Bill original = billService.post(billService.createDraft(new BillData(
                vendor.getId(), null, DATE, null, null, null, null,
                List.of(new BillService.LineData(BillLineType.ITEM, invItem.getId(),
                        warehouse.getId(), new BigDecimal("10"), new BigDecimal("800"),
                        null, null, null)))).getId());

        // VC 6 дона POSTED - лимитнинг бир қисми банд бўлади
        VendorCredit first = createLinkedItemCredit(original, "6");
        assertBalanced(glEntry(VendorCreditService.SOURCE_MODULE, first.getId()));

        // Кумулятив: 6 + 6 = 12 > 10 - РАД
        assertThatThrownBy(() -> createLinkedItemCredit(original, "6"))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RET-006"));

        // Лимитга айнан тенг: 6 + 4 = 10 - ЎТАДИ, GL балансланган
        VendorCredit second = createLinkedItemCredit(original, "4");
        assertBalanced(glEntry(VendorCreditService.SOURCE_MODULE, second.getId()));

        // Тўлиқ қайтарилган - энди 1 дона ҳам сиғмайди
        assertThatThrownBy(() -> createLinkedItemCredit(original, "1"))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RET-006"));

        // Асл bill POSTED бўлиши шарт: REVERSED bill'га кредит РАД
        Bill toReverse = postExpenseBill(null, null, "5000");
        billService.reverse(toReverse.getId(), DATE, "статус тест");
        assertThatThrownBy(() -> vendorCreditService.create(new VendorCreditData(
                vendor.getId(), toReverse.getId(), DATE, null, null, false, null,
                List.of(new LineData(BillLineType.EXPENSE, null, null, null, null,
                        expenseAccountId, new BigDecimal("1000"), null,
                        null, null, null, null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RET-006"));
    }

    /** Ҳаволали ITEM кредит-нотаси (кумулятив тест ёрдамчиси). */
    private VendorCredit createLinkedItemCredit(Bill original, String qty) {
        return vendorCreditService.create(new VendorCreditData(vendor.getId(),
                original.getId(), DATE, null, null, false, null,
                List.of(new LineData(BillLineType.ITEM, invItem.getId(),
                        warehouse.getId(), new BigDecimal(qty), new BigDecimal("800"),
                        null, null, null, null, null, null, null))));
    }

    /**
     * Spec 6-банд: Dr AP (gross) / Cr INVENTORY (сиёсат таннархи) +
     * фарқ shrinkage'га (Cr), хизмат сатри Cr EXPENSE, input ҚҚС Cr.
     */
    @Test
    void post_glMatchesPostingRules_apExpenseTaxAndInventory() {
        // Омборда 10 @ 800 бор - жорий сиёсат (AVCO) таннархи 800
        inventoryService.receive(invItem.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("800"), DATE, "SEED", null, null);
        // Ноёб код - QQS12 seed'да банд (BR-TAX-001)
        var vat = taxRateService.create("RETVC12", "Қайтариш ҚҚС 12%", new BigDecimal("12"));

        // ITEM 2 × 1000 = 2000 net + EXPENSE 500 net (ҚҚС 12% = 60) -
        // gross = 2000 + 560 = 2560
        VendorCredit credit = vendorCreditService.create(new VendorCreditData(
                vendor.getId(), null, DATE, null, null, false, null,
                List.of(new LineData(BillLineType.ITEM, invItem.getId(), warehouse.getId(),
                                new BigDecimal("2"), new BigDecimal("1000"),
                                null, null, null, null, null, null, null),
                        new LineData(BillLineType.EXPENSE, null, null, null, null,
                                expenseAccountId, new BigDecimal("500"), null,
                                null, vat.getId(), null, null))));

        assertThat(credit.getStatus()).isEqualTo(VendorCredit.Status.POSTED);
        assertThat(credit.getVcNumber()).startsWith("VC-2026-");
        assertThat(credit.getTotal()).isEqualByComparingTo("2560");

        JournalEntry entry = glEntry(VendorCreditService.SOURCE_MODULE, credit.getId());
        assertBalanced(entry);
        // Dr AP = gross (AP камаяди)
        assertThat(baseOf(entry, "ACCOUNTS_PAYABLE", true)).isEqualByComparingTo("2560");
        // Cr INVENTORY = сиёсат таннархи (2 × 800), ҳужжат нархи эмас
        assertThat(baseOf(entry, "INVENTORY", false)).isEqualByComparingTo("1600");
        // Фарқ (net 2000 − таннарх 1600 = 400) shrinkage'га Cr
        assertThat(baseOf(entry, "OTHER_COSTS_OF_SERVICE_COS", false)).isEqualByComparingTo("400");
        // Хизмат сатри: Cr EXPENSE (харажат қайтади)
        assertThat(baseOf(entry, "OTHER_MISCELLANEOUS_SERVICE_COST", false)).isEqualByComparingTo("500");
        // Input ҚҚС қайтиши: Cr SALES_TAX_PAYABLE
        assertThat(baseOf(entry, "SALES_TAX_PAYABLE", false)).isEqualByComparingTo("60");
        // Омбордан чиқди: 10 → 8
        assertThat(inventoryService.quantityOnHand(invItem.getId(), warehouse.getId()))
                .isEqualByComparingTo("8");
    }

    /**
     * Фарқ сатри тести: net сиёсат таннархидан КИЧИК бўлса фарқ
     * Dt OTHER_COSTS_OF_SERVICE_COS (posting-rules «манфийда Dt»);
     * нол фарқда лег умуман ёзилмайди.
     */
    @Test
    void post_differenceLeg_debitWhenNetBelowCost_absentWhenZero() {
        inventoryService.receive(invItem.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("800"), DATE, "SEED", null, null);

        // Net 2 × 600 = 1200 < таннарх 1600 - фарқ 400 Dt томонга
        VendorCredit below = vendorCreditService.create(new VendorCreditData(
                vendor.getId(), null, DATE, null, null, false, null,
                List.of(new LineData(BillLineType.ITEM, invItem.getId(), warehouse.getId(),
                        new BigDecimal("2"), new BigDecimal("600"),
                        null, null, null, null, null, null, null))));
        JournalEntry belowEntry = glEntry(VendorCreditService.SOURCE_MODULE, below.getId());
        assertBalanced(belowEntry);
        assertThat(baseOf(belowEntry, "ACCOUNTS_PAYABLE", true)).isEqualByComparingTo("1200");
        assertThat(baseOf(belowEntry, "INVENTORY", false)).isEqualByComparingTo("1600");
        assertThat(baseOf(belowEntry, "OTHER_COSTS_OF_SERVICE_COS", true)).isEqualByComparingTo("400");

        // Net 2 × 800 = 1600 == таннарх - фарқ леги йўқ
        VendorCredit equal = vendorCreditService.create(new VendorCreditData(
                vendor.getId(), null, DATE, null, null, false, null,
                List.of(new LineData(BillLineType.ITEM, invItem.getId(), warehouse.getId(),
                        new BigDecimal("2"), new BigDecimal("800"),
                        null, null, null, null, null, null, null))));
        JournalEntry equalEntry = glEntry(VendorCreditService.SOURCE_MODULE, equal.getId());
        assertBalanced(equalEntry);
        assertThat(baseOf(equalEntry, "OTHER_COSTS_OF_SERVICE_COS", true)).isEqualByComparingTo("0");
        assertThat(baseOf(equalEntry, "OTHER_COSTS_OF_SERVICE_COS", false)).isEqualByComparingTo("0");
    }

    /** Arbitr-087 (BR-RET-008): валюта контактдан derive + мослик гарови. */
    @Test
    void currency_derivedFromContact_mismatchRejected() {
        // Бўш currency - server USD vendor'дан ўзи олади
        VendorCredit derived = vendorCreditService.create(new VendorCreditData(
                usdVendor.getId(), null, DATE, null, new BigDecimal("12600"),
                false, null, List.of(new LineData(BillLineType.EXPENSE, null, null,
                        null, null, expenseAccountId, new BigDecimal("100"), null,
                        null, null, null, null))));
        assertThat(derived.getCurrency().getCode()).isEqualTo("USD");

        // Клиент қиймати контактга (home) зид - BR-RET-008 рад
        assertThatThrownBy(() -> vendorCreditService.create(new VendorCreditData(
                vendor.getId(), null, DATE, "USD", new BigDecimal("12600"),
                false, null, List.of(new LineData(BillLineType.EXPENSE, null, null,
                        null, null, expenseAccountId, new BigDecimal("100"), null,
                        null, null, null, null)))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RET-008"));
    }

    /**
     * Apply: bill balance/кредит қолдиғи камаяди; BR-RET-003 ошиқча;
     * FX фарқи алоҳида JE (VENDOR_CREDIT_APPLICATION, нолда JE йўқ);
     * BR-RET-007 - қўлланган кредит reverse рад, unapply'дан кейин ўтади.
     */
    @Test
    void apply_fx_andReverseGuard() {
        Bill bill = postExpenseBill(null, null, "10000");
        VendorCredit credit = createExpenseCredit(null, null, "3000");

        VendorCreditApplication application = vendorCreditService.apply(
                credit.getId(), bill.getId(), new BigDecimal("3000"));
        assertThat(bill.getBalanceDue()).isEqualByComparingTo("7000");
        assertThat(vendorCreditService.get(credit.getId()).getOpenBalance())
                .isEqualByComparingTo("0");
        // Home валютада FX фарқи нол - JE ёзилмайди
        assertThat(entryRepository.findBySourceModuleAndSourceDocumentIdAndReversalOfIsNull(
                VendorCreditService.APPLICATION_SOURCE_MODULE, application.getId())).isEmpty();

        // BR-RET-003: очиқ қолдиқдан ошиқча қўллаш рад
        Bill second = postExpenseBill(null, null, "5000");
        assertThatThrownBy(() -> vendorCreditService.apply(credit.getId(), second.getId(),
                new BigDecimal("100")))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RET-003"));

        // Кросс-валюта қўллаш рад. Arbitr-087 дан кейин бир контактда икки
        // валютали ҳужжат структуравий имконсиз (валюта контактдан) - USD
        // кредит энди usdVendor'ники, бошқа vendor'нинг UZS bill'ига
        // қўллашда контакт гарови (BR-RET-005) аввал ушлайди; BR-RET-004
        // тарихий (087 дан аввалги) маълумот учун ҳимоя қатлами
        VendorCredit crossCredit = createExpenseCredit("USD", new BigDecimal("12600"), "200");
        assertThatThrownBy(() -> vendorCreditService.apply(crossCredit.getId(), second.getId(),
                new BigDecimal("50")))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RET-005"));

        // BR-RET-007: қўлланган кредит reverse рад
        assertThatThrownBy(() -> vendorCreditService.reverse(credit.getId(), DATE, null))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo("BR-RET-007"));

        vendorCreditService.unapply(application.getId(), DATE);
        assertThat(bill.getBalanceDue()).isEqualByComparingTo("10000");
        assertThat(vendorCreditService.get(credit.getId()).getOpenBalance())
                .isEqualByComparingTo("3000");
        assertThat(vendorCreditService.reverse(credit.getId(), DATE, "тест").getStatus())
                .isEqualTo(VendorCredit.Status.REVERSED);

        // FX: USD bill (12600) га USD кредит (12700) қўлланса фарқ
        // 100 × (12600 − 12700) = −10000 - зарар: AP Cr / фарқ Dt
        Bill usdBill = postExpenseBill("USD", new BigDecimal("12600"), "500");
        VendorCredit usdCredit = createExpenseCredit("USD", new BigDecimal("12700"), "200");
        VendorCreditApplication fxApplication = vendorCreditService.apply(
                usdCredit.getId(), usdBill.getId(), new BigDecimal("100"));
        JournalEntry fxEntry = glEntry(VendorCreditService.APPLICATION_SOURCE_MODULE,
                fxApplication.getId());
        assertBalanced(fxEntry);
        assertThat(baseOf(fxEntry, "ACCOUNTS_PAYABLE", false)).isEqualByComparingTo("10000");
        assertThat(baseOf(fxEntry, "EXCHANGE_GAIN_OR_LOSS", true)).isEqualByComparingTo("10000");
    }

    /**
     * Arbitr-052 (007): create сатр валидация чегаралари - EXPENSE сумма
     * мусбат (BR-RET-001), INVENTORY сатрида омбор шарт (BR-RET-002).
     * Аввал VC create чегаралари умуман қопланмаган эди.
     */
    @Test
    void create_lineBoundaries_rejectedRet001And002() {
        // BR-RET-001: EXPENSE сатр суммаси 0 ёки манфий - рад
        assertVcCreateRejected("BR-RET-001", new LineData(BillLineType.EXPENSE, null, null,
                null, null, expenseAccountId, BigDecimal.ZERO, null, null, null, null, null));
        assertVcCreateRejected("BR-RET-001", new LineData(BillLineType.EXPENSE, null, null,
                null, null, expenseAccountId, new BigDecimal("-10"), null, null, null, null, null));
        // BR-RET-002: INVENTORY (ITEM) сатрида омбор танланмаса - рад
        assertVcCreateRejected("BR-RET-002", new LineData(BillLineType.ITEM, invItem.getId(),
                null, BigDecimal.ONE, new BigDecimal("100"), null, null, null, null, null, null, null));
    }

    /** VC create'ни битта сатр билан чақириб, кутилган BR кодини тасдиқлайди. */
    private void assertVcCreateRejected(String code, LineData line) {
        assertThatThrownBy(() -> vendorCreditService.create(new VendorCreditData(
                vendor.getId(), null, DATE, null, null, false, null, List.of(line))))
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo(code));
    }

    /**
     * Arbitr-050 (CM кўзгуси): эски (ёпилган) даврдаги таъминотчи кредитини
     * янги очиқ даврдаги bill'га қўллаш ЎТАДИ - realized FX JE ҳужжат санаси
     * эмас, ҚЎЛЛАШ (бугун) санасида. Акс ҳолда vcDate (ёпиқ давр) BR-LED-020
     * блокига урарди.
     */
    @Test
    void applyFx_closedCreditPeriod_postsWithApplicationDateNotDocDate() {
        LocalDate today = LocalDate.now(settingsService.zoneId());
        LocalDate creditDate = today.minusMonths(2);   // кейин ёпиладиган давр
        LocalDate closing = today.minusMonths(1);      // давр ёпилиш чегараси

        // USD bill (12700) очиқ санада, USD кредит (12600) эски санада - фарқ бор
        Bill usdBill = billService.post(billService.createDraft(
                new BillData(usdVendor.getId(), null, today, null, "USD",
                        new BigDecimal("12700"), null, List.of(new BillService.LineData(
                                BillLineType.EXPENSE, null, null, null, null,
                                expenseAccountId, new BigDecimal("500"), null)))).getId());
        VendorCredit usdCredit = vendorCreditService.create(new VendorCreditData(
                usdVendor.getId(), null, creditDate, "USD", new BigDecimal("12600"),
                false, null, List.of(new LineData(BillLineType.EXPENSE, null, null,
                        null, null, expenseAccountId, new BigDecimal("200"), null,
                        null, null, null, null))));

        // Ҳар икки ҳужжат POST бўлгач давр ёпилади (creditDate энди ёпиқ)
        var s = settingsService.get();
        settingsService.update(s.getName(), s.homeCurrencyCode(), s.getTimezone(), null, closing);

        // Эски кодда бу қатор BR-LED-020 билан отарди - энди ўтади
        VendorCreditApplication application = vendorCreditService.apply(
                usdCredit.getId(), usdBill.getId(), new BigDecimal("100"));

        JournalEntry fxEntry = glEntry(VendorCreditService.APPLICATION_SOURCE_MODULE,
                application.getId());
        assertBalanced(fxEntry);
        // ЯДРО: FX JE санаси = қўллаш куни (бугун), кредит санаси ЭМАС; ёпилишдан кейин
        assertThat(fxEntry.getEntryDate()).isEqualTo(today);
        assertThat(fxEntry.getEntryDate()).isAfter(closing);
        // Фарқ: 100 × (12700 − 12600) = +10000 - фойда: AP Dt / gain Cr
        assertThat(baseOf(fxEntry, "ACCOUNTS_PAYABLE", true)).isEqualByComparingTo("10000");
        assertThat(baseOf(fxEntry, "EXCHANGE_GAIN_OR_LOSS", false)).isEqualByComparingTo("10000");
    }

    /** Spec 8-банд (VC): reverse - тўлиқ GL сторно + омбор чиқими қайтади. */
    @Test
    void reverse_stornosGl_andRestoresStock() {
        inventoryService.receive(invItem.getId(), warehouse.getId(),
                new BigDecimal("10"), new BigDecimal("800"), DATE, "SEED", null, null);
        VendorCredit credit = vendorCreditService.create(new VendorCreditData(
                vendor.getId(), null, DATE, null, null, false, null,
                List.of(new LineData(BillLineType.ITEM, invItem.getId(), warehouse.getId(),
                        new BigDecimal("2"), new BigDecimal("1000"),
                        null, null, null, null, null, null, null))));
        assertThat(inventoryService.quantityOnHand(invItem.getId(), warehouse.getId()))
                .isEqualByComparingTo("8");

        vendorCreditService.reverse(credit.getId(), DATE, "қайтариш бекор");

        assertThat(glEntry(VendorCreditService.SOURCE_MODULE, credit.getId()).getStatus())
                .isEqualTo(EntryStatus.REVERSED);
        // Омбор чиқими тескари қайтарилди - қолдиқ аслига тушди
        assertThat(inventoryService.quantityOnHand(invItem.getId(), warehouse.getId()))
                .isEqualByComparingTo("10");
        assertThat(vendorCreditService.get(credit.getId()).getStatus())
                .isEqualTo(VendorCredit.Status.REVERSED);
    }
}
