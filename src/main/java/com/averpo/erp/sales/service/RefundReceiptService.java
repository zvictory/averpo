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
import com.averpo.erp.sales.domain.CreditMemo;
import com.averpo.erp.sales.domain.CreditMemoLine;
import com.averpo.erp.sales.domain.Invoice;
import com.averpo.erp.sales.domain.InvoiceLine;
import com.averpo.erp.sales.domain.InvoiceLineType;
import com.averpo.erp.sales.domain.InvoiceStatus;
import com.averpo.erp.sales.domain.RefundReceipt;
import com.averpo.erp.sales.domain.RefundReceiptLine;
import com.averpo.erp.sales.repo.CreditMemoRepository;
import com.averpo.erp.sales.repo.InvoiceRepository;
import com.averpo.erp.sales.repo.RefundReceiptRepository;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Пул қайтариш чекининг ягона public API'си (returns.md). CreditMemo
 * кўзгуси, фарқи: AR ўрнига пул счёти кредитланади (ҳужжатда
 * танланади) ва application ЙЎҚ - пул дарҳол қайтган, тугал ҳужжат.
 * {@link #create} дарҳол POSTED (GL + ITEM сатрларга омбор кирими);
 * {@link #reverse} - GL сторно + кирим тескари қайтарилади.
 *
 * <p>GL фақат PostingService (қоида №2), омбор фақат InventoryService
 * public API'си (қоида №6). Проводкалар posting-rules «Қайтариш»
 * RefundReceipt бандига қатъий мос (қоида №8).
 *
 * @author Zafar
 */
@Service
@Transactional
@RequiredArgsConstructor
public class RefundReceiptService {

    /** GL/омбор ҳаволаларидаги манба модул белгиси (posting-rules). */
    public static final String SOURCE_MODULE = "REFUND_RECEIPT";

    /** Рўйхат саҳифаси ҳажми (Beruniy-perf1 қолипи - рўйхат саҳифаланган туғилади). */
    public static final int LIST_PAGE_SIZE = 25;

    /** Рўйхат тартиби: янгидан эскига, тенг санада яратилиш вақти. */
    private static final Sort LIST_SORT = Sort.by(
            Sort.Order.desc("rrDate"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    /**
     * Чек формаси маълумотлари - CreditMemoData кўзгуси + пул счёти.
     * invoiceId - ихтиёрий асл ҳужжат ҳаволаси (prefill, қайтим
     * таннархи асл сотувдан, BR-RET-006 миқдор чеклови).
     */
    public record RefundReceiptData(UUID customerId, UUID invoiceId, UUID bankAccountId,
                                    LocalDate rrDate, String currency,
                                    BigDecimal exchangeRate, boolean amountsInclusive,
                                    String memo, List<LineData> lines) { }

    /** Битта сатр: CreditMemo LineData'нинг айнан кўзгуси. */
    public record LineData(UUID itemId, UUID warehouseId, BigDecimal quantity,
                           BigDecimal unitPrice, String memo, UUID unitId,
                           UUID taxRateId, BigDecimal taxRateValue, UUID classId) { }

    /** Чеклар репозиторийси. */
    private final RefundReceiptRepository repository;

    /** Ҳаволали prefill/таннарх учун invoice - ўз модулимиз ичида. */
    private final InvoiceRepository invoiceRepository;

    /**
     * CM ҳовузи (BR-RET-006 кумулятив) - CM ва RR битта invoice'нинг
     * қайтимлари, кредит миқдорлари ҳам лимитга киради; ўз модулимиз ичида.
     */
    private final CreditMemoRepository creditMemoRepository;

    /** Ҳужжат рақамлари (RR-2026-NNNNN). */
    private final DocumentSequenceService sequenceService;

    /** Customer текшируви - contact модулининг public API'си. */
    private final ContactService contactService;

    /** Item тури/счётлари - item модулининг public API'си. */
    private final ItemService itemService;

    /** UoM конверсияси (factorBetween) - item модулининг public API'си. */
    private final com.averpo.erp.item.service.UnitService unitService;

    /** Омбор текшируви - inventory модулининг public API'си. */
    private final WarehouseService warehouseService;

    /** Омборга қайтим кирими ва асл сотув таннархи. */
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
    public RefundReceipt get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Чек топилмади: " + id));
    }

    /** Кўриш учун - сатрлари билан. */
    @Transactional(readOnly = true)
    public RefundReceipt getWithLines(UUID id) {
        return repository.findWithLinesById(id)
                .orElseThrow(() -> new NotFoundException("Чек топилмади: " + id));
    }

    /**
     * Рўйхат филтри (Arbitr-068, list-filters.md): барча майдонлар
     * ихтиёрий (null - чекланмаган); q - рақам/изоҳ contains
     * (катта-кичик фарқсиз, кирилл ҳам).
     */
    public record ListFilter(LocalDate from, LocalDate to, RefundReceipt.Status status,
                             UUID customerId, String q) {
    }

    /**
     * Рўйхат экрани - саҳифаланган (янгидан эскига), тўлиқ филтр
     * (Arbitr-068): давр/статус/мижоз/матн битта Specification'да
     * (audit услуби, ListSpecs бўлаклари).
     */
    @Transactional(readOnly = true)
    public Page<RefundReceipt> list(ListFilter filter, int page, int size) {
        return repository.findAll(org.springframework.data.jpa.domain.Specification.allOf(
                        com.averpo.erp.shared.repo.ListSpecs.dateFrom("rrDate", filter.from()),
                        com.averpo.erp.shared.repo.ListSpecs.dateTo("rrDate", filter.to()),
                        com.averpo.erp.shared.repo.ListSpecs.eq("status", filter.status()),
                        com.averpo.erp.shared.repo.ListSpecs.eq("customerId", filter.customerId()),
                        com.averpo.erp.shared.repo.ListSpecs.textContains(filter.q(),
                                "rrNumber", "memo")),
                PageRequest.of(Math.max(0, page), size, LIST_SORT));
    }

    /** Default ҳажм ({@link #LIST_PAGE_SIZE}) билан - эски чақирувчилар/тестлар (ARBITR-105). */
    @Transactional(readOnly = true)
    public Page<RefundReceipt> list(ListFilter filter, int page) {
        return list(filter, page, LIST_PAGE_SIZE);
    }

    /**
     * Яратиш - дарҳол POSTED (bank txn нақши): GL (Dr даромад net +
     * Dr ҚҚС ставка кесимида / Cr пул счёти gross) + ITEM сатрларга
     * StockMovement IN ва Dr INVENTORY / Cr COGS (home, қайтим
     * таннархида). Class сатрдан даромад/COGS легига кўчади
     * (class-tracking.md), пул счёти ва жамланган ҚҚС class'сиз.
     *
     * @throws BusinessRuleException BR-RET-001/002/006, BR-TAX-003/004
     */
    public RefundReceipt create(RefundReceiptData data) {
        Normalized normalized = validate(data);
        RefundReceipt receipt = new RefundReceipt(
                sequenceService.next(DocumentType.REFUND_RECEIPT, data.rrDate()),
                data.customerId(), data.invoiceId(), data.bankAccountId(),
                data.rrDate(), normalized.currency(), normalized.rate(),
                data.amountsInclusive(), Strings.blankToNull(data.memo()));
        for (NormalizedLine line : normalized.lines()) {
            receipt.addLine(line.type(), line.itemId(), line.warehouseId(),
                    line.quantity(), line.unitPrice(), line.unitId(), line.unitFactor(),
                    line.incomeAccountId(), line.amount(),
                    line.taxRateId(), line.taxRateValue(), line.taxAmount(), line.memo())
                    .applyClass(line.classId());
        }
        repository.saveAndFlush(receipt);
        postGl(receipt, normalized.originalInvoice());
        receipt.markPosted(Instant.now());
        return receipt;
    }

    /**
     * Reverse: омбор киримлари тескари қайтарилади (reverseReceive -
     * кейинги ҳаракат гарови BR-INV-010 inventory'дан), кейин GL сторно.
     * Application йўқлиги учун CreditMemo'даги қўлланганлик тўсиғи
     * керак эмас - фақат POSTED ҳолат текширилади.
     */
    public RefundReceipt reverse(UUID id, LocalDate reversalDate, String reason) {
        RefundReceipt receipt = get(id);
        if (receipt.getStatus() != RefundReceipt.Status.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_RET_007,
                    "Фақат POSTED чек reverse қилинади: " + receipt.getRrNumber()
                    + " ҳозир " + receipt.getStatus());
        }
        for (StockMovement movement : inventoryService.byReference(SOURCE_MODULE, receipt.getId())) {
            if (movement.getType().inbound()) {
                inventoryService.reverseReceive(movement.getId(), reversalDate);
            }
        }
        postingService.reverseBySource(SOURCE_MODULE, receipt.getId(), reversalDate,
                reason == null || reason.isBlank() ? "Пул қайтариш чеки reverse" : reason);
        receipt.markReversed();
        return receipt;
    }

    // ---- ички ёрдамчилар ----

    /** Валидациядан ўтган нормаллашган қийматлар. */
    private record Normalized(Currency currency, BigDecimal rate,
                              List<NormalizedLine> lines, Invoice originalInvoice) { }

    /** Нормаллашган сатр - CreditMemo NormalizedLine кўзгуси. */
    private record NormalizedLine(InvoiceLineType type, UUID itemId, UUID warehouseId,
                                  BigDecimal quantity, BigDecimal unitPrice,
                                  UUID incomeAccountId, BigDecimal amount, String memo,
                                  UUID unitId, BigDecimal unitFactor,
                                  UUID taxRateId, BigDecimal taxRateValue,
                                  BigDecimal taxAmount, UUID classId) { }

    /**
     * Сарлавҳа + сатрлар валидацияси (BR-RET-001/002/006). Пул счёти:
     * BANK туридан, фаол/postable ва валютаси ҳужжат валютасига тенг
     * (BR-RET-001 оиласи, каталог «Қўшимча») - пул счётига ўз
     * валютасидан бошқа валютада ёзиб бўлмайди (banking қолипи).
     * Ҳужжат валютаси мижоз контактидан (BR-RET-008, Arbitr-087).
     */
    private Normalized validate(RefundReceiptData data) {
        if (data.customerId() == null) {
            throw new BusinessRuleException(BusinessRule.BR_RET_001,
                    "Customer танланиши шарт");
        }
        Contact customer = contactService.get(data.customerId());
        if (customer.getType() != ContactType.CUSTOMER || !customer.isActive()) {
            throw new BusinessRuleException(BusinessRule.BR_RET_001,
                    "Customer фаол CUSTOMER типдаги контакт бўлиши шарт: "
                    + customer.getDisplayName());
        }
        if (data.rrDate() == null) {
            throw new BusinessRuleException(BusinessRule.BR_RET_001,
                    "Ҳужжат санаси киритилиши шарт");
        }
        if (data.lines() == null || data.lines().isEmpty()) {
            throw new BusinessRuleException(BusinessRule.BR_RET_001,
                    "Камида битта сатр киритилиши шарт");
        }
        // Валюта ҳақиқат манбаи - мижоз контакти (QBO қатъий, Arbitr-087):
        // client қиймати фақат мосликка текширилади, ҳужжатга контактники ёзилади
        Currency currency = currencyService.require(contactService
                .requireDocumentCurrency(customer, data.currency(), BusinessRule.BR_RET_008));
        BigDecimal rate = currencyService.requireDocumentRate(
                currency, data.exchangeRate(), BusinessRule.BR_RET_001);
        requireBankAccount(data.bankAccountId(), currency);

        Invoice original = null;
        if (data.invoiceId() != null) {
            original = invoiceRepository.findWithLinesById(data.invoiceId())
                    .orElseThrow(() -> new NotFoundException(
                            "Invoice топилмади: " + data.invoiceId()));
            if (!original.getCustomerId().equals(data.customerId())) {
                throw new BusinessRuleException(BusinessRule.BR_RET_005,
                        "Асл invoice бошқа мижозники: " + original.getInvoiceNumber());
            }
            // BR-RET-006: DRAFT/REVERSED «асл ҳужжат» бўла олмайди - GL'да
            // акс этмаган (ёки бекор бўлган) сотувга қайтим боғланмайди
            // (CM create кўзгуси)
            if (original.getStatus() != InvoiceStatus.POSTED) {
                throw new BusinessRuleException(BusinessRule.BR_RET_006,
                        "Асл invoice POSTED бўлиши шарт: " + original.getInvoiceNumber()
                        + " ҳозир " + original.getStatus());
            }
        }

        // Батч lookup (Arbitr-045 findAllById, Sanjar-003 - SalesReceipt
        // эталони): сатр-циклда item/омбор/счёт биттадан ўқилмасин; даромад
        // счёти доим item default'идан - id'лар юкланган item'лардан йиғилади
        Map<UUID, Item> items = BatchLookup.byId(
                itemService.findAllById(BatchLookup.ids(data.lines(), LineData::itemId)));
        Map<UUID, Warehouse> warehouses = BatchLookup.byId(
                warehouseService.findAllById(BatchLookup.ids(data.lines(), LineData::warehouseId)));
        Map<UUID, Account> accounts = BatchLookup.byId(
                accountService.findAllById(BatchLookup.ids(items.values(), Item::getIncomeAccountId)));
        List<NormalizedLine> lines = new ArrayList<>();
        int no = 0;
        for (LineData line : data.lines()) {
            no++;
            lines.add(validateLine(no, line, data.amountsInclusive(), original,
                    items, accounts, warehouses));
        }
        if (original != null) {
            requireWithinOriginalQuantities(lines, original);
        }
        return new Normalized(currency, rate, lines, original);
    }

    /**
     * Пул счёти гарови: BANK туридан (банк/касса счётлари шу турда),
     * фаол/postable, валютаси (бўш бўлса home) ҳужжат валютасига тенг.
     */
    private void requireBankAccount(UUID accountId, Currency docCurrency) {
        if (accountId == null) {
            throw new BusinessRuleException(BusinessRule.BR_RET_001,
                    "Пул счёти танланиши шарт");
        }
        Account account = accountService.get(accountId);
        if (account.getType() != AccountType.BANK || !account.isActive()
                || !account.isPostable()) {
            throw new BusinessRuleException(BusinessRule.BR_RET_001,
                    "Пул счёти BANK туридан, фаол ва postable бўлиши шарт: "
                    + account.getName());
        }
        String accountCurrency = account.getCurrency() != null
                ? account.getCurrency().getCode() : settingsService.homeCurrency();
        if (!accountCurrency.equals(docCurrency.getCode())) {
            throw new BusinessRuleException(BusinessRule.BR_RET_001,
                    "Пул счёти валютаси (" + accountCurrency + ") ҳужжат валютаси ("
                    + docCurrency.getCode() + ") билан бир хил бўлиши шарт: "
                    + account.getName());
        }
    }

    /**
     * Сатр валидацияси - CreditMemo validateLine'нинг айнан кўзгуси.
     * item/омбор/счёт олдиндан юкланган батч Map'лардан ўқилади (Sanjar-003) -
     * топилмаса {@link NotFoundException} (аввалги get() хулқи айнан).
     */
    private NormalizedLine validateLine(int no, LineData line, boolean inclusive,
                                        Invoice original, Map<UUID, Item> items,
                                        Map<UUID, Account> accounts,
                                        Map<UUID, Warehouse> warehouses) {
        if (line.itemId() == null) {
            throw new BusinessRuleException(BusinessRule.BR_RET_001,
                    no + "-сатр: item танланиши шарт");
        }
        Item item = items.get(line.itemId());
        if (item == null) {
            throw new NotFoundException("Item топилмади: " + line.itemId());
        }
        if (!item.isActive()) {
            throw new BusinessRuleException(BusinessRule.BR_RET_001,
                    no + "-сатр: нофаол item қайтарилмайди: «" + item.getName() + "»");
        }
        if (line.quantity() == null || line.quantity().signum() <= 0
                || line.unitPrice() == null || line.unitPrice().signum() < 0) {
            throw new BusinessRuleException(BusinessRule.BR_RET_001,
                    no + "-сатр: миқдор мусбат, нарх манфий эмас бўлиши шарт");
        }
        InvoiceLineType type = item.getType() == ItemType.INVENTORY
                ? InvoiceLineType.ITEM : InvoiceLineType.SERVICE;
        UUID warehouseId = null;
        if (type == InvoiceLineType.ITEM) {
            if (line.warehouseId() == null) {
                throw new BusinessRuleException(BusinessRule.BR_RET_002,
                        no + "-сатр: inventory сатрида омбор танланиши шарт");
            }
            warehouseId = line.warehouseId();
            if (warehouses.get(warehouseId) == null) { // мавжудлик (NotFound)
                throw new NotFoundException("Омбор топилмади: " + warehouseId);
            }
        }
        // Ҳаволали ҳужжатда асл сатр (item бўйича) - таннарх/snapshot манбаси
        InvoiceLine originalLine = original == null ? null
                : original.getLines().stream()
                        .filter(l -> l.getItemId().equals(line.itemId()))
                        .findFirst()
                        .orElseThrow(() -> new BusinessRuleException(BusinessRule.BR_RET_006,
                                "Item асл ҳужжатда йўқ: «" + item.getName() + "»"));

        // ҚҚС snapshot (tax.md): берилган қиймат устун; ҳаволали ҳужжатда
        // асл сатр ставкаси (орада каталог ўзгарган бўлса ҳам тўғри қайтим)
        BigDecimal snapshot = line.taxRateValue();
        if (snapshot == null && originalLine != null
                && java.util.Objects.equals(originalLine.getTaxRateId(), line.taxRateId())) {
            snapshot = originalLine.getTaxRateValue();
        }
        BigDecimal taxValue = taxRateService.documentRateValue(line.taxRateId(), snapshot);
        BigDecimal raw = line.quantity().multiply(line.unitPrice())
                .setScale(4, RoundingMode.HALF_UP);
        TaxAmounts ta = TaxAmounts.of(raw, taxValue, inclusive);
        if (ta.net().signum() <= 0) {
            throw new BusinessRuleException(BusinessRule.BR_RET_001,
                    no + "-сатр: сумма мусбат бўлиши шарт");
        }

        UUID incomeAccountId = item.getIncomeAccountId();
        Account income = accounts.get(incomeAccountId);
        if (income == null) {
            throw new NotFoundException("Счёт топилмади: " + incomeAccountId);
        }
        if (!income.isActive() || !income.isPostable()
                || income.getClassification() != AccountClassification.REVENUE) {
            throw new BusinessRuleException(BusinessRule.BR_RET_001,
                    no + "-сатр: даромад счёти REVENUE туркумидан, фаол ва postable "
                    + "бўлиши шарт: " + income.getName());
        }
        return new NormalizedLine(type, line.itemId(), warehouseId,
                line.quantity(), line.unitPrice(), incomeAccountId, ta.net(),
                Strings.blankToNull(line.memo()), line.unitId(),
                unitService.lineFactor(no, item, line.unitId(), line.quantity(), true,
                        BusinessRule.BR_RET_001), line.taxRateId(), taxValue,
                ta.tax(), line.classId());
    }


    /**
     * BR-RET-006 (кумулятив): item бўйича жорий чек + шу invoice'га
     * аввалги POSTED қайтимлар йиғиндиси асл сатр(лар) base миқдоридан
     * ошмайди (қисман қайтариш мумкин) - CM requireWithinOriginalQuantities
     * кўзгуси, ҳовуз умумий (CM + RR).
     */
    private void requireWithinOriginalQuantities(List<NormalizedLine> lines,
                                                 Invoice original) {
        Map<UUID, BigDecimal> returnQty = new HashMap<>();
        for (NormalizedLine line : lines) {
            BigDecimal factor = line.unitFactor() == null ? BigDecimal.ONE : line.unitFactor();
            returnQty.merge(line.itemId(),
                    line.quantity().multiply(factor).setScale(4, RoundingMode.HALF_UP),
                    BigDecimal::add);
        }
        Map<UUID, BigDecimal> prior = priorReturnedQuantities(original.getId());
        for (Map.Entry<UUID, BigDecimal> entry : returnQty.entrySet()) {
            BigDecimal originalQty = original.getLines().stream()
                    .filter(l -> l.getItemId().equals(entry.getKey()))
                    .map(l -> l.getQuantity().multiply(l.unitFactorOrOne())
                            .setScale(4, RoundingMode.HALF_UP))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal previous = prior.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            BigDecimal cumulative = entry.getValue().add(previous);
            if (cumulative.compareTo(originalQty) > 0) {
                throw new BusinessRuleException(BusinessRule.BR_RET_006,
                        "Қайтариш миқдори аввалги қайтимлар (" + previous
                        + ") билан жами (" + cumulative + ") асл ҳужжатдаги ("
                        + originalQty + ") дан ошмайди: " + original.getInvoiceNumber());
            }
        }
    }

    /**
     * Шу invoice'га аллақачон POSTED қайтимларнинг item кесимидаги base
     * миқдорлари - BR-RET-006 кумулятив ҳовузи (CM priorReturnedQuantities
     * кўзгуси): CM ва RR биргаликда, REVERSED кирмайди. Яхлитлаш жорий
     * сатрлардагидек (ҳар сатрда setScale 4).
     */
    private Map<UUID, BigDecimal> priorReturnedQuantities(UUID invoiceId) {
        Map<UUID, BigDecimal> prior = new HashMap<>();
        for (CreditMemo cm : creditMemoRepository.findWithLinesByInvoiceIdAndStatus(
                invoiceId, CreditMemo.Status.POSTED)) {
            for (CreditMemoLine line : cm.getLines()) {
                prior.merge(line.getItemId(),
                        line.getQuantity().multiply(line.unitFactorOrOne())
                                .setScale(4, RoundingMode.HALF_UP),
                        BigDecimal::add);
            }
        }
        for (RefundReceipt rr : repository.findWithLinesByInvoiceIdAndStatus(
                invoiceId, RefundReceipt.Status.POSTED)) {
            for (RefundReceiptLine line : rr.getLines()) {
                prior.merge(line.getItemId(),
                        line.getQuantity().multiply(line.unitFactorOrOne())
                                .setScale(4, RoundingMode.HALF_UP),
                        BigDecimal::add);
            }
        }
        return prior;
    }

    /**
     * GL (posting-rules «Қайтариш» RefundReceipt банди - CreditMemo
     * билан бир хил, AR ўрнига пул счёти) + омбор кирими. Penny
     * rounding CreditMemo қолипи: чет валютада дебет леглар base'и
     * largest-remainder билан пул счёти gross target'ига тақсимланади.
     */
    private void postGl(RefundReceipt receipt, Invoice originalInvoice) {
        String home = settingsService.homeCurrency();
        String docCurrency = receipt.getCurrency().getCode();
        boolean isHome = docCurrency.equals(home);
        BigDecimal rate = receipt.getExchangeRate();

        // 1) Дебет леглар: даромад net (class билан) + ставка кесимида ҚҚС
        record DebitLeg(UUID account, BigDecimal amount, UUID warehouseId,
                        UUID itemId, String memo, UUID classId) { }
        List<DebitLeg> legs = new ArrayList<>();
        Map<BigDecimal, BigDecimal> taxByRate = new LinkedHashMap<>();
        for (RefundReceiptLine line : receipt.getLines()) {
            legs.add(new DebitLeg(line.getIncomeAccountId(), line.getAmount(),
                    line.getWarehouseId(), line.getItemId(), line.getMemo(),
                    line.getClassId()));
            if (line.getTaxAmount().signum() > 0) {
                taxByRate.merge(line.getTaxRateValue(), line.getTaxAmount(), BigDecimal::add);
            }
        }
        UUID taxAccount = accountService.requireSystemAccountId(AccountDetailType.SALES_TAX_PAYABLE);
        for (BigDecimal taxSum : taxByRate.values()) {
            // Жамланган лег - бир нечта class аралашади, class'сиз
            legs.add(new DebitLeg(taxAccount, taxSum, null, null, null, null));
        }

        List<BigDecimal> legBases = null;
        if (!isHome) {
            List<BigDecimal> amounts = new ArrayList<>(legs.size());
            for (DebitLeg leg : legs) {
                amounts.add(leg.amount());
            }
            legBases = MoneyAllocation.lineBases(amounts, rate);
        }
        List<JournalEntryRequest.Line> glLines = new ArrayList<>();
        for (int i = 0; i < legs.size(); i++) {
            DebitLeg leg = legs.get(i);
            Money debit = isHome
                    ? Money.ofBase(leg.amount(), home)
                    : Money.withBase(leg.amount(), docCurrency, legBases.get(i), rate);
            glLines.add(new JournalEntryRequest.Line(leg.account(), debit, null,
                    receipt.getCustomerId(), leg.warehouseId(), leg.itemId(), leg.memo(),
                    leg.classId()));
        }
        // 2) Пул счёти кредити = GROSS (AR эмас - пул дарҳол қайтади;
        //    назорат сатри - class'сиз)
        Money credit = isHome
                ? Money.ofBase(receipt.getTotal(), home)
                : Money.withBase(receipt.getTotal(), docCurrency,
                        MoneyAllocation.targetBase(receipt.getTotal(), rate), rate);
        glLines.add(new JournalEntryRequest.Line(receipt.getBankAccountId(),
                null, credit, receipt.getCustomerId(), null, null, null));

        // 3) ITEM сатрлар: омборга қайтим кирими + Dr INVENTORY / Cr COGS.
        // Батч (Sanjar-003): asset счёти учун item'лар олдиндан битта IN
        // сўровда - сатр циклида биттадан get() қилинмайди
        Map<UUID, Item> itemsById = BatchLookup.byId(itemService.findAllById(
                BatchLookup.ids(receipt.getLines(),
                        l -> l.getType() == InvoiceLineType.ITEM ? l.getItemId() : null)));
        UUID cogsAccount = null;
        for (RefundReceiptLine line : receipt.getLines()) {
            if (line.getType() != InvoiceLineType.ITEM) {
                continue;
            }
            BigDecimal baseQty = line.getQuantity().multiply(line.unitFactorOrOne())
                    .setScale(4, RoundingMode.HALF_UP);
            BigDecimal unitCost = returnUnitCost(line, originalInvoice);
            StockMovement movement = inventoryService.receive(line.getItemId(),
                    line.getWarehouseId(), baseQty, unitCost, receipt.getRrDate(),
                    SOURCE_MODULE, receipt.getId(), receipt.getRrNumber());
            if (movement.getTotalCost().signum() > 0) {
                if (cogsAccount == null) {
                    cogsAccount = accountService.requireSystemAccountId(
                            AccountDetailType.SUPPLIES_MATERIALS_COGS);
                }
                Money cost = Money.ofBase(movement.getTotalCost(), home);
                UUID assetAccount = itemsById.get(line.getItemId())
                        .getInventoryAssetAccountId();
                // COGS қайтиши сатрдан келиб чиқади - class кўчади;
                // INVENTORY леги Balance Sheet назорати - class'сиз
                glLines.add(new JournalEntryRequest.Line(assetAccount, cost, null,
                        receipt.getCustomerId(), line.getWarehouseId(),
                        line.getItemId(), null));
                glLines.add(new JournalEntryRequest.Line(cogsAccount, null, cost,
                        receipt.getCustomerId(), line.getWarehouseId(),
                        line.getItemId(), null, line.getClassId()));
            }
        }

        postingService.createAndPost(new JournalEntryRequest(
                receipt.getRrDate(),
                "Пул қайтариш " + receipt.getRrNumber() + " - "
                        + contactService.get(receipt.getCustomerId()).getDisplayName(),
                SOURCE_MODULE, receipt.getId(), glLines));
    }

    /**
     * Қайтим бирлик таннархи (posting-rules «Inventory қайтим таннархи»,
     * CreditMemo returnUnitCost кўзгуси): ҳаволали ҳужжатда - асл сотув
     * OUT ҳаракатининг бирлик таннархи; ҳаволасиз - жорий сиёсат
     * таннархи (AVCO ўртачаси; FIFO'да янги қатлам шу нархда киради).
     */
    private BigDecimal returnUnitCost(RefundReceiptLine line, Invoice originalInvoice) {
        if (originalInvoice != null) {
            for (StockMovement movement : inventoryService.byReference(
                    InvoiceService.SOURCE_MODULE, originalInvoice.getId())) {
                if (!movement.getType().inbound()
                        && movement.getItemId().equals(line.getItemId())
                        && movement.getWarehouse().getId().equals(line.getWarehouseId())) {
                    return movement.getUnitCost();
                }
            }
            // Асл ҳужжатда шу (item, омбор) чиқими йўқ - жорий сиёсатга тушади
        }
        return inventoryService.currentAvgCost(line.getItemId(), line.getWarehouseId());
    }
}
