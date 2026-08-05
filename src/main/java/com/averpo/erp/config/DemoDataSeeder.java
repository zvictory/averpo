package com.averpo.erp.config;

import com.averpo.erp.bank.service.BankTransactionService;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.inventory.service.InventoryService;
import com.averpo.erp.inventory.service.WarehouseService;
import com.averpo.erp.item.domain.ItemType;
import com.averpo.erp.item.service.ItemCategoryService;
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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@code demo} профилидаги намойиш маълумотлари: тақдимот скриншотлари
 * ва ҳакамлар иловани ўзи юргизиб кўриши учун «Озод Савдо» деган
 * шартли ўзбек савдо компаниясининг ЙИЛ БОШИДАН бугунгача бўлган
 * ҳаёти - кенг мижоз/етказувчи каталоги, товар-хизмат каталоги,
 * кўп омборли қолдиқлар, ойма-ой харид ва сотув ҳужжатлари, тўлиқ
 * ва қисман тўловлар, банк харажатлари, омбор кўчириш ва
 * инвентаризация актлари.
 *
 * <p><b>Нега керак:</b> бўш база билан dashboard графиги, P&amp;L
 * даврлари, AR/AP aging ва омбор ҳисоботлари бўм-бўш кўринади -
 * тизимнинг қиймати кўринмайди. Маълумот январдан бугунгача
 * тарқалгани учун йил бошидан ҳисобот, ойлик тренд ва муддат
 * таҳлили (жорий / муддати ўтган) мазмунли чиқади.
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
 * ҳам order'сиз (LOWEST_PRECEDENCE) - тенг вазнда тартиб
 * кафолатланмайди, шунинг учун {@link #ensureCatalogs()} шартларни
 * ЎЗИ ҳам idempotent равишда таъминлайди.
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

    /** USD тўлов курси - ҳужжат курсидан фарқли, шунда realized курс
     * фарқи проводкаси ҳам демода кўринади (multi-currency намойиши). */
    private static final BigDecimal USD_PAYMENT_RATE = new BigDecimal("12750");

    /** Йил бошида банкка киритиладиган устав капитали - шусиз биринчи
     * ойларда банк қолдиғи манфийга тушиб, dashboard хунук кўринарди. */
    private static final BigDecimal OPENING_CAPITAL = new BigDecimal("300000000");

    /** Асосий (марказий) омбор номи - Liquibase seed'дан келади. */
    private static final String MAIN_WAREHOUSE = "Асосий омбор";

    /** Қўшимча омборлар: ном ва код. Кўп-омбор (QBO'дан фарқимиз)
     * қолдиқ ҳисоботида ва кўчириш актларида кўрингани учун. */
    private static final List<String[]> EXTRA_WAREHOUSES = List.of(
            new String[]{"Чилонзор дўкони", "CHIL"},
            new String[]{"Юнусобод дўкони", "YUN"},
            new String[]{"Сергели омбори", "SER"},
            new String[]{"Мирзо Улуғбек дўкони", "MIRZO"},
            new String[]{"Транзит омбори", "TRANS"});

    /** Home валютадаги банк счёти номи (default chart). */
    private static final String UZS_BANK = "Банк ҳисобварағи";

    /** USD банк счёти номи - чет валюта тўлови фақат шунга тушади (BR-RCPT-002). */
    private static final String USD_BANK = "Валюта ҳисобварағи (USD)";

    /** Касса счёти номи - банкдан ўтказма манзили. */
    private static final String CASH_ACCOUNT = "Касса";

    /** Стандарт ҚҚС ставкаси коди (032-tax seed). */
    private static final String VAT_CODE = "QQS12";

    /** Ой номлари (messages.properties'даги month.N билан бир хил) -
     * ҳужжат изоҳларида «Январь партияси» каби ёзув учун. */
    private static final String[] MONTHS = {
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"};

    /** Мижоз номлари - реалистик ўзбек ташкилий-ҳуқуқий шакллари
     * (МЧЖ/ЯТТ/ХК) ва чет эл харидорлари. Индекс {@link #USD_CUSTOMERS}
     * билан боғланади. */
    private static final List<String> CUSTOMER_NAMES = List.of(
            "«Баркамол Савдо» МЧЖ", "«Наврўз Маркет» МЧЖ", "ЯТТ «Азизов Шерзод»",
            "«Тошкент Техно Сервис» МЧЖ", "«Зарафшон Логистика» МЧЖ",
            "«Олтин Водий» МЧЖ", "«Ситора Трейд» МЧЖ", "ЯТТ «Каримова Дилноза»",
            "«Мега Офис» МЧЖ", "«Соҳибкор Бизнес» МЧЖ", "«Ипак Йўли Савдо» МЧЖ",
            "«Нурафшон Групп» МЧЖ", "ХК «Тошкент Дон Маҳсулотлари»",
            "«Азия Компьютер» МЧЖ", "ЯТТ «Раҳимов Бахтиёр»", "«Юксалиш Қурилиш» МЧЖ",
            "«Самарқанд Савдо Уйи» МЧЖ", "«Бухоро Текстиль» МЧЖ", "«Фарғона Агро» МЧЖ",
            "«Наманган Мебель» МЧЖ", "ЯТТ «Юсупов Жасур»", "«Андижон Авто Сервис» МЧЖ",
            "«Қарши Нон» МЧЖ", "«Нукус Балиқ» МЧЖ", "«Термиз Транс» МЧЖ",
            "ХК «Ўзбек Темир Йўл Сервис»", "Global Trade LLC", "Silk Road Partners LLC",
            "Astana Digital LLP", "«Жиззах Пластик» МЧЖ");

    /** USD валютали мижозлар индекслари ({@link #CUSTOMER_NAMES} ичида) -
     * уларнинг ҳужжатлари чет валютада ёзилади (валюта КОНТАКТдан келади). */
    private static final Set<Integer> USD_CUSTOMERS = Set.of(26, 27, 28);

    /** Етказувчи номлари. Индекс {@link #USD_VENDORS} билан боғланади. */
    private static final List<String> VENDOR_NAMES = List.of(
            "«Ориент Электроникс» МЧЖ", "«Пейпер Плюс» МЧЖ", "ЯТТ «Каримов Транс»",
            "«Техно Импорт» МЧЖ", "«Офис Мастер» МЧЖ", "«Компьютер Ленд» МЧЖ",
            "«Марказий Логистика» МЧЖ", "ХК «Тошкент Таъминот»", "«Сифат Картридж» МЧЖ",
            "«Тошкент Кабель» МЧЖ", "ЯТТ «Собиров Фарҳод»", "«Ситиком Тармоқ» МЧЖ",
            "«Ал-Аҳир Таъминот» МЧЖ", "«Инфо Систем» МЧЖ", "«Гулистон Мебель» МЧЖ",
            "«Замин Реклама» МЧЖ", "Sunrise Electronics Ltd", "Shenzhen TechPro Co Ltd",
            "Almaty Office Supply LLP", "«Ободон Сервис» МЧЖ");

    /** USD валютали етказувчилар индекслари - импорт харидлари шулардан. */
    private static final Set<Integer> USD_VENDORS = Set.of(16, 17, 18);

    /** Item категориялари - каталог филтри ва ҳисоботларда гуруҳлаш учун. */
    private static final List<String> CATEGORIES = List.of(
            "Компьютер техникаси", "Оргтехника", "Аксессуарлар",
            "Сарф материаллар", "Хизматлар");

    /**
     * Каталог қаторининг таърифи - {@link #seedItems} шу массив
     * бўйлаб айланиб item яратади (қўлда 20 та чақирув ёзилмасин).
     *
     * @param name       каталогдаги ном (unique - BR-ITM-002)
     * @param sku        артикул (unique - BR-ITM-003)
     * @param category   категория номи ({@link #CATEGORIES} ичидан)
     * @param inventory  омбор товарими (false - хизмат)
     * @param salesPrice сотув нархи, home валютада (сўм)
     * @param cost       харид таннархи (хизматда null)
     * @param unit       ўлчов бирлиги номи (seed бирликлардан)
     */
    private record ItemSpec(String name, String sku, String category, boolean inventory,
                            String salesPrice, String cost, String unit) { }

    /** Товар ва хизмат каталогининг тўлиқ таърифи (16 товар + 4 хизмат). */
    private static final List<ItemSpec> ITEM_SPECS = List.of(
            new ItemSpec("Ноутбук Lenovo ThinkPad E14", "NB-E14", "Компьютер техникаси",
                    true, "12500000", "9800000", "дона"),
            new ItemSpec("Ноутбук HP ProBook 450 G9", "NB-PB450", "Компьютер техникаси",
                    true, "13900000", "10900000", "дона"),
            new ItemSpec("Компьютер Dell OptiPlex 3000", "PC-OP3000", "Компьютер техникаси",
                    true, "9200000", "7100000", "дона"),
            new ItemSpec("Монитор Dell P2422H 24 дюйм", "MN-P2422", "Компьютер техникаси",
                    true, "3100000", "2350000", "дона"),
            new ItemSpec("Монитор LG 27UP550 27 дюйм", "MN-27UP", "Компьютер техникаси",
                    true, "4650000", "3600000", "дона"),
            new ItemSpec("Принтер HP LaserJet M404dn", "PR-M404", "Оргтехника",
                    true, "4200000", "3250000", "дона"),
            new ItemSpec("МФУ Canon i-SENSYS MF443dw", "PR-MF443", "Оргтехника",
                    true, "5800000", "4500000", "дона"),
            new ItemSpec("Сканер Epson Perfection V39", "SC-V39", "Оргтехника",
                    true, "1650000", "1200000", "дона"),
            new ItemSpec("Проектор Epson EB-X06", "PJ-EBX06", "Оргтехника",
                    true, "7100000", "5500000", "дона"),
            new ItemSpec("Клавиатура ва сичқонча тўплами Logitech MK270", "AC-MK270",
                    "Аксессуарлар", true, "480000", "340000", "дона"),
            new ItemSpec("Wi-Fi роутер TP-Link Archer C6", "AC-ARC6", "Аксессуарлар",
                    true, "690000", "495000", "дона"),
            new ItemSpec("USB флеш Kingston 64GB", "AC-KG64", "Аксессуарлар",
                    true, "125000", "90000", "дона"),
            new ItemSpec("Ташқи диск Seagate 1TB", "AC-ST1TB", "Аксессуарлар",
                    true, "1050000", "780000", "дона"),
            new ItemSpec("Тармоқ кабели UTP cat.6", "AC-UTP6", "Аксессуарлар",
                    true, "14000", "9500", "метр"),
            new ItemSpec("Офис қоғози A4 (500 варақ)", "SP-A4", "Сарф материаллар",
                    true, "65000", "45000", "дона"),
            new ItemSpec("Тонер картриж HP CF259A", "SP-CF259", "Сарф материаллар",
                    true, "580000", "420000", "дона"),
            new ItemSpec("Етказиб бериш хизмати", "SRV-DLV", "Хизматлар",
                    false, "350000", null, "дона"),
            new ItemSpec("Ўрнатиш ва созлаш", "SRV-INST", "Хизматлар",
                    false, "800000", null, "дона"),
            new ItemSpec("IT консультация", "SRV-CONS", "Хизматлар",
                    false, "450000", null, "соат"),
            new ItemSpec("Кафолатли техник хизмат (ойлик)", "SRV-MAINT", "Хизматлар",
                    false, "1200000", null, "дона"));

    /** Компания номи, минтақаси ва home валютаси. */
    private final CompanySettingsService settingsService;

    /** Счётлар режаси - бўшлик гарови ва счёт id'лари (repo эмас, қоида №6). */
    private final AccountService accountService;

    /** UOM гуруҳлари ва бирлик id'лари. */
    private final UnitService unitService;

    /** ҚҚС ставкаси id'си - ҳужжат сатрларига қўйилади. */
    private final TaxRateService taxRateService;

    /** Тўлов шартлари (Net 15/30) - контакт due date'лари учун. */
    private final PaymentTermService paymentTermService;

    /** Омбор каталоги - қўшимча омборлар шу орқали яратилади. */
    private final WarehouseService warehouseService;

    /** Мижоз/етказувчи каталоги. */
    private final ContactService contactService;

    /** Item категориялари каталоги. */
    private final ItemCategoryService itemCategoryService;

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

    /** Банк харажатлари, кирим ва ўтказма. */
    private final BankTransactionService bankService;

    /** Омбор актлари (кўчириш, инвентаризация) ва қолдиқ сўрови. */
    private final InventoryService inventoryService;

    /**
     * Ҳужжат ясагичлар ишлатадиган тайёр id'лар: каталог бир марта
     * яратилиб шу ерда сақланади, кейинги қадамлар (ҳужжатлар) уларни
     * шу record'дан олади - ҳар методга ўнлаб параметр узатилмасин.
     *
     * @param customers  мижоз id'лари, {@link #CUSTOMER_NAMES} тартибида
     * @param vendors    етказувчи id'лари, {@link #VENDOR_NAMES} тартибида
     * @param foreign    чет валютали контактлар id'лари (тез текшириш учун)
     * @param goods      омбор товарлари id'лари (каталог тартибида)
     * @param services   хизматлар id'лари
     * @param salesPrice item → базавий сотув нархи (home валютада)
     * @param cost       item → базавий харид таннархи (home валютада)
     * @param warehouses омбор id'лари; 0-индекс - марказий омбор
     * @param uzsBank    home валютали банк счёти
     * @param usdBank    USD банк счёти
     * @param cash       касса счёти
     * @param capital    устав капитали счёти (йил бошидаги кирим манбаси)
     * @param vatRate    ҚҚС 12% ставкаси (топилмаса null - солиқсиз демо)
     */
    private record Catalog(List<UUID> customers, List<UUID> vendors, Set<UUID> foreign,
                           List<UUID> goods, List<UUID> services,
                           Map<UUID, BigDecimal> salesPrice, Map<UUID, BigDecimal> cost,
                           List<UUID> warehouses, UUID uzsBank, UUID usdBank,
                           UUID cash, UUID capital, UUID vatRate) { }

    /**
     * Яратилган ҳужжатнинг тўлов қадамига керакли қисқа маълумоти -
     * тўлов босқичи entity юкламасин (санадан «ёши», контактдан
     * тўловчи, валютадан банк счёти танланади).
     *
     * @param id        ҳужжат id'си
     * @param contactId мижоз ёки етказувчи id'си
     * @param date      ҳужжат санаси
     * @param foreign   чет валютадами (USD банк счёти талаб қилинади)
     */
    private record Doc(UUID id, UUID contactId, LocalDate date, boolean foreign) { }

    /**
     * Демо базани бир марта тўлдиради: каталоглар → йил бошидан
     * бугунгача ойма-ой ҳужжатлар → тўловлар → омбор актлари.
     * Ҳар қадам ўз service'ининг транзакциясида кетади.
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

        seedOpeningCapital(catalog);
        List<Doc> bills = new ArrayList<>();
        List<Doc> invoices = new ArrayList<>();
        int expenses = 0;
        int stockTransfers = 0;
        int bankTransfers = 0;
        int lastMonth = today().getMonthValue();
        for (int month = 1; month <= lastMonth; month++) {
            bills.addAll(seedMonthBills(catalog, month));
            invoices.addAll(seedMonthInvoices(catalog, month));
            expenses += seedMonthExpenses(catalog, month);
            stockTransfers += seedMonthStockTransfer(catalog, month);
            bankTransfers += seedMonthBankTransfer(catalog, month);
        }
        int receipts = seedInvoicePayments(catalog, invoices);
        int payments = seedBillPayments(catalog, bills);
        int adjustments = seedStockAdjustment(catalog);

        log.info("Demo маълумот тайёр ({} - {} ойлари): {} контакт, {} товар/хизмат, "
                        + "{} омбор, {} bill, {} invoice, {} тушум, {} тўлов, "
                        + "{} харажат, {} банк ўтказма, {} омбор кўчириш, {} акт",
                MONTHS[0], MONTHS[lastMonth - 1],
                catalog.customers().size() + catalog.vendors().size(),
                catalog.goods().size() + catalog.services().size(),
                catalog.warehouses().size(), bills.size(), invoices.size(),
                receipts, payments, expenses, bankTransfers, stockTransfers, adjustments);
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

    /** Барча каталог ёзувларини яратиб ҳужжат қадамлари учун id'ларни қайтаради. */
    private Catalog seedCatalog() {
        Map<String, UUID> accounts = accountIdsByName();
        UUID vatRate = vatRateId();
        List<UUID> warehouses = seedWarehouses();
        Map<String, UUID> categories = seedCategories();
        Set<UUID> foreign = new HashSet<>();
        List<UUID> customers = seedContacts(ContactType.CUSTOMER, CUSTOMER_NAMES,
                USD_CUSTOMERS, foreign);
        List<UUID> vendors = seedContacts(ContactType.VENDOR, VENDOR_NAMES,
                USD_VENDORS, foreign);
        List<UUID> goods = new ArrayList<>();
        List<UUID> services = new ArrayList<>();
        Map<UUID, BigDecimal> salesPrice = new HashMap<>();
        Map<UUID, BigDecimal> cost = new HashMap<>();
        seedItems(categories, vatRate, goods, services, salesPrice, cost);
        log.info("Demo каталог: {} мижоз, {} етказувчи, {} товар, {} хизмат, {} омбор",
                customers.size(), vendors.size(), goods.size(), services.size(),
                warehouses.size());
        return new Catalog(customers, vendors, foreign, goods, services,
                salesPrice, cost, warehouses, accounts.get(UZS_BANK),
                accounts.get(USD_BANK), accounts.get(CASH_ACCOUNT),
                accountIdByDetail(AccountDetailType.COMMON_STOCK), vatRate);
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

    /**
     * Омборлар: seed «Асосий омбор» биринчи (марказий), кейин дўкон/
     * транзит омборлари. Мавжудлари қайта яратилмайди - рўйхат
     * тартиби ҳужжат ясагичлар учун барқарор индекс беради.
     */
    private List<UUID> seedWarehouses() {
        Map<String, UUID> existing = new HashMap<>();
        warehouseService.all().forEach(w -> existing.put(w.getName(), w.getId()));
        List<UUID> warehouses = new ArrayList<>();
        warehouses.add(existing.get(MAIN_WAREHOUSE));
        for (String[] spec : EXTRA_WAREHOUSES) {
            UUID id = existing.get(spec[0]);
            warehouses.add(id != null ? id
                    : warehouseService.create(spec[0], spec[1]).getId());
        }
        return warehouses;
    }

    /** Item категориялари: ном → id (каталог экрани гуруҳлари учун). */
    private Map<String, UUID> seedCategories() {
        Map<String, UUID> categories = new HashMap<>();
        for (String name : CATEGORIES) {
            categories.put(name, itemCategoryService.create(name, null).getId());
        }
        return categories;
    }

    /**
     * Битта тур бўйича контактларни массив бўйлаб яратади: ИНН,
     * телефон, email ва тўлов шарти индексдан детерминистик
     * ҳосил қилинади (қўлда 50 та чақирув ёзилмасин).
     *
     * @param type       CUSTOMER ёки VENDOR
     * @param names      номлар массиви
     * @param usdIndexes чет валютали контактлар индекслари
     * @param foreign    чет валютали id'лар шу тўпламга қўшилади (out-параметр)
     * @return яратилган id'лар, {@code names} билан бир хил тартибда
     */
    private List<UUID> seedContacts(ContactType type, List<String> names,
                                    Set<Integer> usdIndexes, Set<UUID> foreign) {
        boolean customer = type == ContactType.CUSTOMER;
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            boolean usd = usdIndexes.contains(i);
            // Лимит фақат мижозда (BR-CON-006) ва ДОИМ мижоз валютасида
            BigDecimal creditLimit = !customer ? null
                    : usd ? new BigDecimal(5000 + i * 500L)
                    : new BigDecimal((20 + i * 5L) * 1_000_000L);
            UUID id = contactService.create(type, new ContactService.ContactData(
                    names.get(i), names.get(i), null, null,
                    (customer ? "mijoz" : "vendor") + (i + 1) + "@demo.uz",
                    String.format("+998 71 %03d-%02d-%02d", 200 + i, 10 + i % 80, 20 + i % 70),
                    usd ? "USD" : null, rotatingTerm(i),
                    String.valueOf((customer ? 300_000_000L : 400_000_000L) + i * 1237L),
                    creditLimit, null)).getId();
            ids.add(id);
            if (usd) {
                foreign.add(id);
            }
        }
        return ids;
    }

    /** Индекс бўйича тўлов шарти: Net 30 / Net 15 / тўлов дарҳол (айланма). */
    private UUID rotatingTerm(int index) {
        return paymentTermByDays(switch (index % 3) {
            case 0 -> 30;
            case 1 -> 15;
            default -> 0;
        });
    }

    /**
     * Каталогни {@link #ITEM_SPECS} массиви бўйлаб яратади: омбор
     * товарларига ҚҚС default'и қўйилади, хизматларда йўқ - демода
     * солиқли ва солиқсиз сатрлар аралаш кўринсин.
     *
     * @param categories категория номи → id
     * @param vatRate    ҚҚС ставкаси id'си (null - солиқсиз)
     * @param goods      яратилган товар id'лари шунга тўлдирилади (out)
     * @param services   яратилган хизмат id'лари шунга тўлдирилади (out)
     * @param salesPrice item → сотув нархи харитаси (out)
     * @param cost       item → таннарх харитаси (out)
     */
    private void seedItems(Map<String, UUID> categories, UUID vatRate,
                           List<UUID> goods, List<UUID> services,
                           Map<UUID, BigDecimal> salesPrice, Map<UUID, BigDecimal> cost) {
        ItemService.DefaultAccounts goodsAccounts = itemService.defaultsFor(ItemType.INVENTORY);
        ItemService.DefaultAccounts serviceAccounts = itemService.defaultsFor(ItemType.SERVICE);
        Map<String, UUID> units = new HashMap<>();
        unitService.activeUnits().forEach(u -> units.put(u.getName(), u.getId()));
        for (ItemSpec spec : ITEM_SPECS) {
            ItemType type = spec.inventory() ? ItemType.INVENTORY : ItemType.SERVICE;
            ItemService.DefaultAccounts accounts = spec.inventory()
                    ? goodsAccounts : serviceAccounts;
            UUID id = itemService.create(type, new ItemService.ItemData(
                    spec.name(), spec.sku(), categories.get(spec.category()),
                    units.get(spec.unit()), new BigDecimal(spec.salesPrice()), null,
                    accounts.income(),
                    spec.cost() == null ? null : new BigDecimal(spec.cost()), null,
                    accounts.expense(),
                    spec.inventory() ? accounts.inventoryAsset() : null,
                    spec.inventory() ? new BigDecimal("5") : null,
                    null, null,
                    spec.inventory() ? vatRate : null,
                    spec.inventory() ? vatRate : null)).getId();
            salesPrice.put(id, new BigDecimal(spec.salesPrice()));
            if (spec.inventory()) {
                goods.add(id);
                cost.put(id, new BigDecimal(spec.cost()));
            } else {
                services.add(id);
            }
        }
    }

    // ---- ойма-ой ҳужжатлар ----

    /**
     * Йил бошида устав капиталини банкка киритади (QBO Bank Deposit
     * оқими). Шусиз биринчи ойларда харид тўловлари тушумдан олдин
     * кетиб банк қолдиғи манфийга тушар, dashboard хунук кўринарди.
     */
    private void seedOpeningCapital(Catalog catalog) {
        bankService.deposit(new BankTransactionService.TxnData(catalog.uzsBank(),
                dayOf(1, 2), null, null, "Йил бошида устав капитали киритилди",
                List.of(new BankTransactionService.LineData(catalog.capital(),
                        OPENING_CAPITAL, null, "Таъсисчи ҳиссаси"))));
    }

    /**
     * Ойнинг харидлари: ҳар ойда марказий омборга битта катта партия,
     * жуфт ойларда дўкон омборига қўшимча партия, март ва июлда
     * импорт (USD, транзит омборига). Ҳар учинчи ойда партияга ташиш
     * харажати EXPENSE сатр билан қўшилади - bill фақат товардан
     * иборат эмаслиги кўрсин.
     */
    private List<Doc> seedMonthBills(Catalog catalog, int month) {
        List<Doc> bills = new ArrayList<>();
        bills.add(postBill(catalog, catalog.vendors().get(month % 6), dayOf(month, 8),
                catalog.warehouses().get(0), month, 3, month * 2,
                month % 3 == 0, MONTHS[month - 1] + " ойи асосий партияси"));
        if (month % 2 == 0) {
            // Дўкон омборлари (1..4) навбат билан таъминланади - йил
            // давомида ҳар бирида қолдиқ ва ҳаракат бўлсин
            bills.add(postBill(catalog, catalog.vendors().get(6 + month % 6), dayOf(month, 19),
                    catalog.warehouses().get(1 + (month / 2 - 1) % 4), month, 3, month * 2 + 5,
                    false, MONTHS[month - 1] + " ойи дўкон таъминоти"));
        }
        if (month == 3 || month == 7) {
            bills.add(postBill(catalog, catalog.vendors().get(16 + month % 3), dayOf(month, 22),
                    catalog.warehouses().get(5), month, 2, month * 3,
                    false, "Импорт партияси (транзит омбори)"));
        }
        return bills;
    }

    /**
     * Битта bill: {@code lineCount} та товар сатри айланма индекс билан
     * танланади, ҳаммаси битта омборга киради; ихтиёрий ташиш харажати
     * сатри қўшилади. Валюта КОНТАКТдан келади (BR-BILL-013), чет
     * валютада нарх курс бўйича USD'га айлантирилади.
     */
    private Doc postBill(Catalog catalog, UUID vendorId, LocalDate date, UUID warehouseId,
                         int month, int lineCount, int itemOffset,
                         boolean withShipping, String memo) {
        boolean foreign = catalog.foreign().contains(vendorId);
        List<BillService.LineData> lines = new ArrayList<>();
        for (int i = 0; i < lineCount; i++) {
            UUID itemId = catalog.goods().get((itemOffset + i) % catalog.goods().size());
            BigDecimal qty = BigDecimal.valueOf(bulkQuantity(catalog, itemId, month));
            lines.add(new BillService.LineData(BillLineType.ITEM, itemId, warehouseId,
                    qty, priceOf(catalog.cost().get(itemId), month, foreign),
                    null, null, null, null, null,
                    foreign ? null : catalog.vatRate(), null, null));
        }
        if (withShipping) {
            lines.add(new BillService.LineData(BillLineType.EXPENSE, null, null, null, null,
                    accountIdByDetail(AccountDetailType.SHIPPING_FREIGHT_DELIVERY_COS),
                    new BigDecimal(900_000 + month * 50_000L), "Партияни олиб келиш"));
        }
        UUID id = billService.createDraft(new BillService.BillData(vendorId,
                "СФ-" + (1000 + month * 7 + lines.size()), date, null,
                foreign ? "USD" : null, foreign ? USD_RATE : null, memo, lines)).getId();
        billService.post(id);
        return new Doc(id, vendorId, date, foreign);
    }

    /**
     * Партия миқдори миқдордан эмас, СУММАдан келиб чиқади: сатрнинг
     * тахминий қиймати белгиланиб, миқдор таннархга бўлиб олинади
     * (ой билан бирга ўсади). Шунда арзон сарф материал кўп, қиммат
     * техника кам олинади ва ҳар bill'нинг суммаси реал бўлади -
     * қатъий миқдор ишлатилса қиммат товарлар омборни миллиардлаб
     * тўлдириб, банк қолдиғи манфийга тушиб кетар эди.
     */
    private int bulkQuantity(Catalog catalog, UUID itemId, int month) {
        BigDecimal target = new BigDecimal(9_000_000 + month * 400_000L);
        int qty = target.divide(catalog.cost().get(itemId), 0, RoundingMode.HALF_UP)
                .intValue();
        return Math.max(2, qty);
    }

    /**
     * Ойнинг сотувлари: сони ой билан ўсади (январда 2 та, ёзда 6
     * тагача) - dashboard графигида тренд кўринсин. Мижоз каталог
     * бўйлаб айланиб танланади, шунда 30 мижознинг деярли ҳаммасида
     * ҳужжат бўлади.
     */
    private List<Doc> seedMonthInvoices(Catalog catalog, int month) {
        int count = 2 + month / 2;
        int[] days = {5, 11, 17, 22, 26, 29};
        List<Doc> invoices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID customerId = catalog.customers().get(
                    (month * 4 + i) % catalog.customers().size());
            invoices.add(postInvoice(catalog, customerId, dayOf(month, days[i % days.length]),
                    month, month * 3 + i * 2, i, MONTHS[month - 1] + " ойи сотуви"));
        }
        return invoices;
    }

    /**
     * Битта сотув ҳужжати: 2 та товар сатри (қолдиғи етарли омбордан)
     * + 1 та хизмат сатри. Товар қолдиғи ҳеч қайси омборда етмаса сатр
     * ўтказиб юборилади - демо BR-SINV-004 га урилмасин; ҳужжат камида
     * хизмат сатри билан қолади (сатрсиз invoice бўлмайди).
     */
    private Doc postInvoice(Catalog catalog, UUID customerId, LocalDate date, int month,
                            int itemOffset, int sequence, String memo) {
        boolean foreign = catalog.foreign().contains(customerId);
        List<InvoiceService.LineData> lines = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            UUID itemId = catalog.goods().get((itemOffset + i) % catalog.goods().size());
            BigDecimal qty = BigDecimal.valueOf(saleQuantity(catalog, itemId, i));
            UUID warehouseId = warehouseWithStock(catalog, sequence + i, itemId, qty);
            if (warehouseId == null) {
                continue; // қолдиқ йўқ - сатрни ўтказиб юборамиз
            }
            lines.add(new InvoiceService.LineData(itemId, warehouseId, qty,
                    priceOf(catalog.salesPrice().get(itemId), month, foreign), null, null,
                    null, foreign ? null : catalog.vatRate(), null, null));
        }
        UUID serviceId = catalog.services().get(sequence % catalog.services().size());
        lines.add(new InvoiceService.LineData(serviceId, null, BigDecimal.ONE,
                priceOf(catalog.salesPrice().get(serviceId), month, foreign), null, null,
                null, null, null, null));
        UUID id = invoiceService.createDraft(new InvoiceService.InvoiceData(customerId,
                date, null, foreign ? "USD" : null, foreign ? USD_RATE : null,
                memo, lines)).getId();
        invoiceService.post(id);
        return new Doc(id, customerId, date, foreign);
    }

    /**
     * Сотув миқдори ҳам сатр СУММАСИдан келиб чиқади (харид билан бир
     * хил ёндашув): сотилган ҳажм харид ҳажмига мутаносиб бўлади,
     * шунда омбор ҳам, AR ҳам, P&amp;L ҳам реал нисбатда чиқади.
     */
    private int saleQuantity(Catalog catalog, UUID itemId, int lineNo) {
        BigDecimal target = new BigDecimal(5_500_000 + lineNo * 2_000_000L);
        int qty = target.divide(catalog.salesPrice().get(itemId), 0, RoundingMode.HALF_UP)
                .intValue();
        return Math.max(1, qty);
    }

    /**
     * Товар учун қолдиғи етарли омборни танлайди. Қидирув
     * {@code startIndex} дан бошланади - шунда турли ҳужжатлар турли
     * омбордан сотади ва кўп-омбор кесими ҳисоботда кўринади.
     *
     * @return омбор id'си ёки null (ҳеч қаерда етарли қолдиқ йўқ)
     */
    private UUID warehouseWithStock(Catalog catalog, int startIndex, UUID itemId,
                                    BigDecimal qty) {
        List<UUID> warehouses = catalog.warehouses();
        for (int k = 0; k < warehouses.size(); k++) {
            UUID warehouseId = warehouses.get(Math.floorMod(startIndex + k, warehouses.size()));
            if (inventoryService.quantityOnHand(itemId, warehouseId).compareTo(qty) >= 0) {
                return warehouseId;
            }
        }
        return null;
    }

    /**
     * Ойнинг доимий харажатлари: ижара ва коммунал ҳар ойда, транспорт
     * жуфт ойларда - P&amp;L да харажат тренди кўринсин.
     *
     * @return яратилган харажат сони
     */
    private int seedMonthExpenses(Catalog catalog, int month) {
        expense(catalog, dayOf(month, 3), MONTHS[month - 1] + " ойи офис ижараси",
                accountIdByDetail(AccountDetailType.RENT_OR_LEASE_OF_BUILDINGS),
                new BigDecimal(4_500_000 + month * 60_000L), null);
        // Коммунал қишда қиммат, ёзда арзон - реал мавсумийлик
        long utilities = month <= 3 || month >= 11 ? 1_850_000 : 1_150_000;
        expense(catalog, dayOf(month, 10), MONTHS[month - 1] + " ойи коммунал хизматлари",
                accountIdByDetail(AccountDetailType.UTILITIES),
                new BigDecimal(utilities), null);
        if (month % 2 != 0) {
            return 2;
        }
        UUID carrier = catalog.vendors().get(2); // ЯТТ «Каримов Транс»
        expense(catalog, dayOf(month, 17), "Товар ташиш хизмати",
                accountIdByDetail(AccountDetailType.SHIPPING_FREIGHT_DELIVERY_COS),
                new BigDecimal(880_000 + month * 30_000L), carrier);
        return 3;
    }

    /** Битта сатрли банк чиқими - харажат счёти Dt / банк Cr. */
    private void expense(Catalog catalog, LocalDate date, String memo,
                         UUID expenseAccountId, BigDecimal amount, UUID contactId) {
        bankService.expense(new BankTransactionService.TxnData(catalog.uzsBank(), date,
                null, contactId, memo, List.of(new BankTransactionService.LineData(
                        expenseAccountId, amount, contactId, null))));
    }

    /**
     * Омборлараро кўчириш акти - март, май ва июлда: марказий омбордан
     * дўконга, майда эса транзит омборидан марказга. Қолдиғи етмаган
     * сатр тушиб қолади, сатр умуман бўлмаса акт яратилмайди.
     *
     * @return яратилган акт сони (0 ёки 1)
     */
    private int seedMonthStockTransfer(Catalog catalog, int month) {
        if (month != 3 && month != 5 && month != 7) {
            return 0;
        }
        int from = month == 5 ? 5 : 0;
        int to = month == 5 ? 0 : month == 3 ? 4 : 2;
        UUID fromWarehouse = catalog.warehouses().get(from);
        List<InventoryService.TransferLineData> lines = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            UUID itemId = catalog.goods().get((month * 2 + i) % catalog.goods().size());
            BigDecimal qty = new BigDecimal("3");
            if (inventoryService.quantityOnHand(itemId, fromWarehouse).compareTo(qty) >= 0) {
                lines.add(new InventoryService.TransferLineData(itemId, qty, null));
            }
        }
        if (lines.isEmpty()) {
            return 0;
        }
        inventoryService.transferDocument(new InventoryService.DocumentTransferData(
                fromWarehouse, catalog.warehouses().get(to), dayOf(month, 24),
                "Дўкон витринасини тўлдириш", lines));
        return 1;
    }

    /**
     * Банк ўтказмалари: февралда валюта КОНВЕРСИЯСИ (сўмдан USD
     * счётига - импорт тўловлари учун валюта харид қилинади, шунда
     * USD счёт манфийга тушмайди ва ўтказманинг конверсия механикаси
     * демода кўринади), апрель ва августда банкдан кассага оддий
     * ўтказма.
     *
     * @return яратилган ўтказма сони
     */
    private int seedMonthBankTransfer(Catalog catalog, int month) {
        int created = 0;
        if (month == 2) {
            // 5 000 USD × 12 600 = 63 000 000 сўм - иккала томон base'и
            // тенг, шунинг учун курс фарқи сатри ёзилмайди
            bankService.transfer(new BankTransactionService.TransferData(catalog.uzsBank(),
                    catalog.usdBank(), dayOf(month, 6), new BigDecimal("63000000"), null,
                    new BigDecimal("5000"), USD_RATE, "Импорт тўловлари учун валюта харид"));
            created++;
        }
        if (month == 4 || month == 8) {
            bankService.transfer(new BankTransactionService.TransferData(catalog.uzsBank(),
                    catalog.cash(), dayOf(month, 14), new BigDecimal("9000000"), null,
                    null, null, "Кассага кунлик эҳтиёж учун"));
            created++;
        }
        return created;
    }

    // ---- тўловлар ----

    /**
     * Мижоз тушумлари: 2 ойдан эски invoice'лар ТЎЛИҚ, ўтган ойники
     * ҚИСМАН (55%), жорий ойники умуман тўланмаган - AR aging'да
     * «жорий», «муддати ўтган» ва «тўланган» уч ҳолат ҳам кўринсин.
     * Чет валюта тушуми USD банк счётига ва ҳужжатдан фарқли курс
     * билан тушади - realized курс фарқи проводкаси ҳосил бўлади.
     *
     * @return яратилган тушум сони
     */
    private int seedInvoicePayments(Catalog catalog, List<Doc> invoices) {
        int current = today().getMonthValue();
        int count = 0;
        for (Doc doc : invoices) {
            int age = current - doc.date().getMonthValue();
            if (age <= 0) {
                continue; // жорий ой - ҳали тўланмаган
            }
            BigDecimal balance = invoiceService.get(doc.id()).getBalanceDue();
            BigDecimal amount = age == 1 ? part(balance, "0.55") : balance;
            if (amount.signum() <= 0) {
                continue;
            }
            invoicePaymentService.create(new InvoicePaymentService.PaymentData(
                    doc.contactId(), clampToToday(doc.date().plusDays(age == 1 ? 14 : 21)),
                    doc.foreign() ? catalog.usdBank() : catalog.uzsBank(),
                    doc.foreign() ? "USD" : null,
                    doc.foreign() ? USD_PAYMENT_RATE : null,
                    amount, age == 1 ? "Қисман тўлов" : "Тўлиқ тўлов",
                    List.of(new InvoicePaymentService.AllocationData(doc.id(), amount))));
            count++;
        }
        return count;
    }

    /**
     * Етказувчига тўловлар - тушумлар билан бир хил қоида (эскиси
     * тўлиқ, ўтган ойники қисман, жорий ойники очиқ), шунда AP aging
     * ҳам мазмунли чиқади.
     *
     * @return яратилган тўлов сони
     */
    private int seedBillPayments(Catalog catalog, List<Doc> bills) {
        int current = today().getMonthValue();
        int count = 0;
        for (Doc doc : bills) {
            int age = current - doc.date().getMonthValue();
            if (age <= 0) {
                continue;
            }
            BigDecimal balance = billService.get(doc.id()).getBalanceDue();
            BigDecimal amount = age == 1 ? part(balance, "0.50") : balance;
            if (amount.signum() <= 0) {
                continue;
            }
            billPaymentService.create(new BillPaymentService.PaymentData(
                    doc.contactId(), clampToToday(doc.date().plusDays(age == 1 ? 16 : 25)),
                    doc.foreign() ? catalog.usdBank() : catalog.uzsBank(),
                    doc.foreign() ? "USD" : null,
                    doc.foreign() ? USD_PAYMENT_RATE : null,
                    amount, age == 1 ? "Қисман тўлов" : "Тўлиқ тўлов",
                    List.of(new BillPaymentService.AllocationData(doc.id(), amount))));
            count++;
        }
        return count;
    }

    /**
     * Инвентаризация акти: марказий омборда икки товар бўйича камомад
     * қайд этилади (ҳужжатли Adjustment - актнинг ҳамма сатрига битта
     * JE). Қолдиғи камида 5 бўлган товарлар танланади.
     *
     * @return яратилган акт сони (0 ёки 1)
     */
    private int seedStockAdjustment(Catalog catalog) {
        UUID warehouse = catalog.warehouses().get(0);
        List<InventoryService.AdjustLineData> lines = new ArrayList<>();
        for (UUID itemId : catalog.goods()) {
            if (lines.size() == 2) {
                break;
            }
            BigDecimal onHand = inventoryService.quantityOnHand(itemId, warehouse);
            if (onHand.compareTo(new BigDecimal("5")) >= 0) {
                lines.add(new InventoryService.AdjustLineData(itemId,
                        onHand.subtract(BigDecimal.ONE), null, "Ҳисобдаги фарқ"));
            }
        }
        if (lines.isEmpty()) {
            return 0;
        }
        inventoryService.adjustDocument(new InventoryService.DocumentAdjustData(
                warehouse, today(), "Ойлик инвентаризация - камомад", lines));
        return 1;
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

    /** Бугунги сана - компания минтақасида (темир қоида №12). */
    private LocalDate today() {
        return LocalDate.now(settingsService.zoneId());
    }

    /**
     * Ойнинг «нақшли» кунини ҳақиқий санага айлантиради. Тўлиқ ўтган
     * ойларда кун ўзгармайди; ЖОРИЙ ойда эса ойнинг ўтган қисмига
     * пропорционал сиқилади - демо ҳужжати келажак санада пайдо
     * бўлмасин (ҳисоботлар «бугунгача» деб чегараланади).
     */
    private LocalDate dayOf(int month, int patternDay) {
        LocalDate today = today();
        YearMonth yearMonth = YearMonth.of(today.getYear(), month);
        int length = yearMonth.lengthOfMonth();
        int maxDay = month == today.getMonthValue() ? today.getDayOfMonth() : length;
        int day = 1 + (patternDay - 1) * (maxDay - 1) / Math.max(1, length - 1);
        return yearMonth.atDay(Math.min(Math.max(day, 1), maxDay));
    }

    /** Санани бугундан ошиб кетмайдиган қилади (тўлов саналари учун). */
    private LocalDate clampToToday(LocalDate date) {
        LocalDate today = today();
        return date.isAfter(today) ? today : date;
    }

    /**
     * Ҳужжат нархи: базавий нархга ой бўйича кичик ўсиш қўшилади
     * (йил давомида нарх ўсиши P&amp;L трендида кўринсин), чет
     * валютада эса курс бўйича USD'га айлантирилади.
     */
    private BigDecimal priceOf(BigDecimal base, int month, boolean foreign) {
        BigDecimal grown = base.multiply(BigDecimal.ONE.add(
                new BigDecimal("0.012").multiply(BigDecimal.valueOf(month))));
        return foreign ? grown.divide(USD_RATE, 2, RoundingMode.HALF_UP)
                : grown.setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * Қолдиқнинг бир қисми (масалан 55%) - қисман тўловлар учун.
     * 2 хонагача яхлитланади: тўлов суммаси экранда доим 2 хона.
     */
    private BigDecimal part(BigDecimal balance, String ratio) {
        return balance.multiply(new BigDecimal(ratio)).setScale(2, RoundingMode.HALF_UP);
    }
}
