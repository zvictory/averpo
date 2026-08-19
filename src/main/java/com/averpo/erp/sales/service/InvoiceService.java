package com.averpo.erp.sales.service;

import com.averpo.erp.contact.domain.Contact;
import com.averpo.erp.contact.domain.ContactType;
import com.averpo.erp.contact.service.ContactService;
import com.averpo.erp.inventory.domain.StockMovement;
import com.averpo.erp.inventory.domain.Warehouse;
import com.averpo.erp.inventory.service.InventoryService;
import com.averpo.erp.inventory.service.WarehouseService;
import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.domain.ItemType;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountClassification;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.ledger.service.JournalEntryRequest;
import com.averpo.erp.ledger.service.PostingService;
import com.averpo.erp.sales.domain.Invoice;
import com.averpo.erp.sales.domain.InvoiceLine;
import com.averpo.erp.sales.domain.InvoiceLineType;
import com.averpo.erp.sales.domain.InvoiceStatus;
import com.averpo.erp.sales.repo.InvoiceRepository;
import com.averpo.erp.shared.BatchLookup;
import com.averpo.erp.shared.Strings;
import com.averpo.erp.shared.domain.Currency;
import com.averpo.erp.shared.domain.DocumentType;
import com.averpo.erp.shared.domain.Money;
import com.averpo.erp.shared.domain.MoneyAllocation;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.exception.NotFoundException;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.service.CurrencyService;
import com.averpo.erp.shared.service.DocumentSequenceService;
import com.averpo.erp.shared.service.PaymentTermService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Invoice'нинг ягона public API'си (docs/modules/sales.md) -
 * BillService'нинг кўзгу акси. Ҳаёт цикли: DRAFT (таҳрир/ўчириш
 * мумкин) → POSTED (GL: AR Dt / даромад Cr + ITEM сатрларга омбордан
 * чиқим ва COGS, ўзгармас) → REVERSED (GL сторно + товар омборга
 * қайтади). Бошқа модулларга фақат public service'лар орқали (қоида
 * №6); GL - фақат PostingService (№2).
 *
 * <p><b>Стандарт - IFRS 15 «Харидорлар билан шартномалардан даромад»</b>:
 * даромад ҳужжат post қилинганда тан олинади ва проводкада уч нарса
 * АЖРАТИЛАДИ - соф даромад, ҚҚС мажбурияти (контрол счёт) ва сотилган
 * товар таннархи (COGS). Шунинг учун сатрда нетто сумма сақланади,
 * солиқ эса ставка snapshot'и билан алоҳида ҳисобланади: каталогдаги
 * ставка кейин ўзгарса ҳам тарихий ҳужжат бузилмайди. Тақсимланмаган
 * мижоз аванслари Балансда мажбуриятга reclass қилинади (IFRS 15.106) -
 * BalanceSheetService изоҳига қаранг.
 *
 * <p><b>Солиштирув</b>: Xero'да Invoice ва Bill - битта entity, фақат
 * {@code Type} (ACCREC/ACCPAY) билан фарқланади. Бизда улар алоҳида
 * ҳужжат: проводка қоидалари, омбор таъсири ва инвариантлари бошқача
 * (сотувда COGS ва омбор чиқими бор, харидда кирим). QBO ҳам шу йўлдан
 * боради - ҳужжатлар типли, умумий {@code Transaction} супертипи остида.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class InvoiceService {

    /** GL/омбор ҳаволаларидаги манба модул белгиси. */
    public static final String SOURCE_MODULE = "INVOICE";

    /**
     * Битта invoice формаси маълумотлари - create/update учун умумий.
     * amountsInclusive - нархлар ҚҚС ичидами (docs/modules/tax.md).
     */
    public record InvoiceData(UUID customerId, LocalDate invoiceDate,
                              LocalDate dueDate, String currency,
                              BigDecimal exchangeRate, String memo,
                              boolean amountsInclusive, List<LineData> lines) {

        /** Эски 7 майдонли имзо - солиқсиз (tax'дан аввалги чақирувлар). */
        public InvoiceData(UUID customerId, LocalDate invoiceDate, LocalDate dueDate,
                           String currency, BigDecimal exchangeRate, String memo,
                           List<LineData> lines) {
            this(customerId, invoiceDate, dueDate, currency, exchangeRate, memo,
                    false, lines);
        }
    }

    /**
     * Битта сатр маълумотлари. Тур item'нинг ItemType'идан аниқланади,
     * amount - НЕТТО; даромад счёти бўш қолса item'нинг ўз income счёти.
     * unitId - киритилган бирлик (UoM); factor/taxRateValue/taxAmount
     * validate'да snapshot/ҳисоб қилинади.
     */
    public record LineData(UUID itemId, UUID warehouseId, BigDecimal quantity,
                           BigDecimal unitPrice, UUID incomeAccountId, String memo,
                           UUID unitId, UUID taxRateId, BigDecimal taxRateValue,
                           BigDecimal taxAmount, UUID classId) {

        /** 10 майдонли имзо - class'сиз чақирувлар (tax давригача қолип). */
        public LineData(UUID itemId, UUID warehouseId, BigDecimal quantity,
                        BigDecimal unitPrice, UUID incomeAccountId, String memo,
                        UUID unitId, UUID taxRateId, BigDecimal taxRateValue,
                        BigDecimal taxAmount) {
            this(itemId, warehouseId, quantity, unitPrice, incomeAccountId, memo,
                    unitId, taxRateId, taxRateValue, taxAmount, null);
        }

        /** Эски 6 майдонли имзо - бирликсиз/солиқсиз. */
        public LineData(UUID itemId, UUID warehouseId, BigDecimal quantity,
                        BigDecimal unitPrice, UUID incomeAccountId, String memo) {
            this(itemId, warehouseId, quantity, unitPrice, incomeAccountId, memo,
                    null, null, null, null, null);
        }

        /** 7 майдонли имзо - UoM бор, солиқсиз (tax'дан аввалги чақирувлар). */
        public LineData(UUID itemId, UUID warehouseId, BigDecimal quantity,
                        BigDecimal unitPrice, UUID incomeAccountId, String memo,
                        UUID unitId) {
            this(itemId, warehouseId, quantity, unitPrice, incomeAccountId, memo,
                    unitId, null, null, null, null);
        }
    }

    /**
     * Credit limit текшируви натижаси (QBO услуби - фақат
     * ОГОҲЛАНТИРАДИ, post'ни тўсмайди; лойиҳа қарори).
     *
     * <p>Барча суммалар МИЖОЗ ВАЛЮТАСИДА (home эмас): QBO'да мижоз битта
     * валютали (CurrencyRef биринчи ҳужжатдан кейин қулф), лимит шу
     * валютада турар. Мижознинг очиқ invoice'лари ҳам айнан шу валютада
     * (валюта контактдан мажбурланади - {@code validate} →
     * requireDocumentCurrency), шунинг учун конверсиясиз йиғилади.
     *
     * @param exceeded    лимитдан ошганми
     * @param creditLimit мижоз лимити (мижоз валютаси), null - лимит йўқ
     * @param exposure    очиқ AR + янги ҳужжат (мижоз валютаси)
     */
    public record CreditCheck(boolean exceeded, BigDecimal creditLimit,
                              BigDecimal exposure) { }

    /** Invoice репозиторийси. */
    private final InvoiceRepository repository;

    /** Тушумлар жамиси (dashboard paidTotal) - ўз модулимиз ичида. */
    private final com.averpo.erp.sales.repo.InvoicePaymentRepository paymentRepository;

    /** Ҳужжат рақамлари (INV-2026-NNNNN). */
    private final DocumentSequenceService sequenceService;

    /** Customer текшируви - contact модулининг public API'си. */
    private final ContactService contactService;

    /** Item тури/счётлари - item модулининг public API'си. */
    private final ItemService itemService;

    /** Омбор текшируви - inventory модулининг public API'си. */
    private final WarehouseService warehouseService;

    /** Омбордан чиқим/қайтариш. */
    private final InventoryService inventoryService;

    /** Тизим счётлари (AR) ва даромад счёти валидацияси. */
    private final AccountService accountService;

    /** GL'га ёзишнинг ягона йўли. */
    private final PostingService postingService;

    /** Валюта каталоги. */
    private final CurrencyService currencyService;

    /** Home currency - курс валидацияси учун. */
    private final CompanySettingsService settingsService;

    /** Due date ҳисоби учун мижоз тўлов шарти. */
    private final PaymentTermService paymentTermService;

    /** UoM конверсияси (factorBetween) - item модулининг public API'си. */
    private final com.averpo.erp.item.service.UnitService unitService;

    /** ҚҚС ставкаси snapshot/фаоллик текшируви - tax модулининг public API'си. */
    private final com.averpo.erp.tax.service.TaxRateService taxRateService;

    /** Id бўйича топади ёки тушунарли хато отади. */
    @Transactional(readOnly = true)
    public Invoice get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Invoice топилмади: " + id));
    }

    /** Кўриш/post учун - сатрлари билан. */
    @Transactional(readOnly = true)
    public Invoice getWithLines(UUID id) {
        return repository.findWithLinesById(id)
                .orElseThrow(() -> new NotFoundException("Invoice топилмади: " + id));
    }

    /** Рўйхат саҳифаси ҳажми (PERF-perf1 1-босқич). */
    public static final int LIST_PAGE_SIZE = 25;

    /**
     * Рўйхат тартиби - аввалги ORDER BY'га айнан мос (янгидан эскига,
     * тенг санада яратилиш вақти) - саҳифалашга ўтишда экрандаги
     * тартиб ўзгармасин (PERF-perf1 3-банд).
     */
    private static final org.springframework.data.domain.Sort LIST_SORT =
            org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Order.desc("invoiceDate"),
                    org.springframework.data.domain.Sort.Order.desc("createdAt"),
                    org.springframework.data.domain.Sort.Order.desc("id"));

    /**
     * Устун саралаш WHITELIST'и (DEC-105б): th калити → entity
     * property. Хом параметр Sort'га тушмайди - фақат шу харита
     * орқали ({@link com.averpo.erp.shared.web.TableSort}).
     * Сумма/қолдиқ home қийматлар (totalBase/balanceDue) бўйича -
     * ҳар хил валютали ҳужжатлар фақат шунда солиштирма бўлади.
     * customer йўқ: ном бошқа модул каталогида (устунда id туради).
     */
    private static final java.util.Map<String, String> SORT_KEYS = java.util.Map.of(
            "number", "invoiceNumber",
            "date", "invoiceDate",
            "dueDate", "dueDate",
            "total", "totalBase",
            "balance", "balanceDue",
            "status", "status");

    /**
     * Хом ?sort=/&dir= параметрларини рўйхат тартибига ечади -
     * controller шуни чақириб натижа Sort'ини {@code list}'га беради
     * (whitelist service'да, чунки property номлари entity ички иши).
     */
    public static com.averpo.erp.shared.web.TableSort.Applied sortOf(
            String sortKey, String dir) {
        return com.averpo.erp.shared.web.TableSort.resolve(
                sortKey, dir, SORT_KEYS, LIST_SORT);
    }

    /**
     * Рўйхат филтри (DEC-068, list-filters.md): барча майдонлар
     * ихтиёрий (null - чекланмаган). from/to - ҳужжат санаси бўйича
     * инклюзив оралиқ; q - рақам/изоҳ contains (катта-кичик фарқсиз,
     * кирилл ҳам).
     */
    public record ListFilter(java.time.LocalDate from, java.time.LocalDate to,
                             InvoiceStatus status, UUID customerId, String q) {
    }

    /**
     * Рўйхат экрани - саҳифаланган (PERF-perf1), тўлиқ филтр
     * (DEC-068): давр/статус/мижоз/матн битта Specification'да
     * (audit услуби, ListSpecs бўлаклари) - derived query'ларнинг
     * комбинацион портлашисиз.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Invoice> list(ListFilter filter, int page, int size) {
        return list(filter, page, size, LIST_SORT);
    }

    /**
     * Устун саралашли рўйхат (DEC-105б): sort {@link #sortOf}
     * орқали ечиб берилади - хом параметр бу ерга етиб келмайди.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Invoice> list(ListFilter filter, int page,
            int size, org.springframework.data.domain.Sort sort) {
        var pageable = org.springframework.data.domain.PageRequest.of(
                Math.max(0, page), size, sort);
        return repository.findAll(org.springframework.data.jpa.domain.Specification.allOf(
                com.averpo.erp.shared.repo.ListSpecs.dateFrom("invoiceDate", filter.from()),
                com.averpo.erp.shared.repo.ListSpecs.dateTo("invoiceDate", filter.to()),
                com.averpo.erp.shared.repo.ListSpecs.eq("status", filter.status()),
                com.averpo.erp.shared.repo.ListSpecs.eq("customerId", filter.customerId()),
                com.averpo.erp.shared.repo.ListSpecs.textContains(filter.q(),
                        "invoiceNumber", "memo")), pageable);
    }

    /**
     * Default ҳажм ({@link #LIST_PAGE_SIZE}) билан - эски чақирувчилар ва
     * тестлар учун (DEC-105: ҳажм танлаш controller'да
     * {@code PageSizeResolver} орқали, шу overload'га size берилади).
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Invoice> list(ListFilter filter, int page) {
        return list(filter, page, LIST_PAGE_SIZE);
    }

    /** Тушум формаси ва credit limit учун: мижознинг очиқ invoice'лари. */
    @Transactional(readOnly = true)
    public List<Invoice> openInvoices(UUID customerId) {
        return repository.findByCustomerIdAndStatusAndBalanceDueGreaterThanOrderByInvoiceDateAsc(
                customerId, InvoiceStatus.POSTED, BigDecimal.ZERO);
    }

    /**
     * AR aging қатори: мижоз бўйича очиқ қарз home валютада, муддат
     * бўйича корзиналарда (QBO A/R Aging Summary услуби; apAging'нинг
     * кўзгуси). Due date йўқ invoice current'га тушади.
     */
    public record AgingRow(UUID customerId, BigDecimal current, BigDecimal d1to30,
                           BigDecimal d31to60, BigDecimal d61to90,
                           BigDecimal over90, BigDecimal total) { }

    /**
     * AR aging ҳисоботи: очиқ POSTED invoice қолдиқлари home валютада
     * (қолдиқ × ҳужжат курси) мижоз бўйича йиғилади. Мижозлар жами
     * қарзи бўйича камайиш тартибида.
     *
     * <p>ФАҚАТ ЖОРИЙ ҲОЛАТ (BR-RPT-001, IFRS-004): қолдиқлар жорий
     * balance_due'дан ўқилади - ўтган санага сўралса ундан кейинги
     * тўловлар «ортга қайтарилмас» ва ҳисобот TB'га мос келмас эди.
     * Шунинг учун asOf фақат бугун (компания вақт минтақасида)
     * қабул қилинади; тарихий as-of реконструкцияси - 9-босқич
     * (roadmap «Reports»).
     *
     * @param asOf фақат бугунги сана ёки {@code null} (бугун олинади)
     * @throws BusinessRuleException BR-RPT-001 - asOf бугун эмас
     */
    @Transactional(readOnly = true)
    public List<AgingRow> arAging(LocalDate asOf) {
        LocalDate today = LocalDate.now(settingsService.zoneId());
        if (asOf != null && !asOf.isEqual(today)) {
            throw new BusinessRuleException(BusinessRule.BR_RPT_001,
                    "Aging фақат жорий санага: сўралди " + asOf + ", бугун " + today);
        }
        java.util.Map<UUID, BigDecimal[]> byCustomer = new java.util.LinkedHashMap<>();
        for (Invoice invoice : repository.findByStatusAndBalanceDueGreaterThan(
                InvoiceStatus.POSTED, BigDecimal.ZERO)) {
            BigDecimal base = invoice.getBalanceDue().multiply(invoice.getExchangeRate())
                    .setScale(4, RoundingMode.HALF_UP);
            long overdue = invoice.getDueDate() == null ? 0
                    : java.time.temporal.ChronoUnit.DAYS.between(invoice.getDueDate(), today);
            int bucket = overdue <= 0 ? 0 : overdue <= 30 ? 1
                    : overdue <= 60 ? 2 : overdue <= 90 ? 3 : 4;
            BigDecimal[] sums = byCustomer.computeIfAbsent(invoice.getCustomerId(), k -> {
                BigDecimal[] zeros = new BigDecimal[5];
                java.util.Arrays.fill(zeros, BigDecimal.ZERO);
                return zeros;
            });
            sums[bucket] = sums[bucket].add(base);
        }
        List<AgingRow> rows = new ArrayList<>();
        for (var entry : byCustomer.entrySet()) {
            BigDecimal[] s = entry.getValue();
            rows.add(new AgingRow(entry.getKey(), s[0], s[1], s[2], s[3], s[4],
                    s[0].add(s[1]).add(s[2]).add(s[3]).add(s[4])));
        }
        rows.sort((a, b) -> b.total().compareTo(a.total()));
        return rows;
    }

    /**
     * Даврдаги POSTED тушумлар жамиси home валютада (сумма × курс) -
     * dashboard'даги «охирги 30 кунда тўланган» картаси учун
     * (DEC-036, QBO Invoices widget паритети). REVERSED тўловлар
     * кирмайди - улар қайтарилган пул.
     */
    @Transactional(readOnly = true)
    public BigDecimal paidTotal(LocalDate from, LocalDate to) {
        return paymentRepository.sumPostedBaseBetween(from, to);
    }

    /**
     * Credit limit текшируви: очиқ AR қолдиқлари + янги ҳужжат суммаси
     * МИЖОЗ ВАЛЮТАСИДА йиғилиб лимитга солиштирилади (лимит ҳам мижоз
     * валютасида - QBO). Мижоз битта валютали бўлгани учун (валюта
     * контактдан мажбурланади) қолдиқлар конверсиясиз қўшилади - home'га
     * айлантириш ЙЎҚ (акс ҳолда чет валютали мижозда лимит ~курс баробар
     * бузиларди). Пост ТЎСИЛМАЙДИ - натижа форма/кўришда огоҳлантириш учун.
     *
     * @param additional янги ҳужжат суммаси мижоз валютасида (DRAFT'да
     *                   ҳали очиқ AR'да йўқ - қўшилади), null - қўшилмайди
     */
    @Transactional(readOnly = true)
    public CreditCheck creditCheck(UUID customerId, BigDecimal additional) {
        Contact customer = contactService.get(customerId);
        if (customer.getCreditLimit() == null) {
            return new CreditCheck(false, null, null);
        }
        BigDecimal exposure = additional == null ? BigDecimal.ZERO : additional;
        for (Invoice invoice : openInvoices(customerId)) {
            exposure = exposure.add(invoice.getBalanceDue());
        }
        return new CreditCheck(exposure.compareTo(customer.getCreditLimit()) > 0,
                customer.getCreditLimit(), exposure);
    }

    /**
     * Янги DRAFT invoice яратади - рақам DocumentSequence'дан дарҳол
     * олинади (Bill паттерни: draft ҳам рақамли).
     *
     * @throws BusinessRuleException BR-SINV-001..005, 008..010
     */
    public Invoice createDraft(InvoiceData data) {
        Normalized normalized = validate(data);
        Invoice invoice = new Invoice(
                sequenceService.next(DocumentType.INVOICE, data.invoiceDate()),
                data.customerId(), data.invoiceDate(), normalized.dueDate(),
                normalized.currency(), normalized.rate(), data.amountsInclusive(),
                Strings.blankToNull(data.memo()));
        applyLines(invoice, normalized.lines());
        return repository.saveAndFlush(invoice);
    }

    /** DRAFT'ни тўлиқ янгилайди (сарлавҳа + сатрлар қайта терилади). */
    public Invoice updateDraft(UUID id, InvoiceData data) {
        Invoice invoice = getWithLines(id);
        Normalized normalized = validate(data);
        invoice.updateHeader(data.customerId(), data.invoiceDate(),
                normalized.dueDate(), normalized.currency(), normalized.rate(),
                data.amountsInclusive(), Strings.blankToNull(data.memo()));
        invoice.clearLines();
        // ux_invoice_line_no (PERF-010) билан: Hibernate flush'да INSERT
        // DELETE'дан олдин бажарилади - эски сатрлар аввал ўчирилиши шарт,
        // акс ҳолда янги 1-сатр эски (invoice_id, line_no=1) билан тўқнашади
        repository.flush();
        applyLines(invoice, normalized.lines());
        return repository.saveAndFlush(invoice);
    }

    /** DRAFT'ни ўчиради - POSTED/REVERSED ўчирилмайди (қоида №3). */
    public void deleteDraft(UUID id) {
        Invoice invoice = getWithLines(id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new BusinessRuleException(BusinessRule.BR_SINV_006,
                    "Фақат DRAFT ўчирилади: " + invoice.getInvoiceNumber());
        }
        repository.delete(invoice);
    }

    /**
     * Post: ITEM сатрлар омбордан чиқади (home таннарх), кейин GL
     * (posting-rules «Сотув»): AR Dt жамига / ҳар сатр даромад счётига
     * Cr (ҳужжат валютасида) + ҳар ITEM сатрга COGS Dt / item asset Cr
     * (home таннархда, нол таннарх сатр ёзилмайди). Idempotency
     * (BR-LED-012) ва давр қулфи (BR-LED-020) PostingService'дан.
     *
     * @throws BusinessRuleException BR-SINV-004 - омборда қолдиқ етарли эмас
     */
    public Invoice post(UUID id) {
        Invoice invoice = getWithLines(id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new BusinessRuleException(BusinessRule.BR_SINV_006,
                    "Фақат DRAFT invoice post қилинади: " + invoice.getInvoiceNumber()
                    + " ҳозир " + invoice.getStatus());
        }
        // Draft яратилгандан кейин customer/item/счёт ҳолати ўзгарган
        // бўлиши мумкин - post олдидан тўлиқ қайта валидация
        validate(toData(invoice));

        List<JournalEntryRequest.Line> glLines = new ArrayList<>();
        String home = settingsService.homeCurrency();
        String docCurrency = invoice.getCurrency().getCode();
        boolean isHome = docCurrency.equals(home);
        BigDecimal rate = invoice.getExchangeRate();
        // SERVICE-гина invoice'да COGS счёти талаб қилинмайди - lazy
        UUID cogsAccount = null;

        // ҚҚС (docs/modules/tax.md): даромад Cr = net (ставр кесими),
        // ҚҚС Cr = ставка кесимида жамланган, AR Dt = gross. Кредит
        // леглар (net'лар + ҚҚС'лар) largest-remainder билан AR gross
        // target'га тақсимланади (PERF-007 паттерни бузилмайди).
        // COGS/INVENTORY - home таннарх, алоҳида балансланади (аллокацияга кирмайди).
        List<CreditLeg> credits = new ArrayList<>();
        // Батч (OPT-003): ITEM сатрлар asset счёти учун item'лар олдиндан
        // битта IN сўровда - сатр циклида биттадан get() қилинмайди
        Map<UUID, Item> itemsById = BatchLookup.byId(itemService.findAllById(
                BatchLookup.ids(invoice.getLines(),
                        l -> l.getType() == InvoiceLineType.ITEM ? l.getItemId() : null)));
        java.util.Map<BigDecimal, BigDecimal> taxByRate = new java.util.LinkedHashMap<>();
        for (InvoiceLine line : invoice.getLines()) {
            // Class сатрдан даромад легига айнан кўчади (class-tracking.md)
            credits.add(new CreditLeg(line.getIncomeAccountId(), line.getAmount(),
                    line.getWarehouseId(), line.getItemId(), line.getMemo(),
                    line.getClassId()));
            if (line.getTaxAmount().signum() > 0) {
                taxByRate.merge(line.getTaxRateValue(), line.getTaxAmount(), BigDecimal::add);
            }
            if (line.getType() == InvoiceLineType.ITEM) {
                InventoryService.IssueResult issued = issueGuarded(invoice, line);
                if (issued.totalCost().signum() > 0) {
                    if (cogsAccount == null) {
                        cogsAccount = accountService.requireSystemAccountId(AccountDetailType.SUPPLIES_MATERIALS_COGS);
                    }
                    Money cost = Money.ofBase(issued.totalCost(), home);
                    UUID assetAccount = itemsById.get(line.getItemId())
                            .getInventoryAssetAccountId();
                    // COGS ҳам шу сатрдан келиб чиқади - class кўчади
                    // (P&L by Class'да таннарх ҳам кесимда кўринсин);
                    // INVENTORY леги Balance Sheet назорати - class'сиз
                    glLines.add(new JournalEntryRequest.Line(cogsAccount, cost, null,
                            invoice.getCustomerId(), line.getWarehouseId(),
                            line.getItemId(), null, line.getClassId()));
                    glLines.add(new JournalEntryRequest.Line(assetAccount, null, cost,
                            invoice.getCustomerId(), line.getWarehouseId(),
                            line.getItemId(), null));
                }
            }
        }
        UUID taxAccount = accountService.requireSystemAccountId(AccountDetailType.SALES_TAX_PAYABLE);
        for (BigDecimal taxSum : taxByRate.values()) {
            // Ставка кесимида ЖАМЛАНГАН лег - бир нечта class аралашади, class'сиз
            credits.add(new CreditLeg(taxAccount, taxSum, null, null, null, null));
        }

        List<BigDecimal> legBases = null;
        if (!isHome) {
            List<BigDecimal> amounts = new ArrayList<>(credits.size());
            for (CreditLeg leg : credits) {
                amounts.add(leg.amount());
            }
            legBases = MoneyAllocation.lineBases(amounts, rate);
        }
        for (int i = 0; i < credits.size(); i++) {
            CreditLeg leg = credits.get(i);
            Money value = isHome
                    ? Money.ofBase(leg.amount(), home)
                    : Money.withBase(leg.amount(), docCurrency, legBases.get(i), rate);
            glLines.add(new JournalEntryRequest.Line(leg.account(), null, value,
                    invoice.getCustomerId(), leg.warehouseId(), leg.itemId(), leg.memo(),
                    leg.classId()));
        }
        // AR дебети = GROSS (рўйхат бошида - кўриш экранида биринчи сатр)
        Money arDebit = isHome
                ? Money.ofBase(invoice.getTotal(), home)
                : Money.withBase(invoice.getTotal(), docCurrency,
                        MoneyAllocation.targetBase(invoice.getTotal(), rate), rate);
        glLines.add(0, new JournalEntryRequest.Line(
                accountService.requireSystemAccountId(AccountDetailType.ACCOUNTS_RECEIVABLE), arDebit, null,
                invoice.getCustomerId(), null, null, null));
        postingService.createAndPost(new JournalEntryRequest(
                invoice.getInvoiceDate(),
                "Invoice " + invoice.getInvoiceNumber() + " - "
                        + customerName(invoice.getCustomerId()),
                SOURCE_MODULE, invoice.getId(), glLines));
        invoice.markPosted(Instant.now());
        return invoice;
    }

    /**
     * Reverse: товар омборга АЙНАН ейилган партиялар/қийматда қайтади
     * (InventoryService.reverseIssue), кейин GL сторно. Партия нархи
     * чиқимдан кейин ўзгарган бўлса BR-INV-009 (inventory'дан) чиқади.
     */
    public Invoice reverse(UUID id, LocalDate reversalDate, String reason) {
        Invoice invoice = getWithLines(id);
        if (invoice.getStatus() != InvoiceStatus.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_SINV_007,
                    "Фақат POSTED invoice reverse қилинади: " + invoice.getInvoiceNumber()
                    + " ҳозир " + invoice.getStatus());
        }
        for (StockMovement movement : inventoryService.byReference(SOURCE_MODULE, invoice.getId())) {
            if (!movement.getType().inbound()) {
                inventoryService.reverseIssue(movement.getId(), reversalDate);
            }
        }
        postingService.reverseBySource(SOURCE_MODULE, invoice.getId(), reversalDate,
                reason == null || reason.isBlank() ? "Invoice reverse" : reason);
        invoice.markReversed();
        return invoice;
    }

    // ---- ички ёрдамчилар ----

    /** Валидациядан ўтган нормаллашган қийматлар. */
    private record Normalized(Currency currency, BigDecimal rate,
                              LocalDate dueDate, List<NormalizedLine> lines) { }

    /** Нормаллашган сатр - тури аниқланган, net/tax ҳисобланган, snapshot'ли. */
    private record NormalizedLine(InvoiceLineType type, UUID itemId, UUID warehouseId,
                                  BigDecimal quantity, BigDecimal unitPrice,
                                  UUID incomeAccountId, BigDecimal amount, String memo,
                                  UUID unitId, BigDecimal unitFactor,
                                  UUID taxRateId, BigDecimal taxRateValue, BigDecimal taxAmount,
                                  UUID classId) { }

    /** Битта кредит леги: счёт + сумма (net ёки ставка кесимидаги ҚҚС) + dimension'лар. */
    private record CreditLeg(UUID account, BigDecimal amount,
                             UUID warehouseId, UUID itemId, String memo,
                             UUID classId) { }

    /**
     * Тўлиқ валидация (BR-SINV-001..005, 008..011) + нормализация:
     * сатр тури item ItemType'идан, сумма qty × нархдан, даромад счёти
     * item default'идан, due date мижоз тўлов шартидан (берилмаса).
     * Валюта - мижоз контактидан (BR-SINV-011, DEC-087).
     */
    private Normalized validate(InvoiceData data) {
        if (data.customerId() == null) {
            throw new BusinessRuleException(BusinessRule.BR_SINV_001,
                    "Customer танланиши шарт");
        }
        Contact customer = contactService.get(data.customerId());
        if (customer.getType() != ContactType.CUSTOMER || !customer.isActive()) {
            throw new BusinessRuleException(BusinessRule.BR_SINV_001,
                    "Customer фаол CUSTOMER типдаги контакт бўлиши шарт: "
                    + customer.getDisplayName());
        }
        if (data.invoiceDate() == null) {
            throw new BusinessRuleException(BusinessRule.BR_SINV_009,
                    "Invoice санаси киритилиши шарт");
        }
        // Валюта ҳақиқат манбаи - мижоз контакти (QBO қатъий, DEC-087):
        // client қиймати фақат мосликка текширилади, ҳужжатга контактники ёзилади
        Currency currency = currencyService.require(contactService
                .requireDocumentCurrency(customer, data.currency(), BusinessRule.BR_SINV_011));
        // Курс инварианти умумий helper'да (QA-005: policy бир жойда),
        // ҳужжатга хос BR код бу ердан берилади
        BigDecimal rate = currencyService.requireDocumentRate(
                currency, data.exchangeRate(), BusinessRule.BR_SINV_008);

        if (data.lines() == null || data.lines().isEmpty()) {
            throw new BusinessRuleException(BusinessRule.BR_SINV_002,
                    "Invoice'да камида битта сатр бўлиши шарт");
        }
        // Батч lookup (DEC-045 findAllById, OPT-003 - SalesReceipt
        // эталони): сатр-циклда item/омбор/счёт биттадан ўқилмасин,
        // id'лар олдиндан йиғилиб учта IN сўров билан Map'га олинади
        Map<UUID, Item> items = BatchLookup.byId(
                itemService.findAllById(BatchLookup.ids(data.lines(), LineData::itemId)));
        Map<UUID, Warehouse> warehouses = BatchLookup.byId(
                warehouseService.findAllById(BatchLookup.ids(data.lines(), LineData::warehouseId)));
        Map<UUID, Account> accounts = BatchLookup.byId(
                accountService.findAllById(incomeAccountIds(data.lines(), items)));
        List<NormalizedLine> normalizedLines = new ArrayList<>();
        int no = 0;
        for (LineData line : data.lines()) {
            no++;
            normalizedLines.add(validateLine(no, line, data.amountsInclusive(),
                    items, accounts, warehouses));
        }
        LocalDate dueDate = data.dueDate();
        if (dueDate == null && customer.getPaymentTermId() != null) {
            dueDate = paymentTermService.byId(customer.getPaymentTermId())
                    .map(term -> data.invoiceDate().plusDays(term.getDays()))
                    .orElse(null);
        }
        return new Normalized(currency, rate, dueDate, normalizedLines);
    }

    /**
     * Сатрларда ишлатиладиган даромад счёти id'лари: сатрда танланган
     * бўлса ўша, бўлмаса item'нинг default income счёти (validateLine
     * резолюцияси айнан шу тартибда) - батч IN сўров учун.
     */
    private Set<UUID> incomeAccountIds(List<LineData> lines, Map<UUID, Item> items) {
        Set<UUID> ids = new HashSet<>();
        for (LineData line : lines) {
            if (line.incomeAccountId() != null) {
                ids.add(line.incomeAccountId());
            } else if (line.itemId() != null) {
                Item item = items.get(line.itemId());
                if (item != null && item.getIncomeAccountId() != null) {
                    ids.add(item.getIncomeAccountId());
                }
            }
        }
        return ids;
    }

    /**
     * Сатр валидацияси: item фаоллиги, тур, омбор, сонлар, даромад счёти.
     * ҚҚС бўлиниши (docs/modules/tax.md): raw = qty × price ставка+режим
     * бўйича net/tax'га ажратилади; {@code amount} - НЕТТО. item/омбор/
     * счёт олдиндан юкланган батч Map'лардан ўқилади (OPT-003) -
     * топилмаса {@link NotFoundException} (аввалги get() хулқи айнан).
     */
    private NormalizedLine validateLine(int no, LineData line, boolean inclusive,
                                        Map<UUID, Item> items, Map<UUID, Account> accounts,
                                        Map<UUID, Warehouse> warehouses) {
        if (line.itemId() == null) {
            throw new BusinessRuleException(BusinessRule.BR_SINV_010,
                    no + "-сатр: item танланиши шарт");
        }
        Item item = items.get(line.itemId());
        if (item == null) {
            throw new NotFoundException("Item топилмади: " + line.itemId());
        }
        if (!item.isActive()) {
            throw new BusinessRuleException(BusinessRule.BR_SINV_010,
                    no + "-сатр: нофаол item сотилмайди: «" + item.getName() + "»");
        }
        if (line.quantity() == null || line.quantity().signum() <= 0
                || line.unitPrice() == null || line.unitPrice().signum() < 0) {
            throw new BusinessRuleException(BusinessRule.BR_SINV_003,
                    no + "-сатр: миқдор мусбат, нарх манфий эмас бўлиши шарт");
        }
        BigDecimal raw = line.quantity().multiply(line.unitPrice())
                .setScale(4, RoundingMode.HALF_UP);
        // Ставка snapshot/фаоллик (BR-TAX-003/004) + net/tax бўлиниши
        BigDecimal taxValue = taxRateService.documentRateValue(
                line.taxRateId(), line.taxRateValue());
        com.averpo.erp.tax.service.TaxAmounts ta =
                com.averpo.erp.tax.service.TaxAmounts.of(raw, taxValue, inclusive);
        if (ta.net().signum() <= 0) {
            throw new BusinessRuleException(BusinessRule.BR_SINV_003,
                    no + "-сатр: нетто сумма мусбат бўлиши шарт");
        }
        InvoiceLineType type;
        UUID warehouseId;
        if (item.getType() == ItemType.INVENTORY) {
            type = InvoiceLineType.ITEM;
            if (line.warehouseId() == null) {
                throw new BusinessRuleException(BusinessRule.BR_SINV_004,
                        no + "-сатр: INVENTORY item учун омбор танланиши шарт");
            }
            if (warehouses.get(line.warehouseId()) == null) {
                throw new NotFoundException("Омбор топилмади: " + line.warehouseId());
            }
            warehouseId = line.warehouseId(); // мавжудлик текширилди (фаоллик issue'да)
        } else {
            type = InvoiceLineType.SERVICE;
            warehouseId = null; // SERVICE омборга тегмайди - қиймат ташланади
        }
        UUID incomeAccountId = line.incomeAccountId() != null
                ? line.incomeAccountId() : item.getIncomeAccountId();
        Account income = accounts.get(incomeAccountId);
        if (income == null) {
            throw new NotFoundException("Счёт топилмади: " + incomeAccountId);
        }
        if (!income.isActive() || !income.isPostable()
                || income.getClassification() != AccountClassification.REVENUE) {
            throw new BusinessRuleException(BusinessRule.BR_SINV_005,
                    no + "-сатр: даромад счёти INCOME туркумидан, фаол ва postable "
                    + "бўлиши шарт: " + income.getName());
        }
        return new NormalizedLine(type, line.itemId(), warehouseId,
                line.quantity(), line.unitPrice(), incomeAccountId, ta.net(),
                Strings.blankToNull(line.memo()), line.unitId(),
                unitService.lineFactor(no, item, line.unitId(), line.quantity(),
                        type == InvoiceLineType.ITEM, BusinessRule.BR_SINV_003),
                line.taxRateId(), taxValue, ta.tax(), line.classId());
    }


    /** Омбордан чиқим - қолдиқ етмаса BR-INV-003 ни BR-SINV-004 га ўрайди. */
    private InventoryService.IssueResult issueGuarded(Invoice invoice, InvoiceLine line) {
        try {
            // UoM: чиқим BASE бирликда - миқдор × factor snapshot
            BigDecimal baseQty = line.getQuantity().multiply(line.unitFactorOrOne())
                    .setScale(4, RoundingMode.HALF_UP);
            return inventoryService.issue(line.getItemId(), line.getWarehouseId(),
                    baseQty, invoice.getInvoiceDate(),
                    SOURCE_MODULE, invoice.getId(), line.getMemo());
        } catch (BusinessRuleException e) {
            if (e.getRule() == BusinessRule.BR_INV_003) {
                throw new BusinessRuleException(BusinessRule.BR_SINV_004,
                        line.getLineNo() + "-сатр: " + e.getMessage());
            }
            throw e;
        }
    }

    /** Сатрларни invoice'га теради (қийматлар validate'да нормаллашган). */
    private void applyLines(Invoice invoice, List<NormalizedLine> lines) {
        for (NormalizedLine line : lines) {
            invoice.addLine(line.type(), line.itemId(), line.warehouseId(),
                    line.quantity(), line.unitPrice(),
                    line.unitId(), line.unitFactor(),
                    line.incomeAccountId(), line.amount(),
                    line.taxRateId(), line.taxRateValue(), line.taxAmount(), line.memo())
                    .applyClass(line.classId());
        }
    }

    /**
     * Post олдидан қайта валидация учун entity'дан InvoiceData ясайди.
     * ҚҚС snapshot (taxRateValue) ва режим сақланади - қайта валидация
     * draft'даги ставка қийматини ишлатади (каталог ўзгарса ҳам).
     */
    private InvoiceData toData(Invoice invoice) {
        List<LineData> lines = new ArrayList<>();
        for (InvoiceLine line : invoice.getLines()) {
            lines.add(new LineData(line.getItemId(), line.getWarehouseId(),
                    line.getQuantity(), line.getUnitPrice(),
                    line.getIncomeAccountId(), line.getMemo(), line.getUnitId(),
                    line.getTaxRateId(), line.getTaxRateValue(), line.getTaxAmount(),
                    line.getClassId()));
        }
        return new InvoiceData(invoice.getCustomerId(), invoice.getInvoiceDate(),
                invoice.getDueDate(), invoice.getCurrency().getCode(),
                invoice.getExchangeRate(), invoice.getMemo(),
                invoice.isAmountsInclusive(), lines);
    }


    /** Customer номи - GL тавсифи учун. */
    private String customerName(UUID customerId) {
        return contactService.get(customerId).getDisplayName();
    }

}
