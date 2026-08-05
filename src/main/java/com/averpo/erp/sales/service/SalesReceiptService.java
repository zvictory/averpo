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
import com.averpo.erp.ledger.domain.AccountType;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.ledger.service.JournalEntryRequest;
import com.averpo.erp.ledger.service.PostingService;
import com.averpo.erp.sales.domain.InvoiceLineType;
import com.averpo.erp.sales.domain.SalesReceipt;
import com.averpo.erp.sales.domain.SalesReceiptLine;
import com.averpo.erp.sales.repo.SalesReceiptRepository;
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
import com.averpo.erp.tax.service.TaxAmounts;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Сотув чекининг ягона public API'си (posting-rules «Сотув чеки»).
 * Invoice'нинг AR'сiz кўзгуси: {@link #create} дарҳол POSTED (bank txn
 * нақши) - GL (Dr банк/касса gross / Cr даромад net + Cr ҚҚС) ва ITEM
 * сатрларга омбордан чиқим + Dr COGS / Cr INVENTORY; {@link #reverse} -
 * товар омборга айнан ейилган қийматда қайтади, кейин GL сторно. AR ва
 * allocation ЙЎҚ (тўлов дарҳол).
 *
 * <p>GL фақат PostingService (қоида №2), омбор фақат InventoryService
 * public API'си (қоида №6). Проводкалар posting-rules «Сотув чеки»
 * бандига қатъий мос (қоида №8).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class SalesReceiptService {

    /** GL/омбор ҳаволаларидаги манба модул белгиси (posting-rules). */
    public static final String SOURCE_MODULE = "SALES_RECEIPT";

    /** Рўйхат саҳифаси ҳажми (PERF-perf1 қолипи). */
    public static final int LIST_PAGE_SIZE = 25;

    /** Рўйхат тартиби: янгидан эскига, тенг санада яратилиш вақти. */
    private static final Sort LIST_SORT = Sort.by(
            Sort.Order.desc("srDate"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    /**
     * Чек формаси маълумотлари - InvoiceData кўзгуси, лекин пул счёти
     * билан ва due date/invoice ҳаваласисиз (тўлов дарҳол).
     */
    public record SalesReceiptData(UUID customerId, UUID bankAccountId, LocalDate srDate,
                                   String currency, BigDecimal exchangeRate,
                                   boolean amountsInclusive, String memo,
                                   List<LineData> lines) { }

    /** Битта сатр - Invoice/RefundReceipt LineData кўзгуси. */
    public record LineData(UUID itemId, UUID warehouseId, BigDecimal quantity,
                           BigDecimal unitPrice, String memo, UUID unitId,
                           UUID taxRateId, BigDecimal taxRateValue, UUID classId) { }

    /** Чеклар репозиторийси. */
    private final SalesReceiptRepository repository;

    /** Ҳужжат рақамлари (SR-2026-NNNNN). */
    private final DocumentSequenceService sequenceService;

    /** Customer текшируви - contact модулининг public API'си. */
    private final ContactService contactService;

    /** Item тури/счётлари - item модулининг public API'си. */
    private final ItemService itemService;

    /** UoM конверсияси (factorBetween) - item модулининг public API'си. */
    private final com.averpo.erp.item.service.UnitService unitService;

    /** Омбор текшируви - inventory модулининг public API'си. */
    private final WarehouseService warehouseService;

    /** Омбордан чиқим ва reverse. */
    private final InventoryService inventoryService;

    /** Пул счёти валидацияси ва тизим счётлари (ҚҚС, COGS). */
    private final AccountService accountService;

    /** GL'га ёзишнинг ягона йўли. */
    private final PostingService postingService;

    /** Валюта каталоги. */
    private final CurrencyService currencyService;

    /** Home currency - курс валидацияси учун. */
    private final CompanySettingsService settingsService;

    /** ҚҚС ставкаси snapshot/фаоллик - tax модулининг public API'си. */
    private final com.averpo.erp.tax.service.TaxRateService taxRateService;

    /** Id бўйича топади ёки тушунарли хато отади. */
    @Transactional(readOnly = true)
    public SalesReceipt get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Сотув чеки топилмади: " + id));
    }

    /** Кўриш учун - сатрлари билан. */
    @Transactional(readOnly = true)
    public SalesReceipt getWithLines(UUID id) {
        return repository.findWithLinesById(id)
                .orElseThrow(() -> new NotFoundException("Сотув чеки топилмади: " + id));
    }

    /**
     * Рўйхат филтри (DEC-068, list-filters.md): барча майдонлар
     * ихтиёрий (null - чекланмаган); q - рақам/изоҳ contains
     * (катта-кичик фарқсиз, кирилл ҳам).
     */
    public record ListFilter(LocalDate from, LocalDate to, SalesReceipt.Status status,
                             UUID customerId, String q) {
    }

    /**
     * Рўйхат экрани - саҳифаланган (янгидан эскига), тўлиқ филтр
     * (DEC-068): давр/статус/мижоз/матн битта Specification'да
     * (audit услуби, ListSpecs бўлаклари).
     */
    @Transactional(readOnly = true)
    public Page<SalesReceipt> list(ListFilter filter, int page, int size) {
        return repository.findAll(org.springframework.data.jpa.domain.Specification.allOf(
                        com.averpo.erp.shared.repo.ListSpecs.dateFrom("srDate", filter.from()),
                        com.averpo.erp.shared.repo.ListSpecs.dateTo("srDate", filter.to()),
                        com.averpo.erp.shared.repo.ListSpecs.eq("status", filter.status()),
                        com.averpo.erp.shared.repo.ListSpecs.eq("customerId", filter.customerId()),
                        com.averpo.erp.shared.repo.ListSpecs.textContains(filter.q(),
                                "srNumber", "memo")),
                PageRequest.of(Math.max(0, page), size, LIST_SORT));
    }

    /** Default ҳажм ({@link #LIST_PAGE_SIZE}) билан - эски чақирувчилар/тестлар (DEC-105). */
    @Transactional(readOnly = true)
    public Page<SalesReceipt> list(ListFilter filter, int page) {
        return list(filter, page, LIST_PAGE_SIZE);
    }

    /**
     * Яратиш - дарҳол POSTED (bank txn нақши): ITEM сатрлар омбордан
     * чиқади, кейин GL (Dr банк/касса gross / ҳар сатр даромад Cr net +
     * ставка кесимида ҚҚС Cr) ва ҳар ITEM сатрга Dr COGS / Cr INVENTORY
     * (home таннарх). Class сатрдан даромад/COGS легига кўчади, банк ва
     * жамланган ҚҚС class'сиз.
     *
     * @throws BusinessRuleException BR-SR-001/002, BR-TAX-003/004
     */
    public SalesReceipt create(SalesReceiptData data) {
        Normalized normalized = validate(data);
        SalesReceipt receipt = new SalesReceipt(
                sequenceService.next(DocumentType.SALES_RECEIPT, data.srDate()),
                data.customerId(), data.bankAccountId(), data.srDate(),
                normalized.currency(), normalized.rate(), data.amountsInclusive(),
                Strings.blankToNull(data.memo()));
        for (NormalizedLine line : normalized.lines()) {
            receipt.addLine(line.type(), line.itemId(), line.warehouseId(),
                    line.quantity(), line.unitPrice(), line.unitId(), line.unitFactor(),
                    line.incomeAccountId(), line.amount(),
                    line.taxRateId(), line.taxRateValue(), line.taxAmount(), line.memo())
                    .applyClass(line.classId());
        }
        repository.saveAndFlush(receipt);
        postGl(receipt, assetAccountByItem(normalized));
        receipt.markPosted(Instant.now());
        return receipt;
    }

    /**
     * Reverse: товар омборга АЙНАН ейилган партиялар/қийматда қайтади
     * (InventoryService.reverseIssue - партия нархи чиқимдан кейин
     * ўзгарган бўлса BR-INV-009), кейин GL сторно. Allocation йўқлиги
     * учун фақат POSTED ҳолат текширилади (BR-SR-003).
     */
    public SalesReceipt reverse(UUID id, LocalDate reversalDate, String reason) {
        SalesReceipt receipt = get(id);
        if (receipt.getStatus() != SalesReceipt.Status.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_SR_003,
                    "Фақат POSTED сотув чеки reverse қилинади: " + receipt.getSrNumber()
                    + " ҳозир " + receipt.getStatus());
        }
        for (StockMovement movement : inventoryService.byReference(SOURCE_MODULE, receipt.getId())) {
            if (!movement.getType().inbound()) {
                inventoryService.reverseIssue(movement.getId(), reversalDate);
            }
        }
        postingService.reverseBySource(SOURCE_MODULE, receipt.getId(), reversalDate,
                reason == null || reason.isBlank() ? "Сотув чеки reverse" : reason);
        receipt.markReversed();
        return receipt;
    }

    // ---- ички ёрдамчилар ----

    /** Валидациядан ўтган нормаллашган қийматлар. */
    private record Normalized(Currency currency, BigDecimal rate, List<NormalizedLine> lines) { }

    /**
     * NormalizedLine'лардан item → inventory asset счёт харитаси - postGl
     * ITEM сатрларда asset счётни item'ни қайта юкламай шундан олади
     * (PERF-035). Фақат ITEM сатрларда asset бор (SERVICE'да null).
     */
    private Map<UUID, UUID> assetAccountByItem(Normalized normalized) {
        Map<UUID, UUID> byItem = new HashMap<>();
        for (NormalizedLine line : normalized.lines()) {
            if (line.inventoryAssetAccountId() != null) {
                byItem.put(line.itemId(), line.inventoryAssetAccountId());
            }
        }
        return byItem;
    }

    /**
     * Нормаллашган сатр - Invoice NormalizedLine кўзгуси. {@code
     * inventoryAssetAccountId} - фақат ITEM сатрда (item'дан валидацияда
     * олинган); postGl шу орқали asset счётни item'ни қайта юкламай олади
     * (PERF-035). Domain SalesReceiptLine'га сақланмайди - фақат яратиш
     * оқимидаги транзиент қиймат (schema ўзгармайди).
     */
    private record NormalizedLine(InvoiceLineType type, UUID itemId, UUID warehouseId,
                                  BigDecimal quantity, BigDecimal unitPrice,
                                  UUID incomeAccountId, BigDecimal amount, String memo,
                                  UUID unitId, BigDecimal unitFactor,
                                  UUID taxRateId, BigDecimal taxRateValue,
                                  BigDecimal taxAmount, UUID classId,
                                  UUID inventoryAssetAccountId) { }

    /**
     * Сарлавҳа + сатрлар валидацияси. Customer фаол CUSTOMER (BR-SR-001);
     * пул счёти BANK туридан, фаол/postable, валютаси ҳужжат валютасига
     * тенг (BR-SR-002) - пул счётига ўз валютасидан бошқа валютада ёзиб
     * бўлмайди, FX фарқи туғилмайди. Ҳужжат валютаси мижоз контактидан
     * (BR-SR-004, DEC-087) - BR-SR-002 энди «мижоз валютасига мос
     * тўлов счёти» маъносини беради.
     */
    private Normalized validate(SalesReceiptData data) {
        if (data.customerId() == null) {
            throw new BusinessRuleException(BusinessRule.BR_SR_001, "Customer танланиши шарт");
        }
        Contact customer = contactService.get(data.customerId());
        if (customer.getType() != ContactType.CUSTOMER || !customer.isActive()) {
            throw new BusinessRuleException(BusinessRule.BR_SR_001,
                    "Customer фаол CUSTOMER типдаги контакт бўлиши шарт: "
                    + customer.getDisplayName());
        }
        if (data.srDate() == null) {
            throw new BusinessRuleException(BusinessRule.BR_SR_001,
                    "Ҳужжат санаси киритилиши шарт");
        }
        if (data.lines() == null || data.lines().isEmpty()) {
            throw new BusinessRuleException(BusinessRule.BR_SR_001,
                    "Камида битта сатр киритилиши шарт");
        }
        // Валюта ҳақиқат манбаи - мижоз контакти (QBO қатъий, DEC-087):
        // client қиймати фақат мосликка текширилади, ҳужжатга контактники ёзилади
        Currency currency = currencyService.require(contactService
                .requireDocumentCurrency(customer, data.currency(), BusinessRule.BR_SR_004));
        BigDecimal rate = currencyService.requireDocumentRate(
                currency, data.exchangeRate(), BusinessRule.BR_SR_001);

        // Батч lookup (DEC-045 findAllById нақши): сатр-циклда item/счёт/
        // омбор қайта-қайта ўқилмасин (PERF-035 - бир хил item 20 марта
        // такрорланса ҳам битта round-trip). Item'лар ва омборлар ягона id
        // тўпламларида IN сўров билан; счётлар каталоги битта марта Map'га
        // (PayrollRun buildGlLines нақши) - банк ва даромад счётлари шундан.
        Map<UUID, Item> items = itemsByIds(data.lines());
        Map<UUID, Account> accounts = accountsById();
        Map<UUID, Warehouse> warehouses = warehousesByIds(data.lines());

        requireBankAccount(data.bankAccountId(), currency, accounts);

        List<NormalizedLine> lines = new ArrayList<>();
        int no = 0;
        for (LineData line : data.lines()) {
            no++;
            lines.add(validateLine(no, line, data.amountsInclusive(), items, accounts, warehouses));
        }
        return new Normalized(currency, rate, lines);
    }

    /** Сатрлардаги item'ларни битта IN сўровда Map'га (DEC-045 findAllById). */
    private Map<UUID, Item> itemsByIds(List<LineData> lines) {
        Set<UUID> ids = new HashSet<>();
        for (LineData line : lines) {
            if (line.itemId() != null) {
                ids.add(line.itemId());
            }
        }
        Map<UUID, Item> byId = new HashMap<>();
        for (Item item : itemService.findAllById(ids)) {
            byId.put(item.getId(), item);
        }
        return byId;
    }

    /** Сатрлардаги омборларни битта IN сўровда Map'га (DEC-045 findAllById). */
    private Map<UUID, Warehouse> warehousesByIds(List<LineData> lines) {
        Set<UUID> ids = new HashSet<>();
        for (LineData line : lines) {
            if (line.warehouseId() != null) {
                ids.add(line.warehouseId());
            }
        }
        Map<UUID, Warehouse> byId = new HashMap<>();
        for (Warehouse warehouse : warehouseService.findAllById(ids)) {
            byId.put(warehouse.getId(), warehouse);
        }
        return byId;
    }

    /**
     * Счётлар каталогини битта findAll билан id бўйича Map'га (PayrollRun
     * buildGlLines нақши). Кичик QBO chart'да битта all() ҳам битта IN
     * сўров ({@code AccountService.findAllById}) билан тенг арзон - банк
     * ва даромад счётлари шу Map'дан текширилади.
     */
    private Map<UUID, Account> accountsById() {
        Map<UUID, Account> byId = new HashMap<>();
        for (Account account : accountService.all()) {
            byId.put(account.getId(), account);
        }
        return byId;
    }

    /**
     * Пул счёти гарови (BR-SR-002): BANK туридан (банк/касса счётлари шу
     * турда), фаол/postable, валютаси (бўш бўлса home) ҳужжат валютасига
     * тенг (QBO DepositToAccount қоидаси - FX фарқи туғилмайди).
     */
    private void requireBankAccount(UUID accountId, Currency docCurrency,
                                    Map<UUID, Account> accounts) {
        if (accountId == null) {
            throw new BusinessRuleException(BusinessRule.BR_SR_002,
                    "Тўлов счёти танланиши шарт");
        }
        Account account = accounts.get(accountId);
        if (account == null) {
            throw new NotFoundException("Счёт топилмади: " + accountId);
        }
        if (account.getType() != AccountType.BANK || !account.isActive()
                || !account.isPostable()) {
            throw new BusinessRuleException(BusinessRule.BR_SR_002,
                    "Тўлов счёти BANK туридан, фаол ва postable бўлиши шарт: "
                    + account.getName());
        }
        String accountCurrency = account.getCurrency() != null
                ? account.getCurrency().getCode() : settingsService.homeCurrency();
        if (!accountCurrency.equals(docCurrency.getCode())) {
            throw new BusinessRuleException(BusinessRule.BR_SR_002,
                    "Тўлов счёти валютаси (" + accountCurrency + ") ҳужжат валютаси ("
                    + docCurrency.getCode() + ") билан бир хил бўлиши шарт: "
                    + account.getName());
        }
    }

    /**
     * Сатр валидацияси - Invoice validateLine кўзгуси: item фаоллиги,
     * тур, омбор (ITEM'да), сонлар, ҚҚС бўлиниши (net/tax), даромад счёти
     * REVENUE. amount - НЕТТО. item/счёт/омбор олдиндан юкланган батч
     * Map'лардан ўқилади (PERF-035) - топилмаса {@link NotFoundException}
     * (мавжуд get() хулқи айнан). ITEM сатрда inventory asset счёти шу ерда
     * item'дан олиниб NormalizedLine'га сақланади (postGl item'ни қайта
     * юкламайди).
     */
    private NormalizedLine validateLine(int no, LineData line, boolean inclusive,
                                        Map<UUID, Item> items, Map<UUID, Account> accounts,
                                        Map<UUID, Warehouse> warehouses) {
        if (line.itemId() == null) {
            throw new BusinessRuleException(BusinessRule.BR_SR_001,
                    no + "-сатр: item танланиши шарт");
        }
        Item item = items.get(line.itemId());
        if (item == null) {
            throw new NotFoundException("Item топилмади: " + line.itemId());
        }
        if (!item.isActive()) {
            throw new BusinessRuleException(BusinessRule.BR_SR_001,
                    no + "-сатр: нофаол item сотилмайди: «" + item.getName() + "»");
        }
        if (line.quantity() == null || line.quantity().signum() <= 0
                || line.unitPrice() == null || line.unitPrice().signum() < 0) {
            throw new BusinessRuleException(BusinessRule.BR_SR_001,
                    no + "-сатр: миқдор мусбат, нарх манфий эмас бўлиши шарт");
        }
        BigDecimal raw = line.quantity().multiply(line.unitPrice())
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal taxValue = taxRateService.documentRateValue(line.taxRateId(), line.taxRateValue());
        TaxAmounts ta = TaxAmounts.of(raw, taxValue, inclusive);
        if (ta.net().signum() <= 0) {
            throw new BusinessRuleException(BusinessRule.BR_SR_001,
                    no + "-сатр: нетто сумма мусбат бўлиши шарт");
        }
        InvoiceLineType type;
        UUID warehouseId;
        UUID inventoryAssetAccountId;
        if (item.getType() == ItemType.INVENTORY) {
            type = InvoiceLineType.ITEM;
            if (line.warehouseId() == null) {
                throw new BusinessRuleException(BusinessRule.BR_SR_001,
                        no + "-сатр: INVENTORY item учун омбор танланиши шарт");
            }
            if (warehouses.get(line.warehouseId()) == null) {
                throw new NotFoundException("Омбор топилмади: " + line.warehouseId());
            }
            warehouseId = line.warehouseId(); // мавжудлик текширилди (фаоллик issue'да)
            // Asset счёти постингда керак - item'дан шу ерда олиб NormalizedLine'га
            // (postGl'да itemService.get қайта чақирилмайди, PERF-035)
            inventoryAssetAccountId = item.getInventoryAssetAccountId();
        } else {
            type = InvoiceLineType.SERVICE;
            warehouseId = null;
            inventoryAssetAccountId = null;
        }
        UUID incomeAccountId = item.getIncomeAccountId();
        Account income = accounts.get(incomeAccountId);
        if (income == null) {
            throw new NotFoundException("Счёт топилмади: " + incomeAccountId);
        }
        if (!income.isActive() || !income.isPostable()
                || income.getClassification() != AccountClassification.REVENUE) {
            throw new BusinessRuleException(BusinessRule.BR_SR_001,
                    no + "-сатр: даромад счёти INCOME туркумидан, фаол ва postable "
                    + "бўлиши шарт: " + income.getName());
        }
        return new NormalizedLine(type, line.itemId(), warehouseId,
                line.quantity(), line.unitPrice(), incomeAccountId, ta.net(),
                Strings.blankToNull(line.memo()), line.unitId(),
                unitService.lineFactor(no, item, line.unitId(), line.quantity(),
                        type == InvoiceLineType.ITEM, BusinessRule.BR_SR_001),
                line.taxRateId(), taxValue,
                ta.tax(), line.classId(), inventoryAssetAccountId);
    }


    /**
     * GL (posting-rules «Сотув чеки»): Dr банк/касса gross / Cr даромад
     * net (сатр кесимида, class билан) + Cr ҚҚС (ставка кесимида
     * жамланган, class'сиз); ITEM сатрларга омбордан чиқим + Dr COGS /
     * Cr INVENTORY (home таннарх). Penny rounding: чет валютада кредит
     * леглар base'и largest-remainder билан банк gross target'ига
     * тақсимланади (Invoice қолипи).
     *
     * @param assetAccountByItem ITEM сатрлар учун item → inventory asset
     *        счёт харитаси (валидацияда йиғилган) - asset счёт item'ни
     *        қайта юкламай шундан олинади (PERF-035)
     */
    private void postGl(SalesReceipt receipt, Map<UUID, UUID> assetAccountByItem) {
        String home = settingsService.homeCurrency();
        String docCurrency = receipt.getCurrency().getCode();
        boolean isHome = docCurrency.equals(home);
        BigDecimal rate = receipt.getExchangeRate();

        List<JournalEntryRequest.Line> glLines = new ArrayList<>();

        // 1) Кредит леглар: даромад net (class билан) + ставка кесимида ҚҚС;
        //    ITEM сатрларда омбордан чиқим + Dr COGS / Cr INVENTORY (home)
        record CreditLeg(UUID account, BigDecimal amount, UUID warehouseId,
                         UUID itemId, String memo, UUID classId) { }
        List<CreditLeg> credits = new ArrayList<>();
        Map<BigDecimal, BigDecimal> taxByRate = new LinkedHashMap<>();
        UUID cogsAccount = null;
        for (SalesReceiptLine line : receipt.getLines()) {
            credits.add(new CreditLeg(line.getIncomeAccountId(), line.getAmount(),
                    line.getWarehouseId(), line.getItemId(), line.getMemo(),
                    line.getClassId()));
            if (line.getTaxAmount().signum() > 0) {
                taxByRate.merge(line.getTaxRateValue(), line.getTaxAmount(), BigDecimal::add);
            }
            if (line.getType() == InvoiceLineType.ITEM) {
                InventoryService.IssueResult issued = issueGuarded(receipt, line);
                if (issued.totalCost().signum() > 0) {
                    if (cogsAccount == null) {
                        cogsAccount = accountService.requireSystemAccountId(
                                AccountDetailType.SUPPLIES_MATERIALS_COGS);
                    }
                    Money cost = Money.ofBase(issued.totalCost(), home);
                    UUID assetAccount = assetAccountByItem.get(line.getItemId());
                    // COGS сатрдан - class кўчади (P&L by Class'да таннарх ҳам
                    // кесимда); INVENTORY леги Balance Sheet назорати - class'сиз
                    glLines.add(new JournalEntryRequest.Line(cogsAccount, cost, null,
                            receipt.getCustomerId(), line.getWarehouseId(),
                            line.getItemId(), null, line.getClassId()));
                    glLines.add(new JournalEntryRequest.Line(assetAccount, null, cost,
                            receipt.getCustomerId(), line.getWarehouseId(),
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
                    receipt.getCustomerId(), leg.warehouseId(), leg.itemId(), leg.memo(),
                    leg.classId()));
        }
        // Банк/касса дебети = GROSS (рўйхат бошида - кўриш экранида биринчи сатр)
        Money bankDebit = isHome
                ? Money.ofBase(receipt.getTotal(), home)
                : Money.withBase(receipt.getTotal(), docCurrency,
                        MoneyAllocation.targetBase(receipt.getTotal(), rate), rate);
        glLines.add(0, new JournalEntryRequest.Line(receipt.getBankAccountId(),
                bankDebit, null, receipt.getCustomerId(), null, null, null));

        postingService.createAndPost(new JournalEntryRequest(
                receipt.getSrDate(),
                "Сотув чеки " + receipt.getSrNumber() + " - "
                        + contactService.get(receipt.getCustomerId()).getDisplayName(),
                SOURCE_MODULE, receipt.getId(), glLines));
    }

    /** Омбордан чиқим - қолдиқ етмаса BR-INV-003 ни BR-SR-001 га ўрайди. */
    private InventoryService.IssueResult issueGuarded(SalesReceipt receipt, SalesReceiptLine line) {
        try {
            BigDecimal baseQty = line.getQuantity().multiply(line.unitFactorOrOne())
                    .setScale(4, RoundingMode.HALF_UP);
            return inventoryService.issue(line.getItemId(), line.getWarehouseId(),
                    baseQty, receipt.getSrDate(), SOURCE_MODULE, receipt.getId(), line.getMemo());
        } catch (BusinessRuleException e) {
            if (e.getRule() == BusinessRule.BR_INV_003) {
                throw new BusinessRuleException(BusinessRule.BR_SR_001,
                        line.getLineNo() + "-сатр: " + e.getMessage());
            }
            throw e;
        }
    }
}
