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
import com.averpo.erp.purchase.repo.BillRepository;
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
import com.averpo.erp.tax.service.TaxAmounts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bill'нинг ягона public API'си (docs/modules/purchases.md).
 * Ҳаёт цикли: DRAFT (таҳрир/ўчириш мумкин) → POSTED (GL + омбор
 * кирими, ўзгармас) → REVERSED (GL сторно + омбор қайтариши).
 * Бошқа модулларга (repo'ларга эмас) фақат public service'лар орқали
 * мурожаат қилади (ТЕМИР ҚОИДА №6); GL - фақат PostingService (№2).
 *
 * @author Zafar
 */
@Service
@Transactional
@RequiredArgsConstructor
public class BillService {

    /** GL/омбор ҳаволаларидаги манба модул белгиси. */
    public static final String SOURCE_MODULE = "BILL";

    /**
     * Битта bill формаси маълумотлари - create/update учун умумий.
     * amountsInclusive - нархлар ҚҚС ичидами (docs/modules/tax.md).
     */
    public record BillData(UUID vendorId, String vendorInvoiceNumber,
                           LocalDate billDate, LocalDate dueDate,
                           String currency, BigDecimal exchangeRate,
                           String memo, boolean amountsInclusive, List<LineData> lines) {

        /** Эски 8 майдонли имзо - солиқсиз (tax'дан аввалги чақирувлар). */
        public BillData(UUID vendorId, String vendorInvoiceNumber,
                        LocalDate billDate, LocalDate dueDate, String currency,
                        BigDecimal exchangeRate, String memo, List<LineData> lines) {
            this(vendorId, vendorInvoiceNumber, billDate, dueDate, currency,
                    exchangeRate, memo, false, lines);
        }
    }

    /**
     * Битта сатр маълумотлари. amount - НЕТТО (солиқсиз); unitId -
     * ITEM сатрида киритилган бирлик (null - item base); unitFactor ва
     * taxRateValue/taxAmount формадан келмайди - validate snapshot
     * қилиб тўлдиради (docs/modules/tax.md).
     */
    public record LineData(BillLineType type, UUID itemId, UUID warehouseId,
                           BigDecimal quantity, BigDecimal unitPrice,
                           UUID accountId, BigDecimal amount, String memo,
                           UUID unitId, BigDecimal unitFactor,
                           UUID taxRateId, BigDecimal taxRateValue, BigDecimal taxAmount,
                           UUID classId) {

        /** 13 майдонли имзо - class'сиз чақирувлар (tax давригача қолип). */
        public LineData(BillLineType type, UUID itemId, UUID warehouseId,
                        BigDecimal quantity, BigDecimal unitPrice,
                        UUID accountId, BigDecimal amount, String memo,
                        UUID unitId, BigDecimal unitFactor,
                        UUID taxRateId, BigDecimal taxRateValue, BigDecimal taxAmount) {
            this(type, itemId, warehouseId, quantity, unitPrice, accountId, amount,
                    memo, unitId, unitFactor, taxRateId, taxRateValue, taxAmount, null);
        }

        /** Эски 8 майдонли имзо - бирликсиз/солиқсиз. */
        public LineData(BillLineType type, UUID itemId, UUID warehouseId,
                        BigDecimal quantity, BigDecimal unitPrice,
                        UUID accountId, BigDecimal amount, String memo) {
            this(type, itemId, warehouseId, quantity, unitPrice,
                    accountId, amount, memo, null, null, null, null, null, null);
        }

        /** 10 майдонли имзо - UoM бор, солиқсиз (tax'дан аввалги чақирувлар). */
        public LineData(BillLineType type, UUID itemId, UUID warehouseId,
                        BigDecimal quantity, BigDecimal unitPrice, UUID accountId,
                        BigDecimal amount, String memo, UUID unitId, BigDecimal unitFactor) {
            this(type, itemId, warehouseId, quantity, unitPrice, accountId,
                    amount, memo, unitId, unitFactor, null, null, null);
        }
    }

    /** Bill репозиторийси. */
    private final BillRepository repository;

    /** Ҳужжат рақамлари (BILL-2026-NNNNN). */
    private final DocumentSequenceService sequenceService;

    /** Vendor текшируви - contact модулининг public API'си. */
    private final ContactService contactService;

    /** Item текшируви ва asset счёти - item модулининг public API'си. */
    private final ItemService itemService;

    /** UoM конверсияси (factorBetween) - item модулининг public API'си. */
    private final com.averpo.erp.item.service.UnitService unitService;

    /** Омбор текшируви - inventory модулининг public API'си. */
    private final WarehouseService warehouseService;

    /** Омбор кирими/қайтариши. */
    private final InventoryService inventoryService;

    /** Reverse олдидан фаол тақсимот текшируви (BR-BILL-012) - ўз модулимиз service'и. */
    private final LandedCostService landedCostService;

    /** Тизим счётлари (AP, INVENTORY_CLEARING) ва счёт валидацияси. */
    private final AccountService accountService;

    /** GL'га ёзишнинг ягона йўли. */
    private final PostingService postingService;

    /** Валюта каталоги. */
    private final CurrencyService currencyService;

    /** Home currency - курс валидацияси учун. */
    private final CompanySettingsService settingsService;

    /** Due date ҳисоби учун vendor тўлов шарти. */
    private final PaymentTermService paymentTermService;

    /** ҚҚС ставкаси snapshot/фаоллик текшируви - tax модулининг public API'си. */
    private final com.averpo.erp.tax.service.TaxRateService taxRateService;

    /** Id бўйича топади ёки тушунарли хато отади. */
    @Transactional(readOnly = true)
    public Bill get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Bill топилмади: " + id));
    }

    /** Кўриш/post учун - сатрлари билан. */
    @Transactional(readOnly = true)
    public Bill getWithLines(UUID id) {
        return repository.findWithLinesById(id)
                .orElseThrow(() -> new NotFoundException("Bill топилмади: " + id));
    }

    /** Рўйхат саҳифаси ҳажми (Beruniy-perf1 1-босқич). */
    public static final int LIST_PAGE_SIZE = 25;

    /**
     * Рўйхат тартиби - аввалги ORDER BY'га айнан мос (янгидан эскига,
     * тенг санада яратилиш вақти) - саҳифалашга ўтишда экрандаги
     * тартиб ўзгармасин (Beruniy-perf1 3-банд).
     */
    private static final org.springframework.data.domain.Sort LIST_SORT =
            org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Order.desc("billDate"),
                    org.springframework.data.domain.Sort.Order.desc("createdAt"),
                    org.springframework.data.domain.Sort.Order.desc("id"));

    /**
     * Устун саралаш WHITELIST'и (ARBITR-105б): th калити → entity
     * property. Хом параметр Sort'га тушмайди - фақат шу харита
     * орқали ({@link com.averpo.erp.shared.web.TableSort}).
     * Сумма/қолдиқ home қийматлар (totalBase/balanceDue) бўйича -
     * ҳар хил валютали ҳужжатлар фақат шунда солиштирма бўлади.
     * vendor йўқ: ном бошқа модул каталогида (устунда id туради).
     */
    private static final java.util.Map<String, String> SORT_KEYS = java.util.Map.of(
            "number", "billNumber",
            "vendorInvoice", "vendorInvoiceNumber",
            "date", "billDate",
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
     * Рўйхат филтри (Arbitr-068, list-filters.md): барча майдонлар
     * ихтиёрий (null - чекланмаган); q - bill рақами/vendor ҳисобварақ
     * рақами/изоҳ contains (катта-кичик фарқсиз, кирилл ҳам).
     */
    public record ListFilter(java.time.LocalDate from, java.time.LocalDate to,
                             BillStatus status, UUID vendorId, String q) {
    }

    /**
     * Рўйхат экрани - саҳифаланган (Beruniy-perf1), тўлиқ филтр
     * (Arbitr-068): давр/статус/vendor/матн битта Specification'да
     * (audit услуби, ListSpecs бўлаклари) - derived query'ларнинг
     * комбинацион портлашисиз.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Bill> list(ListFilter filter, int page, int size) {
        return list(filter, page, size, LIST_SORT);
    }

    /**
     * Устун саралашли рўйхат (ARBITR-105б): sort {@link #sortOf}
     * орқали ечиб берилади - хом параметр бу ерга етиб келмайди.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Bill> list(ListFilter filter, int page,
            int size, org.springframework.data.domain.Sort sort) {
        var pageable = org.springframework.data.domain.PageRequest.of(
                Math.max(0, page), size, sort);
        return repository.findAll(org.springframework.data.jpa.domain.Specification.allOf(
                com.averpo.erp.shared.repo.ListSpecs.dateFrom("billDate", filter.from()),
                com.averpo.erp.shared.repo.ListSpecs.dateTo("billDate", filter.to()),
                com.averpo.erp.shared.repo.ListSpecs.eq("status", filter.status()),
                com.averpo.erp.shared.repo.ListSpecs.eq("vendorId", filter.vendorId()),
                com.averpo.erp.shared.repo.ListSpecs.textContains(filter.q(),
                        "billNumber", "vendorInvoiceNumber", "memo")), pageable);
    }

    /** Default ҳажм ({@link #LIST_PAGE_SIZE}) билан - эски чақирувчилар/тестлар (ARBITR-105). */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Bill> list(ListFilter filter, int page) {
        return list(filter, page, LIST_PAGE_SIZE);
    }

    /** Тўлов формаси учун: vendor'нинг очиқ (қолдиқли POSTED) bill'лари. */
    @Transactional(readOnly = true)
    public List<Bill> openBills(UUID vendorId) {
        return repository.findByVendorIdAndStatusAndBalanceDueGreaterThanOrderByBillDateAsc(
                vendorId, BillStatus.POSTED, BigDecimal.ZERO);
    }

    /**
     * AP aging қатори: vendor бўйича очиқ қарз home валютада, муддат
     * бўйича корзиналарда (QBO A/P Aging Summary услуби). Корзина
     * due date'дан кечикиш кунига қараб: current (муддати келмаган),
     * 1-30, 31-60, 61-90, 90+. Due date йўқ bill current'га тушади.
     */
    public record AgingRow(UUID vendorId, BigDecimal current, BigDecimal d1to30,
                           BigDecimal d31to60, BigDecimal d61to90,
                           BigDecimal over90, BigDecimal total) { }

    /**
     * AP aging ҳисоботи: очиқ POSTED bill қолдиқлари home валютада
     * (қолдиқ × ҳужжат курси) vendor бўйича йиғилади. Vendor'лар
     * жами қарзи бўйича камайиш тартибида.
     *
     * <p>ФАҚАТ ЖОРИЙ ҲОЛАТ (BR-RPT-001, Komil-004): қолдиқлар жорий
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
    public List<AgingRow> apAging(LocalDate asOf) {
        LocalDate today = LocalDate.now(settingsService.zoneId());
        if (asOf != null && !asOf.isEqual(today)) {
            throw new BusinessRuleException(BusinessRule.BR_RPT_001,
                    "Aging фақат жорий санага: сўралди " + asOf + ", бугун " + today);
        }
        java.util.Map<UUID, BigDecimal[]> byVendor = new java.util.LinkedHashMap<>();
        for (Bill bill : repository.findByStatusAndBalanceDueGreaterThan(
                BillStatus.POSTED, BigDecimal.ZERO)) {
            BigDecimal base = bill.getBalanceDue().multiply(bill.getExchangeRate())
                    .setScale(4, RoundingMode.HALF_UP);
            long overdue = bill.getDueDate() == null ? 0
                    : java.time.temporal.ChronoUnit.DAYS.between(bill.getDueDate(), today);
            int bucket = overdue <= 0 ? 0 : overdue <= 30 ? 1
                    : overdue <= 60 ? 2 : overdue <= 90 ? 3 : 4;
            BigDecimal[] sums = byVendor.computeIfAbsent(bill.getVendorId(), k -> {
                BigDecimal[] zeros = new BigDecimal[5];
                java.util.Arrays.fill(zeros, BigDecimal.ZERO);
                return zeros;
            });
            sums[bucket] = sums[bucket].add(base);
        }
        List<AgingRow> rows = new ArrayList<>();
        for (var entry : byVendor.entrySet()) {
            BigDecimal[] s = entry.getValue();
            rows.add(new AgingRow(entry.getKey(), s[0], s[1], s[2], s[3], s[4],
                    s[0].add(s[1]).add(s[2]).add(s[3]).add(s[4])));
        }
        rows.sort((a, b) -> b.total().compareTo(a.total()));
        return rows;
    }

    /**
     * Янги DRAFT bill яратади - рақам DocumentSequence'дан дарҳол
     * олинади (spec қарори: draft ҳам рақамли).
     *
     * @throws BusinessRuleException BR-BILL-001..006, 009, 011
     */
    public Bill createDraft(BillData data) {
        Normalized normalized = validate(data, null);
        Bill bill = new Bill(
                sequenceService.next(DocumentType.BILL, data.billDate()),
                data.vendorId(), Strings.blankToNull(data.vendorInvoiceNumber()),
                data.billDate(), normalized.dueDate(), normalized.currency(),
                normalized.rate(), data.amountsInclusive(), Strings.blankToNull(data.memo()));
        applyLines(bill, normalized.lines());
        return saveGuarded(bill, data);
    }

    /** DRAFT'ни тўлиқ янгилайди (сарлавҳа + сатрлар қайта терилади). */
    public Bill updateDraft(UUID id, BillData data) {
        Bill bill = getWithLines(id);
        Normalized normalized = validate(data, id);
        bill.updateHeader(data.vendorId(), Strings.blankToNull(data.vendorInvoiceNumber()),
                data.billDate(), normalized.dueDate(), normalized.currency(),
                normalized.rate(), data.amountsInclusive(), Strings.blankToNull(data.memo()));
        bill.clearLines();
        // ux_bill_line_no (Beruniy-010) билан: Hibernate flush'да INSERT
        // DELETE'дан олдин бажарилади - эски сатрлар аввал ўчирилиши шарт,
        // акс ҳолда янги 1-сатр эски (bill_id, line_no=1) билан тўқнашади
        repository.flush();
        applyLines(bill, normalized.lines());
        return saveGuarded(bill, data);
    }

    /** DRAFT'ни ўчиради - POSTED/REVERSED ўчирилмайди (қоида №3). */
    public void deleteDraft(UUID id) {
        Bill bill = getWithLines(id);
        if (bill.getStatus() != BillStatus.DRAFT) {
            throw new BusinessRuleException(BusinessRule.BR_BILL_007,
                    "Фақат DRAFT ўчирилади: " + bill.getBillNumber());
        }
        repository.delete(bill);
    }

    /**
     * Post: GL проводка (posting-rules «Харид») + ҳар ITEM сатр учун
     * омбор кирими (home қийматда). Idempotency (BR-LED-012) ва давр
     * қулфи (BR-LED-020) PostingService'дан автоматик.
     */
    public Bill post(UUID id) {
        Bill bill = getWithLines(id);
        if (bill.getStatus() != BillStatus.DRAFT) {
            throw new BusinessRuleException(BusinessRule.BR_BILL_007,
                    "Фақат DRAFT bill post қилинади: " + bill.getBillNumber()
                    + " ҳозир " + bill.getStatus());
        }
        // Draft яратилгандан кейин vendor/item/счёт ҳолати ўзгарган
        // бўлиши мумкин - post олдидан тўлиқ қайта валидация
        validate(toData(bill), id);

        postingService.createAndPost(new JournalEntryRequest(
                bill.getBillDate(),
                "Bill " + bill.getBillNumber() + " - " + vendorName(bill.getVendorId()),
                SOURCE_MODULE, bill.getId(), buildGlLines(bill)));

        for (BillLine line : bill.getLines()) {
            if (line.getType() == BillLineType.ITEM) {
                // Омборга BASE бирликда (UoM: миқдор × factor snapshot) ва
                // НЕТТО (солиқсиз) home қийматда киради (tax.md): ҳисобга
                // олинадиган ҚҚС таннархга кирмайди. Unit cost = net × курс /
                // base миқдор - inclusive режимда ҳам тўғри (unitPrice эмас,
                // amount=net асос: inclusive'да unitPrice gross-per-unit эди)
                BigDecimal factor = line.unitFactorOrOne();
                BigDecimal baseQty = line.getQuantity().multiply(factor)
                        .setScale(4, RoundingMode.HALF_UP);
                BigDecimal unitCostBase = line.getAmount().multiply(bill.getExchangeRate())
                        .divide(baseQty, 12, RoundingMode.HALF_UP);
                inventoryService.receive(line.getItemId(), line.getWarehouseId(),
                        baseQty, unitCostBase,
                        bill.getBillDate(), SOURCE_MODULE, bill.getId(), line.getMemo());
            }
        }
        bill.markPosted(Instant.now());
        return bill;
    }

    /**
     * Reverse: омбор киримлари айнан ўз нархида қайтарилади
     * (InventoryService.reverseReceive - receipt шу кесимдаги энг
     * охирги ҳаракат ва FIFO партияси тўлиқ турган бўлиши шарт), кейин
     * GL сторно. Товар ишлатилган/кейин ҳаракат бўлса BR-BILL-010
     * (inventory BR-INV-003/010 хатоси ўралади).
     *
     * <p>BR-BILL-012 (Beruniy-005): receipt'ларга ФАОЛ landed cost
     * тақсимоти бўлса reverse тўсилади - Bill сторноси LANDED_COST
     * JE'сини қайтармайди, тақсимот кучда қолса юкланган қиймат ва
     * клиринг кредити GL'да «осилиб» қолар эди. Фойдаланувчи аввал
     * тақсимотни reverse қилади (тарих изчил, ҳар қадам аудитда аниқ).
     */
    public Bill reverse(UUID id, LocalDate reversalDate, String reason) {
        Bill bill = getWithLines(id);
        if (bill.getStatus() != BillStatus.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_BILL_008,
                    "Фақат POSTED bill reverse қилинади: " + bill.getBillNumber()
                    + " ҳозир " + bill.getStatus());
        }
        List<StockMovement> movements =
                inventoryService.byReference(SOURCE_MODULE, bill.getId());
        for (StockMovement movement : movements) {
            if (movement.getType().inbound()
                    && landedCostService.activeAllocationExists(movement.getId())) {
                throw new BusinessRuleException(BusinessRule.BR_BILL_012,
                        "Reverse тақиқ - receipt'га фаол landed cost тақсимоти бор, "
                        + "аввал ўша тақсимотни reverse қилинг: " + bill.getBillNumber());
            }
        }
        try {
            for (StockMovement movement : movements) {
                if (movement.getType().inbound()) {
                    inventoryService.reverseReceive(movement.getId(), reversalDate);
                }
            }
        } catch (BusinessRuleException e) {
            if (e.getRule() == BusinessRule.BR_INV_003
                    || e.getRule() == BusinessRule.BR_INV_010) {
                throw new BusinessRuleException(BusinessRule.BR_BILL_010,
                        "Reverse тақиқ - киритилган товар ишлатилган ёки receipt'дан "
                        + "кейин омбор ҳаракати бор: " + e.getMessage());
            }
            throw e;
        }
        postingService.reverseBySource(SOURCE_MODULE, bill.getId(), reversalDate,
                reason == null || reason.isBlank() ? "Bill reverse" : reason);
        bill.markReversed();
        return bill;
    }

    // ---- ички ёрдамчилар ----

    /** Валидациядан ўтган нормаллашган қийматлар. */
    private record Normalized(Currency currency, BigDecimal rate,
                              LocalDate dueDate, List<LineData> lines) { }

    /**
     * Тўлиқ валидация (BR-BILL-001..005, 009, 011, 013) + нормализация:
     * ITEM сатр суммаси qty × нарх дан ҳисобланади, due date vendor
     * тўлов шартидан келади (берилмаса). Валюта - vendor контактидан
     * (BR-BILL-013, Arbitr-087).
     */
    private Normalized validate(BillData data, UUID selfId) {
        if (data.vendorId() == null) {
            throw new BusinessRuleException(BusinessRule.BR_BILL_001,
                    "Vendor танланиши шарт");
        }
        Contact vendor = contactService.get(data.vendorId());
        if (vendor.getType() != ContactType.VENDOR || !vendor.isActive()) {
            throw new BusinessRuleException(BusinessRule.BR_BILL_001,
                    "Vendor фаол VENDOR типдаги контакт бўлиши шарт: " + vendor.getDisplayName());
        }
        if (data.billDate() == null) {
            throw new BusinessRuleException(BusinessRule.BR_BILL_011,
                    "Bill санаси киритилиши шарт");
        }
        // Валюта ҳақиқат манбаи - таъминотчи контакти (QBO қатъий, Arbitr-087):
        // client қиймати фақат мосликка текширилади, ҳужжатга контактники ёзилади
        Currency currency = currencyService.require(contactService
                .requireDocumentCurrency(vendor, data.currency(), BusinessRule.BR_BILL_013));
        BigDecimal rate = currencyService.requireDocumentRate(
                currency, data.exchangeRate(), BusinessRule.BR_BILL_009);

        if (data.lines() == null || data.lines().isEmpty()) {
            throw new BusinessRuleException(BusinessRule.BR_BILL_002,
                    "Bill'да камида битта сатр бўлиши шарт");
        }
        // Батч lookup (Arbitr-045 findAllById, Sanjar-003 - SalesReceipt
        // эталони): сатр-циклда item/омбор/счёт биттадан ўқилмасин,
        // id'лар олдиндан йиғилиб учта IN сўров билан Map'га олинади
        Map<UUID, Item> items = BatchLookup.byId(
                itemService.findAllById(BatchLookup.ids(data.lines(), LineData::itemId)));
        Map<UUID, Warehouse> warehouses = BatchLookup.byId(
                warehouseService.findAllById(BatchLookup.ids(data.lines(), LineData::warehouseId)));
        Map<UUID, Account> accounts = BatchLookup.byId(
                accountService.findAllById(BatchLookup.ids(data.lines(), LineData::accountId)));
        List<LineData> normalizedLines = new ArrayList<>();
        int no = 0;
        for (LineData line : data.lines()) {
            no++;
            normalizedLines.add(validateLine(no, line, data.amountsInclusive(),
                    items, accounts, warehouses));
        }
        requireVendorInvoiceFree(data.vendorId(),
                Strings.blankToNull(data.vendorInvoiceNumber()), selfId);

        LocalDate dueDate = data.dueDate();
        if (dueDate == null && vendor.getPaymentTermId() != null) {
            dueDate = paymentTermService.byId(vendor.getPaymentTermId())
                    .map(term -> data.billDate().plusDays(term.getDays()))
                    .orElse(null);
        }
        return new Normalized(currency, rate, dueDate, normalizedLines);
    }

    /**
     * Сатр валидацияси тури бўйича; ITEM суммаси қайта ҳисобланади.
     * ҚҚС бўлиниши (docs/modules/tax.md): raw сумма (ITEM'да qty×price,
     * бошқада киритилган) ставка+режим бўйича net/tax'га ажратилади;
     * сақланадиган {@code amount} - НЕТТО, {@code taxAmount} - ҚҚС.
     * item/омбор/счёт олдиндан юкланган батч Map'лардан ўқилади
     * (Sanjar-003) - топилмаса {@link NotFoundException} (get() хулқи айнан).
     */
    private LineData validateLine(int no, LineData line, boolean inclusive,
                                  Map<UUID, Item> items, Map<UUID, Account> accounts,
                                  Map<UUID, Warehouse> warehouses) {
        if (line.type() == null) {
            throw new BusinessRuleException(BusinessRule.BR_BILL_003,
                    no + "-сатр: тури танланиши шарт");
        }
        // Ставка snapshot/фаоллик - барча тур учун бир хил (BR-TAX-003/004)
        BigDecimal taxValue = taxRateService.documentRateValue(
                line.taxRateId(), line.taxRateValue());
        switch (line.type()) {
            case ITEM -> {
                if (line.itemId() == null || line.warehouseId() == null) {
                    throw new BusinessRuleException(BusinessRule.BR_BILL_004,
                            no + "-сатр: item ва омбор танланиши шарт");
                }
                Item item = items.get(line.itemId());
                if (item == null) {
                    throw new NotFoundException("Item топилмади: " + line.itemId());
                }
                if (item.getType() != ItemType.INVENTORY) {
                    throw new BusinessRuleException(BusinessRule.BR_BILL_004,
                            no + "-сатр: фақат INVENTORY типдаги item: «"
                            + item.getName() + "» - " + item.getType());
                }
                if (warehouses.get(line.warehouseId()) == null) {
                    // мавжудлик текширилди (фаоллик receive'да)
                    throw new NotFoundException("Омбор топилмади: " + line.warehouseId());
                }
                if (line.quantity() == null || line.quantity().signum() <= 0
                        || line.unitPrice() == null || line.unitPrice().signum() < 0) {
                    throw new BusinessRuleException(BusinessRule.BR_BILL_003,
                            no + "-сатр: миқдор мусбат, нарх манфий эмас бўлиши шарт");
                }
                BigDecimal raw = line.quantity().multiply(line.unitPrice())
                        .setScale(4, RoundingMode.HALF_UP);
                TaxAmounts ta = TaxAmounts.of(raw, taxValue, inclusive);
                requirePositiveNet(no, ta.net());
                BigDecimal unitFactor = unitService.lineFactor(no, item, line.unitId(),
                        line.quantity(), true, BusinessRule.BR_BILL_003);
                return new LineData(line.type(), line.itemId(), line.warehouseId(),
                        line.quantity(), line.unitPrice(), null, ta.net(),
                        Strings.blankToNull(line.memo()), line.unitId(), unitFactor,
                        line.taxRateId(), taxValue, ta.tax(), line.classId());
            }
            case EXPENSE -> {
                if (line.accountId() == null) {
                    throw new BusinessRuleException(BusinessRule.BR_BILL_005,
                            no + "-сатр: харажат счёти танланиши шарт");
                }
                Account account = accounts.get(line.accountId());
                if (account == null) {
                    throw new NotFoundException("Счёт топилмади: " + line.accountId());
                }
                if (!account.isActive() || !account.isPostable()
                        || account.getClassification() != AccountClassification.EXPENSE) {
                    throw new BusinessRuleException(BusinessRule.BR_BILL_005,
                            no + "-сатр: счёт EXPENSE/COGS туркумидан, фаол ва postable "
                            + "бўлиши шарт: " + account.getName());
                }
                requirePositiveAmount(no, line.amount());
                TaxAmounts ta = TaxAmounts.of(line.amount(), taxValue, inclusive);
                requirePositiveNet(no, ta.net());
                return new LineData(line.type(), null, null, null, null,
                        line.accountId(), ta.net(), Strings.blankToNull(line.memo()),
                        null, null, line.taxRateId(), taxValue, ta.tax(), line.classId());
            }
            default -> {
                requirePositiveAmount(no, line.amount());
                TaxAmounts ta = TaxAmounts.of(line.amount(), taxValue, inclusive);
                requirePositiveNet(no, ta.net());
                return new LineData(line.type(), null, null, null, null,
                        null, ta.net(), Strings.blankToNull(line.memo()),
                        null, null, line.taxRateId(), taxValue, ta.tax(), line.classId());
            }
        }
    }

    /** BR-BILL-003: сумма мусбат. */
    private void requirePositiveAmount(int no, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException(BusinessRule.BR_BILL_003,
                    no + "-сатр: сумма мусбат бўлиши шарт");
        }
    }

    /** BR-BILL-003: нетто сумма мусбат (ҚҚС бўлингандан кейин). */
    private void requirePositiveNet(int no, BigDecimal net) {
        if (net == null || net.signum() <= 0) {
            throw new BusinessRuleException(BusinessRule.BR_BILL_003,
                    no + "-сатр: нетто сумма мусбат бўлиши шарт");
        }
    }


    /** BR-BILL-006: фаол статусларда vendor invoice рақами банд эмас. */
    private void requireVendorInvoiceFree(UUID vendorId, String number, UUID selfId) {
        if (number == null) {
            return;
        }
        repository.findByVendorIdAndVendorInvoiceNumberAndStatusIn(vendorId, number,
                        List.of(BillStatus.DRAFT, BillStatus.POSTED))
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw new BusinessRuleException(BusinessRule.BR_BILL_006,
                            "Бу vendor invoice рақами аллақачон киритилган: " + number
                            + " (" + other.getBillNumber() + ")");
                });
    }

    /** Сатрларни bill'га теради (amount'лар validate'да нормаллашган). */
    private void applyLines(Bill bill, List<LineData> lines) {
        for (LineData line : lines) {
            bill.addLine(line.type(), line.itemId(), line.warehouseId(),
                    line.quantity(), line.unitPrice(),
                    line.unitId(), line.unitFactor(),
                    line.accountId(), line.amount(),
                    line.taxRateId(), line.taxRateValue(), line.taxAmount(), line.memo())
                    .applyClass(line.classId());
        }
    }

    /**
     * saveAndFlush + DB partial unique index'ини аниқ BR кодга ўраш -
     * parallel иккита киритишда service текшируви ўтиб кетиши мумкин,
     * ҳақиқий кафолат ux_bill_vendor_invoice (PostingServiceImpl паттерни).
     */
    private Bill saveGuarded(Bill bill, BillData data) {
        try {
            return repository.saveAndFlush(bill);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            if (isVendorInvoiceViolation(e)) {
                throw new BusinessRuleException(BusinessRule.BR_BILL_006,
                        "Бу vendor invoice рақами аллақачон киритилган: "
                        + data.vendorInvoiceNumber());
            }
            throw e;
        }
    }

    /** DataIntegrityViolation айнан vendor guard index'иданми. */
    private boolean isVendorInvoiceViolation(org.springframework.dao.DataIntegrityViolationException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains("ux_bill_vendor_invoice")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Post олдидан қайта валидация учун entity'дан BillData ясайди.
     * ҚҚС snapshot (taxRateValue) ва режим сақланади - қайта валидация
     * draft'даги ставка қийматини ишлатади (каталог ўзгарса ҳам).
     */
    private BillData toData(Bill bill) {
        List<LineData> lines = new ArrayList<>();
        for (BillLine line : bill.getLines()) {
            lines.add(new LineData(line.getType(), line.getItemId(),
                    line.getWarehouseId(), line.getQuantity(), line.getUnitPrice(),
                    line.getAccountId(), line.getAmount(), line.getMemo(),
                    line.getUnitId(), line.getUnitFactor(),
                    line.getTaxRateId(), line.getTaxRateValue(), line.getTaxAmount(),
                    line.getClassId()));
        }
        return new BillData(bill.getVendorId(), bill.getVendorInvoiceNumber(),
                bill.getBillDate(), bill.getDueDate(), bill.getCurrency().getCode(),
                bill.getExchangeRate(), bill.getMemo(), bill.isAmountsInclusive(), lines);
    }

    /** Битта дебет леги: счёт + сумма (net ёки ставка кесимидаги ҚҚС) + dimension'лар. */
    private record DebitLeg(UUID account, BigDecimal amount,
                            UUID warehouseId, UUID itemId, String memo,
                            UUID classId) { }

    /**
     * GL сатрлари posting-rules «Харид» жадвалига қатъий мос.
     *
     * <p>ҚҚС (docs/modules/tax.md): ҳар сатр НЕТТО дебети + ставка
     * кесимида жамланган ҚҚС дебети (SALES_TAX_PAYABLE, нол жами
     * ёзилмайди) + AP кредити = GROSS. Ҳисобга олинадиган ҚҚС таннархга
     * кирмайди - алоҳида леги.
     *
     * <p>Penny rounding (Beruniy-001 + Asrorxoja-002): чет валютада AP
     * кредити (назорат сатри) base'и gross × rate'нинг БИТТА яхлитлаши
     * ({@link MoneyAllocation#targetBase}), қолган ЛЕГЛАР (net'лар +
     * ҚҚС'лар) base'лари largest-remainder билан айнан шу target'га
     * тақсимланади. Леглар йиғиндиси (Σnet + Σtax) = gross бўлгани учун
     * дебет base йиғиндиси == AP base (BR-LED-006 ✓), ҳар лег четлашиши
     * ≤ 0.0001 (BR-LED-003 ✓). Bill.totalBase ҳам худди шу target.
     */
    private List<JournalEntryRequest.Line> buildGlLines(Bill bill) {
        String home = settingsService.homeCurrency();
        String docCurrency = bill.getCurrency().getCode();
        boolean isHome = docCurrency.equals(home);
        BigDecimal rate = bill.getExchangeRate();

        // Батч (Sanjar-003): ITEM сатрлар asset счёти учун item'лар олдиндан
        // битта IN сўровда - сатр циклида биттадан get() қилинмайди
        Map<UUID, Item> itemsById = BatchLookup.byId(itemService.findAllById(
                BatchLookup.ids(bill.getLines(),
                        l -> l.getType() == BillLineType.ITEM ? l.getItemId() : null)));

        // 1) Net леглар (тартиб сақланади) + ставка кесимида ҚҚС йиғиш
        List<DebitLeg> legs = new ArrayList<>();
        java.util.Map<BigDecimal, BigDecimal> taxByRate = new java.util.LinkedHashMap<>();
        for (BillLine line : bill.getLines()) {
            UUID account = switch (line.getType()) {
                case ITEM -> itemsById.get(line.getItemId()).getInventoryAssetAccountId();
                case EXPENSE -> line.getAccountId();
                case LANDED_COST -> accountService.requireSystemAccountId(AccountDetailType.INVENTORY_CLEARING);
            };
            // Class сатрдан ўз легига айнан кўчади (class-tracking.md)
            legs.add(new DebitLeg(account, line.getAmount(),
                    line.getWarehouseId(), line.getItemId(), line.getMemo(),
                    line.getClassId()));
            if (line.getTaxAmount().signum() > 0) {
                taxByRate.merge(line.getTaxRateValue(), line.getTaxAmount(), BigDecimal::add);
            }
        }
        UUID taxAccount = accountService.requireSystemAccountId(AccountDetailType.SALES_TAX_PAYABLE);
        for (BigDecimal taxSum : taxByRate.values()) {
            // Ставка кесимида ЖАМЛАНГАН лег - бир нечта class аралашади, class'сиз
            legs.add(new DebitLeg(taxAccount, taxSum, null, null, null, null));
        }

        // 2) Penny rounding: чет валютада леглар base'и largest-remainder
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
                    bill.getVendorId(), leg.warehouseId(), leg.itemId(), leg.memo(),
                    leg.classId()));
        }

        // 3) AP кредити = GROSS (bill.total)
        Money credit = isHome
                ? Money.ofBase(bill.getTotal(), home)
                : Money.withBase(bill.getTotal(), docCurrency,
                        MoneyAllocation.targetBase(bill.getTotal(), rate), rate);
        glLines.add(new JournalEntryRequest.Line(
                accountService.requireSystemAccountId(AccountDetailType.ACCOUNTS_PAYABLE), null, credit,
                bill.getVendorId(), null, null, null));
        return glLines;
    }


    /** Vendor номи - GL тавсифи учун. */
    private String vendorName(UUID vendorId) {
        return contactService.get(vendorId).getDisplayName();
    }

}
