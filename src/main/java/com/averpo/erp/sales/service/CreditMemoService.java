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
import com.averpo.erp.sales.domain.CreditApplication;
import com.averpo.erp.sales.domain.CreditMemo;
import com.averpo.erp.sales.domain.CreditMemoLine;
import com.averpo.erp.sales.domain.Invoice;
import com.averpo.erp.sales.domain.InvoiceLine;
import com.averpo.erp.sales.domain.InvoiceLineType;
import com.averpo.erp.sales.domain.InvoiceStatus;
import com.averpo.erp.sales.domain.RefundReceipt;
import com.averpo.erp.sales.domain.RefundReceiptLine;
import com.averpo.erp.sales.repo.CreditApplicationRepository;
import com.averpo.erp.sales.repo.CreditMemoRepository;
import com.averpo.erp.sales.repo.InvoiceRepository;
import com.averpo.erp.sales.repo.RefundReceiptRepository;
import com.averpo.erp.shared.BatchLookup;
import com.averpo.erp.shared.Fx;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Мижоз кредит-нотасининг ягона public API'си (docs/modules/returns.md).
 * Invoice КЎЗГУСИ: DRAFT йўқ - {@link #create} дарҳол POSTED (GL +
 * ITEM сатрларга омбор кирими); {@link #apply} - GL'сиз subledger
 * қўллаш (фақат FX фарқи алоҳида JE); {@link #reverse} - фақат
 * қўлланмаган кредитда (BR-RET-007).
 *
 * <p>GL фақат PostingService (қоида №2), омбор фақат InventoryService
 * public API'си (қоида №6). Проводкалар posting-rules «Қайтариш»
 * бўлимига қатъий мос (қоида №8).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CreditMemoService {

    /** GL/омбор ҳаволаларидаги манба модул белгиси (posting-rules). */
    public static final String SOURCE_MODULE = "CREDIT_MEMO";

    /** FX фарқи JE'ларининг манба белгиси - docId = application id. */
    public static final String APPLICATION_SOURCE_MODULE = "CREDIT_APPLICATION";

    /**
     * Кредит-нота формаси маълумотлари. invoiceId - ихтиёрий асл ҳужжат
     * ҳаволаси: сатрлар prefill бўлади, қайтим таннархи асл сотув
     * ҳаракатидан, ҚҚС snapshot асл сатрдан (BR-RET-006 миқдор чеклови
     * билан).
     */
    public record CreditMemoData(UUID customerId, UUID invoiceId, LocalDate cmDate,
                                 String currency, BigDecimal exchangeRate,
                                 boolean amountsInclusive, String memo,
                                 List<LineData> lines) { }

    /**
     * Битта сатр: invoice LineData кўзгуси. taxRateValue - ҳаволали
     * prefill'дан келган snapshot (бўш бўлса ҳаволали ҳужжатда асл
     * сатрдан олинади, акс ҳолда каталог қиймати).
     */
    public record LineData(UUID itemId, UUID warehouseId, BigDecimal quantity,
                           BigDecimal unitPrice, String memo, UUID unitId,
                           UUID taxRateId, BigDecimal taxRateValue, UUID classId) { }

    /** Кредит-ноталар репозиторийси. */
    private final CreditMemoRepository repository;

    /** Қўллашлар репозиторийси. */
    private final CreditApplicationRepository applicationRepository;

    /** Invoice денормализацияси (balance) учун - ўз модулимиз ичида. */
    private final InvoiceRepository invoiceRepository;

    /**
     * RR ҳовузи (BR-RET-006 кумулятив) - CM ва RR битта invoice'нинг
     * қайтимлари, чек миқдорлари ҳам лимитга киради; ўз модулимиз ичида.
     */
    private final RefundReceiptRepository refundReceiptRepository;

    /** Ҳужжат рақамлари (CM-2026-NNNNN). */
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

    /** Тизим счётлари (AR, COGS) ва даромад счёти валидацияси. */
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
    public CreditMemo get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Кредит-нота топилмади: " + id));
    }

    /** Кўриш учун - сатрлари билан. */
    @Transactional(readOnly = true)
    public CreditMemo getWithLines(UUID id) {
        return repository.findWithLinesById(id)
                .orElseThrow(() -> new NotFoundException("Кредит-нота топилмади: " + id));
    }

    /** Рўйхат саҳифаси ҳажми (Beruniy-perf1 2-босқич). */
    public static final int LIST_PAGE_SIZE = 25;

    /**
     * Рўйхат тартиби - аввалги ORDER BY'га айнан мос (янгидан эскига,
     * тенг санада яратилиш вақти) - саҳифалашга ўтишда экран тартиби
     * ўзгармасин (Beruniy-perf1). A-тўлқин рўйхати retrofit'и.
     */
    private static final org.springframework.data.domain.Sort LIST_SORT =
            org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Order.desc("cmDate"),
                    org.springframework.data.domain.Sort.Order.desc("createdAt"),
                    org.springframework.data.domain.Sort.Order.desc("id"));

    /**
     * Рўйхат филтри (Arbitr-068, list-filters.md): барча майдонлар
     * ихтиёрий (null - чекланмаган); q - рақам/изоҳ contains
     * (катта-кичик фарқсиз, кирилл ҳам).
     */
    public record ListFilter(java.time.LocalDate from, java.time.LocalDate to,
                             CreditMemo.Status status, UUID customerId, String q) {
    }

    /**
     * Рўйхат экрани - саҳифаланган (Beruniy-perf1), тўлиқ филтр
     * (Arbitr-068): давр/статус/мижоз/матн битта Specification'да
     * (audit услуби, ListSpecs бўлаклари).
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<CreditMemo> list(ListFilter filter, int page, int size) {
        return repository.findAll(org.springframework.data.jpa.domain.Specification.allOf(
                        com.averpo.erp.shared.repo.ListSpecs.dateFrom("cmDate", filter.from()),
                        com.averpo.erp.shared.repo.ListSpecs.dateTo("cmDate", filter.to()),
                        com.averpo.erp.shared.repo.ListSpecs.eq("status", filter.status()),
                        com.averpo.erp.shared.repo.ListSpecs.eq("customerId", filter.customerId()),
                        com.averpo.erp.shared.repo.ListSpecs.textContains(filter.q(),
                                "cmNumber", "memo")),
                org.springframework.data.domain.PageRequest.of(
                        Math.max(0, page), size, LIST_SORT));
    }

    /** Default ҳажм ({@link #LIST_PAGE_SIZE}) билан - эски чақирувчилар/тестлар (ARBITR-105). */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<CreditMemo> list(ListFilter filter, int page) {
        return list(filter, page, LIST_PAGE_SIZE);
    }

    /** Кредитнинг қўллашлари - кўриш экрани ва тестлар учун. */
    @Transactional(readOnly = true)
    public List<CreditApplication> applicationsOf(UUID creditMemoId) {
        return applicationRepository.findByCreditMemoIdOrderByCreatedAtAsc(creditMemoId);
    }

    /** Invoice'га қўлланган кредитлар - invoice кўриш экрани учун. */
    @Transactional(readOnly = true)
    public List<CreditApplication> applicationsForInvoice(UUID invoiceId) {
        return applicationRepository.findByInvoiceIdOrderByCreatedAtAsc(invoiceId);
    }

    /** Invoice'дан яратилган кредитлар - invoice кўриш экрани учун. */
    @Transactional(readOnly = true)
    public List<CreditMemo> byInvoice(UUID invoiceId) {
        return repository.findByInvoiceIdOrderByCreatedAtAsc(invoiceId);
    }

    /**
     * Яратиш - дарҳол POSTED (bank txn нақши): GL (Dr даромад net +
     * Dr ҚҚС ставка кесимида / Cr AR gross) + ITEM сатрларга
     * StockMovement IN ва Dr INVENTORY / Cr COGS (home, қайтим
     * таннархида). Class сатрдан даромад/COGS легига кўчади
     * (class-tracking.md), AR ва жамланган ҚҚС class'сиз.
     *
     * @throws BusinessRuleException BR-RET-001/002/006, BR-TAX-003/004
     */
    public CreditMemo create(CreditMemoData data) {
        Normalized normalized = validate(data);
        CreditMemo memo = new CreditMemo(
                sequenceService.next(DocumentType.CREDIT_MEMO, data.cmDate()),
                data.customerId(), data.invoiceId(), data.cmDate(),
                normalized.currency(), normalized.rate(), data.amountsInclusive(),
                Strings.blankToNull(data.memo()));
        for (NormalizedLine line : normalized.lines()) {
            memo.addLine(line.type(), line.itemId(), line.warehouseId(),
                    line.quantity(), line.unitPrice(), line.unitId(), line.unitFactor(),
                    line.incomeAccountId(), line.amount(),
                    line.taxRateId(), line.taxRateValue(), line.taxAmount(), line.memo())
                    .applyClass(line.classId());
        }
        repository.saveAndFlush(memo);
        postGl(memo, normalized.originalInvoice());
        memo.markPosted(Instant.now());
        return memo;
    }

    /**
     * Кредитни invoice'га қўллаш - GL'СИЗ subledger ҳаракати (иккала
     * ҳужжат ўз JE'сини ёзган, AR тўғри); фақат realized FX фарқи
     * алоҳида JE (APPLICATION_SOURCE_MODULE, payment allocation нақши).
     * Invoice balance'и тўлов каби камаяди (paid денормализацияси).
     *
     * @throws BusinessRuleException BR-RET-001/003/004/005
     */
    public CreditApplication apply(UUID creditMemoId, UUID invoiceId, BigDecimal amount) {
        CreditMemo memo = get(creditMemoId);
        if (memo.getStatus() != CreditMemo.Status.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_RET_003,
                    "Фақат POSTED кредит қўлланади: " + memo.getCmNumber()
                    + " ҳозир " + memo.getStatus());
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException(BusinessRule.BR_RET_001,
                    "Қўллаш суммаси мусбат бўлиши шарт");
        }
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new NotFoundException("Invoice топилмади: " + invoiceId));
        if (invoice.getStatus() != InvoiceStatus.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_RET_003,
                    "Кредит фақат POSTED invoice'га қўлланади: "
                    + invoice.getInvoiceNumber() + " ҳозир " + invoice.getStatus());
        }
        if (!invoice.getCustomerId().equals(memo.getCustomerId())) {
            throw new BusinessRuleException(BusinessRule.BR_RET_005,
                    "Invoice бошқа мижозники: " + invoice.getInvoiceNumber());
        }
        if (!invoice.getCurrency().getCode().equals(memo.getCurrency().getCode())) {
            throw new BusinessRuleException(BusinessRule.BR_RET_004,
                    "Кредит валютаси (" + memo.getCurrency().getCode()
                    + ") invoice валютаси (" + invoice.getCurrency().getCode()
                    + ") билан бир хил бўлиши шарт: " + invoice.getInvoiceNumber());
        }
        if (amount.compareTo(memo.getOpenBalance()) > 0) {
            throw new BusinessRuleException(BusinessRule.BR_RET_003,
                    "Қўллаш (" + amount + ") кредитнинг очиқ қолдиғидан ("
                    + memo.getOpenBalance() + ") ошмайди: " + memo.getCmNumber());
        }
        if (amount.compareTo(invoice.getBalanceDue()) > 0) {
            throw new BusinessRuleException(BusinessRule.BR_RET_003,
                    "Қўллаш (" + amount + ") invoice қолдиғидан ("
                    + invoice.getBalanceDue() + ") ошмайди: " + invoice.getInvoiceNumber());
        }
        if (applicationRepository.existsByCreditMemoIdAndInvoiceId(memo.getId(), invoice.getId())) {
            throw new BusinessRuleException(BusinessRule.BR_RET_003,
                    "Бу кредитдан бу invoice'га қўллаш аллақачон бор: "
                    + invoice.getInvoiceNumber());
        }
        CreditApplication application = applicationRepository.saveAndFlush(
                new CreditApplication(memo, invoice, amount));
        invoice.applyPaidAmount(invoice.getPaidAmount().add(amount));
        memo.applyAppliedAmount(memo.getAppliedAmount().add(amount));
        postFxDifference(memo, invoice, application);
        return application;
    }

    /**
     * Қўллашни бекор қилиш (unapply): FX JE бўлса сторно, invoice ва
     * кредит денормализациялари тикланади, ёзув ЎЧИРИЛАДИ (кредитнинг
     * ўзи очиқ қолдиғи билан туради - BR-RET-007 йўли шу).
     */
    public void unapply(UUID applicationId, LocalDate reversalDate) {
        CreditApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException(
                        "Қўллаш топилмади: " + applicationId));
        CreditMemo memo = application.getCreditMemo();
        Invoice invoice = application.getInvoice();
        // Нол фарқда JE ёзилмаган - детерминистик қайта ҳисоб (payment нақши)
        if (Fx.realizedFxDifference(application.getAmount(), memo.getExchangeRate(),
                invoice.getExchangeRate()).signum() != 0) {
            postingService.reverseBySource(APPLICATION_SOURCE_MODULE,
                    application.getId(), reversalDate, "Кредит қўллаши бекор қилинди");
        }
        invoice.applyPaidAmount(invoice.getPaidAmount().subtract(application.getAmount()));
        memo.applyAppliedAmount(memo.getAppliedAmount().subtract(application.getAmount()));
        applicationRepository.delete(application);
    }

    /**
     * Reverse: фақат қўлланмаган кредитда (BR-RET-007 - аввал unapply),
     * омбор киримлари тескари қайтарилади (reverseReceive - кейинги
     * ҳаракат гарови BR-INV-010 inventory'дан), кейин GL сторно.
     *
     * @throws BusinessRuleException BR-RET-007
     */
    public CreditMemo reverse(UUID id, LocalDate reversalDate, String reason) {
        CreditMemo memo = get(id);
        if (memo.getStatus() != CreditMemo.Status.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_RET_007,
                    "Фақат POSTED кредит reverse қилинади: " + memo.getCmNumber()
                    + " ҳозир " + memo.getStatus());
        }
        if (memo.getAppliedAmount().signum() > 0) {
            throw new BusinessRuleException(BusinessRule.BR_RET_007,
                    "Қўлланган кредит reverse қилинмайди - аввал қўллашлар бекор "
                    + "қилинади: " + memo.getCmNumber());
        }
        for (StockMovement movement : inventoryService.byReference(SOURCE_MODULE, memo.getId())) {
            if (movement.getType().inbound()) {
                inventoryService.reverseReceive(movement.getId(), reversalDate);
            }
        }
        postingService.reverseBySource(SOURCE_MODULE, memo.getId(), reversalDate,
                reason == null || reason.isBlank() ? "Кредит-нота reverse" : reason);
        memo.markReversed();
        return memo;
    }

    // ---- ички ёрдамчилар ----

    /** Валидациядан ўтган нормаллашган қийматлар. */
    private record Normalized(Currency currency, BigDecimal rate,
                              List<NormalizedLine> lines, Invoice originalInvoice) { }

    /** Нормаллашган сатр - invoice NormalizedLine кўзгуси. */
    private record NormalizedLine(InvoiceLineType type, UUID itemId, UUID warehouseId,
                                  BigDecimal quantity, BigDecimal unitPrice,
                                  UUID incomeAccountId, BigDecimal amount, String memo,
                                  UUID unitId, BigDecimal unitFactor,
                                  UUID taxRateId, BigDecimal taxRateValue,
                                  BigDecimal taxAmount, UUID classId,
                                  InvoiceLine originalLine) { }

    /** Сарлавҳа + сатрлар валидацияси (BR-RET-001/002/006/008 - валюта мижоздан, Arbitr-087). */
    private Normalized validate(CreditMemoData data) {
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
        if (data.cmDate() == null) {
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
            // (apply POSTED текшируви билан симметрия)
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
     * Сатр валидацияси - invoice validateLine кўзгуси (BR-RET кодларида).
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
                ta.tax(), line.classId(), originalLine);
    }


    /**
     * BR-RET-006 (кумулятив): item бўйича жорий ҳужжат + шу invoice'га
     * аввалги POSTED қайтимлар йиғиндиси асл сатр(лар) base миқдоридан
     * ошмайди (қисман қайтариш мумкин). Фақат жорий ҳужжат текширилса
     * 10 доналик сотувга 10 доналик CM + 10 доналик RR киритилиб қайтим
     * таннархи (Dr INVENTORY / Cr COGS) икки марта ёзиларди - IAS 2.34.
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
     * миқдорлари - BR-RET-006 кумулятив ҳовузи. CM ва RR биргаликда
     * (иккиси ҳам ўша invoice қайтими); REVERSED кирмайди - сторно
     * қайтимни бекор қилган. Яхлитлаш жорий сатрлардагидек (ҳар сатрда
     * setScale 4) - икки томон бир хил ўлчовда солиштирилсин.
     */
    private Map<UUID, BigDecimal> priorReturnedQuantities(UUID invoiceId) {
        Map<UUID, BigDecimal> prior = new HashMap<>();
        for (CreditMemo cm : repository.findWithLinesByInvoiceIdAndStatus(
                invoiceId, CreditMemo.Status.POSTED)) {
            for (CreditMemoLine line : cm.getLines()) {
                prior.merge(line.getItemId(),
                        line.getQuantity().multiply(line.unitFactorOrOne())
                                .setScale(4, RoundingMode.HALF_UP),
                        BigDecimal::add);
            }
        }
        for (RefundReceipt rr : refundReceiptRepository.findWithLinesByInvoiceIdAndStatus(
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
     * GL (posting-rules «Қайтариш» CreditMemo жадвали) + омбор кирими.
     * Penny rounding Bill/Invoice қолипи: чет валютада дебет леглар
     * base'и largest-remainder билан AR gross target'ига тақсимланади
     * (MoneyAllocation) - ҳар сатр BR-LED-003, йиғинди BR-LED-006.
     */
    private void postGl(CreditMemo memo, Invoice originalInvoice) {
        String home = settingsService.homeCurrency();
        String docCurrency = memo.getCurrency().getCode();
        boolean isHome = docCurrency.equals(home);
        BigDecimal rate = memo.getExchangeRate();

        // 1) Дебет леглар: даромад net (class билан) + ставка кесимида ҚҚС
        record DebitLeg(UUID account, BigDecimal amount, UUID warehouseId,
                        UUID itemId, String memo, UUID classId) { }
        List<DebitLeg> legs = new ArrayList<>();
        java.util.Map<BigDecimal, BigDecimal> taxByRate = new java.util.LinkedHashMap<>();
        for (CreditMemoLine line : memo.getLines()) {
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
                    memo.getCustomerId(), leg.warehouseId(), leg.itemId(), leg.memo(),
                    leg.classId()));
        }
        // 2) AR кредити = GROSS (назорат сатри - class'сиз)
        Money credit = isHome
                ? Money.ofBase(memo.getTotal(), home)
                : Money.withBase(memo.getTotal(), docCurrency,
                        MoneyAllocation.targetBase(memo.getTotal(), rate), rate);
        glLines.add(new JournalEntryRequest.Line(
                accountService.requireSystemAccountId(AccountDetailType.ACCOUNTS_RECEIVABLE),
                null, credit, memo.getCustomerId(), null, null, null));

        // 3) ITEM сатрлар: омборга қайтим кирими + Dr INVENTORY / Cr COGS.
        // Батч (Sanjar-003): asset счёти учун item'лар олдиндан битта IN
        // сўровда - сатр циклида биттадан get() қилинмайди
        Map<UUID, Item> itemsById = BatchLookup.byId(itemService.findAllById(
                BatchLookup.ids(memo.getLines(),
                        l -> l.getType() == InvoiceLineType.ITEM ? l.getItemId() : null)));
        UUID cogsAccount = null;
        for (CreditMemoLine line : memo.getLines()) {
            if (line.getType() != InvoiceLineType.ITEM) {
                continue;
            }
            BigDecimal baseQty = line.getQuantity().multiply(line.unitFactorOrOne())
                    .setScale(4, RoundingMode.HALF_UP);
            BigDecimal unitCost = returnUnitCost(memo, line, originalInvoice);
            StockMovement movement = inventoryService.receive(line.getItemId(),
                    line.getWarehouseId(), baseQty, unitCost, memo.getCmDate(),
                    SOURCE_MODULE, memo.getId(), memo.getCmNumber());
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
                        memo.getCustomerId(), line.getWarehouseId(),
                        line.getItemId(), null));
                glLines.add(new JournalEntryRequest.Line(cogsAccount, null, cost,
                        memo.getCustomerId(), line.getWarehouseId(),
                        line.getItemId(), null, line.getClassId()));
            }
        }

        postingService.createAndPost(new JournalEntryRequest(
                memo.getCmDate(),
                "Кредит-нота " + memo.getCmNumber() + " - "
                        + contactService.get(memo.getCustomerId()).getDisplayName(),
                SOURCE_MODULE, memo.getId(), glLines));
    }

    /**
     * Қайтим бирлик таннархи (posting-rules «Inventory қайтим таннархи»):
     * ҳаволали ҳужжатда - асл сотув OUT ҳаракатининг бирлик таннархи
     * (марж бузилмайди); ҳаволасиз - жорий сиёсат таннархи (AVCO
     * ўртачаси; FIFO'да янги қатлам шу нархда киради).
     */
    private BigDecimal returnUnitCost(CreditMemo memo, CreditMemoLine line,
                                      Invoice originalInvoice) {
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

    /**
     * Realized курс фарқи - алоҳида кичик JE (payment allocation нақши,
     * posting-rules «Қайтариш» Application банди). Фарқ base =
     * қўллаш × (кредит курси − invoice курси); мусбат - фойда (AR Dt /
     * gain Cr), нол - JE ёзилмайди.
     *
     * <p>JE санаси = ҚЎЛЛАШ куни (компания timezone'идаги бугун), кредит
     * санаси ЭМАС (Arbitr-050 / Беруний-031): realized FX қўллаш пайтида
     * тан олинади (BillPayment payment_date прецеденти) - эски даврдаги
     * кредитни янги очиқ даврдаги invoice'га қўллаш BR-LED-020 ёпиқ давр
     * блокига урилмайди ва фарқ тўғри даврга тушади.
     */
    private void postFxDifference(CreditMemo memo, Invoice invoice,
                                  CreditApplication application) {
        BigDecimal diff = Fx.realizedFxDifference(application.getAmount(),
                memo.getExchangeRate(), invoice.getExchangeRate());
        if (diff.signum() == 0) {
            return;
        }
        String home = settingsService.homeCurrency();
        Money value = Money.ofBase(diff.abs(), home);
        UUID ar = accountService.requireSystemAccountId(AccountDetailType.ACCOUNTS_RECEIVABLE);
        UUID fx = accountService.requireSystemAccountId(AccountDetailType.EXCHANGE_GAIN_OR_LOSS);
        JournalEntryRequest.Line arLine = diff.signum() > 0
                ? new JournalEntryRequest.Line(ar, value, null,
                        memo.getCustomerId(), null, null, null)
                : new JournalEntryRequest.Line(ar, null, value,
                        memo.getCustomerId(), null, null, null);
        JournalEntryRequest.Line fxLine = diff.signum() > 0
                ? JournalEntryRequest.Line.credit(fx, value, null)
                : JournalEntryRequest.Line.debit(fx, value, null);
        postingService.createAndPost(new JournalEntryRequest(
                LocalDate.now(settingsService.zoneId()),
                "Курс фарқи: " + memo.getCmNumber() + " → " + invoice.getInvoiceNumber(),
                APPLICATION_SOURCE_MODULE, application.getId(), List.of(arLine, fxLine)));
    }
}
