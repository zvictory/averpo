package com.averpo.erp.purchase.service;

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
import com.averpo.erp.purchase.domain.Bill;
import com.averpo.erp.purchase.domain.BillLine;
import com.averpo.erp.purchase.domain.BillLineType;
import com.averpo.erp.purchase.domain.BillStatus;
import com.averpo.erp.purchase.domain.VendorCredit;
import com.averpo.erp.purchase.domain.VendorCreditApplication;
import com.averpo.erp.purchase.domain.VendorCreditLine;
import com.averpo.erp.purchase.repo.BillRepository;
import com.averpo.erp.purchase.repo.VendorCreditApplicationRepository;
import com.averpo.erp.purchase.repo.VendorCreditRepository;
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
 * Таъминотчи кредит-нотасининг ягона public API'си (returns.md).
 * CreditMemo'нинг AP кўзгуси: {@link #create} дарҳол POSTED (GL +
 * ITEM сатрларга омбор ЧИҚИМИ жорий сиёсат таннархида); {@link #apply}
 * - GL'сиз subledger қўллаш (фақат FX фарқи алоҳида JE);
 * {@link #reverse} - фақат қўлланмаган кредитда (BR-RET-007).
 *
 * <p>GL фақат PostingService (қоида №2), омбор фақат InventoryService
 * public API'си (қоида №6). Проводкалар posting-rules «Қайтариш»
 * VendorCredit жадвалига қатъий мос (қоида №8).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class VendorCreditService {

    /** GL/омбор ҳаволаларидаги манба модул белгиси (posting-rules). */
    public static final String SOURCE_MODULE = "VENDOR_CREDIT";

    /** FX фарқи JE'ларининг манба белгиси - docId = application id. */
    public static final String APPLICATION_SOURCE_MODULE = "VENDOR_CREDIT_APPLICATION";

    /** Рўйхат саҳифаси ҳажми (PERF-perf1 қолипи - рўйхат саҳифаланган туғилади). */
    public static final int LIST_PAGE_SIZE = 25;

    /** Рўйхат тартиби: янгидан эскига, тенг санада яратилиш вақти. */
    private static final Sort LIST_SORT = Sort.by(
            Sort.Order.desc("vcDate"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    /**
     * Кредит-нота формаси маълумотлари. billId - ихтиёрий асл ҳужжат
     * ҳаволаси: сатрлар prefill бўлади, ҚҚС snapshot асл сатрдан,
     * ITEM миқдорлари асл сатрдан ошмайди (BR-RET-006).
     */
    public record VendorCreditData(UUID vendorId, UUID billId, LocalDate vcDate,
                                   String currency, BigDecimal exchangeRate,
                                   boolean amountsInclusive, String memo,
                                   List<LineData> lines) { }

    /**
     * Битта сатр: bill LineData'нинг қайтариш кўзгуси. ITEM'да
     * item/омбор/миқдор/нарх, EXPENSE'да account/amount тўлдирилади;
     * taxRateValue - ҳаволали prefill'дан келган snapshot.
     */
    public record LineData(BillLineType type, UUID itemId, UUID warehouseId,
                           BigDecimal quantity, BigDecimal unitPrice,
                           UUID accountId, BigDecimal amount, String memo,
                           UUID unitId, UUID taxRateId, BigDecimal taxRateValue,
                           UUID classId) { }

    /** Кредит-ноталар репозиторийси. */
    private final VendorCreditRepository repository;

    /** Қўллашлар репозиторийси. */
    private final VendorCreditApplicationRepository applicationRepository;

    /** Bill денормализацияси (balance) учун - ўз модулимиз ичида. */
    private final BillRepository billRepository;

    /** Ҳужжат рақамлари (VC-2026-NNNNN). */
    private final DocumentSequenceService sequenceService;

    /** Vendor текшируви - contact модулининг public API'си. */
    private final ContactService contactService;

    /** Item тури/счётлари - item модулининг public API'си. */
    private final ItemService itemService;

    /** UoM конверсияси (factorBetween) - item модулининг public API'си. */
    private final com.averpo.erp.item.service.UnitService unitService;

    /** Омбор текшируви - inventory модулининг public API'си. */
    private final WarehouseService warehouseService;

    /** Омбордан қайтим чиқими (жорий сиёсат таннархи). */
    private final InventoryService inventoryService;

    /** Тизим счётлари (AP, ҚҚС, фарқ) ва харажат счёти валидацияси. */
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
    public VendorCredit get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Кредит-нота топилмади: " + id));
    }

    /** Кўриш учун - сатрлари билан. */
    @Transactional(readOnly = true)
    public VendorCredit getWithLines(UUID id) {
        return repository.findWithLinesById(id)
                .orElseThrow(() -> new NotFoundException("Кредит-нота топилмади: " + id));
    }

    /**
     * Рўйхат филтри (DEC-068, list-filters.md): барча майдонлар
     * ихтиёрий (null - чекланмаган); q - рақам/изоҳ contains
     * (катта-кичик фарқсиз, кирилл ҳам).
     */
    public record ListFilter(java.time.LocalDate from, java.time.LocalDate to,
                             VendorCredit.Status status, UUID vendorId, String q) {
    }

    /**
     * Рўйхат экрани - саҳифаланган (янгидан эскига), тўлиқ филтр
     * (DEC-068): давр/статус/vendor/матн битта Specification'да
     * (audit услуби, ListSpecs бўлаклари).
     */
    @Transactional(readOnly = true)
    public Page<VendorCredit> list(ListFilter filter, int page, int size) {
        return repository.findAll(org.springframework.data.jpa.domain.Specification.allOf(
                        com.averpo.erp.shared.repo.ListSpecs.dateFrom("vcDate", filter.from()),
                        com.averpo.erp.shared.repo.ListSpecs.dateTo("vcDate", filter.to()),
                        com.averpo.erp.shared.repo.ListSpecs.eq("status", filter.status()),
                        com.averpo.erp.shared.repo.ListSpecs.eq("vendorId", filter.vendorId()),
                        com.averpo.erp.shared.repo.ListSpecs.textContains(filter.q(),
                                "vcNumber", "memo")),
                PageRequest.of(Math.max(0, page), size, LIST_SORT));
    }

    /** Default ҳажм ({@link #LIST_PAGE_SIZE}) билан - эски чақирувчилар/тестлар (DEC-105). */
    @Transactional(readOnly = true)
    public Page<VendorCredit> list(ListFilter filter, int page) {
        return list(filter, page, LIST_PAGE_SIZE);
    }

    /** Кредитнинг қўллашлари - кўриш экрани ва тестлар учун. */
    @Transactional(readOnly = true)
    public List<VendorCreditApplication> applicationsOf(UUID vendorCreditId) {
        return applicationRepository.findByVendorCreditIdOrderByCreatedAtAsc(vendorCreditId);
    }

    /** Bill'га қўлланган кредитлар - bill кўриш экрани учун. */
    @Transactional(readOnly = true)
    public List<VendorCreditApplication> applicationsForBill(UUID billId) {
        return applicationRepository.findByBillIdOrderByCreatedAtAsc(billId);
    }

    /** Bill'дан яратилган кредитлар - bill кўриш экрани учун. */
    @Transactional(readOnly = true)
    public List<VendorCredit> byBill(UUID billId) {
        return repository.findByBillIdOrderByCreatedAtAsc(billId);
    }

    /**
     * Яратиш - дарҳол POSTED (bank txn нақши). GL posting-rules
     * VendorCredit жадвалида: Dr AP (gross) / Cr харажат (net, EXPENSE
     * сатрлар) + Cr ҚҚС (ставка кесимида, input қайтиши) + ITEM
     * сатрларда StockMovement OUT (жорий сиёсат таннархи) билан
     * Cr INVENTORY + фарқ OTHER_COSTS_OF_SERVICE_COS.
     *
     * @throws BusinessRuleException BR-RET-001/002/006, BR-TAX-003/004,
     *         BR-INV-003 (омборда етарли қолдиқ йўқ)
     */
    public VendorCredit create(VendorCreditData data) {
        Normalized normalized = validate(data);
        VendorCredit credit = new VendorCredit(
                sequenceService.next(DocumentType.VENDOR_CREDIT, data.vcDate()),
                data.vendorId(), data.billId(), data.vcDate(),
                normalized.currency(), normalized.rate(), data.amountsInclusive(),
                Strings.blankToNull(data.memo()));
        for (NormalizedLine line : normalized.lines()) {
            credit.addLine(line.type(), line.itemId(), line.warehouseId(),
                    line.quantity(), line.unitPrice(), line.unitId(), line.unitFactor(),
                    line.accountId(), line.amount(),
                    line.taxRateId(), line.taxRateValue(), line.taxAmount(), line.memo())
                    .applyClass(line.classId());
        }
        repository.saveAndFlush(credit);
        postGl(credit);
        credit.markPosted(Instant.now());
        return credit;
    }

    /**
     * Кредитни bill'га қўллаш - GL'СИЗ subledger ҳаракати (иккала
     * ҳужжат ўз JE'сини ёзган, AP тўғри); фақат realized FX фарқи
     * алоҳида JE (APPLICATION_SOURCE_MODULE, BillPayment allocation
     * нақши). Bill balance'и тўлов каби камаяди (paid денормализацияси).
     *
     * @throws BusinessRuleException BR-RET-001/003/004/005
     */
    public VendorCreditApplication apply(UUID vendorCreditId, UUID billId, BigDecimal amount) {
        VendorCredit credit = get(vendorCreditId);
        if (credit.getStatus() != VendorCredit.Status.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_RET_003,
                    "Фақат POSTED кредит қўлланади: " + credit.getVcNumber()
                    + " ҳозир " + credit.getStatus());
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException(BusinessRule.BR_RET_001,
                    "Қўллаш суммаси мусбат бўлиши шарт");
        }
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new NotFoundException("Bill топилмади: " + billId));
        if (bill.getStatus() != BillStatus.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_RET_003,
                    "Кредит фақат POSTED bill'га қўлланади: "
                    + bill.getBillNumber() + " ҳозир " + bill.getStatus());
        }
        if (!bill.getVendorId().equals(credit.getVendorId())) {
            throw new BusinessRuleException(BusinessRule.BR_RET_005,
                    "Bill бошқа таъминотчиники: " + bill.getBillNumber());
        }
        if (!bill.getCurrency().getCode().equals(credit.getCurrency().getCode())) {
            throw new BusinessRuleException(BusinessRule.BR_RET_004,
                    "Кредит валютаси (" + credit.getCurrency().getCode()
                    + ") bill валютаси (" + bill.getCurrency().getCode()
                    + ") билан бир хил бўлиши шарт: " + bill.getBillNumber());
        }
        if (amount.compareTo(credit.getOpenBalance()) > 0) {
            throw new BusinessRuleException(BusinessRule.BR_RET_003,
                    "Қўллаш (" + amount + ") кредитнинг очиқ қолдиғидан ("
                    + credit.getOpenBalance() + ") ошмайди: " + credit.getVcNumber());
        }
        if (amount.compareTo(bill.getBalanceDue()) > 0) {
            throw new BusinessRuleException(BusinessRule.BR_RET_003,
                    "Қўллаш (" + amount + ") bill қолдиғидан ("
                    + bill.getBalanceDue() + ") ошмайди: " + bill.getBillNumber());
        }
        if (applicationRepository.existsByVendorCreditIdAndBillId(credit.getId(), bill.getId())) {
            throw new BusinessRuleException(BusinessRule.BR_RET_003,
                    "Бу кредитдан бу bill'га қўллаш аллақачон бор: "
                    + bill.getBillNumber());
        }
        VendorCreditApplication application = applicationRepository.saveAndFlush(
                new VendorCreditApplication(credit, bill, amount));
        bill.applyPaidAmount(bill.getPaidAmount().add(amount));
        credit.applyAppliedAmount(credit.getAppliedAmount().add(amount));
        postFxDifference(credit, bill, application);
        return application;
    }

    /**
     * Қўллашни бекор қилиш (unapply): FX JE бўлса сторно, bill ва
     * кредит денормализациялари тикланади, ёзув ЎЧИРИЛАДИ (кредитнинг
     * ўзи очиқ қолдиғи билан туради - BR-RET-007 йўли шу).
     */
    public void unapply(UUID applicationId, LocalDate reversalDate) {
        VendorCreditApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException(
                        "Қўллаш топилмади: " + applicationId));
        VendorCredit credit = application.getVendorCredit();
        Bill bill = application.getBill();
        // Нол фарқда JE ёзилмаган - детерминистик қайта ҳисоб (payment нақши)
        if (Fx.realizedFxDifference(application.getAmount(), bill.getExchangeRate(),
                credit.getExchangeRate()).signum() != 0) {
            postingService.reverseBySource(APPLICATION_SOURCE_MODULE,
                    application.getId(), reversalDate, "Кредит қўллаши бекор қилинди");
        }
        bill.applyPaidAmount(bill.getPaidAmount().subtract(application.getAmount()));
        credit.applyAppliedAmount(credit.getAppliedAmount().subtract(application.getAmount()));
        applicationRepository.delete(application);
    }

    /**
     * Reverse: фақат қўлланмаган кредитда (BR-RET-007 - аввал unapply),
     * омбор чиқимлари тескари қайтарилади (reverseIssue - FIFO'да партия
     * нархи ўзгармаган бўлиши гарови BR-INV-009 inventory'дан), кейин
     * GL сторно.
     *
     * @throws BusinessRuleException BR-RET-007
     */
    public VendorCredit reverse(UUID id, LocalDate reversalDate, String reason) {
        VendorCredit credit = get(id);
        if (credit.getStatus() != VendorCredit.Status.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_RET_007,
                    "Фақат POSTED кредит reverse қилинади: " + credit.getVcNumber()
                    + " ҳозир " + credit.getStatus());
        }
        if (credit.getAppliedAmount().signum() > 0) {
            throw new BusinessRuleException(BusinessRule.BR_RET_007,
                    "Қўлланган кредит reverse қилинмайди - аввал қўллашлар бекор "
                    + "қилинади: " + credit.getVcNumber());
        }
        for (StockMovement movement : inventoryService.byReference(SOURCE_MODULE, credit.getId())) {
            if (!movement.getType().inbound()) {
                inventoryService.reverseIssue(movement.getId(), reversalDate);
            }
        }
        postingService.reverseBySource(SOURCE_MODULE, credit.getId(), reversalDate,
                reason == null || reason.isBlank() ? "Кредит-нота reverse" : reason);
        credit.markReversed();
        return credit;
    }

    // ---- ички ёрдамчилар ----

    /** Валидациядан ўтган нормаллашган қийматлар. */
    private record Normalized(Currency currency, BigDecimal rate,
                              List<NormalizedLine> lines) { }

    /** Нормаллашган сатр - bill LineData'нинг қайтариш кўзгуси. */
    private record NormalizedLine(BillLineType type, UUID itemId, UUID warehouseId,
                                  BigDecimal quantity, BigDecimal unitPrice,
                                  UUID accountId, BigDecimal amount, String memo,
                                  UUID unitId, BigDecimal unitFactor,
                                  UUID taxRateId, BigDecimal taxRateValue,
                                  BigDecimal taxAmount, UUID classId) { }

    /** Сарлавҳа + сатрлар валидацияси (BR-RET-001/002/006/008 - валюта vendor'дан, DEC-087). */
    private Normalized validate(VendorCreditData data) {
        if (data.vendorId() == null) {
            throw new BusinessRuleException(BusinessRule.BR_RET_001,
                    "Vendor танланиши шарт");
        }
        Contact vendor = contactService.get(data.vendorId());
        if (vendor.getType() != ContactType.VENDOR || !vendor.isActive()) {
            throw new BusinessRuleException(BusinessRule.BR_RET_001,
                    "Vendor фаол VENDOR типдаги контакт бўлиши шарт: "
                    + vendor.getDisplayName());
        }
        if (data.vcDate() == null) {
            throw new BusinessRuleException(BusinessRule.BR_RET_001,
                    "Ҳужжат санаси киритилиши шарт");
        }
        if (data.lines() == null || data.lines().isEmpty()) {
            throw new BusinessRuleException(BusinessRule.BR_RET_001,
                    "Камида битта сатр киритилиши шарт");
        }
        // Валюта ҳақиқат манбаи - таъминотчи контакти (QBO қатъий, DEC-087):
        // client қиймати фақат мосликка текширилади, ҳужжатга контактники ёзилади
        Currency currency = currencyService.require(contactService
                .requireDocumentCurrency(vendor, data.currency(), BusinessRule.BR_RET_008));
        BigDecimal rate = currencyService.requireDocumentRate(
                currency, data.exchangeRate(), BusinessRule.BR_RET_001);

        Bill original = null;
        if (data.billId() != null) {
            original = billRepository.findWithLinesById(data.billId())
                    .orElseThrow(() -> new NotFoundException(
                            "Bill топилмади: " + data.billId()));
            if (!original.getVendorId().equals(data.vendorId())) {
                throw new BusinessRuleException(BusinessRule.BR_RET_005,
                        "Асл bill бошқа таъминотчиники: " + original.getBillNumber());
            }
            // BR-RET-006: DRAFT/REVERSED «асл ҳужжат» бўла олмайди - GL'да
            // акс этмаган (ёки бекор бўлган) харидга қайтим боғланмайди
            // (apply POSTED текшируви билан симметрия, CM/RR кўзгуси)
            if (original.getStatus() != BillStatus.POSTED) {
                throw new BusinessRuleException(BusinessRule.BR_RET_006,
                        "Асл bill POSTED бўлиши шарт: " + original.getBillNumber()
                        + " ҳозир " + original.getStatus());
            }
        }

        // Батч lookup (DEC-045 findAllById, OPT-003 - SalesReceipt
        // эталони): сатр-циклда item/омбор/счёт биттадан ўқилмасин,
        // id'лар олдиндан йиғилиб учта IN сўров билан Map'га олинади
        Map<UUID, Item> items = BatchLookup.byId(
                itemService.findAllById(BatchLookup.ids(data.lines(), LineData::itemId)));
        Map<UUID, Warehouse> warehouses = BatchLookup.byId(
                warehouseService.findAllById(BatchLookup.ids(data.lines(), LineData::warehouseId)));
        Map<UUID, Account> accounts = BatchLookup.byId(
                accountService.findAllById(BatchLookup.ids(data.lines(), LineData::accountId)));
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
        return new Normalized(currency, rate, lines);
    }

    /**
     * Сатр валидацияси тури бўйича (bill validateLine'нинг BR-RET
     * кўзгуси): ITEM - INVENTORY item + омбор (BR-RET-002) + миқдор/нарх;
     * EXPENSE - фаол postable EXPENSE счёти + мусбат сумма.
     * LANDED_COST қайтарилмайди (bill'нинг ўз механизми).
     * item/омбор/счёт олдиндан юкланган батч Map'лардан ўқилади
     * (OPT-003) - топилмаса {@link NotFoundException} (get() хулқи айнан).
     */
    private NormalizedLine validateLine(int no, LineData line, boolean inclusive,
                                        Bill original, Map<UUID, Item> items,
                                        Map<UUID, Account> accounts,
                                        Map<UUID, Warehouse> warehouses) {
        if (line.type() == null || line.type() == BillLineType.LANDED_COST) {
            throw new BusinessRuleException(BusinessRule.BR_RET_001,
                    no + "-сатр: тури ITEM ёки EXPENSE бўлиши шарт");
        }
        // ҚҚС snapshot (tax.md): берилган қиймат устун; ҳаволали ҳужжатда
        // асл сатр ставкаси (орада каталог ўзгарган бўлса ҳам тўғри қайтим)
        BigDecimal snapshot = line.taxRateValue();
        if (snapshot == null && original != null) {
            snapshot = originalTaxSnapshot(line, original);
        }
        BigDecimal taxValue = taxRateService.documentRateValue(line.taxRateId(), snapshot);

        if (line.type() == BillLineType.ITEM) {
            if (line.itemId() == null) {
                throw new BusinessRuleException(BusinessRule.BR_RET_001,
                        no + "-сатр: item танланиши шарт");
            }
            Item item = items.get(line.itemId());
            if (item == null) {
                throw new NotFoundException("Item топилмади: " + line.itemId());
            }
            if (item.getType() != ItemType.INVENTORY || !item.isActive()) {
                throw new BusinessRuleException(BusinessRule.BR_RET_001,
                        no + "-сатр: фаол INVENTORY типдаги item бўлиши шарт: «"
                        + item.getName() + "»");
            }
            if (line.warehouseId() == null) {
                throw new BusinessRuleException(BusinessRule.BR_RET_002,
                        no + "-сатр: inventory сатрида омбор танланиши шарт");
            }
            if (warehouses.get(line.warehouseId()) == null) { // мавжудлик (NotFound)
                throw new NotFoundException("Омбор топилмади: " + line.warehouseId());
            }
            if (line.quantity() == null || line.quantity().signum() <= 0
                    || line.unitPrice() == null || line.unitPrice().signum() < 0) {
                throw new BusinessRuleException(BusinessRule.BR_RET_001,
                        no + "-сатр: миқдор мусбат, нарх манфий эмас бўлиши шарт");
            }
            if (original != null && original.getLines().stream()
                    .noneMatch(l -> l.getType() == BillLineType.ITEM
                            && line.itemId().equals(l.getItemId()))) {
                throw new BusinessRuleException(BusinessRule.BR_RET_006,
                        "Item асл ҳужжатда йўқ: «" + item.getName() + "»");
            }
            BigDecimal raw = line.quantity().multiply(line.unitPrice())
                    .setScale(4, RoundingMode.HALF_UP);
            TaxAmounts ta = TaxAmounts.of(raw, taxValue, inclusive);
            requirePositiveNet(no, ta.net());
            return new NormalizedLine(line.type(), line.itemId(), line.warehouseId(),
                    line.quantity(), line.unitPrice(), null, ta.net(),
                    Strings.blankToNull(line.memo()), line.unitId(),
                    unitService.lineFactor(no, item, line.unitId(), line.quantity(), true,
                            BusinessRule.BR_RET_001),
                    line.taxRateId(), taxValue, ta.tax(), line.classId());
        }
        // EXPENSE: қайтадиган харажат счёти (bill BR-BILL-005 кўзгуси)
        if (line.accountId() == null) {
            throw new BusinessRuleException(BusinessRule.BR_RET_001,
                    no + "-сатр: харажат счёти танланиши шарт");
        }
        Account account = accounts.get(line.accountId());
        if (account == null) {
            throw new NotFoundException("Счёт топилмади: " + line.accountId());
        }
        if (!account.isActive() || !account.isPostable()
                || account.getClassification() != AccountClassification.EXPENSE) {
            throw new BusinessRuleException(BusinessRule.BR_RET_001,
                    no + "-сатр: счёт EXPENSE/COGS туркумидан, фаол ва postable "
                    + "бўлиши шарт: " + account.getName());
        }
        if (line.amount() == null || line.amount().signum() <= 0) {
            throw new BusinessRuleException(BusinessRule.BR_RET_001,
                    no + "-сатр: сумма мусбат бўлиши шарт");
        }
        TaxAmounts ta = TaxAmounts.of(line.amount(), taxValue, inclusive);
        requirePositiveNet(no, ta.net());
        return new NormalizedLine(line.type(), null, null, null, null,
                line.accountId(), ta.net(), Strings.blankToNull(line.memo()),
                null, null, line.taxRateId(), taxValue, ta.tax(), line.classId());
    }

    /**
     * Асл bill сатридан ҚҚС snapshot: ITEM'да item, EXPENSE'да счёт
     * мослигида ва ставка id бир хил бўлса асл қиймат олинади.
     */
    private BigDecimal originalTaxSnapshot(LineData line, Bill original) {
        for (BillLine ol : original.getLines()) {
            boolean sameSubject = line.type() == BillLineType.ITEM
                    ? ol.getType() == BillLineType.ITEM
                            && java.util.Objects.equals(ol.getItemId(), line.itemId())
                    : ol.getType() == BillLineType.EXPENSE
                            && java.util.Objects.equals(ol.getAccountId(), line.accountId());
            if (sameSubject && java.util.Objects.equals(ol.getTaxRateId(), line.taxRateId())) {
                return ol.getTaxRateValue();
            }
        }
        return null;
    }

    /** BR-RET-001: нетто сумма мусбат (ҚҚС бўлингандан кейин). */
    private void requirePositiveNet(int no, BigDecimal net) {
        if (net == null || net.signum() <= 0) {
            throw new BusinessRuleException(BusinessRule.BR_RET_001,
                    no + "-сатр: нетто сумма мусбат бўлиши шарт");
        }
    }


    /**
     * BR-RET-006 (кумулятив): item бўйича жорий ҳужжат + шу bill'га
     * аввалги POSTED VC'лар йиғиндиси асл ITEM сатр(лар) base миқдоридан
     * ошмайди (қисман қайтариш мумкин). EXPENSE сатрларга миқдор
     * тушунчаси йўқ - чекланмайди. Акс ҳолда 10 доналик харидга иккита
     * 10 доналик VC киритилиб омбордан 20 дона чиқарилар эди (AP кўзгуси).
     */
    private void requireWithinOriginalQuantities(List<NormalizedLine> lines, Bill original) {
        Map<UUID, BigDecimal> returnQty = new HashMap<>();
        for (NormalizedLine line : lines) {
            if (line.type() != BillLineType.ITEM) {
                continue;
            }
            BigDecimal factor = line.unitFactor() == null ? BigDecimal.ONE : line.unitFactor();
            returnQty.merge(line.itemId(),
                    line.quantity().multiply(factor).setScale(4, RoundingMode.HALF_UP),
                    BigDecimal::add);
        }
        Map<UUID, BigDecimal> prior = priorReturnedQuantities(original.getId());
        for (Map.Entry<UUID, BigDecimal> entry : returnQty.entrySet()) {
            BigDecimal originalQty = original.getLines().stream()
                    .filter(l -> l.getType() == BillLineType.ITEM
                            && entry.getKey().equals(l.getItemId()))
                    .map(l -> l.getQuantity().multiply(l.unitFactorOrOne())
                            .setScale(4, RoundingMode.HALF_UP))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal previous = prior.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            BigDecimal cumulative = entry.getValue().add(previous);
            if (cumulative.compareTo(originalQty) > 0) {
                throw new BusinessRuleException(BusinessRule.BR_RET_006,
                        "Қайтариш миқдори аввалги қайтимлар (" + previous
                        + ") билан жами (" + cumulative + ") асл ҳужжатдаги ("
                        + originalQty + ") дан ошмайди: " + original.getBillNumber());
            }
        }
    }

    /**
     * Шу bill'га аллақачон POSTED VC'ларнинг item кесимидаги base
     * миқдорлари - BR-RET-006 кумулятив ҳовузи (bill томонида фақат VC
     * бор). REVERSED кирмайди - сторно қайтимни бекор қилган; EXPENSE
     * сатрлар миқдорсиз - ҳисобга кирмайди. Яхлитлаш жорий сатрлардагидек
     * (ҳар сатрда setScale 4).
     */
    private Map<UUID, BigDecimal> priorReturnedQuantities(UUID billId) {
        Map<UUID, BigDecimal> prior = new HashMap<>();
        for (VendorCredit vc : repository.findWithLinesByBillIdAndStatus(
                billId, VendorCredit.Status.POSTED)) {
            for (VendorCreditLine line : vc.getLines()) {
                if (line.getType() != BillLineType.ITEM) {
                    continue;
                }
                prior.merge(line.getItemId(),
                        line.getQuantity().multiply(line.unitFactorOrOne())
                                .setScale(4, RoundingMode.HALF_UP),
                        BigDecimal::add);
            }
        }
        return prior;
    }

    /** Битта кредит леги: счёт + home base + dimension'лар (ITEM легларга). */
    private record HomeLeg(UUID account, BigDecimal base, boolean credit,
                           UUID warehouseId, UUID itemId, String memo, UUID classId) { }

    /**
     * GL (posting-rules «Қайтариш» VendorCredit жадвали) + омбор чиқими.
     *
     * <p>Penny rounding (PERF-001/LOG-002 қолипи): AP дебети
     * (назорат) base'и gross'нинг БИТТА яхлитлаши (targetBase); кредит
     * леглар base'лари largest-remainder билан айнан шу target'га
     * тақсимланади. ITEM сатрнинг тақсимланган net base'и иккига
     * бўлинади: Cr INVENTORY (StockMovement OUT total_cost, home) +
     * фарқ OTHER_COSTS_OF_SERVICE_COS (мусбатда Cr, манфийда Dt, нол
     * ёзилмайди) - иккиси home валютада, йиғиндиси айнан net base
     * бўлгани учун дебет == кредит сақланади (BR-LED-006).
     */
    private void postGl(VendorCredit credit) {
        String home = settingsService.homeCurrency();
        String docCurrency = credit.getCurrency().getCode();
        boolean isHome = docCurrency.equals(home);
        BigDecimal rate = credit.getExchangeRate();

        // 1) Кредит леглар рўйхати ҳужжат валютасида: сатр net'лари
        //    (тартиб сақланади) + ставка кесимида жамланган ҚҚС
        record CreditLeg(VendorCreditLine line, UUID taxAccount, BigDecimal amount) { }
        List<CreditLeg> legs = new ArrayList<>();
        Map<BigDecimal, BigDecimal> taxByRate = new LinkedHashMap<>();
        for (VendorCreditLine line : credit.getLines()) {
            legs.add(new CreditLeg(line, null, line.getAmount()));
            if (line.getTaxAmount().signum() > 0) {
                taxByRate.merge(line.getTaxRateValue(), line.getTaxAmount(), BigDecimal::add);
            }
        }
        UUID taxAccount = accountService.requireSystemAccountId(AccountDetailType.SALES_TAX_PAYABLE);
        for (BigDecimal taxSum : taxByRate.values()) {
            legs.add(new CreditLeg(null, taxAccount, taxSum));
        }

        // 2) Base тақсимоти: AP target = gross'нинг битта яхлитлаши
        List<BigDecimal> legBases;
        if (isHome) {
            legBases = new ArrayList<>(legs.size());
            for (CreditLeg leg : legs) {
                legBases.add(leg.amount());
            }
        } else {
            List<BigDecimal> amounts = new ArrayList<>(legs.size());
            for (CreditLeg leg : legs) {
                amounts.add(leg.amount());
            }
            legBases = MoneyAllocation.lineBases(amounts, rate);
        }

        List<JournalEntryRequest.Line> glLines = new ArrayList<>();
        // 3) AP дебети = GROSS (назорат сатри - class'сиз)
        Money debit = isHome
                ? Money.ofBase(credit.getTotal(), home)
                : Money.withBase(credit.getTotal(), docCurrency,
                        MoneyAllocation.targetBase(credit.getTotal(), rate), rate);
        glLines.add(new JournalEntryRequest.Line(
                accountService.requireSystemAccountId(AccountDetailType.ACCOUNTS_PAYABLE),
                debit, null, credit.getVendorId(), null, null, null));

        // 4) Кредит леглар: EXPENSE/ҚҚС - ҳужжат валютасида; ITEM -
        //    омбор чиқими + INVENTORY/фарқ жуфти (home).
        // Батч (OPT-003): asset счёти учун item'лар олдиндан битта IN
        // сўровда - сатр циклида биттадан get() қилинмайди
        Map<UUID, Item> itemsById = BatchLookup.byId(itemService.findAllById(
                BatchLookup.ids(credit.getLines(),
                        l -> l.getType() == BillLineType.ITEM ? l.getItemId() : null)));
        UUID diffAccount = null;
        for (int i = 0; i < legs.size(); i++) {
            CreditLeg leg = legs.get(i);
            if (leg.line() == null) {
                // Ставка кесимида ЖАМЛАНГАН ҚҚС леги - class'сиз
                Money tax = isHome
                        ? Money.ofBase(leg.amount(), home)
                        : Money.withBase(leg.amount(), docCurrency, legBases.get(i), rate);
                glLines.add(new JournalEntryRequest.Line(leg.taxAccount(), null, tax,
                        credit.getVendorId(), null, null, null));
                continue;
            }
            VendorCreditLine line = leg.line();
            if (line.getType() == BillLineType.EXPENSE) {
                // Харажат қайтади - сатр class'и ўз легига кўчади
                Money net = isHome
                        ? Money.ofBase(leg.amount(), home)
                        : Money.withBase(leg.amount(), docCurrency, legBases.get(i), rate);
                glLines.add(new JournalEntryRequest.Line(line.getAccountId(), null, net,
                        credit.getVendorId(), null, null, line.getMemo(),
                        line.getClassId()));
                continue;
            }
            // ITEM: омбордан жорий сиёсат таннархида чиқади (adjustment
            // нақши - AVCO/FIFO бутунлиги ҳужжат нархидан устун)
            BigDecimal baseQty = line.getQuantity().multiply(line.unitFactorOrOne())
                    .setScale(4, RoundingMode.HALF_UP);
            InventoryService.IssueResult issue = inventoryService.issue(
                    line.getItemId(), line.getWarehouseId(), baseQty,
                    credit.getVcDate(), SOURCE_MODULE, credit.getId(),
                    credit.getVcNumber());
            BigDecimal netBase = legBases.get(i);
            // Cr INVENTORY - item'нинг ўз asset счёти, Balance Sheet
            // назорати, class'сиз (CreditMemo INVENTORY леги кўзгуси)
            if (issue.totalCost().signum() > 0) {
                glLines.add(new JournalEntryRequest.Line(
                        itemsById.get(line.getItemId()).getInventoryAssetAccountId(),
                        null, Money.ofBase(issue.totalCost(), home),
                        credit.getVendorId(), line.getWarehouseId(),
                        line.getItemId(), null));
            }
            // Фарқ (net − сиёсат таннархи): мусбатда Cr, манфийда Dt,
            // нол ёзилмайди (posting-rules). P&L томони шу - сатр
            // class'и шу легга кўчади
            BigDecimal diff = netBase.subtract(issue.totalCost());
            if (diff.signum() != 0) {
                if (diffAccount == null) {
                    diffAccount = accountService.requireSystemAccountId(
                            AccountDetailType.OTHER_COSTS_OF_SERVICE_COS);
                }
                Money value = Money.ofBase(diff.abs(), home);
                glLines.add(diff.signum() > 0
                        ? new JournalEntryRequest.Line(diffAccount, null, value,
                                credit.getVendorId(), line.getWarehouseId(),
                                line.getItemId(), null, line.getClassId())
                        : new JournalEntryRequest.Line(diffAccount, value, null,
                                credit.getVendorId(), line.getWarehouseId(),
                                line.getItemId(), null, line.getClassId()));
            }
        }

        postingService.createAndPost(new JournalEntryRequest(
                credit.getVcDate(),
                "Таъминотчи кредити " + credit.getVcNumber() + " - "
                        + contactService.get(credit.getVendorId()).getDisplayName(),
                SOURCE_MODULE, credit.getId(), glLines));
    }

    /**
     * Realized курс фарқи - алоҳида кичик JE (BillPayment allocation
     * нақши айнан: кредит тўлов ролини ўйнайди; posting-rules «Қайтариш»
     * Application банди). Фарқ base = қўллаш × (bill курси − кредит
     * курси); мусбат (қарз base'и кредитдан катта) - фойда: AP Dt /
     * gain Cr; манфий - тескари; нол - JE ёзилмайди.
     *
     * <p>JE санаси = ҚЎЛЛАШ куни (компания timezone'идаги бугун), кредит
     * санаси ЭМАС (DEC-050): CM кўзгуси - realized FX қўллаш пайтида
     * тан олинади (BillPayment payment_date прецеденти), ёпиқ давр блоки
     * бартараф.
     */
    private void postFxDifference(VendorCredit credit, Bill bill,
                                  VendorCreditApplication application) {
        BigDecimal diff = Fx.realizedFxDifference(application.getAmount(),
                bill.getExchangeRate(), credit.getExchangeRate());
        if (diff.signum() == 0) {
            return;
        }
        String home = settingsService.homeCurrency();
        Money value = Money.ofBase(diff.abs(), home);
        UUID ap = accountService.requireSystemAccountId(AccountDetailType.ACCOUNTS_PAYABLE);
        UUID fx = accountService.requireSystemAccountId(AccountDetailType.EXCHANGE_GAIN_OR_LOSS);
        JournalEntryRequest.Line apLine = diff.signum() > 0
                ? new JournalEntryRequest.Line(ap, value, null,
                        credit.getVendorId(), null, null, null)
                : new JournalEntryRequest.Line(ap, null, value,
                        credit.getVendorId(), null, null, null);
        JournalEntryRequest.Line fxLine = diff.signum() > 0
                ? JournalEntryRequest.Line.credit(fx, value, null)
                : JournalEntryRequest.Line.debit(fx, value, null);
        postingService.createAndPost(new JournalEntryRequest(
                LocalDate.now(settingsService.zoneId()),
                "Курс фарқи: " + credit.getVcNumber() + " → " + bill.getBillNumber(),
                APPLICATION_SOURCE_MODULE, application.getId(), List.of(apLine, fxLine)));
    }
}
