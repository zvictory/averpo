package com.averpo.erp.inventory.service;

import com.averpo.erp.inventory.domain.CostLayer;
import com.averpo.erp.inventory.domain.CostLayerConsumption;
import com.averpo.erp.inventory.domain.MovementType;
import com.averpo.erp.inventory.domain.StockAdjustment;
import com.averpo.erp.inventory.domain.StockBalance;
import com.averpo.erp.inventory.domain.StockMovement;
import com.averpo.erp.inventory.domain.StockTransfer;
import com.averpo.erp.inventory.repo.CostLayerConsumptionRepository;
import com.averpo.erp.inventory.repo.CostLayerRepository;
import com.averpo.erp.inventory.repo.StockAdjustmentRepository;
import com.averpo.erp.inventory.repo.StockBalanceRepository;
import com.averpo.erp.inventory.repo.StockMovementRepository;
import com.averpo.erp.inventory.repo.StockTransferRepository;
import com.averpo.erp.inventory.domain.Warehouse;
import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.domain.ItemType;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.ledger.service.JournalEntryRequest;
import com.averpo.erp.ledger.service.PostingService;
import com.averpo.erp.shared.BatchLookup;
import com.averpo.erp.shared.domain.DocumentType;
import com.averpo.erp.shared.domain.InventoryValuationMethod;
import com.averpo.erp.shared.domain.Money;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.exception.NotFoundException;
import com.averpo.erp.shared.repo.ListSpecs;
import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.service.DocumentSequenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Омбор ҳисобининг ягона public API'си (docs/modules/inventory.md):
 * кирим/чиқим/инвентаризация/кўчириш ва valuation - AVCO ёки FIFO,
 * метод ҳар чақириқда CompanySettings'дан ўқилади (биринчи ҳаракатдан
 * кейин у ерда қулфланади - InventoryValuationLockImpl).
 *
 * <p>GL: receive/issue проводкасини ҳужжат модули (Bill/Invoice,
 * 6-7-босқич) қилади; adjustment эса мустақил ҳужжат - GL'га шу ерда,
 * PostingService орқали ёзилади (posting-rules «Омбор»). Transfer
 * GL'сиз. Барча қийматлар home валютада.
 *
 * <p><b>Стандарт - IAS 2 «Захиралар»</b>: захира таннархи фақат ўртача
 * нарх (AVCO) ёки FIFO билан ўлчанади - IAS 2 рухсат берган икки метод.
 * <b>LIFO атайлаб амалга оширилмаган, чунки IAS 2 уни тақиқлайди</b> -
 * бу «улгурилмаган иш» эмас, стандарт талаби.
 *
 * <p><b>Бошқа тизимларда қандай</b> (2026-08 да бирламчи манбалардан
 * текширилган; README «Қиёсий тадқиқот» бўлими):
 * <ul>
 *   <li><b>Xero</b> - ядрода фақат ўртача нарх, FIFO умуман йўқ; омбор
 *       тушунчаси ҳам йўқ (кўп-жой фақат алоҳида Inventory Plus
 *       қўшимчасида, у ҳам АҚШда). Инвентар тузатиш ҳужжати ҳам йўқ -
 *       расмий ечим нол суммали счёт-фактура/кредит-нота.</li>
 *   <li><b>QuickBooks Online</b> - асосий тарифда ўртача нарх, FIFO
 *       фақат Advanced'да; омбор кесими умуман йўқ.</li>
 *   <li><b>NetSuite</b> - етти метод (Average default, FIFO, LIFO,
 *       Standard, Group Average, Specific, Lot Numbered), партия
 *       даражасида - бизнинг FIFO layer моделимизга энг яқин.</li>
 *   <li><b>SAP S/4HANA</b> - перпетуал баҳолаш фақат moving average (V)
 *       ёки standard price (S). SAP'даги FIFO/LIFO - даврий баланс
 *       баҳолаш жараёни, партия-даражасидаги доимий костинг ЭМАС; LIFO
 *       S/4HANA Cloud'да умуман йўқ (халқаро стандартлар сабабли).</li>
 * </ul>
 * Бизнинг {@code (item, warehouse)} кесимидаги перпетуал AVCO/FIFO
 * модели SME сегментидагилардан кучлироқ, NetSuite йўлига яқин ва
 * IAS 2 га тўлиқ мос.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class InventoryService {

    /** Ўртача/бирлик қийматлар аниқлиги (DB numeric(24,12) билан мос). */
    private static final int COST_SCALE = 12;

    /** Жами қийматлар аниқлиги (DB numeric(19,4) билан мос). */
    private static final int TOTAL_SCALE = 4;

    /** GL манба модул белгиси (BR-LED-012 idempotency калити). */
    public static final String SOURCE_MODULE = "INVENTORY";

    /**
     * Чиқим натижаси.
     *
     * @param movement  яратилган OUT ҳаракати
     * @param totalCost чиқим таннархи (home) - COGS проводкасига киради
     */
    public record IssueResult(StockMovement movement, BigDecimal totalCost) { }

    /**
     * Кўчириш натижаси.
     *
     * @param outbound  манбадаги TRANSFER_OUT ёзуви
     * @param inbound   манзилдаги TRANSFER_IN ёзуви
     * @param totalCost кўчган қиймат (home)
     */
    public record TransferResult(StockMovement outbound, StockMovement inbound,
                                 BigDecimal totalCost) { }

    /** Ҳаракатлар журнали. */
    private final StockMovementRepository movementRepository;

    /** Қолдиқлар. */
    private final StockBalanceRepository balanceRepository;

    /** FIFO партиялари. */
    private final CostLayerRepository layerRepository;

    /** FIFO ейилиш изи. */
    private final CostLayerConsumptionRepository consumptionRepository;

    /** Омбор текшируви - ўз модулимиз service'и. */
    private final WarehouseService warehouseService;

    /** Item тип текшируви - item модулининг public API'си (қоида №6). */
    private final ItemService itemService;

    /** Valuation методи ва home currency шу ердан ўқилади. */
    private final CompanySettingsService settingsService;

    /** GL'га ёзишнинг ягона йўли (ТЕМИР ҚОИДА №2) - adjustment учун. */
    private final PostingService postingService;

    /** Shrinkage тизим счётини detail type орқали топиш учун. */
    private final AccountService accountService;

    /** Ҳужжатли акт рақамлари (ADJ-/WTR-2026-NNNNN) - умумий механизм. */
    private final DocumentSequenceService sequenceService;

    /** Ҳужжатли инвентаризация актлари сақлагичи (Arbitr-093). */
    private final StockAdjustmentRepository adjustmentRepository;

    /** Ҳужжатли кўчириш актлари сақлагичи (Arbitr-093). */
    private final StockTransferRepository transferRepository;

    /** Манба ҳужжатнинг омбор ҳаракатлари - ҳужжат модуллари reverse'да ишлатади. */
    @Transactional(readOnly = true)
    public List<StockMovement> byReference(String referenceType, UUID referenceId) {
        return movementRepository.findByReferenceTypeAndReferenceIdOrderByCreatedAtAsc(
                referenceType, referenceId);
    }

    /** Ҳаракатлар рўйхати саҳифаси ҳажми (Beruniy-perf1 2-босқич). */
    public static final int MOVEMENTS_PAGE_SIZE = 25;

    /**
     * Ҳаракатлар рўйхати тартиби - аввалги ORDER BY'га айнан мос
     * (янгидан эскига, тенг санада ёзилиш вақти). Саҳифалашга ўтишда
     * экран тартиби ўзгармасин (Beruniy-perf1, BillService.LIST_SORT қолипи).
     */
    private static final org.springframework.data.domain.Sort MOVEMENTS_SORT =
            org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Order.desc("movementDate"),
                    org.springframework.data.domain.Sort.Order.desc("createdAt"),
                    org.springframework.data.domain.Sort.Order.desc("id"));

    /**
     * Ҳаракатлар экрани - саҳифаланган (Beruniy-perf1 2-босқич): аввалги
     * findTop100 ФУНКЦИОНАЛ ТЕШИК эди - 100-ёзувдан эскиси УМУМАН
     * кўринмасди. Энди ҳақиқий LIMIT/OFFSET, ихтиёрий омбор ва/ёки item
     * филтри (T9 drill-down) билан тўртала комбинация.
     *
     * <p>ДИҚҚАТ: landed cost формасидаги номзод танлагич
     * (findTop100ByTypeAndReferenceType) бунга тегишли эмас - у алоҳида
     * қолади (топ-100 receipt етарли).
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<StockMovement> movements(
            UUID warehouseId, UUID itemId, int page) {
        var pageable = org.springframework.data.domain.PageRequest.of(
                Math.max(0, page), MOVEMENTS_PAGE_SIZE, MOVEMENTS_SORT);
        if (itemId != null && warehouseId != null) {
            return movementRepository.findByItemIdAndWarehouseId(itemId, warehouseId, pageable);
        }
        if (itemId != null) {
            return movementRepository.findByItemId(itemId, pageable);
        }
        if (warehouseId != null) {
            return movementRepository.findByWarehouseId(warehouseId, pageable);
        }
        return movementRepository.findAll(pageable);
    }

    /**
     * Ҳаракатлар экранининг мукаммал филтри (Arbitr-093, фойдаланувчи
     * талаби): тур/омбор/item/сана оралиғи/ҳужжат рақами. Specification
     * билан server-side; warehouse ва counterpart to-one fetch фақат DATA
     * сўровида қўшилади (count'да эмас) - N+1'дан қочилади ва to-one
     * fetch pagination'ни бузмайди (to-many эмас, in-memory paging
     * хатари йўқ). Ҳужжат рақами (ADJ-/WTR-) акт id'сига ечилиб
     * reference_id кесимига айланади; рақам топилмаса натижа бўш.
     */
    @Transactional(readOnly = true)
    public Page<StockMovement> movements(MovementFilter filter, int page) {
        Specification<StockMovement> spec = (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("warehouse", jakarta.persistence.criteria.JoinType.LEFT);
                root.fetch("counterpartWarehouse", jakarta.persistence.criteria.JoinType.LEFT);
                query.distinct(true);
            }
            List<jakarta.persistence.criteria.Predicate> ps = new ArrayList<>();
            if (filter.type() != null) {
                ps.add(cb.equal(root.get("type"), filter.type()));
            }
            if (filter.warehouseId() != null) {
                ps.add(cb.equal(root.get("warehouse").get("id"), filter.warehouseId()));
            }
            if (filter.itemId() != null) {
                ps.add(cb.equal(root.get("itemId"), filter.itemId()));
            }
            if (filter.from() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("movementDate"), filter.from()));
            }
            if (filter.to() != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("movementDate"), filter.to()));
            }
            if (filter.docNumber() != null && !filter.docNumber().isBlank()) {
                UUID docId = resolveDocumentId(filter.docNumber());
                // Рақам топилмаса - қасддан бўш натижа (disjunction = FALSE)
                ps.add(docId == null ? cb.disjunction()
                        : cb.equal(root.get("referenceId"), docId));
            }
            return cb.and(ps.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return movementRepository.findAll(spec,
                PageRequest.of(Math.max(0, page), MOVEMENTS_PAGE_SIZE, MOVEMENTS_SORT));
    }

    /** Ҳаракатлар экрани филтри: барчаси ихтиёрий (null - чекланмаган). */
    public record MovementFilter(MovementType type, UUID warehouseId, UUID itemId,
                                 LocalDate from, LocalDate to, String docNumber) { }

    /** Ҳужжат рақами (ADJ-/WTR-) → акт id; топилмаса null (бўш натижа). */
    private UUID resolveDocumentId(String docNumber) {
        String n = docNumber.strip();
        return adjustmentRepository.findByAdjNumber(n).map(StockAdjustment::getId)
                .or(() -> transferRepository.findByWtrNumber(n).map(StockTransfer::getId))
                .orElse(null);
    }

    /**
     * Битта ҳаракат ёзуви - ҳужжат модуллари учун (landed cost).
     * warehouse граф билан юкланади - шаблонларда lazy хатоси бўлмасин.
     */
    @Transactional(readOnly = true)
    public StockMovement movement(UUID id) {
        return movementRepository.findWithWarehouseById(id)
                .orElseThrow(() -> new com.averpo.erp.shared.exception.NotFoundException(
                        "Омбор ҳаракати топилмади: " + id));
    }

    /**
     * Танланган ҳаракатлар warehouse графи билан битта IN сўровда
     * (Arbitr-045 findAllById нақши, Sanjar-007) - landed cost N
     * receipt'ни биттадан {@link #movement} қилмасин. Топилмаганлар
     * рўйхатда бўлмайди; мавжудликни чақирувчи текширади.
     */
    @Transactional(readOnly = true)
    public List<StockMovement> movementsByIds(Collection<UUID> ids) {
        return ids.isEmpty() ? List.of() : movementRepository.findWithWarehouseByIdIn(ids);
    }

    /** Landed cost формаси учун: охирги BILL киримлари (номзод receipt'лар). */
    @Transactional(readOnly = true)
    public List<StockMovement> billReceipts() {
        return movementRepository
                .findTop100ByTypeAndReferenceTypeOrderByMovementDateDescCreatedAtDesc(
                        MovementType.IN, "BILL");
    }

    /** Битта (омбор, item) қолдиғи - йўқ бўлса нол қайтади. */
    @Transactional(readOnly = true)
    public BigDecimal quantityOnHand(UUID itemId, UUID warehouseId) {
        return balanceRepository.findByWarehouseIdAndItemId(warehouseId, itemId)
                .map(StockBalance::getQty)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Жорий ўртача таннарх (home, scale 12) - қайтариш ҳужжатларининг
     * «жорий сиёсат таннархи» манбаси (returns.md: ҳаволасиз қайтим
     * кирими шу нархда киради; FIFO'да ҳам янги қатлам шу нархда -
     * posting-rules «Inventory қайтим таннархи»). Ҳаракат бўлмаган
     * (омбор, item)да нол.
     */
    @Transactional(readOnly = true)
    public BigDecimal currentAvgCost(UUID itemId, UUID warehouseId) {
        return balanceRepository.findByWarehouseIdAndItemId(warehouseId, itemId)
                .map(StockBalance::getAvgCost)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Кирим: қолдиқ ошади, AVCO ўртачаси янгиланади, FIFO'да янги
     * партия очилади.
     *
     * @throws BusinessRuleException BR-INV-001/002/004/006/008
     */
    public StockMovement receive(UUID itemId, UUID warehouseId, BigDecimal qty,
                                 BigDecimal unitCost, LocalDate date,
                                 String referenceType, UUID referenceId, String memo) {
        requireInventoryItem(itemId);
        Warehouse warehouse = requireActiveWarehouse(warehouseId);
        requirePositiveQty(qty);
        requireDate(date);
        requireNonNegativeCost(unitCost);
        return performIn(MovementType.IN, warehouse, null, itemId,
                List.of(new InChunk(qty, unitCost, date)),
                date, referenceType, referenceId, memo);
    }

    /**
     * Чиқим: қолдиқ камаяди, таннарх valuation методи бўйича - AVCO'да
     * qty × жорий ўртача, FIFO'да партиялар received_date тартибида
     * ейилади (consumption изи билан).
     *
     * @throws BusinessRuleException BR-INV-001/002/003/006/008
     */
    public IssueResult issue(UUID itemId, UUID warehouseId, BigDecimal qty,
                             LocalDate date, String referenceType,
                             UUID referenceId, String memo) {
        requireInventoryItem(itemId);
        Warehouse warehouse = requireActiveWarehouse(warehouseId);
        requirePositiveQty(qty);
        requireDate(date);

        StockBalance balance = requireSufficient(warehouseId, itemId, qty);
        OutPlan plan = planOut(balance, warehouseId, itemId, qty);
        StockMovement movement = performOut(MovementType.OUT, balance, warehouse,
                null, itemId, qty, date, referenceType, referenceId, memo, plan);
        return new IssueResult(movement, movement.getTotalCost());
    }

    /**
     * Инвентаризация тузатиши: мусбат delta - кўпайиш (ADJUST_IN),
     * манфий - камомад (ADJUST_OUT). GL проводка шу ернинг ўзида
     * (posting-rules «Омбор»): sourceModule=INVENTORY, docId=movement id
     * - BR-LED-012 idempotency ва BR-LED-020 период қулфи автоматик.
     *
     * <p>Кўпайиш нархи: берилса ўша (BR-INV-004), берилмаса жорий
     * қиймат - AVCO'да ўртача, FIFO'да охирги фаол партия нархи;
     * қолдиқ нолда нарх мажбурий (BR-INV-007). Нол қийматли тузатиш
     * GL'га ёзилмайди (XOR қоидасига зид бўлар эди).
     *
     * @param deltaQty ижобий - кўпайиш, манфий - камайиш (нол тақиқ)
     * @param unitCost фақат кўпайишда, ихтиёрий
     */
    public StockMovement adjust(UUID itemId, UUID warehouseId, BigDecimal deltaQty,
                                BigDecimal unitCost, LocalDate date, String memo) {
        Item item = requireInventoryItem(itemId);
        Warehouse warehouse = requireActiveWarehouse(warehouseId);
        if (deltaQty == null || deltaQty.signum() == 0) {
            throw new BusinessRuleException(BusinessRule.BR_INV_002,
                    "Тузатиш миқдори нолдан фарқли бўлиши шарт");
        }
        requireDate(date);

        StockMovement movement;
        boolean increase = deltaQty.signum() > 0;
        if (increase) {
            BigDecimal cost = resolveAdjustCost(warehouseId, itemId, unitCost);
            movement = performIn(MovementType.ADJUST_IN, warehouse, null, itemId,
                    List.of(new InChunk(deltaQty, cost, date)),
                    date, "ADJUSTMENT", null, memo);
        } else {
            BigDecimal qty = deltaQty.negate();
            StockBalance balance = requireSufficient(warehouseId, itemId, qty);
            OutPlan plan = planOut(balance, warehouseId, itemId, qty);
            movement = performOut(MovementType.ADJUST_OUT, balance, warehouse,
                    null, itemId, qty, date, "ADJUSTMENT", null, memo, plan);
        }
        postAdjustment(movement, item, increase);
        return movement;
    }

    /**
     * Омборлараро кўчириш - GL проводка ЙЎҚ (posting-rules). Иккита
     * ҳаракат ёзилади: TRANSFER_OUT манбада, TRANSFER_IN манзилда
     * (counterpart орқали боғланган). AVCO'да қиймат манба ўртачасида
     * кўчади; FIFO'да ейилган партиялар манзилда худди шу нарх ва
     * received_date билан қайта яратилади - FIFO тартиби сақланади.
     *
     * @throws BusinessRuleException BR-INV-001/002/003/005/006/008
     */
    public TransferResult transfer(UUID itemId, UUID fromWarehouseId,
                                   UUID toWarehouseId, BigDecimal qty,
                                   LocalDate date, String memo) {
        requireInventoryItem(itemId);
        if (fromWarehouseId != null && fromWarehouseId.equals(toWarehouseId)) {
            throw new BusinessRuleException(BusinessRule.BR_INV_005,
                    "Кўчиришда манба ва манзил омбор ҳар хил бўлиши шарт");
        }
        Warehouse from = requireActiveWarehouse(fromWarehouseId);
        Warehouse to = requireActiveWarehouse(toWarehouseId);
        requirePositiveQty(qty);
        requireDate(date);

        StockBalance sourceBalance = requireSufficient(fromWarehouseId, itemId, qty);
        OutPlan plan = planOut(sourceBalance, fromWarehouseId, itemId, qty);
        StockMovement outbound = performOut(MovementType.TRANSFER_OUT, sourceBalance,
                from, to, itemId, qty, date, "TRANSFER", null, memo, plan);
        // Манзилга ейилган бўлаклар айнан ўз нархи/санаси билан киради
        StockMovement inbound = performIn(MovementType.TRANSFER_IN, to, from, itemId,
                plan.chunks(), date, "TRANSFER", outbound.getId(), memo);
        return new TransferResult(outbound, inbound, outbound.getTotalCost());
    }

    // ---- ҳужжатли актлар (Arbitr-093, docs/modules/inventory.md) ----

    /** reference_type: ҳужжатли акт ҳаракатлари шу белги орқали боғланади. */
    public static final String ADJUSTMENT_REFERENCE = "STOCK_ADJUSTMENT";

    /** reference_type: ҳужжатли кўчириш ҳаракатлари. */
    public static final String TRANSFER_REFERENCE = "STOCK_TRANSFER";

    /** Актлар рўйхати саҳифаси ҳажми (movements билан бир хил тартиб). */
    public static final int DOCUMENTS_PAGE_SIZE = 25;

    /** Битта акт сатри маълумоти: item + ЯНГИ qty + ихтиёрий нарх. */
    public record AdjustLineData(UUID itemId, BigDecimal newQty, BigDecimal unitCost, String memo) { }

    /**
     * Инвентаризация акти маълумоти: битта омбор, кўп сатр + ихтиёрий
     * ташқи ҳужжат рақами (Arbitr-109, QBO «Reference no.»).
     */
    public record DocumentAdjustData(UUID warehouseId, LocalDate date, String memo,
                                     String externalRef, List<AdjustLineData> lines) {
        /** Compat: externalRef'сиз чақирувчилар (мавжуд тестлар) - null. */
        public DocumentAdjustData(UUID warehouseId, LocalDate date, String memo,
                                  List<AdjustLineData> lines) {
            this(warehouseId, date, memo, null, lines);
        }
    }

    /** Кўчириш акти сатри: item + qty. */
    public record TransferLineData(UUID itemId, BigDecimal qty, String memo) { }

    /**
     * Кўчириш акти маълумоти: манба/манзил омбор, кўп сатр + ихтиёрий
     * ташқи ҳужжат рақами (Arbitr-109).
     */
    public record DocumentTransferData(UUID fromWarehouseId, UUID toWarehouseId,
                                       LocalDate date, String memo, String externalRef,
                                       List<TransferLineData> lines) {
        /** Compat: externalRef'сиз чақирувчилар (мавжуд тестлар) - null. */
        public DocumentTransferData(UUID fromWarehouseId, UUID toWarehouseId,
                                    LocalDate date, String memo,
                                    List<TransferLineData> lines) {
            this(fromWarehouseId, toWarehouseId, date, memo, null, lines);
        }
    }

    /** Актлар рўйхати филтри (068 ListFilter нақши): омбор + сана оралиғи. */
    public record DocumentFilter(UUID warehouseId, LocalDate from, LocalDate to) { }

    /**
     * Ҳужжатли инвентаризация акти (Arbitr-093): кўп сатрли, БИТТА омбор,
     * дарҳол POSTED, актнинг ҲАММА сатрига БИТТА JE (posting-rules
     * «Ҳужжатли Adjustment»). Ҳар сатр ЯНГИ qty киритилади → delta авто
     * (new − жорий); мусбат delta - ADJUST_IN (Dr item inventory), манфий -
     * ADJUST_OUT (Cr item inventory), shrinkage томони жамланиб битта легга
     * нетто ёзилади. Нол delta - ҳаракатсиз сатр (аудит), нол қийматли сатр
     * (avg 0) - легсиз (BR-LED-002 XOR). Таннарх ҳисоби мавжуд движокдан
     * (performIn/performOut) - GL легларини бу метод йиғиб бир марта ёзади,
     * шунда per-movement postAdjustment чақирилмайди (ташқи имзолар сақланади).
     *
     * @throws BusinessRuleException BR-INV-011 сатр йўқ; BR-INV-012 item
     *         такрор; BR-INV-001/002/003/006/007/008 сатр даражасида
     */
    public StockAdjustment adjustDocument(DocumentAdjustData data) {
        Warehouse warehouse = requireActiveWarehouse(data.warehouseId());
        requireDate(data.date());
        requireLines(data.lines());
        requireUniqueItems(data.lines(), AdjustLineData::itemId);

        String home = settingsService.homeCurrency();
        StockAdjustment act = new StockAdjustment(
                sequenceService.next(DocumentType.STOCK_ADJUSTMENT, data.date()),
                warehouse, data.date(),
                com.averpo.erp.shared.Strings.blankToNull(data.memo()),
                com.averpo.erp.shared.Strings.blankToNull(data.externalRef()));

        List<JournalEntryRequest.Line> legs = new ArrayList<>();
        // Батч (Arbitr-045 findAllById, Sanjar-008): requireUniqueItems
        // такрорни тақиқлаган - N сатр айнан N item SELECT эди; энди
        // ҳаммаси битта IN сўровда, циклда Map.get()
        Map<UUID, Item> items = BatchLookup.byId(itemService.findAllById(
                BatchLookup.ids(data.lines(), AdjustLineData::itemId)));
        // netShrinkage = камайишлар қиймати − кўпайишлар қиймати; мусбат -
        // shrinkage Dt (харажат), манфий - shrinkage Cr (ортиқча/фойда)
        BigDecimal netShrinkage = BigDecimal.ZERO;
        for (AdjustLineData ld : data.lines()) {
            Item item = requireInventoryItem(ld.itemId(), items);
            if (ld.newQty() == null || ld.newQty().signum() < 0) {
                throw new BusinessRuleException(BusinessRule.BR_INV_002,
                        "Янги қолдиқ манфий бўлмаган сон бўлиши шарт: «" + item.getName() + "»");
            }
            String lineMemo = com.averpo.erp.shared.Strings.blankToNull(ld.memo());
            BigDecimal current = quantityOnHand(ld.itemId(), warehouse.getId());
            BigDecimal delta = ld.newQty().subtract(current);
            if (delta.signum() == 0) {
                // Ҳаракатсиз сатр - фойдаланувчи қолдиқ мослигини тасдиқлади
                act.addLine(ld.itemId(), ld.newQty(), BigDecimal.ZERO, null, BigDecimal.ZERO, lineMemo);
                continue;
            }
            BigDecimal lineCost;
            BigDecimal unitCostSnapshot = null;
            if (delta.signum() > 0) {
                BigDecimal cost = resolveAdjustCost(warehouse.getId(), ld.itemId(), ld.unitCost());
                unitCostSnapshot = cost;
                StockMovement mv = performIn(MovementType.ADJUST_IN, warehouse, null,
                        ld.itemId(), List.of(new InChunk(delta, cost, data.date())),
                        data.date(), ADJUSTMENT_REFERENCE, act.getId(), lineMemo);
                lineCost = mv.getTotalCost();
                if (lineCost.signum() != 0) {
                    legs.add(new JournalEntryRequest.Line(item.getInventoryAssetAccountId(),
                            Money.ofBase(lineCost, home), null, null,
                            warehouse.getId(), ld.itemId(), null));
                    netShrinkage = netShrinkage.subtract(lineCost);
                }
            } else {
                BigDecimal qty = delta.negate();
                StockBalance balance = requireSufficient(warehouse.getId(), ld.itemId(), qty);
                OutPlan plan = planOut(balance, warehouse.getId(), ld.itemId(), qty);
                StockMovement mv = performOut(MovementType.ADJUST_OUT, balance, warehouse,
                        null, ld.itemId(), qty, data.date(), ADJUSTMENT_REFERENCE,
                        act.getId(), lineMemo, plan);
                lineCost = mv.getTotalCost().negate();
                if (mv.getTotalCost().signum() != 0) {
                    legs.add(new JournalEntryRequest.Line(item.getInventoryAssetAccountId(),
                            null, Money.ofBase(mv.getTotalCost(), home), null,
                            warehouse.getId(), ld.itemId(), null));
                    netShrinkage = netShrinkage.add(mv.getTotalCost());
                }
            }
            act.addLine(ld.itemId(), ld.newQty(), delta, unitCostSnapshot, lineCost, lineMemo);
        }

        if (netShrinkage.signum() != 0) {
            UUID shrinkage = accountService
                    .requireSystemAccountId(AccountDetailType.OTHER_COSTS_OF_SERVICE_COS);
            Money money = Money.ofBase(netShrinkage.abs(), home);
            legs.add(netShrinkage.signum() > 0
                    ? new JournalEntryRequest.Line(shrinkage, money, null, null, null, null, null)
                    : new JournalEntryRequest.Line(shrinkage, null, money, null, null, null, null));
        }

        adjustmentRepository.save(act);
        if (!legs.isEmpty()) {
            postingService.createAndPost(new JournalEntryRequest(data.date(),
                    "Инвентаризация акти " + act.getAdjNumber() + " ("
                            + warehouse.getName() + ")",
                    SOURCE_MODULE, act.getId(), legs));
        }
        act.markPosted(Instant.now());
        return act;
    }

    /**
     * Ҳужжатли омборлараро кўчириш акти (Arbitr-093): кўп сатрли, манба/
     * манзил омбор, дарҳол POSTED, GL'СИЗ (posting-rules). Ҳар сатр
     * TRANSFER_OUT+IN жуфти (reference=акт id); таннарх мавжуд движок
     * transfer мантиғидек (манба ўртачаси/FIFO партиялари сақланади).
     *
     * @throws BusinessRuleException BR-INV-011 сатр йўқ; BR-INV-012 item
     *         такрор; BR-INV-005 омборлар тенг; BR-INV-001/002/003/006/008
     */
    public StockTransfer transferDocument(DocumentTransferData data) {
        if (data.fromWarehouseId() != null
                && data.fromWarehouseId().equals(data.toWarehouseId())) {
            throw new BusinessRuleException(BusinessRule.BR_INV_005,
                    "Кўчиришда манба ва манзил омбор ҳар хил бўлиши шарт");
        }
        Warehouse from = requireActiveWarehouse(data.fromWarehouseId());
        Warehouse to = requireActiveWarehouse(data.toWarehouseId());
        requireDate(data.date());
        requireLines(data.lines());
        requireUniqueItems(data.lines(), TransferLineData::itemId);

        StockTransfer act = new StockTransfer(
                sequenceService.next(DocumentType.STOCK_TRANSFER, data.date()),
                from, to, data.date(),
                com.averpo.erp.shared.Strings.blankToNull(data.memo()),
                com.averpo.erp.shared.Strings.blankToNull(data.externalRef()));
        // Батч (Sanjar-008): item каталог текшируви битта IN сўровда
        // (adjustDocument нақши) - balance/FIFO query'лари ўзгармайди
        Map<UUID, Item> items = BatchLookup.byId(itemService.findAllById(
                BatchLookup.ids(data.lines(), TransferLineData::itemId)));
        for (TransferLineData ld : data.lines()) {
            requireInventoryItem(ld.itemId(), items);
            requirePositiveQty(ld.qty());
            String lineMemo = com.averpo.erp.shared.Strings.blankToNull(ld.memo());
            StockBalance sourceBalance = requireSufficient(from.getId(), ld.itemId(), ld.qty());
            OutPlan plan = planOut(sourceBalance, from.getId(), ld.itemId(), ld.qty());
            StockMovement outbound = performOut(MovementType.TRANSFER_OUT, sourceBalance,
                    from, to, ld.itemId(), ld.qty(), data.date(), TRANSFER_REFERENCE,
                    act.getId(), lineMemo, plan);
            performIn(MovementType.TRANSFER_IN, to, from, ld.itemId(), plan.chunks(),
                    data.date(), TRANSFER_REFERENCE, act.getId(), lineMemo);
            act.addLine(ld.itemId(), ld.qty(), outbound.getTotalCost(), lineMemo);
        }
        transferRepository.save(act);
        act.markPosted(Instant.now());
        return act;
    }

    /** Инвентаризация актлари рўйхати - филтр + саҳифа (янгидан эскига). */
    @Transactional(readOnly = true)
    public Page<StockAdjustment> adjustments(DocumentFilter filter, int page) {
        return adjustmentRepository.findAll(
                Specification.allOf(
                        ListSpecs.<StockAdjustment>eq("warehouse", warehouseRef(filter.warehouseId())),
                        ListSpecs.<StockAdjustment>dateFrom("adjDate", filter.from()),
                        ListSpecs.<StockAdjustment>dateTo("adjDate", filter.to())),
                documentsPageable(page, "adjDate"));
    }

    /** Кўчириш актлари рўйхати - филтр (омбор - манба ЁКИ манзил) + саҳифа. */
    @Transactional(readOnly = true)
    public Page<StockTransfer> transfers(DocumentFilter filter, int page) {
        Warehouse ref = warehouseRef(filter.warehouseId());
        // ListSpecs конвенцияси: Specification ўзи ҲЕЧ ҚАЧОН null эмас -
        // қиймат бўлмаса toPredicate null қайтаради (no-op). Nullable
        // Specification'ни allOf'га бериш Spring Data 4 да IAE отади
        // («Other specification must not be null») - Deploy 4 hotfix.
        Specification<StockTransfer> whFilter = (root, query, cb) -> ref == null ? null
                : cb.or(
                        cb.equal(root.get("fromWarehouse"), ref),
                        cb.equal(root.get("toWarehouse"), ref));
        return transferRepository.findAll(
                Specification.allOf(whFilter,
                        ListSpecs.<StockTransfer>dateFrom("wtrDate", filter.from()),
                        ListSpecs.<StockTransfer>dateTo("wtrDate", filter.to())),
                documentsPageable(page, "wtrDate"));
    }

    /** Битта инвентаризация акти сатрлари билан - кўриш экрани учун. */
    @Transactional(readOnly = true)
    public StockAdjustment adjustment(UUID id) {
        return adjustmentRepository.findWithLinesById(id)
                .orElseThrow(() -> new NotFoundException("Инвентаризация акти топилмади: " + id));
    }

    /** Битта кўчириш акти сатрлари билан - кўриш экрани учун. */
    @Transactional(readOnly = true)
    public StockTransfer transferDoc(UUID id) {
        return transferRepository.findWithLinesById(id)
                .orElseThrow(() -> new NotFoundException("Кўчириш акти топилмади: " + id));
    }

    /** Актлар рўйхати учун Pageable - янгидан эскига (сана, кейин id). */
    private Pageable documentsPageable(int page, String dateField) {
        return PageRequest.of(Math.max(0, page), DOCUMENTS_PAGE_SIZE,
                Sort.by(Sort.Order.desc(dateField), Sort.Order.desc("id")));
    }

    /** Филтр омбори - id берилса managed ref, акс ҳолда null (филтр йўқ). */
    private Warehouse warehouseRef(UUID warehouseId) {
        return warehouseId == null ? null : warehouseService.get(warehouseId);
    }

    /** BR-INV-011: актда камида битта сатр. */
    private void requireLines(List<?> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessRuleException(BusinessRule.BR_INV_011,
                    "Ҳужжатли актда камида битта сатр бўлиши шарт");
        }
    }

    /** BR-INV-012: актда битта item такрорланмайди (DB unique ҳам ҳимоя қилади). */
    private <T> void requireUniqueItems(List<T> lines,
                                        java.util.function.Function<T, UUID> itemIdOf) {
        Set<UUID> seen = new HashSet<>();
        for (T line : lines) {
            if (!seen.add(itemIdOf.apply(line))) {
                throw new BusinessRuleException(BusinessRule.BR_INV_012,
                        "Актда битта item такрорланмайди");
            }
        }
    }

    /**
     * Кирим ҳаракатини АЙНАН ўз нархида тескари қайтаради (Bill reverse
     * учун): оддий issue ярамайди - FIFO'да бошқа (эскироқ) партия
     * ейилиб, GL сторноси (асл суммада) билан омбор қиймати ажралиб
     * кетар эди. FIFO'да шу кирим яратган партия ТЎЛИҚ турган бўлиши
     * шарт; AVCO'да қиймат асл unit cost билан айирилиб ўртача қайта
     * ҳисобланади.
     *
     * <p>BR-INV-010 (Beruniy-003): кирим фақат шу (item, warehouse)даги
     * ЭНГ ОХИРГИ ҳаракат бўлсагина қайтарилади - кейин чиқим/кирим
     * бўлган бўлса AVCO ўртачаси маълумот йўқотган (асл нархда айириш
     * бошқа партия қийматини «ўғирлайди», кейинги COGS/P&amp;L бузилади),
     * қоида FIFO'га ҳам бир хил қўлланади. Ўша манба ҳужжатнинг ўз
     * ҳаракатлари истисно - кўп сатрли bill receipt'лари биргаликда
     * қайтарилади.
     *
     * @throws BusinessRuleException BR-INV-010 - киримдан кейин бошқа
     *         ҳужжат ҳаракати бор; BR-INV-003 - партия қисман ейилган
     *         ёки қолдиқ етарли эмас (чақирувчи ўз кодига ўраши мумкин)
     */
    public StockMovement reverseReceive(UUID movementId, LocalDate date) {
        StockMovement original = movementRepository.findById(movementId)
                .orElseThrow(() -> new com.averpo.erp.shared.exception.NotFoundException(
                        "Омбор ҳаракати топилмади: " + movementId));
        if (!original.getType().inbound()) {
            throw new BusinessRuleException(BusinessRule.BR_INV_002,
                    "Фақат кирим ҳаракати тескари қайтарилади: " + original.getType());
        }
        requireDate(date);
        Warehouse warehouse = original.getWarehouse();
        UUID itemId = original.getItemId();
        BigDecimal qty = original.getQuantity();

        requireNoMovementsAfterAnchor(original, original.getReferenceId());
        StockBalance balance = requireSufficient(warehouse.getId(), itemId, qty);
        StockMovement reversal = movementRepository.save(new StockMovement(
                MovementType.OUT, itemId, warehouse, null, qty,
                original.getUnitCost(), original.getTotalCost(), date,
                original.getReferenceType() == null ? "REVERSAL"
                        : original.getReferenceType() + "_REVERSAL",
                original.getReferenceId(),
                "Қайтариш: " + original.getId()));

        if (valuationMethod() == InventoryValuationMethod.FIFO) {
            CostLayer layer = layerRepository.findBySourceMovementId(movementId)
                    .orElseThrow(() -> insufficientStock(BigDecimal.ZERO, qty));
            if (layer.getRemainingQty().compareTo(layer.getOriginalQty()) != 0) {
                // Партия қисман ейилган - қайтариб бўлмайди (аниқ хато)
                throw insufficientStock(layer.getRemainingQty(), qty);
            }
            layer.consume(layer.getRemainingQty());
            consumptionRepository.save(new CostLayerConsumption(layer, reversal, qty));
            applyFifoBalance(balance, warehouse.getId(), itemId);
        } else {
            // AVCO: қиймат асл нархда айирилади, ўртача қайта ҳисобланади -
            // кейинги киримлар таъсири сақланиб қолади
            BigDecimal newQty = balance.getQty().subtract(qty);
            BigDecimal newValue = balance.getQty().multiply(balance.getAvgCost())
                    .subtract(qty.multiply(original.getUnitCost()));
            BigDecimal newAvg = newQty.signum() == 0 ? BigDecimal.ZERO
                    : newValue.divide(newQty, COST_SCALE, RoundingMode.HALF_UP)
                            .max(BigDecimal.ZERO);
            balance.apply(newQty, newAvg);
        }
        return reversal;
    }

    /**
     * Чиқим ҳаракатини АЙНАН ортга қайтаради (Invoice reverse учун) -
     * reverseReceive'нинг жуфти. FIFO'да шу чиқимнинг consumption изи
     * бўйича ҳар партияга ейилган миқдор қайтарилади (FIFO тартиби
     * сақланади - товар ўз партиясига қайтади); AVCO'да қиймат чиқим
     * total_cost'ида қайтади (ўртача қайта ҳисобланади).
     *
     * <p>FIFO ҳимояси (BR-INV-009): чиқимдан кейин ейилган партия
     * нархи ўзгарган бўлса (landed cost) қайтариладиган қиймат GL
     * сторноси (асл таннарх) билан мос келмай қолади - блокланади,
     * тузатиш inventory adjustment билан.
     *
     * @throws BusinessRuleException BR-INV-002 - ҳаракат чиқим эмас;
     *         BR-INV-009 - партия нархи ўзгарган
     */
    public StockMovement reverseIssue(UUID movementId, LocalDate date) {
        StockMovement original = movementRepository.findById(movementId)
                .orElseThrow(() -> new com.averpo.erp.shared.exception.NotFoundException(
                        "Омбор ҳаракати топилмади: " + movementId));
        if (original.getType().inbound()) {
            throw new BusinessRuleException(BusinessRule.BR_INV_002,
                    "Фақат чиқим ҳаракати тескари қайтарилади: " + original.getType());
        }
        requireDate(date);
        Warehouse warehouse = original.getWarehouse();
        UUID itemId = original.getItemId();
        BigDecimal qty = original.getQuantity();

        StockMovement reversal = movementRepository.save(new StockMovement(
                MovementType.IN, itemId, warehouse, null, qty,
                original.getUnitCost(), original.getTotalCost(), date,
                original.getReferenceType() == null ? "REVERSAL"
                        : original.getReferenceType() + "_REVERSAL",
                original.getReferenceId(),
                "Қайтариш: " + original.getId()));

        StockBalance balance = balanceRepository
                .findByWarehouseIdAndItemId(warehouse.getId(), itemId)
                .orElseGet(() -> balanceRepository.save(new StockBalance(warehouse, itemId)));

        if (valuationMethod() == InventoryValuationMethod.FIFO) {
            List<CostLayerConsumption> consumptions =
                    consumptionRepository.findByMovementIdOrderByCreatedAtAsc(movementId);
            // Қайтариладиган қиймат асл таннарх билан тенглиги олдиндан
            // текширилади - партия нархи landed cost билан ўзгарган бўлса
            // GL сторноси билан омбор қиймати ажралиб кетар эди
            BigDecimal restoreValue = BigDecimal.ZERO;
            for (CostLayerConsumption consumption : consumptions) {
                restoreValue = restoreValue.add(consumption.getQuantity()
                        .multiply(consumption.getLayer().getUnitCost()));
            }
            if (restoreValue.setScale(TOTAL_SCALE, RoundingMode.HALF_UP)
                    .compareTo(original.getTotalCost()) != 0) {
                throw new BusinessRuleException(BusinessRule.BR_INV_009,
                        "Ейилган партия нархи чиқимдан кейин ўзгарган: қайтариш "
                        + restoreValue.setScale(TOTAL_SCALE, RoundingMode.HALF_UP)
                        + ", асл таннарх " + original.getTotalCost());
            }
            for (CostLayerConsumption consumption : consumptions) {
                consumption.getLayer().restore(consumption.getQuantity());
            }
            applyFifoBalance(balance, warehouse.getId(), itemId);
        } else {
            // AVCO: қиймат чиқимдаги total_cost билан қайтади - GL
            // сторноси билан айнан мос
            BigDecimal newQty = balance.getQty().add(qty);
            BigDecimal newValue = balance.getQty().multiply(balance.getAvgCost())
                    .add(original.getTotalCost());
            balance.apply(newQty,
                    newValue.divide(newQty, COST_SCALE, RoundingMode.HALF_UP));
        }
        return reversal;
    }

    /**
     * Кирим қийматини ошириш натижаси (landed cost тақсимоти).
     *
     * @param inventoryShare омбор қийматига қўшилган қисм (home)
     * @param cogsShare      сотилган улушга тўғри келган қисм - COGS'га
     * @param remainingQty   тақсимот пайтидаги қолдиқ - reverse гарови
     */
    public record ReceiptValueResult(BigDecimal inventoryShare, BigDecimal cogsShare,
                                     BigDecimal remainingQty) { }

    /**
     * Кирим (receipt) қийматини оширади - landed cost тақсимоти
     * (docs/modules/purchases.md «Landed cost»). Миқдор ҳаракати
     * ЁЗИЛМАЙДИ - бу қийматнинг қайта баҳоланиши; GL проводкани
     * чақирувчи ҳужжат модули қилади (receive/issue паттерни).
     *
     * <p>delta = amount / Q (кирим миқдори). FIFO: шу кирим партияси
     * unit_cost += delta - қолган R донага R × delta қиймат қўшилади;
     * AVCO'да партия сақланмагани учун R «эски аввал сотилади» фарази
     * билан баҳоланади: R = min(Q, жорий қолдиқ − шу receipt'дан КЕЙИН
     * кирган миқдор), манфий бўлса нол (Beruniy-004: бутун қолдиқни
     * олиш сотилган receipt харажатини кейинги партия активига ёзиб
     * қўяр эди). Сотилган улуш (amount - inventoryShare) чақирувчида
     * COGS'га боради.
     */
    public ReceiptValueResult addReceiptValue(UUID movementId, BigDecimal amount) {
        return addReceiptValue(movement(movementId), amount);
    }

    /**
     * {@link #addReceiptValue(UUID, BigDecimal)} нинг юкланган (managed)
     * receipt устидаги варианти (Sanjar-007): landed cost биринчи циклда
     * юклаган entity'ни қайта select қилмай тўғридан-тўғри узатади - id
     * имзоси бошқа чақирувчилар учун {@link #movement} орқали шу ерга
     * делегат қилади, хулқ айнан.
     */
    public ReceiptValueResult addReceiptValue(StockMovement receipt, BigDecimal amount) {
        if (!receipt.getType().inbound()) {
            throw new BusinessRuleException(BusinessRule.BR_INV_002,
                    "Қиймат фақат кирим ҳаракатига қўшилади: " + receipt.getType());
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException(BusinessRule.BR_INV_004,
                    "Қўшиладиган қиймат мусбат бўлиши шарт");
        }
        UUID warehouseId = receipt.getWarehouse().getId();
        UUID itemId = receipt.getItemId();
        BigDecimal q = receipt.getQuantity();
        BigDecimal delta = amount.divide(q, COST_SCALE, RoundingMode.HALF_UP);

        BigDecimal remaining;
        if (valuationMethod() == InventoryValuationMethod.FIFO) {
            CostLayer layer = layerRepository.findBySourceMovementId(receipt.getId())
                    .orElseThrow(() -> insufficientStock(BigDecimal.ZERO, q));
            remaining = layer.getRemainingQty();
            layer.addUnitCost(delta);
            StockBalance balance = balanceRepository
                    .findByWarehouseIdAndItemId(warehouseId, itemId)
                    .orElseThrow(() -> insufficientStock(BigDecimal.ZERO, q));
            applyFifoBalance(balance, warehouseId, itemId);
        } else {
            StockBalance balance = balanceRepository
                    .findByWarehouseIdAndItemId(warehouseId, itemId).orElse(null);
            // «Эски аввал сотилади»: кейин кирган миқдор бошқа партияники,
            // қолдиқдан айирилади - фақат шундан ортгани шу receipt'дан
            remaining = balance == null ? BigDecimal.ZERO
                    : balance.getQty()
                            .subtract(inboundQtyAfterAnchor(receipt))
                            .max(BigDecimal.ZERO).min(q);
            if (balance != null && balance.getQty().signum() > 0 && remaining.signum() > 0) {
                BigDecimal inventoryShare = remaining.multiply(delta)
                        .setScale(TOTAL_SCALE, RoundingMode.HALF_UP);
                BigDecimal newValue = balance.getQty().multiply(balance.getAvgCost())
                        .add(inventoryShare);
                balance.apply(balance.getQty(),
                        newValue.divide(balance.getQty(), COST_SCALE, RoundingMode.HALF_UP));
            }
        }
        BigDecimal inventoryShare = remaining.multiply(delta)
                .setScale(TOTAL_SCALE, RoundingMode.HALF_UP);
        // COGS улуши аниқ комплемент - яхлитлаш дрейфи бўлмайди
        return new ReceiptValueResult(inventoryShare,
                amount.subtract(inventoryShare), remaining);
    }

    /**
     * {@link #addReceiptValue} нинг аниқ тескариси (landed cost
     * reverse). Гаров: FIFO'да партия қолдиғи ТАҚСИМОТ ПАЙТИДАГИ билан
     * тенг бўлиши шарт (акс ҳолда юкланган қиймат қисман COGS'га кетиб
     * бўлган - GL сторноси билан омбор қиймати ажралиб кетар эди);
     * AVCO'да қолдиқ ўшандагидан кам эмас ВА тақсимотдан кейин шу
     * (item, warehouse)да умуман ҳаракат бўлмаган бўлиши шарт
     * (BR-INV-010, Asrorxoja-001: qty шартининг ўзи етмайди - оралиқ
     * чиқим + янги кирим уни алдаб ўтади, ортиқча айирма бошқа партия
     * қийматидан «ўғирланар» эди). Бузилса BR-INV-003/BR-INV-010 -
     * чақирувчи ўз кодига (BR-LC-006) ўрайди.
     *
     * @param allocatedAt тақсимот ёзилган пайт (аллокация қатори
     *        created_at'и) - AVCO гарови «кейин ҳаракат борми»ни шу
     *        пайтдан бошлаб текширади
     */
    public void removeReceiptValue(UUID movementId, BigDecimal amount,
                                   BigDecimal inventoryShare,
                                   BigDecimal remainingQtyAtAlloc,
                                   java.time.Instant allocatedAt) {
        StockMovement receipt = movement(movementId);
        UUID warehouseId = receipt.getWarehouse().getId();
        UUID itemId = receipt.getItemId();
        BigDecimal q = receipt.getQuantity();
        BigDecimal delta = amount.divide(q, COST_SCALE, RoundingMode.HALF_UP);

        if (valuationMethod() == InventoryValuationMethod.FIFO) {
            CostLayer layer = layerRepository.findBySourceMovementId(movementId)
                    .orElseThrow(() -> insufficientStock(BigDecimal.ZERO, q));
            if (layer.getRemainingQty().compareTo(remainingQtyAtAlloc) != 0) {
                throw insufficientStock(layer.getRemainingQty(), remainingQtyAtAlloc);
            }
            layer.addUnitCost(delta.negate());
            StockBalance balance = balanceRepository
                    .findByWarehouseIdAndItemId(warehouseId, itemId)
                    .orElseThrow(() -> insufficientStock(BigDecimal.ZERO, q));
            applyFifoBalance(balance, warehouseId, itemId);
        } else {
            // Битта инвариант (BR-INV-010): AVCO'да қиймат фақат ҳолат
            // ўзгармаган бўлса аниқ қайтади - FIFO'даги қатъий партия
            // гаровининг AVCO'даги эквиваленти
            requireNoLaterMovements(warehouseId, itemId, allocatedAt, null);
            StockBalance balance = balanceRepository
                    .findByWarehouseIdAndItemId(warehouseId, itemId)
                    .orElseThrow(() -> insufficientStock(BigDecimal.ZERO, remainingQtyAtAlloc));
            if (balance.getQty().compareTo(remainingQtyAtAlloc) < 0) {
                throw insufficientStock(balance.getQty(), remainingQtyAtAlloc);
            }
            if (balance.getQty().signum() > 0 && inventoryShare.signum() > 0) {
                BigDecimal newValue = balance.getQty().multiply(balance.getAvgCost())
                        .subtract(inventoryShare);
                balance.apply(balance.getQty(),
                        newValue.divide(balance.getQty(), COST_SCALE, RoundingMode.HALF_UP)
                                .max(BigDecimal.ZERO));
            }
        }
    }

    // ---- кирим/чиқим umumiy ядроси ----

    /** Кирим бўлаги: миқдор + нарх + партия санаси (FIFO layer учун). */
    private record InChunk(BigDecimal qty, BigDecimal unitCost, LocalDate receivedDate) { }

    /** FIFO ейилиш режасининг битта қадами. */
    private record Take(CostLayer layer, BigDecimal qty) { }

    /**
     * Чиқим режаси: жами хом таннарх + FIFO қадамлари + transfer учун
     * бўлаклар (нарх/сана сақланган ҳолда).
     */
    private record OutPlan(BigDecimal totalRaw, List<Take> takes, List<InChunk> chunks) { }

    /**
     * Кирим ҳаракатини бажаради: movement + balance (+ FIFO layer'лар).
     * Бир нечта бўлак фақат transfer'дан келади - ҳар бўлак ўз
     * нархи/санаси билан алоҳида layer бўлади.
     */
    private StockMovement performIn(MovementType type, Warehouse warehouse,
                                    Warehouse counterpart, UUID itemId,
                                    List<InChunk> chunks, LocalDate date,
                                    String referenceType, UUID referenceId, String memo) {
        BigDecimal qty = BigDecimal.ZERO;
        BigDecimal totalRaw = BigDecimal.ZERO;
        for (InChunk chunk : chunks) {
            qty = qty.add(chunk.qty());
            totalRaw = totalRaw.add(chunk.qty().multiply(chunk.unitCost()));
        }
        BigDecimal unitCost = qty.signum() == 0 ? BigDecimal.ZERO
                : totalRaw.divide(qty, COST_SCALE, RoundingMode.HALF_UP);
        StockMovement movement = movementRepository.save(new StockMovement(
                type, itemId, warehouse, counterpart, qty, unitCost,
                totalRaw.setScale(TOTAL_SCALE, RoundingMode.HALF_UP),
                date, referenceType, referenceId, memo));

        StockBalance balance = balanceRepository
                .findByWarehouseIdAndItemId(warehouse.getId(), itemId)
                .orElseGet(() -> balanceRepository.save(new StockBalance(warehouse, itemId)));

        if (valuationMethod() == InventoryValuationMethod.FIFO) {
            for (InChunk chunk : chunks) {
                layerRepository.save(new CostLayer(warehouse, itemId,
                        chunk.receivedDate(), chunk.unitCost(), chunk.qty(),
                        movement.getId()));
            }
            applyFifoBalance(balance, warehouse.getId(), itemId);
        } else {
            // AVCO: янги ўртача = (эски qty × эски avg + кирим қиймати) / жами qty
            BigDecimal newQty = balance.getQty().add(qty);
            BigDecimal newValue = balance.getQty().multiply(balance.getAvgCost())
                    .add(totalRaw);
            balance.apply(newQty, newValue.divide(newQty, COST_SCALE, RoundingMode.HALF_UP));
        }
        return movement;
    }

    /** Чиқим режасини тузади - таннарх movement сақланишидан ОЛДИН аниқ. */
    private OutPlan planOut(StockBalance balance, UUID warehouseId, UUID itemId,
                            BigDecimal qty) {
        if (valuationMethod() != InventoryValuationMethod.FIFO) {
            BigDecimal totalRaw = qty.multiply(balance.getAvgCost());
            return new OutPlan(totalRaw, List.of(),
                    List.of(new InChunk(qty, balance.getAvgCost(), null)));
        }
        List<CostLayer> layers = layerRepository
                .findByWarehouseIdAndItemIdAndExhaustedFalseOrderByReceivedDateAscIdAsc(
                        warehouseId, itemId);
        List<Take> takes = new ArrayList<>();
        List<InChunk> chunks = new ArrayList<>();
        BigDecimal remaining = qty;
        BigDecimal totalRaw = BigDecimal.ZERO;
        for (CostLayer layer : layers) {
            if (remaining.signum() == 0) {
                break;
            }
            BigDecimal take = layer.getRemainingQty().min(remaining);
            takes.add(new Take(layer, take));
            chunks.add(new InChunk(take, layer.getUnitCost(), layer.getReceivedDate()));
            totalRaw = totalRaw.add(take.multiply(layer.getUnitCost()));
            remaining = remaining.subtract(take);
        }
        if (remaining.signum() > 0) {
            // Balance текшируви ўтган бўлса layer'лар йиғиндиси кам бўлиши
            // мумкин эмас - бўлса маълумот изчиллиги бузилган (аниқ хато)
            throw insufficientStock(qty.subtract(remaining), qty);
        }
        return new OutPlan(totalRaw, takes, chunks);
    }

    /** Чиқим ҳаракатини бажаради: movement + balance (+ FIFO ейилиши). */
    private StockMovement performOut(MovementType type, StockBalance balance,
                                     Warehouse warehouse, Warehouse counterpart,
                                     UUID itemId, BigDecimal qty, LocalDate date,
                                     String referenceType, UUID referenceId,
                                     String memo, OutPlan plan) {
        BigDecimal unitCost = plan.totalRaw()
                .divide(qty, COST_SCALE, RoundingMode.HALF_UP);
        StockMovement movement = movementRepository.save(new StockMovement(
                type, itemId, warehouse, counterpart, qty, unitCost,
                plan.totalRaw().setScale(TOTAL_SCALE, RoundingMode.HALF_UP),
                date, referenceType, referenceId, memo));

        if (valuationMethod() == InventoryValuationMethod.FIFO) {
            for (Take take : plan.takes()) {
                take.layer().consume(take.qty());
                consumptionRepository.save(new CostLayerConsumption(
                        take.layer(), movement, take.qty()));
            }
            applyFifoBalance(balance, warehouse.getId(), itemId);
        } else {
            // AVCO: ўртача чиқимда ўзгармайди - фақат миқдор камаяди
            balance.apply(balance.getQty().subtract(qty), balance.getAvgCost());
        }
        return movement;
    }

    /**
     * FIFO режимида balance (qty, avg) қолган партиялардан қайта
     * ҳисобланади - avg_cost «маълумот учун» аниқ қолади (spec),
     * қолдиқлар экрани қийматни тўғри кўрсатади.
     */
    private void applyFifoBalance(StockBalance balance, UUID warehouseId, UUID itemId) {
        List<CostLayer> layers = layerRepository
                .findByWarehouseIdAndItemIdAndExhaustedFalseOrderByReceivedDateAscIdAsc(
                        warehouseId, itemId);
        BigDecimal qty = BigDecimal.ZERO;
        BigDecimal value = BigDecimal.ZERO;
        for (CostLayer layer : layers) {
            qty = qty.add(layer.getRemainingQty());
            value = value.add(layer.getRemainingQty().multiply(layer.getUnitCost()));
        }
        BigDecimal avg = qty.signum() == 0
                ? BigDecimal.ZERO
                : value.divide(qty, COST_SCALE, RoundingMode.HALF_UP);
        balance.apply(qty, avg);
    }

    // ---- adjustment GL ----

    /**
     * Adjustment проводкаси (posting-rules «Омбор»): кўпайиш -
     * INVENTORY Dt / shrinkage Cr, камайиш - тескари. INVENTORY томони
     * item'нинг ўз asset счёти (QBO услуби), shrinkage - тизим счёти.
     * Нол қиймат - GL'га ёзилмайди (миқдор ҳаракати ўз кучида).
     */
    private void postAdjustment(StockMovement movement, Item item, boolean increase) {
        if (movement.getTotalCost().signum() == 0) {
            return;
        }
        UUID inventoryAccountId = item.getInventoryAssetAccountId();
        UUID shrinkageAccountId = accountService
                .requireSystemAccountId(AccountDetailType.OTHER_COSTS_OF_SERVICE_COS);
        Money money = Money.ofBase(movement.getTotalCost(), settingsService.homeCurrency());
        UUID debitAccount = increase ? inventoryAccountId : shrinkageAccountId;
        UUID creditAccount = increase ? shrinkageAccountId : inventoryAccountId;
        // Dimension'лар (warehouse, item) сатрларга ёзилади - кейинги
        // ҳисоботлар омбор/товар кесимида GL'ни ажрата олади
        List<JournalEntryRequest.Line> lines = List.of(
                new JournalEntryRequest.Line(debitAccount, money, null,
                        null, movement.getWarehouse().getId(), movement.getItemId(), null),
                new JournalEntryRequest.Line(creditAccount, null, money,
                        null, movement.getWarehouse().getId(), movement.getItemId(), null));
        postingService.createAndPost(new JournalEntryRequest(
                movement.getMovementDate(),
                "Инвентаризация: " + item.getName() + " (" + movement.getWarehouse().getName() + ")",
                SOURCE_MODULE, movement.getId(), lines));
    }

    // ---- умумий валидациялар ----

    /** BR-INV-007 ечими: кўпайиш нархи - берилган ёки жорий қиймат. */
    private BigDecimal resolveAdjustCost(UUID warehouseId, UUID itemId, BigDecimal given) {
        if (given != null) {
            requireNonNegativeCost(given);
            return given;
        }
        if (valuationMethod() == InventoryValuationMethod.FIFO) {
            List<CostLayer> layers = layerRepository
                    .findByWarehouseIdAndItemIdAndExhaustedFalseOrderByReceivedDateAscIdAsc(
                            warehouseId, itemId);
            if (!layers.isEmpty()) {
                // Охирги фаол партия нархи (spec, «Қатъий қарорлар»)
                return layers.get(layers.size() - 1).getUnitCost();
            }
        } else {
            StockBalance balance = balanceRepository
                    .findByWarehouseIdAndItemId(warehouseId, itemId).orElse(null);
            if (balance != null && balance.getQty().signum() > 0) {
                return balance.getAvgCost();
            }
        }
        throw new BusinessRuleException(BusinessRule.BR_INV_007,
                "Қолдиқ нол - кўпайиш тузатишига нарх киритилиши шарт");
    }

    /**
     * BR-INV-010 гарови - «қиймат ортга қайтариш фақат кейин ҳаракат
     * бўлмаганда» инвариантининг ягона жойи (Beruniy-003 ва
     * Asrorxoja-001 иккаласи шу орқали ёпилади). Хронология created_at
     * бўйича: valuation ҳаракатларни айнан ёзилиш тартибида қўллаган.
     * Instant варианти - landed cost reverse учун (anchor movement йўқ,
     * таянч - аллокация қаторининг ёзилиш пайти).
     *
     * @param sameDocumentReferenceId берилса ўша манба ҳужжатнинг ўз
     *        ҳаракатлари ҳисобга олинмайди: кўп сатрли bill'нинг
     *        receipt'лари ва уларнинг қайтаришлари битта reverse
     *        доирасида биргаликда ортга олинади - улар инвариантни
     *        бузмайди
     */
    private void requireNoLaterMovements(UUID warehouseId, UUID itemId,
                                         java.time.Instant since,
                                         UUID sameDocumentReferenceId) {
        for (StockMovement later : movementRepository
                .findByWarehouseIdAndItemIdAndCreatedAtAfter(warehouseId, itemId, since)) {
            if (sameDocumentReferenceId != null
                    && sameDocumentReferenceId.equals(later.getReferenceId())) {
                continue;
            }
            throw new BusinessRuleException(BusinessRule.BR_INV_010,
                    "Қийматни ортга қайтариб бўлмайди: кейин ҳаракат бор ("
                    + later.getType() + ", " + later.getMovementDate()
                    + ") - тузатиш adjustment орқали");
        }
    }

    /**
     * BR-INV-010 гарови (anchor варианти) - кирим reverse учун.
     * created_at АЙНАН ТЕНГ бўлиб қолиши мумкин (бир транзакцияда тез
     * кетма-кет ёзилган ҳаракатлар - тест флейки шуни кўрсатди), шунда
     * UUIDv7 id тартиби ҳал қилади: id монотоник, ёзилиш тартибини
     * аниқ акс эттиради.
     */
    private void requireNoMovementsAfterAnchor(StockMovement anchor,
                                               UUID sameDocumentReferenceId) {
        for (StockMovement later : movementsAfterAnchor(anchor)) {
            if (sameDocumentReferenceId != null
                    && sameDocumentReferenceId.equals(later.getReferenceId())) {
                continue;
            }
            throw new BusinessRuleException(BusinessRule.BR_INV_010,
                    "Қийматни ортга қайтариб бўлмайди: кейин ҳаракат бор ("
                    + later.getType() + ", " + later.getMovementDate()
                    + ") - тузатиш adjustment орқали");
        }
    }

    /**
     * Anchor'дан КЕЙИН ёзилган ҳаракатлар: created_at катта, тенг бўлса
     * id (UUIDv7, монотоник) катта. Anchor'нинг ўзи киритилмайди.
     */
    private List<StockMovement> movementsAfterAnchor(StockMovement anchor) {
        List<StockMovement> result = new ArrayList<>();
        for (StockMovement candidate : movementRepository
                .findByWarehouseIdAndItemIdAndCreatedAtGreaterThanEqual(
                        anchor.getWarehouse().getId(), anchor.getItemId(),
                        anchor.getCreatedAt())) {
            if (candidate.getId().equals(anchor.getId())) {
                continue;
            }
            if (candidate.getCreatedAt().isAfter(anchor.getCreatedAt())
                    || candidate.getId().compareTo(anchor.getId()) > 0) {
                result.add(candidate);
            }
        }
        return result;
    }

    /**
     * Receipt'дан КЕЙИН кирган миқдор (барча inbound турлар) - AVCO
     * «эски аввал сотилади» фаразида шу receipt'дан қолганини баҳолаш
     * учун (Beruniy-004). Кейинлик anchor қоидаси билан: created_at
     * тенг бўлса UUIDv7 id тартиби ҳал қилади.
     */
    private BigDecimal inboundQtyAfterAnchor(StockMovement receipt) {
        BigDecimal sum = BigDecimal.ZERO;
        for (StockMovement later : movementsAfterAnchor(receipt)) {
            if (later.getType().inbound()) {
                sum = sum.add(later.getQuantity());
            }
        }
        return sum;
    }

    /** BR-INV-003: қолдиқ етарлилигини текшириб balance қайтаради. */
    private StockBalance requireSufficient(UUID warehouseId, UUID itemId, BigDecimal qty) {
        StockBalance balance = balanceRepository
                .findByWarehouseIdAndItemId(warehouseId, itemId)
                .orElseThrow(() -> insufficientStock(BigDecimal.ZERO, qty));
        if (balance.getQty().compareTo(qty) < 0) {
            throw insufficientStock(balance.getQty(), qty);
        }
        return balance;
    }

    /** BR-INV-001: фақат INVENTORY типдаги item ҳаракатланади. */
    private Item requireInventoryItem(UUID itemId) {
        return requireInventoryType(itemService.get(itemId));
    }

    /**
     * BR-INV-001 - олдиндан юкланган батч Map варианти (Sanjar-008,
     * ҳужжатли актлар): топилмаса {@link NotFoundException} (аввалги
     * get() хулқи айнан), тур гарови бир хил.
     */
    private Item requireInventoryItem(UUID itemId, Map<UUID, Item> items) {
        Item item = items.get(itemId);
        if (item == null) {
            throw new NotFoundException("Item топилмади: " + itemId);
        }
        return requireInventoryType(item);
    }

    /** BR-INV-001 тур гарови - иккала requireInventoryItem варианти учун. */
    private Item requireInventoryType(Item item) {
        if (item.getType() != ItemType.INVENTORY) {
            throw new BusinessRuleException(BusinessRule.BR_INV_001,
                    "Омбор ҳаракати фақат INVENTORY типдаги item учун: «"
                    + item.getName() + "» - " + item.getType());
        }
        return item;
    }

    /** BR-INV-006: омбор мавжуд ва фаол бўлиши шарт. */
    private Warehouse requireActiveWarehouse(UUID warehouseId) {
        Warehouse warehouse = warehouseService.get(warehouseId);
        if (!warehouse.isActive()) {
            throw new BusinessRuleException(BusinessRule.BR_INV_006,
                    "Нофаол омборга янги ҳаракат тақиқ: " + warehouse.getName());
        }
        return warehouse;
    }

    /** BR-INV-002: миқдор мусбат. */
    private void requirePositiveQty(BigDecimal qty) {
        if (qty == null || qty.signum() <= 0) {
            throw new BusinessRuleException(BusinessRule.BR_INV_002,
                    "Миқдор мусбат сон бўлиши шарт");
        }
    }

    /** BR-INV-004: unit cost манфий эмас. */
    private void requireNonNegativeCost(BigDecimal unitCost) {
        if (unitCost == null || unitCost.signum() < 0) {
            throw new BusinessRuleException(BusinessRule.BR_INV_004,
                    "Unit cost манфий бўлмаган сон бўлиши шарт");
        }
    }

    /** BR-INV-008: сана шарт. */
    private void requireDate(LocalDate date) {
        if (date == null) {
            throw new BusinessRuleException(BusinessRule.BR_INV_008,
                    "Ҳаракат санаси киритилиши шарт");
        }
    }

    /** BR-INV-003 хатоси - мавжуд/сўралган миқдорлар билан. */
    private BusinessRuleException insufficientStock(BigDecimal available, BigDecimal requested) {
        return new BusinessRuleException(BusinessRule.BR_INV_003,
                "Омборда етарли қолдиқ йўқ: мавжуд " + available.stripTrailingZeros().toPlainString()
                + ", сўралди " + requested.stripTrailingZeros().toPlainString());
    }

    /** Жорий valuation методи - ҳар чақириқда созламадан ўқилади. */
    private InventoryValuationMethod valuationMethod() {
        return settingsService.valuationMethod();
    }
}
