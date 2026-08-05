package com.averpo.erp.config;

import com.averpo.erp.bank.service.BankTransactionService;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.inventory.service.InventoryService;
import com.averpo.erp.inventory.service.WarehouseService;
import com.averpo.erp.item.domain.ItemType;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.item.service.UnitService;
import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.purchase.domain.BillLineType;
import com.averpo.erp.purchase.service.BillPaymentService;
import com.averpo.erp.purchase.service.BillService;
import com.averpo.erp.sales.service.InvoicePaymentService;
import com.averpo.erp.sales.service.InvoiceService;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.service.PaymentTermService;
import com.averpo.erp.tax.service.TaxRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code demo} профилидаги намойиш маълумотлари: тақдимот скриншотлари
 * ва ҳакамлар иловани ўзи юргизиб кўриши учун «Озод Савдо» деган
 * шартли ўзбек савдо компаниясининг охирги 3 ойлик реал ҳаёти -
 * мижоз/етказувчи каталоги, товар-хизмат каталоги, харид ва сотув
 * ҳужжатлари, қисман тўловлар, банк харажатлари, омбор ҳаракатлари.
 *
 * <p><b>Нега керак:</b> бўш база билан dashboard графиги, P&L даврлари,
 * AR/AP aging ва омбор ҳисоботлари бўм-бўш кўринади - тизимнинг
 * қиймати кўринмайди. Seed маълумот шу экранларнинг ҳаммасини бир
 * старт билан «тирик» қилади.
 *
 * <p><b>Қатлам (ТЕМИР ҚОИДА №6 ва №2):</b> сеедер ҳеч бир модулнинг
 * repository'сига тегмайди - фақат public service интерфейслари
 * орқали ишлайди, ҳужжатлар ўз service'и билан яратилиб post қилинади,
 * шунда GL'ни доим {@code PostingService} ёзади ва проводкалар
 * docs/posting-rules.md га мос бўлади. Демо маълумот ҳам ҳақиқий
 * ҳужжат оқимидан ўтади - «қўлдан ясалган» GL йўқ.
 *
 * <p><b>Идемпотентлик:</b> базада камида битта CUSTOMER контакт бўлса
 * сеедер ҳеч нарса қилмайди. Такрор старт (ёки илова рестарти) демо
 * ҳужжатларни иккилантирмайди - {@code DefaultChartInitializer}
 * қолипи.
 *
 * <p><b>Тартиб ({@link Order}):</b> счётлар режаси ва UOM гуруҳлари
 * аввал ўрнатилиши шарт - шунинг учун runner энг охирига қўйилган.
 * Лекин {@code DefaultChartInitializer}/{@code DefaultUnitsInitializer}
 * ҳам ordersiz (LOWEST_PRECEDENCE) - тенг вазнда тартиб кафолатланмайди,
 * шунинг учун {@link #ensureCatalogs()} шартларни ЎЗИ ҳам idempotent
 * равишда таъминлайди. Демо базанинг тайёрлиги runner тартибига
 * боғлиқ бўлиб қолмайди.
 *
 * @author Zafar
 */
@Component
@Profile("demo")
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class DemoDataSeeder implements ApplicationRunner {

    /** Демо компаниянинг номи - созламалар ҳали default бўлса қўйилади.
     * Кирилл ёзувда: демо базадаги бошқа ном/изоҳлар ҳам кирилл, UI
     * тили ҳам шундай - тақдимот скриншотида ёзув аралашмасин. */
    private static final String COMPANY_NAME = "«Озод Савдо» МЧЖ";

    /** Созламалардаги default ном - фақат шуни алмаштирамиз (фойдаланувчи
     * ўзгартирган номга тегилмайди). */
    private static final String DEFAULT_COMPANY_NAME = "Компания";

    /** Чет валюта ҳужжатларининг курси (1 USD = ... UZS) - реалга яқин
     * шартли қиймат; каталогдан ўқилмайди, демо детерминистик бўлсин. */
    private static final BigDecimal USD_RATE = new BigDecimal("12600");

    /** USD тушум курси - invoice курсидан фарқли, шунда realized курс
     * фарқи проводкаси ҳам демода кўринади (multi-currency намойиши). */
    private static final BigDecimal USD_PAYMENT_RATE = new BigDecimal("12750");

    /** Асосий омбор номи (seed) - ҳужжат сатрлари шунга ёзилади. */
    private static final String MAIN_WAREHOUSE = "Асосий омбор";

    /** Иккинчи омбор - multi-warehouse (QBO'дан фарқимиз) кўрингани учун. */
    private static final String SHOP_WAREHOUSE = "Чилонзор дўкони";

    /** Home валютадаги банк счёти номи (default chart). */
    private static final String UZS_BANK = "Банк ҳисобварағи";

    /** USD банк счёти номи - чет валюта тушуми фақат шунга тушади (BR-RCPT-002). */
    private static final String USD_BANK = "Валюта ҳисобварағи (USD)";

    /** Касса счёти номи - ўтказма манзили. */
    private static final String CASH_ACCOUNT = "Касса";

    /** Стандарт ҚҚС ставкаси коди (032-tax seed). */
    private static final String VAT_CODE = "QQS12";

    /** Компания номи ва home валютаси - демо санаси компания минтақасида олинади. */
    private final CompanySettingsService settingsService;

    /** Счётлар режаси - бўшлик гарови ва счёт id'лари (repo эмас, қоида №6). */
    private final AccountService accountService;

    /** UOM гуруҳлари - товар бирликлари учун. */
    private final UnitService unitService;

    /** ҚҚС ставкаси id'си - ҳужжат сатрларига қўйилади. */
    private final TaxRateService taxRateService;

    /** Тўлов шартлари (Net 15/30) - контакт due date'лари учун. */
    private final PaymentTermService paymentTermService;

    /** Омбор каталоги - иккинчи омбор шу орқали яратилади. */
    private final WarehouseService warehouseService;

    /** Мижоз/етказувчи каталоги. */
    private final ContactService contactService;

    /** Товар ва хизмат каталоги. */
    private final ItemService itemService;

    /** Харид ҳужжатлари (омборга кирим + AP). */
    private final BillService billService;

    /** Сотув ҳужжатлари (омбордан чиқим + AR). */
    private final InvoiceService invoiceService;

    /** Мижоз тушумлари (AR ёпилиши). */
    private final InvoicePaymentService invoicePaymentService;

    /** Етказувчига тўловлар (AP ёпилиши). */
    private final BillPaymentService billPaymentService;

    /** Банк харажатлари ва ўтказма. */
    private final BankTransactionService bankService;

    /** Омбор актлари (кўчириш, инвентаризация). */
    private final InventoryService inventoryService;

    /**
     * Демо маълумотнинг барча ясагич методлари ишлатадиган тайёр
     * id'лар: каталог элементлари бир марта яратилиб шу ерда сақланади,
     * кейинги қадамлар (ҳужжатлар) уларни номли калит билан олади -
     * ҳар методга ўнлаб параметр узатилмасин.
     *
     * @param customers мижозлар: калит → id
     * @param vendors   етказувчилар: калит → id
     * @param items     товар/хизматлар: калит → id
     * @param mainWarehouse асосий омбор id'си
     * @param shopWarehouse дўкон омбори id'си
     * @param uzsBank   home валютали банк счёти id'си
     * @param usdBank   USD банк счёти id'си
     * @param cash      касса счёти id'си
     * @param vatRate   ҚҚС 12% ставкаси id'си (топилмаса null - солиқсиз)
     */
    private record Catalog(Map<String, UUID> customers, Map<String, UUID> vendors,
                           Map<String, UUID> items, UUID mainWarehouse,
                           UUID shopWarehouse, UUID uzsBank, UUID usdBank,
                           UUID cash, UUID vatRate) { }

    /**
     * Демо базани бир марта тўлдиради. Ҳар қадам ўз service'ининг
     * транзакциясида кетади - оралиқ хато бўлса аввалги қадамлар
     * базада қолади, лекин ҳужжат оқими шундай қурилганки, кейинги
     * қадам аввалгисига таянади (bill'сиз omborда товар бўлмайди).
     */
    @Override
    public void run(ApplicationArguments args) {
        if (alreadySeeded()) {
            log.info("Demo маълумот аллақачон бор - сеедер ўтказиб юборилди");
            return;
        }
        ensureCatalogs();
        renameCompany();
        Catalog catalog = seedCatalog();
        List<UUID> bills = seedBills(catalog);
        List<UUID> invoices = seedInvoices(catalog);
        seedInvoicePayments(catalog, invoices);
        seedBillPayments(catalog, bills);
        seedExpenses(catalog);
        seedBankTransfer(catalog);
        seedStockDocuments(catalog);
        log.info("Demo маълумот тайёр: {} контакт, {} товар/хизмат, {} bill, "
                        + "{} invoice, 4 тушум, 3 тўлов, 4 харажат, 1 ўтказма, "
                        + "1 омбор кўчириш, 1 инвентаризация акти",
                catalog.customers().size() + catalog.vendors().size(),
                catalog.items().size(), bills.size(), invoices.size());
    }

    /**
     * Демо маълумот аллақачон яратилганми: битта CUSTOMER контакт
     * бўлса етарли белги (демо биринчи навбатда мижозларни яратади,
     * жонли базада ҳам мижозсиз савдо бўлмайди).
     */
    private boolean alreadySeeded() {
        return !contactService.list(
                new ContactService.ListFilter(ContactType.CUSTOMER, null, null)).isEmpty();
    }

    /**
     * Счётлар режаси ва UOM гуруҳлари мавжудлигини таъминлайди.
     * Иккала чақирув ҳам idempotent - тегишли initializer аллақачон
     * ишлаган бўлса ҳеч нарса ўзгармайди; ишламаган бўлса (runner
     * тартиби кафолатланмаган) шу ерда ўрнатилади.
     */
    private void ensureCatalogs() {
        if (accountService.isEmpty()) {
            accountService.importDefaultChart();
        }
        if (unitService.groups().isEmpty()) {
            unitService.installDefaultUnits();
        }
    }

    /**
     * Компания номини демо номига алмаштиради - фақат ҳали default
     * («Компания») бўлса. Валюта/минтақа/valuation тегилмайди: улар
     * биринчи ҳужжатдан кейин қулфланадиган қийматлар.
     */
    private void renameCompany() {
        var settings = settingsService.get();
        if (!DEFAULT_COMPANY_NAME.equals(settings.getName())) {
            return;
        }
        settingsService.update(COMPANY_NAME, settings.homeCurrencyCode(),
                settings.getTimezone(), settings.getInventoryValuation(),
                settings.getClosingDate());
    }

    // ---- каталоглар ----

    /**
     * Барча каталог ёзувларини (омбор, контакт, товар/хизмат) яратиб
     * ҳужжат қадамлари учун id харитасини қайтаради.
     */
    private Catalog seedCatalog() {
        Map<String, UUID> accounts = accountIdsByName();
        UUID mainWarehouse = warehouseIdByName(MAIN_WAREHOUSE);
        UUID shopWarehouse = ensureShopWarehouse();
        UUID vatRate = vatRateId();
        Catalog catalog = new Catalog(seedCustomers(), seedVendors(),
                seedItems(vatRate), mainWarehouse, shopWarehouse,
                accounts.get(UZS_BANK), accounts.get(USD_BANK),
                accounts.get(CASH_ACCOUNT), vatRate);
        log.info("Demo каталог: {} мижоз, {} етказувчи, {} товар/хизмат",
                catalog.customers().size(), catalog.vendors().size(),
                catalog.items().size());
        return catalog;
    }

    /**
     * Счёт номи → id харитаси. CHECKING турида иккита счёт бор (UZS ва
     * USD), шунинг учун {@code requireSystemAccount(detailType)} ишламайди -
     * default chart номлари бўйича оламиз (номлар CSV'да қатъий).
     */
    private Map<String, UUID> accountIdsByName() {
        Map<String, UUID> byName = new HashMap<>();
        for (Account account : accountService.all()) {
            byName.put(account.getName(), account.getId());
        }
        return byName;
    }

    /** Омборни номи бўйича топади (seed «Асосий омбор» доим мавжуд). */
    private UUID warehouseIdByName(String name) {
        return warehouseService.all().stream()
                .filter(w -> name.equals(w.getName()))
                .map(w -> w.getId())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Омбор топилмади: " + name));
    }

    /**
     * Дўкон омборини яратади (мавжуд бўлса ўшани олади) - multi-warehouse
     * қолдиқ ҳисоботи иккита омборни кўрсатсин.
     */
    private UUID ensureShopWarehouse() {
        return warehouseService.all().stream()
                .filter(w -> SHOP_WAREHOUSE.equals(w.getName()))
                .map(w -> w.getId())
                .findFirst()
                .orElseGet(() -> warehouseService.create(SHOP_WAREHOUSE, "SHOP").getId());
    }

    /** Стандарт ҚҚС ставкаси id'си; каталогда бўлмаса null (солиқсиз демо). */
    private UUID vatRateId() {
        return taxRateService.activeRates().stream()
                .filter(rate -> VAT_CODE.equals(rate.getCode()))
                .map(rate -> rate.getId())
                .findFirst()
                .orElse(null);
    }

    /** Тўлов шартини кун сони бўйича топади (Net 15/30); топилмаса null. */
    private UUID paymentTermByDays(int days) {
        return paymentTermService.active().stream()
                .filter(term -> term.getDays() == days)
                .map(term -> term.getId())
                .findFirst()
                .orElse(null);
    }

    /** Бирликни номи бўйича топади (дона/соат); топилмаса null - бирликсиз item. */
    private UUID unitByName(String name) {
        return unitService.activeUnits().stream()
                .filter(unit -> name.equals(unit.getName()))
                .map(unit -> unit.getId())
                .findFirst()
                .orElse(null);
    }

    /**
     * 6 та мижоз: турли тўлов шарти ва credit limit билан, биттаси
     * (Global Trade) USD валютали - multi-currency AR намойиши учун.
     * Лимит доим МИЖОЗ валютасида (BR-CON-006 / creditCheck қоидаси).
     */
    private Map<String, UUID> seedCustomers() {
        UUID net15 = paymentTermByDays(15);
        UUID net30 = paymentTermByDays(30);
        UUID immediate = paymentTermByDays(0);
        Map<String, UUID> customers = new HashMap<>();
        customers.put("BARKAMOL", customer("«Баркамол Савдо» МЧЖ", "info@barkamol.uz",
                "+998 71 200-14-25", null, net30, "302145678",
                new BigDecimal("150000000")));
        customers.put("NAVRUZ", customer("«Наврўз Маркет» МЧЖ", "savdo@navruzmarket.uz",
                "+998 71 244-08-31", null, net15, "303871204",
                new BigDecimal("80000000")));
        customers.put("AZIZOV", customer("ЯТТ «Азизов Шерзод»", "sh.azizov@mail.uz",
                "+998 90 331-77-14", null, immediate, "451209873",
                new BigDecimal("25000000")));
        customers.put("TEXNO", customer("«Тошкент Техно Сервис» МЧЖ", "office@ttservis.uz",
                "+998 71 207-56-90", null, net30, "306554120",
                new BigDecimal("120000000")));
        customers.put("GLOBAL", customer("Global Trade LLC", "orders@globaltrade.com",
                "+1 212 555-0148", "USD", net30, "771002456",
                new BigDecimal("25000")));
        customers.put("ZARAFSHON", customer("«Зарафшон Логистика» МЧЖ", "info@zarlog.uz",
                "+998 66 233-19-02", null, net15, "208994512",
                new BigDecimal("60000000")));
        return customers;
    }

    /** Битта мижоз ясагич - такрорланувчи ContactData қуришни қисқартиради. */
    private UUID customer(String name, String email, String phone, String currency,
                          UUID paymentTermId, String taxId, BigDecimal creditLimit) {
        return contactService.create(ContactType.CUSTOMER, new ContactService.ContactData(
                name, name, null, null, email, phone, currency, paymentTermId,
                taxId, creditLimit, null)).getId();
    }

    /**
     * 4 та етказувчи: техника, офис моллари, чет эл (USD) ва транспорт.
     * Vendor'да credit limit бўлмайди (BR-CON-006 - фақат мижозники).
     */
    private Map<String, UUID> seedVendors() {
        UUID net15 = paymentTermByDays(15);
        UUID net30 = paymentTermByDays(30);
        UUID immediate = paymentTermByDays(0);
        Map<String, UUID> vendors = new HashMap<>();
        vendors.put("ORIENT", vendor("«Ориент Электроникс» МЧЖ", "sales@orientel.uz",
                "+998 71 150-22-40", null, net30, "301778452"));
        vendors.put("PAPER", vendor("«Пейпер Плюс» МЧЖ", "zakaz@paperplus.uz",
                "+998 71 279-63-18", null, net15, "305112097"));
        vendors.put("SUNRISE", vendor("Sunrise Electronics Ltd", "export@sunrise-el.com",
                "+86 755 8888 1200", "USD", net30, "990114872"));
        vendors.put("KARIMOV", vendor("ЯТТ «Каримов Транс»", "karimov.trans@mail.uz",
                "+998 93 415-60-27", null, immediate, "452330118"));
        return vendors;
    }

    /** Битта етказувчи ясагич. */
    private UUID vendor(String name, String email, String phone, String currency,
                        UUID paymentTermId, String taxId) {
        return contactService.create(ContactType.VENDOR, new ContactService.ContactData(
                name, name, null, null, email, phone, currency, paymentTermId,
                taxId, null, null)).getId();
    }

    /**
     * 6 та омбор товари + 4 та хизмат. Товарларда ҚҚС ставкаси default
     * қилиб қўйилади (ҳужжат формаси олдиндан тўлдиради), хизматларда
     * йўқ - демода солиқли ва солиқсиз сатрлар аралаш кўринсин.
     */
    private Map<String, UUID> seedItems(UUID vatRate) {
        UUID piece = unitByName("дона");
        UUID hour = unitByName("соат");
        ItemService.DefaultAccounts goods = itemService.defaultsFor(ItemType.INVENTORY);
        ItemService.DefaultAccounts services = itemService.defaultsFor(ItemType.SERVICE);
        Map<String, UUID> items = new HashMap<>();
        items.put("NOTEBOOK", inventoryItem("Ноутбук Lenovo ThinkPad E14", "NB-E14", piece,
                "12500000", "9800000", "5", goods, vatRate));
        items.put("PRINTER", inventoryItem("Принтер HP LaserJet M404dn", "PR-M404", piece,
                "4200000", "3250000", "4", goods, vatRate));
        items.put("MONITOR", inventoryItem("Монитор Dell P2422H 24\"", "MN-P2422", piece,
                "3100000", "2350000", "6", goods, vatRate));
        items.put("PAPER", inventoryItem("Офис қоғози A4 (500 варақ)", "PP-A4", piece,
                "65000", "45000", "50", goods, vatRate));
        items.put("KEYBOARD", inventoryItem("Клавиатура ва сичқонча тўплами Logitech MK270",
                "KB-MK270", piece, "480000", "340000", "10", goods, vatRate));
        items.put("ROUTER", inventoryItem("Wi-Fi роутер TP-Link Archer C6", "RT-C6", piece,
                "690000", "495000", "8", goods, vatRate));
        items.put("DELIVERY", serviceItem("Етказиб бериш хизмати", "SRV-DLV", piece,
                "350000", services));
        items.put("INSTALL", serviceItem("Ўрнатиш ва созлаш", "SRV-INST", piece,
                "800000", services));
        items.put("CONSULT", serviceItem("IT консультация (соат)", "SRV-CONS", hour,
                "450000", services));
        items.put("MAINT", serviceItem("Кафолатли техник хизмат (ойлик)", "SRV-MAINT", piece,
                "1200000", services));
        return items;
    }

    /** Омбор товари ясагич: даромад/COGS/inventory asset счётлари тип default'идан. */
    private UUID inventoryItem(String name, String sku, UUID unitId, String salesPrice,
                               String purchaseCost, String reorderPoint,
                               ItemService.DefaultAccounts accounts, UUID vatRate) {
        return itemService.create(ItemType.INVENTORY, new ItemService.ItemData(
                name, sku, null, unitId, new BigDecimal(salesPrice), null,
                accounts.income(), new BigDecimal(purchaseCost), null,
                accounts.expense(), accounts.inventoryAsset(),
                new BigDecimal(reorderPoint), null, null, vatRate, vatRate)).getId();
    }

    /** Хизмат ясагич: омбор счёти йўқ (SERVICE тип уни сақламайди). */
    private UUID serviceItem(String name, String sku, UUID unitId, String salesPrice,
                             ItemService.DefaultAccounts accounts) {
        return itemService.create(ItemType.SERVICE, new ItemService.ItemData(
                name, sku, null, unitId, new BigDecimal(salesPrice), null,
                accounts.income(), null, null, accounts.expense(), null, null)).getId();
    }

    // ---- ҳужжатлар ----

    /**
     * 5 та POSTED харид: тўртталаси home валютада, биттаси USD
     * (Sunrise) - AP aging ва multi-currency бирга кўрингани учун.
     * Ҳар bill 2-3 сатрли; охиргисида ташиш харажати EXPENSE сатр
     * билан (харид ҳужжати фақат товардан иборат эмаслиги кўрсин).
     *
     * @return яратилган bill id'лари - тўлов қадами уларга таянади
     */
    private List<UUID> seedBills(Catalog catalog) {
        UUID orient = catalog.vendors().get("ORIENT");
        UUID warehouse = catalog.mainWarehouse();
        UUID vat = catalog.vatRate();
        List<UUID> bills = new ArrayList<>();
        bills.add(postBill(orient, "ОЭ-2451", daysAgo(88), null, null,
                "Ноутбук ва мониторлар партияси", List.of(
                        billItem(catalog, "NOTEBOOK", warehouse, "10", "9800000", vat),
                        billItem(catalog, "MONITOR", warehouse, "8", "2350000", vat))));
        bills.add(postBill(catalog.vendors().get("PAPER"), "ПП-1180", daysAgo(74), null, null,
                "Офис моллари", List.of(
                        billItem(catalog, "PAPER", warehouse, "200", "45000", vat),
                        billItem(catalog, "KEYBOARD", warehouse, "20", "340000", vat))));
        bills.add(postBill(orient, "ОЭ-2688", daysAgo(55), null, null,
                "Принтер ва тармоқ ускуналари", List.of(
                        billItem(catalog, "PRINTER", warehouse, "12", "3250000", vat),
                        billItem(catalog, "ROUTER", warehouse, "15", "495000", vat))));
        // Импорт партияси: валюта КОНТАКТдан келади (BR-BILL-013), курс
        // ҳужжатда қатъий сақланади - ҚҚС қўйилмайди (импорт ҚҚСи алоҳида оқим)
        bills.add(postBill(catalog.vendors().get("SUNRISE"), "SE-7741", daysAgo(36),
                "USD", USD_RATE, "Импорт партияси (Shenzhen)", List.of(
                        billItem(catalog, "NOTEBOOK", warehouse, "5", "780", null),
                        billItem(catalog, "MONITOR", warehouse, "6", "185", null))));
        bills.add(postBill(orient, "ОЭ-2903", daysAgo(15), null, null,
                "Сентябрь партияси + етказиб бериш", List.of(
                        billItem(catalog, "NOTEBOOK", warehouse, "6", "9950000", vat),
                        billItem(catalog, "MONITOR", warehouse, "10", "2380000", vat),
                        billExpense(accountIdByDetail(AccountDetailType.SHIPPING_FREIGHT_DELIVERY_COS),
                                "1200000", "Партияни олиб келиш"))));
        return bills;
    }

    /** Bill'ни draft қилиб яратиб дарҳол post қилади (демода DRAFT қолмайди). */
    private UUID postBill(UUID vendorId, String vendorInvoiceNumber, LocalDate date,
                          String currency, BigDecimal rate, String memo,
                          List<BillService.LineData> lines) {
        UUID id = billService.createDraft(new BillService.BillData(vendorId,
                vendorInvoiceNumber, date, null, currency, rate, memo, lines)).getId();
        billService.post(id);
        return id;
    }

    /** Bill'нинг омбор сатри: миқдор × нарх, ҚҚС ставкаси ихтиёрий. */
    private BillService.LineData billItem(Catalog catalog, String itemKey, UUID warehouseId,
                                          String qty, String price, UUID taxRateId) {
        return new BillService.LineData(BillLineType.ITEM, catalog.items().get(itemKey),
                warehouseId, new BigDecimal(qty), new BigDecimal(price), null, null,
                null, null, null, taxRateId, null, null);
    }

    /** Bill'нинг харажат сатри: счёт + сумма (омборга тегмайди). */
    private BillService.LineData billExpense(UUID accountId, String amount, String memo) {
        return new BillService.LineData(BillLineType.EXPENSE, null, null, null, null,
                accountId, new BigDecimal(amount), memo);
    }

    /**
     * 8 та POSTED сотув: турли мижоз, охирги 3 ойга тарқалган сана,
     * товар+хизмат аралаш сатрлар; биттаси USD (Global Trade).
     * Сотилган миқдорлар харид қилинганидан кам - post'да BR-SINV-004
     * (қолдиқ етарлимас) чиқмайди.
     *
     * @return яратилган invoice id'лари - тушум қадами уларга таянади
     */
    private List<UUID> seedInvoices(Catalog catalog) {
        UUID warehouse = catalog.mainWarehouse();
        UUID vat = catalog.vatRate();
        List<UUID> invoices = new ArrayList<>();
        invoices.add(postInvoice(catalog.customers().get("BARKAMOL"), daysAgo(80), null, null,
                "Офис учун ноутбуклар", List.of(
                        invoiceItem(catalog, "NOTEBOOK", warehouse, "3", "12500000", vat),
                        invoiceLine(catalog, "INSTALL", "1", "800000", vat))));
        invoices.add(postInvoice(catalog.customers().get("NAVRUZ"), daysAgo(66), null, null,
                "Дўкон учун техника", List.of(
                        invoiceItem(catalog, "PRINTER", warehouse, "2", "4200000", vat),
                        invoiceItem(catalog, "PAPER", warehouse, "30", "65000", vat),
                        invoiceLine(catalog, "DELIVERY", "1", "350000", null))));
        invoices.add(postInvoice(catalog.customers().get("AZIZOV"), daysAgo(52), null, null,
                "Иш жойлари жиҳозлаш", List.of(
                        invoiceItem(catalog, "MONITOR", warehouse, "4", "3100000", vat),
                        invoiceItem(catalog, "KEYBOARD", warehouse, "2", "480000", vat))));
        // Экспорт: валюта мижоздан (USD), курс ҳужжатда қотирилади
        invoices.add(postInvoice(catalog.customers().get("GLOBAL"), daysAgo(41),
                "USD", USD_RATE, "Export order GT-118", List.of(
                        invoiceItem(catalog, "NOTEBOOK", warehouse, "2", "1150", null),
                        invoiceLine(catalog, "CONSULT", "3", "40", null))));
        invoices.add(postInvoice(catalog.customers().get("TEXNO"), daysAgo(31), null, null,
                "Тармоқ ускуналари ва созлаш", List.of(
                        invoiceItem(catalog, "ROUTER", warehouse, "5", "690000", vat),
                        invoiceLine(catalog, "INSTALL", "1", "800000", vat),
                        invoiceLine(catalog, "CONSULT", "4", "450000", null))));
        invoices.add(postInvoice(catalog.customers().get("BARKAMOL"), daysAgo(22), null, null,
                "Иккинчи партия", List.of(
                        invoiceItem(catalog, "NOTEBOOK", warehouse, "4", "12600000", vat),
                        invoiceItem(catalog, "MONITOR", warehouse, "3", "3100000", vat),
                        invoiceLine(catalog, "DELIVERY", "1", "350000", null))));
        invoices.add(postInvoice(catalog.customers().get("ZARAFSHON"), daysAgo(11), null, null,
                "Офис таъминоти + йиллик хизмат", List.of(
                        invoiceItem(catalog, "PAPER", warehouse, "50", "65000", vat),
                        invoiceItem(catalog, "KEYBOARD", warehouse, "3", "480000", vat),
                        invoiceLine(catalog, "MAINT", "1", "1200000", null))));
        invoices.add(postInvoice(catalog.customers().get("NAVRUZ"), daysAgo(4), null, null,
                "Филиал учун техника", List.of(
                        invoiceItem(catalog, "PRINTER", warehouse, "3", "4250000", vat),
                        invoiceItem(catalog, "MONITOR", warehouse, "2", "3150000", vat),
                        invoiceLine(catalog, "INSTALL", "1", "800000", vat))));
        return invoices;
    }

    /** Invoice'ни draft қилиб яратиб дарҳол post қилади. */
    private UUID postInvoice(UUID customerId, LocalDate date, String currency,
                             BigDecimal rate, String memo,
                             List<InvoiceService.LineData> lines) {
        UUID id = invoiceService.createDraft(new InvoiceService.InvoiceData(customerId,
                date, null, currency, rate, memo, lines)).getId();
        invoiceService.post(id);
        return id;
    }

    /** Invoice'нинг омбор сатри - омбор МАЖБУРИЙ (BR-SINV-004). */
    private InvoiceService.LineData invoiceItem(Catalog catalog, String itemKey,
                                                UUID warehouseId, String qty,
                                                String price, UUID taxRateId) {
        return new InvoiceService.LineData(catalog.items().get(itemKey), warehouseId,
                new BigDecimal(qty), new BigDecimal(price), null, null,
                null, taxRateId, null, null);
    }

    /** Invoice'нинг хизмат сатри - омбор йўқ (SERVICE омборга тегмайди). */
    private InvoiceService.LineData invoiceLine(Catalog catalog, String itemKey,
                                                String qty, String price,
                                                UUID taxRateId) {
        return new InvoiceService.LineData(catalog.items().get(itemKey), null,
                new BigDecimal(qty), new BigDecimal(price), null, null,
                null, taxRateId, null, null);
    }

    /**
     * 4 та мижоз тушуми: иккитаси тўлиқ, иккитаси қисман - AR aging
     * ҳисоботида «тўланган / қисман / очиқ» уч ҳолат ҳам кўринсин.
     * USD тушум курси invoice курсидан фарқли - realized курс фарқи
     * проводкаси демода ҳам ҳосил бўлади.
     */
    private void seedInvoicePayments(Catalog catalog, List<UUID> invoices) {
        receipt(catalog.customers().get("BARKAMOL"), daysAgo(72), catalog.uzsBank(),
                null, null, invoices.get(0), balanceOfInvoice(invoices.get(0)),
                "Тўлиқ тўлов (пластик карта)");
        receipt(catalog.customers().get("NAVRUZ"), daysAgo(58), catalog.uzsBank(),
                null, null, invoices.get(1), part(balanceOfInvoice(invoices.get(1)), "0.60"),
                "Қисман тўлов");
        receipt(catalog.customers().get("TEXNO"), daysAgo(24), catalog.uzsBank(),
                null, null, invoices.get(4), balanceOfInvoice(invoices.get(4)),
                "Тўлиқ тўлов");
        receipt(catalog.customers().get("GLOBAL"), daysAgo(18), catalog.usdBank(),
                "USD", USD_PAYMENT_RATE, invoices.get(3),
                part(balanceOfInvoice(invoices.get(3)), "0.50"), "Advance 50%");
    }

    /** Битта тушум: тақсимоти билан бирга (allocation тушум ичида келади). */
    private void receipt(UUID customerId, LocalDate date, UUID depositAccountId,
                         String currency, BigDecimal rate, UUID invoiceId,
                         BigDecimal amount, String memo) {
        invoicePaymentService.create(new InvoicePaymentService.PaymentData(customerId,
                date, depositAccountId, currency, rate, amount, memo,
                List.of(new InvoicePaymentService.AllocationData(invoiceId, amount))));
    }

    /**
     * 3 та етказувчи тўлови: иккитаси тўлиқ, биттаси қисман. USD bill
     * (Sunrise) ва охирги bill атайлаб очиқ қолдирилади - AP aging
     * жадвалида «муддати ўтган / жорий» иккиси ҳам бўлсин.
     */
    private void seedBillPayments(Catalog catalog, List<UUID> bills) {
        payment(catalog.vendors().get("ORIENT"), daysAgo(82), catalog.uzsBank(),
                bills.get(0), balanceOfBill(bills.get(0)), "Тўлиқ тўлов");
        payment(catalog.vendors().get("PAPER"), daysAgo(62), catalog.uzsBank(),
                bills.get(1), part(balanceOfBill(bills.get(1)), "0.50"), "Қисман тўлов");
        payment(catalog.vendors().get("ORIENT"), daysAgo(40), catalog.uzsBank(),
                bills.get(2), balanceOfBill(bills.get(2)), "Тўлиқ тўлов");
    }

    /** Битта етказувчи тўлови тақсимоти билан. */
    private void payment(UUID vendorId, LocalDate date, UUID bankAccountId,
                         UUID billId, BigDecimal amount, String memo) {
        billPaymentService.create(new BillPaymentService.PaymentData(vendorId, date,
                bankAccountId, null, null, amount, memo,
                List.of(new BillPaymentService.AllocationData(billId, amount))));
    }

    /**
     * 4 та банк харажати (QBO Expense оқими - AP'сиз тўғри тўлов):
     * ижара, коммунал, транспорт ва банк хизмати. Transport сатрига
     * контакт (Каримов Транс) боғланади - GL dimension демода кўринсин.
     */
    private void seedExpenses(Catalog catalog) {
        expense(catalog.uzsBank(), daysAgo(70), null, "Июль ойи офис ижараси",
                accountIdByDetail(AccountDetailType.RENT_OR_LEASE_OF_BUILDINGS),
                "4500000", null);
        expense(catalog.uzsBank(), daysAgo(56), null, "Электр ва сув (коммунал)",
                accountIdByDetail(AccountDetailType.UTILITIES), "1350000", null);
        expense(catalog.uzsBank(), daysAgo(38), catalog.vendors().get("KARIMOV"),
                "Товар ташиш хизмати",
                accountIdByDetail(AccountDetailType.SHIPPING_FREIGHT_DELIVERY_COS),
                "980000", catalog.vendors().get("KARIMOV"));
        expense(catalog.uzsBank(), daysAgo(9), null, "Банк хизмат ҳақи (ойлик)",
                accountIdByDetail(AccountDetailType.BANK_CHARGES), "320000", null);
    }

    /** Битта сатрли банк чиқими - харажат счёти Dt / банк Cr. */
    private void expense(UUID bankAccountId, LocalDate date, UUID contactId, String memo,
                         UUID expenseAccountId, String amount, UUID lineContactId) {
        bankService.expense(new BankTransactionService.TxnData(bankAccountId, date,
                null, contactId, memo, List.of(new BankTransactionService.LineData(
                        expenseAccountId, new BigDecimal(amount), lineContactId, null))));
    }

    /** Банкдан кассага ўтказма - банк журналида TRANSFER тури ҳам кўринсин. */
    private void seedBankTransfer(Catalog catalog) {
        bankService.transfer(new BankTransactionService.TransferData(catalog.uzsBank(),
                catalog.cash(), daysAgo(33), new BigDecimal("8000000"), null,
                null, null, "Кассага кунлик эҳтиёж учун"));
    }

    /**
     * Омбор ҳужжатлари: асосий омбордан дўконга кўчириш (multi-warehouse
     * қолдиқ намойиши) ва қоғоз бўйича инвентаризация акти (камомад).
     * Иккиси ҳам сотув ҳужжатларидан КЕЙИН - қолдиқ етарли бўлсин.
     */
    private void seedStockDocuments(Catalog catalog) {
        inventoryService.transferDocument(new InventoryService.DocumentTransferData(
                catalog.mainWarehouse(), catalog.shopWarehouse(), daysAgo(13),
                "Дўкон витринасига", List.of(
                        new InventoryService.TransferLineData(
                                catalog.items().get("NOTEBOOK"), new BigDecimal("3"), null),
                        new InventoryService.TransferLineData(
                                catalog.items().get("MONITOR"), new BigDecimal("4"), null))));
        // Акт ЯНГИ қолдиқни киритади (delta авто) - жорий қолдиқдан 2 дона кам
        UUID paper = catalog.items().get("PAPER");
        BigDecimal counted = inventoryService.quantityOnHand(paper, catalog.mainWarehouse())
                .subtract(new BigDecimal("2"));
        inventoryService.adjustDocument(new InventoryService.DocumentAdjustData(
                catalog.mainWarehouse(), daysAgo(6), "Ойлик инвентаризация - камомад",
                List.of(new InventoryService.AdjustLineData(paper, counted, null,
                        "Қоғоз ғилофи шикастланган"))));
    }

    // ---- ёрдамчилар ----

    /**
     * Detail type бўйича ягона фаол postable счёт id'си - default
     * chart'да бу турлар биттадан (CHECKING эмас!), шунинг учун
     * тизим счёти резолвери ишлайди.
     */
    private UUID accountIdByDetail(AccountDetailType detailType) {
        return accountService.requireSystemAccountId(detailType);
    }

    /** Бугундан {@code days} кун олдинги сана (компания минтақасида). */
    private LocalDate daysAgo(int days) {
        return LocalDate.now(settingsService.zoneId()).minusDays(days);
    }

    /** Invoice'нинг жорий қолдиғи - тушум суммаси шундан олинади. */
    private BigDecimal balanceOfInvoice(UUID invoiceId) {
        return invoiceService.get(invoiceId).getBalanceDue();
    }

    /** Bill'нинг жорий қолдиғи - тўлов суммаси шундан олинади. */
    private BigDecimal balanceOfBill(UUID billId) {
        return billService.get(billId).getBalanceDue();
    }

    /**
     * Қолдиқнинг бир қисми (масалан 60%) - қисман тўловлар учун.
     * 2 хонагача яхлитланади: тўлов суммаси экранда доим 2 хона.
     */
    private BigDecimal part(BigDecimal balance, String ratio) {
        return balance.multiply(new BigDecimal(ratio)).setScale(2, RoundingMode.HALF_UP);
    }
}
