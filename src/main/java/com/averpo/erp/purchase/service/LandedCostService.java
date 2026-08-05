package com.averpo.erp.purchase.service;

import com.averpo.erp.inventory.domain.StockMovement;
import com.averpo.erp.inventory.service.InventoryService;
import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.ledger.service.JournalEntryRequest;
import com.averpo.erp.ledger.service.PostingService;
import com.averpo.erp.purchase.domain.LandedCostAllocation;
import com.averpo.erp.purchase.domain.LandedCostAllocationLine;
import com.averpo.erp.purchase.repo.LandedCostAllocationLineRepository;
import com.averpo.erp.purchase.repo.LandedCostAllocationRepository;
import com.averpo.erp.shared.BatchLookup;
import com.averpo.erp.shared.Strings;
import com.averpo.erp.shared.domain.DocumentType;
import com.averpo.erp.shared.domain.Money;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.exception.NotFoundException;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.service.DocumentSequenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Landed cost тақсимотининг ягона public API'си
 * (docs/modules/purchases.md «Landed cost»). Клирингдаги сумма
 * танланган receipt'ларга ҚИЙМАТ НИСБАТИДА тарқатилади: қолган
 * миқдор улуши омбор қийматига (InventoryService.addReceiptValue),
 * сотилган улуши COGS'га. DRAFT йўқ - яратилди = POSTED.
 *
 * <p>GL - фақат PostingService (ТЕМИР ҚОИДА №2); омбор қийматлари -
 * фақат InventoryService public API орқали (№6).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class LandedCostService {

    /** GL манба модул белгиси (posting-rules «Харид»). */
    public static final String SOURCE_MODULE = "LANDED_COST";

    /** Тақсимот формаси маълумотлари (сумма home валютада). */
    public record AllocationData(LocalDate date, BigDecimal totalAmount,
                                 String memo, List<UUID> movementIds) { }

    /** Тақсимот ҳужжатлари репозиторийси. */
    private final LandedCostAllocationRepository repository;

    /** Қаторлар репозиторийси. */
    private final LandedCostAllocationLineRepository lineRepository;

    /** Ҳужжат рақамлари (LC-2026-NNNNN). */
    private final DocumentSequenceService sequenceService;

    /** Receipt маълумоти ва қиймат ошириш/қайтариш. */
    private final InventoryService inventoryService;

    /** Item asset счётини топиш учун - item модулининг public API'си. */
    private final ItemService itemService;

    /** Тизим счётлари (INVENTORY_CLEARING, COGS). */
    private final AccountService accountService;

    /** GL'га ёзишнинг ягона йўли. */
    private final PostingService postingService;

    /** Home currency - GL суммалари учун. */
    private final CompanySettingsService settingsService;

    /** Id бўйича топади ёки тушунарли хато отади. */
    @Transactional(readOnly = true)
    public LandedCostAllocation get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Тақсимот топилмади: " + id));
    }

    /**
     * Рўйхат филтри (DEC-068, list-filters.md): барча майдонлар
     * ихтиёрий (null - чекланмаган); q - рақам/изоҳ contains
     * (катта-кичик фарқсиз, кирилл ҳам). Контакт майдони йўқ - landed
     * cost тақсимоти таъминотчига боғланмайди.
     */
    public record ListFilter(java.time.LocalDate from, java.time.LocalDate to,
                             LandedCostAllocation.Status status, String q) {
    }

    /**
     * Рўйхат экрани - янгидан эскига, тўлиқ филтр (DEC-068): давр/
     * статус/матн битта Specification'да (audit услуби). Саҳифаланмаган
     * (рўйхат табиатан қисқа) - тартиб аввалгига айнан мос.
     */
    @Transactional(readOnly = true)
    public List<LandedCostAllocation> list(ListFilter filter) {
        return repository.findAll(org.springframework.data.jpa.domain.Specification.allOf(
                        com.averpo.erp.shared.repo.ListSpecs.dateFrom("allocationDate", filter.from()),
                        com.averpo.erp.shared.repo.ListSpecs.dateTo("allocationDate", filter.to()),
                        com.averpo.erp.shared.repo.ListSpecs.eq("status", filter.status()),
                        com.averpo.erp.shared.repo.ListSpecs.textContains(filter.q(),
                                "allocationNumber", "memo")),
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.desc("allocationDate"),
                        org.springframework.data.domain.Sort.Order.desc("createdAt")));
    }

    /** Ҳужжат қаторлари - кўриш экрани ва тестлар учун. */
    @Transactional(readOnly = true)
    public List<LandedCostAllocationLine> linesOf(UUID allocationId) {
        return lineRepository.findByAllocationIdOrderByCreatedAtAsc(allocationId);
    }

    /**
     * Receipt'га ФАОЛ (POSTED) тақсимот борми - BillService.reverse
     * BR-BILL-012 гарови учун: тақсимот кучда туриб bill қайтарилса,
     * receipt'га юкланган қиймат ва клиринг кредити GL'да «осилиб»
     * қолар эди (PERF-005).
     */
    @Transactional(readOnly = true)
    public boolean activeAllocationExists(UUID movementId) {
        return lineRepository.existsByMovementIdAndAllocationStatus(
                movementId, LandedCostAllocation.Status.POSTED);
    }

    /**
     * Тақсимот яратади - дарҳол POSTED: receipt'лар қиймати ошади,
     * GL проводка ёзилади (нол улуш сатрлар ташланади). Давр қулфи
     * (BR-LED-020) PostingService'дан автоматик.
     *
     * @throws BusinessRuleException BR-LC-001..004, 007
     */
    public LandedCostAllocation create(AllocationData data) {
        if (data.totalAmount() == null || data.totalAmount().signum() <= 0) {
            throw new BusinessRuleException(BusinessRule.BR_LC_001,
                    "Тақсимот суммаси мусбат бўлиши шарт");
        }
        if (data.date() == null) {
            throw new BusinessRuleException(BusinessRule.BR_LC_002,
                    "Тақсимот санаси киритилиши шарт");
        }
        if (data.movementIds() == null || data.movementIds().isEmpty()) {
            throw new BusinessRuleException(BusinessRule.BR_LC_003,
                    "Камида битта receipt танланиши шарт");
        }
        if (new HashSet<>(data.movementIds()).size() != data.movementIds().size()) {
            throw new BusinessRuleException(BusinessRule.BR_LC_003,
                    "Receipt такрор танланган");
        }
        // Батч (OPT-007): танланган receipt'лар warehouse'и билан битта
        // IN сўровда - биттадан movement() қилинмайди; мавжудлик (NotFound,
        // аввалги movement() хулқи айнан) ва BR-LC-004 input тартибида
        Map<UUID, StockMovement> receiptsById = BatchLookup.byId(
                inventoryService.movementsByIds(data.movementIds()));
        List<StockMovement> receipts = new ArrayList<>();
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (UUID movementId : data.movementIds()) {
            StockMovement receipt = receiptsById.get(movementId);
            if (receipt == null) {
                throw new NotFoundException("Омбор ҳаракати топилмади: " + movementId);
            }
            if (!receipt.getType().inbound() || !"BILL".equals(receipt.getReferenceType())) {
                throw new BusinessRuleException(BusinessRule.BR_LC_004,
                        "Receipt BILL манбали кирим бўлиши шарт: " + receipt.getType()
                        + "/" + receipt.getReferenceType());
            }
            receipts.add(receipt);
            totalWeight = totalWeight.add(receipt.getTotalCost());
        }
        if (totalWeight.signum() == 0) {
            throw new BusinessRuleException(BusinessRule.BR_LC_007,
                    "Танланган receipt'лар қиймати нол - нисбат аниқланмайди");
        }

        LandedCostAllocation allocation = new LandedCostAllocation(
                sequenceService.next(DocumentType.LANDED_COST, data.date()),
                data.date(), data.totalAmount(), Strings.blankToNull(data.memo()));
        repository.saveAndFlush(allocation);

        String home = settingsService.homeCurrency();
        UUID cogsAccount = accountService
                .requireSystemAccountId(AccountDetailType.SUPPLIES_MATERIALS_COGS);
        // Батч (OPT-007): asset счёти учун receipt item'лари олдиндан
        // битта IN сўровда - тақсимлаш циклида биттадан get() қилинмайди
        Map<UUID, Item> itemsById = BatchLookup.byId(itemService.findAllById(
                BatchLookup.ids(receipts, StockMovement::getItemId)));
        List<JournalEntryRequest.Line> glLines = new ArrayList<>();

        // Кумулятив яхлитлаш: target_i = round(A × cumW_i / W), улуш =
        // target фарқи - улушлар манфий бўлмайди, йиғинди айнан A
        BigDecimal cumulativeWeight = BigDecimal.ZERO;
        BigDecimal assigned = BigDecimal.ZERO;
        for (StockMovement receipt : receipts) {
            cumulativeWeight = cumulativeWeight.add(receipt.getTotalCost());
            BigDecimal target = data.totalAmount().multiply(cumulativeWeight)
                    .divide(totalWeight, 4, RoundingMode.HALF_UP);
            BigDecimal share = target.subtract(assigned);
            assigned = target;
            if (share.signum() == 0) {
                continue; // нол улуш - қатор ҳам, GL сатр ҳам ёзилмайди
            }
            // Юкланган managed receipt тўғридан-тўғри узатилади (OPT-007) -
            // id имзосидаги иккинчи movement() SELECT'и йўқолади
            InventoryService.ReceiptValueResult result =
                    inventoryService.addReceiptValue(receipt, share);
            lineRepository.save(new LandedCostAllocationLine(allocation,
                    receipt.getId(), share, result.inventoryShare(),
                    result.cogsShare(), result.remainingQty()));

            UUID warehouseId = receipt.getWarehouse().getId();
            if (result.inventoryShare().signum() > 0) {
                glLines.add(new JournalEntryRequest.Line(
                        itemsById.get(receipt.getItemId()).getInventoryAssetAccountId(),
                        Money.ofBase(result.inventoryShare(), home), null,
                        null, warehouseId, receipt.getItemId(), null));
            }
            if (result.cogsShare().signum() > 0) {
                glLines.add(new JournalEntryRequest.Line(cogsAccount,
                        Money.ofBase(result.cogsShare(), home), null,
                        null, warehouseId, receipt.getItemId(), null));
            }
        }
        glLines.add(JournalEntryRequest.Line.credit(
                accountService.requireSystemAccountId(AccountDetailType.INVENTORY_CLEARING),
                Money.ofBase(data.totalAmount(), home), null));
        postingService.createAndPost(new JournalEntryRequest(
                data.date(), "Landed cost " + allocation.getAllocationNumber(),
                SOURCE_MODULE, allocation.getId(), glLines));
        return allocation;
    }

    /**
     * Reverse: омбор қийматлари айнан ортга (FIFO'да партия қолдиғи
     * тақсимот пайтидаги билан тенг бўлиши шарт - акс ҳолда юкланган
     * қиймат қисман COGS'га кетиб бўлган, BR-LC-006), кейин GL сторно.
     *
     * @throws BusinessRuleException BR-LC-005, BR-LC-006
     */
    public LandedCostAllocation reverse(UUID id, LocalDate reversalDate, String reason) {
        LandedCostAllocation allocation = get(id);
        if (allocation.getStatus() != LandedCostAllocation.Status.POSTED) {
            throw new BusinessRuleException(BusinessRule.BR_LC_005,
                    "Фақат POSTED тақсимот reverse қилинади: "
                    + allocation.getAllocationNumber() + " ҳозир " + allocation.getStatus());
        }
        try {
            for (LandedCostAllocationLine line : linesOf(id)) {
                inventoryService.removeReceiptValue(line.getMovementId(),
                        line.getAmount(), line.getInventoryShare(),
                        line.getRemainingQtyAtAlloc(), line.getCreatedAt());
            }
        } catch (BusinessRuleException e) {
            // BR-INV-003 (FIFO партия гарови) ҳам, BR-INV-010 (AVCO'даги
            // «кейин ҳаракат бўлмаганда» инварианти) ҳам чақирувчига
            // битта LC контекстли код билан етади
            if (e.getRule() == BusinessRule.BR_INV_003
                    || e.getRule() == BusinessRule.BR_INV_010) {
                throw new BusinessRuleException(BusinessRule.BR_LC_006,
                        "Reverse тақиқ - тақсимотдан кейин омбор ҳолати ўзгарган: "
                        + e.getMessage());
            }
            throw e;
        }
        postingService.reverseBySource(SOURCE_MODULE, id, reversalDate,
                reason == null || reason.isBlank() ? "Landed cost reverse" : reason);
        allocation.markReversed(reversalDate);
        return allocation;
    }

}
