package com.averpo.erp.item.service;

import com.averpo.erp.shared.Strings;
import com.averpo.erp.shared.exception.BusinessRule;

import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.domain.ItemCategory;
import com.averpo.erp.item.domain.ItemType;
import com.averpo.erp.item.domain.Unit;
import com.averpo.erp.item.repo.ItemCategoryRepository;
import com.averpo.erp.item.repo.ItemRepository;
import com.averpo.erp.item.repo.UnitRepository;
import com.averpo.erp.ledger.domain.Account;
import com.averpo.erp.ledger.domain.AccountDetailType;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Item CRUD - счёт боғлашлар ledger'нинг public AccountService'и
 * орқали текширилади (ТЕМИР ҚОИДА №6: repository'га тегиш тақиқ).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ItemService {

    /** Item репозиторийси. */
    private final ItemRepository repository;

    /** Категория текшируви учун. */
    private final ItemCategoryRepository categoryRepository;

    /** Бирлик текшируви учун. */
    private final UnitRepository unitRepository;

    /** Ledger public API - счёт валидацияси ва default'лар. */
    private final AccountService accountService;

    /** ҚҚС ставкаси мавжудлиги (item default'лари) - tax модули public API'си. */
    private final com.averpo.erp.tax.service.TaxRateService taxRateService;

    /**
     * Битта item формаси маълумотлари - create/update учун умумий.
     * purchaseUnitId/salesUnitId - ҳужжат сатрларидаги default бирликлар
     * (docs/modules/uom.md, BR-ITM-012: base билан бир гуруҳдан).
     */
    public record ItemData(String name, String sku, UUID categoryId, UUID unitId,
                           BigDecimal salesPrice, String salesDescription,
                           UUID incomeAccountId, BigDecimal purchaseCost,
                           String purchaseDescription, UUID expenseAccountId,
                           UUID inventoryAssetAccountId, BigDecimal reorderPoint,
                           UUID purchaseUnitId, UUID salesUnitId,
                           UUID salesTaxRateId, UUID purchaseTaxRateId) {

        /** Эски 12 майдонли имзо - default бирлик/солиқларсиз. */
        public ItemData(String name, String sku, UUID categoryId, UUID unitId,
                        BigDecimal salesPrice, String salesDescription,
                        UUID incomeAccountId, BigDecimal purchaseCost,
                        String purchaseDescription, UUID expenseAccountId,
                        UUID inventoryAssetAccountId, BigDecimal reorderPoint) {
            this(name, sku, categoryId, unitId, salesPrice, salesDescription,
                    incomeAccountId, purchaseCost, purchaseDescription,
                    expenseAccountId, inventoryAssetAccountId, reorderPoint,
                    null, null, null, null);
        }

        /** 14 майдонли имзо - UoM бор, солиқ default'ларсиз. */
        public ItemData(String name, String sku, UUID categoryId, UUID unitId,
                        BigDecimal salesPrice, String salesDescription,
                        UUID incomeAccountId, BigDecimal purchaseCost,
                        String purchaseDescription, UUID expenseAccountId,
                        UUID inventoryAssetAccountId, BigDecimal reorderPoint,
                        UUID purchaseUnitId, UUID salesUnitId) {
            this(name, sku, categoryId, unitId, salesPrice, salesDescription,
                    incomeAccountId, purchaseCost, purchaseDescription,
                    expenseAccountId, inventoryAssetAccountId, reorderPoint,
                    purchaseUnitId, salesUnitId, null, null);
        }
    }

    /**
     * Форма олдиндан тўлдирадиган default счётлар - detail type орқали
     * (QBO услуби). Топилмаса null - фойдаланувчи ўзи танлайди.
     *
     * @param income         SALES_OF_PRODUCT_INCOME (SERVICE: SERVICE_FEE_INCOME)
     * @param expense        SUPPLIES_MATERIALS_COGS ёки OTHER_MISCELLANEOUS_SERVICE_COST
     * @param inventoryAsset INVENTORY detail type счёти
     */
    public record DefaultAccounts(UUID income, UUID expense, UUID inventoryAsset) { }

    /** Id бўйича топади ёки тушунарли хато отади. */
    @Transactional(readOnly = true)
    public Item get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Item топилмади: " + id));
    }

    /**
     * Сўралган id'лар бўйича тўлиқ item'лар битта IN сўровда (Arbitr-045
     * findAllById нақши) - ҳужжат service'лари сатр-циклда item'ни
     * қайта-қайта {@link #get} билан юкламасин (SalesReceipt Beruniy-035:
     * бир хил item такрорланса ҳам битта round-trip). Топилмаганлар
     * рўйхатда бўлмайди; мавжудликни чақирувчи ўз сатр хатоси билан текширади.
     */
    @Transactional(readOnly = true)
    public List<Item> findAllById(Collection<UUID> ids) {
        return ids.isEmpty() ? List.of() : repository.findAllById(ids);
    }

    /**
     * Каталог рўйхати филтри (Arbitr-068, list-filters.md): тур ихтиёрий;
     * active - TRUE фақат фаол / FALSE фақат нофаол / null ҳаммаси;
     * q - ном/SKU contains (катта-кичик фарқсиз, кирилл ҳам).
     */
    public record ListFilter(ItemType type, Boolean active, String q) {
    }

    /**
     * Рўйхат экрани - тўлиқ филтр (Arbitr-068): тур/фаоллик/матн битта
     * Specification'да (audit услуби, ListSpecs бўлаклари), ном тартибида.
     * category (LAZY) ва unit LEFT fetch - шаблонда lazy хатоси ва N+1
     * бўлмасин (аввалги @EntityGraph нақшига тенг; List йўли - count
     * сўрови йўқ, to-one fetch хавфсиз).
     */
    @Transactional(readOnly = true)
    public List<Item> list(ListFilter filter) {
        org.springframework.data.jpa.domain.Specification<Item> withRefs =
                (root, query, cb) -> {
                    root.fetch("category", jakarta.persistence.criteria.JoinType.LEFT);
                    root.fetch("unit", jakarta.persistence.criteria.JoinType.LEFT);
                    return null;
                };
        return repository.findAll(org.springframework.data.jpa.domain.Specification.allOf(
                        withRefs,
                        com.averpo.erp.shared.repo.ListSpecs.eq("type", filter.type()),
                        com.averpo.erp.shared.repo.ListSpecs.eq("active", filter.active()),
                        com.averpo.erp.shared.repo.ListSpecs.textContains(filter.q(),
                                "name", "sku")),
                org.springframework.data.domain.Sort.by("name"));
    }

    /**
     * Рўйхат: ихтиёрий тип филтри ва нофаоллар билан - эски имзо
     * (import ва бошқа чақирувчилар учун); янги филтрлига делегат.
     */
    @Transactional(readOnly = true)
    public List<Item> list(ItemType type, boolean includeInactive) {
        return list(new ListFilter(type, includeInactive ? null : Boolean.TRUE, null));
    }

    /**
     * Енгил item ссылкаси (id + ном + бирлик номи) - ном хариталари ва
     * select'лар учун; entity ва унинг EAGER боғлари хотирага
     * юкланмайди (Beruniy-018 overfetch'га қарши).
     */
    public record ItemRef(UUID id, String name, String unitName) { }

    /**
     * Сўралган id'ларнинг енгил ссылкалари битта IN сўровда.
     * Фаоллик филтрланмайди - тарихий сатрларда нофаол item номи ҳам
     * кўрсатилиши керак.
     */
    @Transactional(readOnly = true)
    public List<ItemRef> refsByIds(Collection<UUID> ids) {
        return ids.isEmpty() ? List.of() : repository.findRefsByIdIn(ids);
    }

    /**
     * id → ном харитаси битта IN сўровда (ARBITR-105б, Ulugbek-003 §1):
     * view/рўйхат name-map'лари бутун каталогни юкламасин. Топилмаган id
     * харитада бўлмайди - чақирувчи {@code getOrDefault} билан ўқийди.
     */
    @Transactional(readOnly = true)
    public java.util.Map<UUID, String> namesByIds(Collection<UUID> ids) {
        java.util.Map<UUID, String> names = new java.util.HashMap<>();
        for (ItemRef ref : refsByIds(ids)) {
            names.put(ref.id(), ref.name());
        }
        return names;
    }

    /** Фаол item'ларнинг енгил рўйхати - select учун, ном тартибида. */
    @Transactional(readOnly = true)
    public List<ItemRef> activeRefs() {
        return repository.findActiveRefs();
    }

    /** Янги item формаси учун тип бўйича default счётлар. */
    @Transactional(readOnly = true)
    public DefaultAccounts defaultsFor(ItemType type) {
        AccountDetailType incomeType = type == ItemType.SERVICE
                ? AccountDetailType.SERVICE_FEE_INCOME
                : AccountDetailType.SALES_OF_PRODUCT_INCOME;
        AccountDetailType expenseType = type == ItemType.INVENTORY
                ? AccountDetailType.SUPPLIES_MATERIALS_COGS
                : AccountDetailType.OTHER_MISCELLANEOUS_SERVICE_COST;
        return new DefaultAccounts(
                accountService.findSystemAccount(incomeType).map(Account::getId).orElse(null),
                accountService.findSystemAccount(expenseType).map(Account::getId).orElse(null),
                accountService.findSystemAccount(AccountDetailType.INVENTORY)
                        .map(Account::getId).orElse(null));
    }

    /**
     * Янги item яратади.
     *
     * @throws BusinessRuleException BR-ITM-* кодлари билан - ном/SKU банд,
     *         счётлар нотўғри ёки INVENTORY типда inventory asset счёти
     *         йўқ бўлса (тўлиқ рўйхат: docs/business-rules.md)
     */
    public Item create(ItemType type, ItemData data) {
        validate(type, data, null);
        Item item = new Item(type, data.name().strip(),
                data.incomeAccountId(), data.expenseAccountId());
        apply(item, data);
        return repository.save(item);
    }

    /** Item'ни янгилайди - тип ўзгармайди (spec'даги қарор). */
    public Item update(UUID id, ItemData data, boolean active) {
        Item item = get(id);
        validate(item.getType(), data, id);
        apply(item, data);
        item.setActive(active);
        return item;
    }

    /** Умумий валидация: ном/SKU unique, счётлар, INVENTORY талаблари. */
    private void validate(ItemType type, ItemData data, UUID selfId) {
        if (data.name() == null || data.name().isBlank()) {
            throw new BusinessRuleException(BusinessRule.BR_ITM_001, "Ном бўш бўлиши мумкин эмас");
        }
        repository.findByName(data.name().strip())
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw new BusinessRuleException(BusinessRule.BR_ITM_002, "Бу ном банд: " + data.name().strip());
                });
        String sku = Strings.blankToNull(data.sku());
        if (sku != null) {
            repository.findBySku(sku)
                    .filter(other -> !other.getId().equals(selfId))
                    .ifPresent(other -> {
                        throw new BusinessRuleException(BusinessRule.BR_ITM_003, "Бу SKU банд: " + sku);
                    });
        }
        // Classification текшируви: банк счётини «даромад счёти» қилиб
        // бўлмасин - QBO ҳам item счётларини тур бўйича чеклайди
        Account income = requirePostableAccount(data.incomeAccountId(), "Даромад счёти");
        if (income.getClassification()
                != com.averpo.erp.ledger.domain.AccountClassification.REVENUE) {
            throw new BusinessRuleException(BusinessRule.BR_ITM_004, "Даромад счёти REVENUE туркумидан бўлиши шарт: " + income.getName());
        }
        Account expense = requirePostableAccount(data.expenseAccountId(), "Харажат счёти");
        if (expense.getClassification()
                != com.averpo.erp.ledger.domain.AccountClassification.EXPENSE) {
            throw new BusinessRuleException(BusinessRule.BR_ITM_005, "Харажат счёти EXPENSE туркумидан бўлиши шарт: " + expense.getName());
        }
        if (type == ItemType.INVENTORY) {
            if (data.inventoryAssetAccountId() == null) {
                throw new BusinessRuleException(BusinessRule.BR_ITM_006, "INVENTORY тип учун inventory asset счёти шарт");
            }
            Account asset = requirePostableAccount(
                    data.inventoryAssetAccountId(), "Inventory asset счёти");
            if (asset.getDetailType() != AccountDetailType.INVENTORY) {
                throw new BusinessRuleException(BusinessRule.BR_ITM_007, "Inventory asset счёти detail type'и INVENTORY бўлиши шарт: "
                        + asset.getName());
            }
        }
        if (data.salesPrice() != null && data.salesPrice().signum() < 0) {
            throw new BusinessRuleException(BusinessRule.BR_ITM_009, "Сотув нархи манфий бўлолмайди");
        }
        if (data.purchaseCost() != null && data.purchaseCost().signum() < 0) {
            throw new BusinessRuleException(BusinessRule.BR_ITM_009, "Харид нархи манфий бўлолмайди");
        }
        requireSameGroupAsBase(data, data.purchaseUnitId(), "Харид бирлиги");
        requireSameGroupAsBase(data, data.salesUnitId(), "Сотув бирлиги");
    }

    /**
     * BR-ITM-012: default бирлик item base бирлиги билан бир гуруҳдан.
     * Base танланмаган ёки гуруҳсиз бўлса default қўйиб бўлмайди -
     * конверсиясиз default маъносиз (docs/modules/uom.md).
     */
    private void requireSameGroupAsBase(ItemData data, UUID defaultUnitId, String label) {
        if (defaultUnitId == null) {
            return;
        }
        if (data.unitId() == null) {
            throw new BusinessRuleException(BusinessRule.BR_ITM_012,
                    label + " учун аввал асосий бирлик танланиши керак");
        }
        Unit base = unitRepository.findById(data.unitId())
                .orElseThrow(() -> new NotFoundException("Бирлик топилмади"));
        Unit defaultUnit = unitRepository.findById(defaultUnitId)
                .orElseThrow(() -> new NotFoundException("Бирлик топилмади"));
        if (base.getGroup() == null || defaultUnit.getGroup() == null
                || !base.getGroup().getId().equals(defaultUnit.getGroup().getId())) {
            throw new BusinessRuleException(BusinessRule.BR_ITM_012,
                    label + " base бирлик билан бир гуруҳдан бўлиши керак: «"
                    + defaultUnit.getName() + "»");
        }
    }

    /** Счёт мавжуд, фаол ва postable эканини текширади. */
    private Account requirePostableAccount(UUID accountId, String label) {
        if (accountId == null) {
            throw new BusinessRuleException(BusinessRule.BR_ITM_008, label + " танланиши шарт");
        }
        Account account = accountService.get(accountId);
        if (!account.isActive() || !account.isPostable()) {
            throw new BusinessRuleException(BusinessRule.BR_ITM_008, label + " фаол ва postable бўлиши шарт: " + account.getName());
        }
        return account;
    }

    /** Форма маълумотларини entity'га кўчиради. */
    private void apply(Item item, ItemData data) {
        ItemCategory category = data.categoryId() == null ? null
                : categoryRepository.findById(data.categoryId())
                        .orElseThrow(() -> new NotFoundException("Категория топилмади"));
        Unit unit = resolveUnit(data.unitId());
        item.update(data.name().strip(), Strings.blankToNull(data.sku()), category, unit,
                resolveUnit(data.purchaseUnitId()), resolveUnit(data.salesUnitId()),
                data.salesPrice(), Strings.blankToNull(data.salesDescription()),
                data.incomeAccountId(), data.purchaseCost(),
                Strings.blankToNull(data.purchaseDescription()), data.expenseAccountId(),
                item.getType() == ItemType.INVENTORY ? data.inventoryAssetAccountId() : null,
                data.reorderPoint());
        // Default ҚҚС ставкалари (docs/modules/tax.md): мавжудлик
        // текшируви - null qoldirilsa солиқсиз (BR-TAX-004 tampered'дан ҳимоя)
        item.applyTaxDefaults(
                requireTaxRateOrNull(data.salesTaxRateId()),
                requireTaxRateOrNull(data.purchaseTaxRateId()));
    }

    /** Танланган ставка мавжудлигини текширади (BR-TAX-004); null - солиқсиз. */
    private UUID requireTaxRateOrNull(UUID taxRateId) {
        if (taxRateId != null) {
            taxRateService.get(taxRateId); // NotFound → tampered select
        }
        return taxRateId;
    }

    /** Бирликни id бўйича юклайди; null - танланмаган. */
    private Unit resolveUnit(UUID unitId) {
        return unitId == null ? null
                : unitRepository.findById(unitId)
                        .orElseThrow(() -> new NotFoundException("Бирлик топилмади"));
    }

}
