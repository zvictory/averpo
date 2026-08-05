package com.averpo.erp.item.service;

import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.domain.Unit;
import com.averpo.erp.item.domain.UnitGroup;
import com.averpo.erp.item.repo.UnitGroupRepository;
import com.averpo.erp.item.repo.UnitRepository;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import com.averpo.erp.shared.exception.NotFoundException;
import com.averpo.erp.shared.service.DefaultUnitsInstaller;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Ўлчов бирликлари ва UoM гуруҳлари CRUD + конверсия ёрдамчилари
 * (docs/modules/uom.md). Инвариантлар шу ерда: гуруҳда айнан битта
 * base (BR-UOM-004), base factor 1 (BR-UOM-003), factor мусбат
 * (BR-UOM-002), гуруҳсизга factor/base йўқ (BR-UOM-005), конверсия
 * фақат бир гуруҳ ичида (BR-UOM-006).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class UnitService implements DefaultUnitsInstaller {

    /** factorBetween бўлинмасининг scale'i - курс scale'i билан бир хил. */
    private static final int FACTOR_SCALE = 12;

    /**
     * Стандарт бирлик - гуруҳдаги ном ва base'га нисбатан factor
     * ({@code null} - гуруҳнинг base бирлиги, ҳар гуруҳда биринчиси).
     */
    private record DefaultUnit(String name, BigDecimal factor) {
        /** base бирлик - factor кейин {@code addUnitToGroup}'да автоматик 1. */
        static DefaultUnit base(String name) {
            return new DefaultUnit(name, null);
        }

        /** base'дан бошқа бирлик: 1 шу бирлик = factor × base. */
        static DefaultUnit of(String name, String factor) {
            return new DefaultUnit(name, new BigDecimal(factor));
        }
    }

    /** Стандарт гуруҳ - ном ва бирликлари (биринчиси - base). */
    private record DefaultGroup(String name, List<DefaultUnit> units) { }

    /**
     * Янги ўрнатиш/заводга қайтаришда тайёр келадиган стандарт UOM
     * тўплами (DEC-147). Кичик бизнес амалиётида энг кўп учрайдиган
     * ўлчовлар - фойдаланувчи буларни қўлда киритмасин (керак бўлмаса
     * ўчиради/нофаоллайди). Мавжуд seed бирликлар (дона, кг, литр, метр,
     * соат) айнан шу номлар билан тегишли гуруҳга ютилади (дубликат
     * яратилмайди); «хизмат» гуруҳсиз қолади (ўлчов гуруҳига кирмайди).
     */
    private static final List<DefaultGroup> DEFAULT_GROUPS = List.of(
            new DefaultGroup("Дона", List.of(
                    DefaultUnit.base("дона"))),
            new DefaultGroup("Оғирлик", List.of(
                    DefaultUnit.base("кг"),
                    DefaultUnit.of("г", "0.001"),
                    DefaultUnit.of("тонна", "1000"))),
            new DefaultGroup("Узунлик", List.of(
                    DefaultUnit.base("метр"),
                    DefaultUnit.of("см", "0.01"),
                    DefaultUnit.of("мм", "0.001"))),
            new DefaultGroup("Ҳажм", List.of(
                    DefaultUnit.base("литр"),
                    DefaultUnit.of("мл", "0.001"),
                    DefaultUnit.of("м³", "1000"))),
            new DefaultGroup("Юза", List.of(
                    DefaultUnit.base("м²"),
                    DefaultUnit.of("см²", "0.0001"))),
            new DefaultGroup("Вақт", List.of(
                    DefaultUnit.base("соат"),
                    DefaultUnit.of("кун", "24"))));

    /** Бирликлар репозиторийси. */
    private final UnitRepository repository;

    /** Гуруҳлар репозиторийси. */
    private final UnitGroupRepository groupRepository;

    // ---- бирликлар ----

    /** Id бўйича топади ёки тушунарли хато отади. */
    @Transactional(readOnly = true)
    public Unit get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Бирлик топилмади: " + id));
    }

    /** Item формасидаги select учун фаоллар. */
    @Transactional(readOnly = true)
    public List<Unit> activeUnits() {
        return repository.findByActiveTrueOrderByName();
    }

    /** Созламалар экрани учун ҳаммаси. */
    @Transactional(readOnly = true)
    public List<Unit> all() {
        return repository.findAllByOrderByName();
    }

    /** Янги гуруҳсиз бирлик (эски оқим - конверсиясиз). */
    public Unit create(String name) {
        return create(name, null, null, false);
    }

    /**
     * Янги бирлик - гуруҳ/factor/base билан.
     *
     * @param factor 1 шу бирлик = factor × base; base'да null/1
     * @throws BusinessRuleException BR-UNT-001, BR-UOM-002..005
     */
    public Unit create(String name, UUID groupId, BigDecimal factor, boolean base) {
        requireFreeName(name, null);
        Unit unit = new Unit(name.strip());
        applyGrouping(unit, groupId, factor, base, null);
        return repository.save(unit);
    }

    /** Ном/фаолликни янгилайди - гуруҳ майдонларига тегмайди (эски имзо). */
    public Unit update(UUID id, String name, boolean active) {
        Unit unit = get(id);
        requireFreeName(name, id);
        unit.update(name.strip(), active);
        return unit;
    }

    /**
     * Тўлиқ янгилаш - гуруҳ/factor/base билан.
     *
     * @throws BusinessRuleException BR-UNT-001, BR-UOM-002..005
     */
    public Unit update(UUID id, String name, boolean active,
                       UUID groupId, BigDecimal factor, boolean base) {
        Unit unit = get(id);
        requireFreeName(name, id);
        unit.update(name.strip(), active);
        applyGrouping(unit, groupId, factor, base, unit);
        return unit;
    }

    /**
     * Фақат гуруҳ майдонларини ўзгартиради (ном/фаолликка тегмайди) -
     * гуруҳлар саҳифаси учун: гуруҳга киритиш, factor ўзгартириш,
     * гуруҳдан чиқариш (groupId null). Инвариантлар applyGrouping'да
     * (BR-UOM-002..005, base «етим» қолдирмайди).
     */
    public Unit assignGroup(UUID unitId, UUID groupId, BigDecimal factor, boolean base) {
        Unit unit = get(unitId);
        applyGrouping(unit, groupId, factor, base, unit);
        return unit;
    }

    /**
     * Гуруҳга бирлик қўшишнинг СОДДА йўли (гуруҳлар экрани, битта
     * форма): ном бўйича - мавжуд гуруҳсиз бирлик бўлса гуруҳга киради,
     * бўлмаса янгиси яратилади. Base АВТОМАТИК: гуруҳ бўш бўлса шу
     * бирлик base бўлади (factor 1, киритилган factor эътиборсиз),
     * акс ҳолда factor шарт (BR-UOM-002). Бошқа гуруҳдаги бирлик
     * номи берилса аниқ хато - жимгина кўчирилмайди.
     */
    public Unit addUnitToGroup(UUID groupId, String name, BigDecimal factor) {
        UnitGroup group = getGroup(groupId);
        boolean base = repository.findByGroupIdOrderByBaseDescNameAsc(groupId).isEmpty();
        Unit existing = name == null ? null
                : repository.findByName(name.strip()).orElse(null);
        if (existing == null) {
            return create(name, groupId, base ? null : factor, base);
        }
        if (existing.getGroup() != null) {
            throw new BusinessRuleException(BusinessRule.BR_UOM_006,
                    "«" + existing.getName() + "» аллақачон «"
                    + existing.getGroup().getName() + "» гуруҳида");
        }
        applyGrouping(existing, groupId, base ? null : factor, base, existing);
        return existing;
    }

    // ---- гуруҳлар ----

    /** Id бўйича топади ёки тушунарли хато отади. */
    @Transactional(readOnly = true)
    public UnitGroup getGroup(UUID id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Гуруҳ топилмади: " + id));
    }

    /** Созламалар экрани учун гуруҳлар. */
    @Transactional(readOnly = true)
    public List<UnitGroup> groups() {
        return groupRepository.findAllByOrderByName();
    }

    /** Гуруҳ бирликлари (base биринчи) - экран ва тестлар учун. */
    @Transactional(readOnly = true)
    public List<Unit> groupUnits(UUID groupId) {
        return repository.findByGroupIdOrderByBaseDescNameAsc(groupId);
    }

    /** Янги гуруҳ - ном unique (BR-UOM-001). */
    public UnitGroup createGroup(String name) {
        requireFreeGroupName(name, null);
        return groupRepository.save(new UnitGroup(name.strip()));
    }

    /** Гуруҳ номини янгилайди (BR-UOM-001). */
    public UnitGroup renameGroup(UUID id, String name) {
        UnitGroup group = getGroup(id);
        requireFreeGroupName(name, id);
        group.rename(name.strip());
        return group;
    }

    /**
     * Стандарт UOM гуруҳларини ({@link #DEFAULT_GROUPS}) ўрнатади -
     * порт имплементацияси (DEC-147, {@link DefaultUnitsInstaller}).
     *
     * <p>Idempotent - гуруҳ НОМИ мавжуд бўлса бутун гуруҳ ўтказиб
     * юборилади (фойдаланувчи ўзгартиргани/ўчиргани бузилмайди, такрор
     * яратилмайди). Ҳар гуруҳ ичида {@link #addUnitToGroup} ишлатилади:
     * у ном бўйича мавжуд ГУРУҲСИЗ бирликни (seed - дона/кг/литр/метр/
     * соат) гуруҳга ютади, йўғини яратади - шунда {@code unit.name}
     * unique шарти бузилмайди. Биринчи бирлик автоматик base (factor 1),
     * қолганлари ўз factor'и билан.
     */
    @Override
    public void installDefaultUnits() {
        for (DefaultGroup spec : DEFAULT_GROUPS) {
            if (groupRepository.findByName(spec.name()).isPresent()) {
                continue; // idempotent: мавжуд гуруҳ тегилмайди
            }
            UnitGroup group = createGroup(spec.name());
            for (DefaultUnit unit : spec.units()) {
                // base (factor null) биринчи - addUnitToGroup бўш гуруҳда
                // уни автоматик base қилади (берилган factor эътиборсиз)
                addUnitToGroup(group.getId(), unit.name(), unit.factor());
            }
        }
    }

    // ---- конверсия (3-4-турткилар ишлатади) ----

    /** Киритилган миқдорни base бирликка ўгиради: qty × factor. */
    public BigDecimal toBase(Unit unit, BigDecimal qty) {
        return qty.multiply(unit.getFactor());
    }

    /**
     * from → to конверсия factor'и (1 from = натижа × to). Иккиси бир
     * гуруҳдан бўлиши шарт - гуруҳлараро конверсия маъносиз.
     *
     * @throws BusinessRuleException BR-UOM-006
     */
    public BigDecimal factorBetween(Unit from, Unit to) {
        if (from.getId().equals(to.getId())) {
            return BigDecimal.ONE;
        }
        if (from.getGroup() == null || to.getGroup() == null
                || !from.getGroup().getId().equals(to.getGroup().getId())) {
            throw new BusinessRuleException(BusinessRule.BR_UOM_006,
                    "Конверсия фақат бир гуруҳ ичида: «" + from.getName()
                    + "» ва «" + to.getName() + "»");
        }
        return from.getFactor().divide(to.getFactor(), FACTOR_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Ҳужжат сатридаги бирлик factor'ининг snapshot'и (uom.md) - олти
     * ҳужжат service'и (Invoice/Bill/CreditMemo/VendorCredit/RefundReceipt/
     * SalesReceipt) учун ЯГОНА helper (QA-041: 6 байтма-байт нусха
     * бирлаштирилди - бир хиллик кейин жимгина бузилмасин). Мантиқ:
     * (1) бирлик танланмаган → null (factor 1); (2) item'да base бирлик
     * йўқ → BR-UOM-006; (3) танланган бирлик base'нинг ўзи → null;
     * (4) {@link #factorBetween} (гуруҳлараро бўлса BR-UOM-006);
     * (5) {@code requireBaseQty} бўлса base миқдор (qty × factor, scale 4,
     * HALF_UP) нолга юмалоқланмаслиги текширилади.
     *
     * <p>{@code requireBaseQty}: Invoice/SalesReceipt SERVICE сатрда
     * омборга тегмагани учун текширмайди ({@code type == ITEM}'да true);
     * Bill/CM/VC/RR ҳар доим true. {@code zeroBaseQtyRule} - фақат
     * хабар/HTTP status манбаи (BR-SINV-003 / BR-SR-001 / BR-BILL-003 /
     * BR-RET-001), UoM қоидаси эмас - шунинг учун чақирувчидан келади.
     *
     * @throws BusinessRuleException BR-UOM-006 (item base бирликсиз ёки
     *         гуруҳлараро конверсия) ёки {@code zeroBaseQtyRule} (base
     *         миқдор нолга юмалоқланса)
     */
    @Transactional(readOnly = true)
    public BigDecimal lineFactor(int lineNo, Item item, UUID unitId, BigDecimal quantity,
                                 boolean requireBaseQty, BusinessRule zeroBaseQtyRule) {
        if (unitId == null) {
            return null;
        }
        if (item.getUnit() == null) {
            throw new BusinessRuleException(BusinessRule.BR_UOM_006,
                    lineNo + "-сатр: item'да асосий бирлик йўқ - бирлик танлаб бўлмайди: «"
                    + item.getName() + "»");
        }
        if (unitId.equals(item.getUnit().getId())) {
            return null; // base'нинг ўзи
        }
        BigDecimal factor = factorBetween(get(unitId), item.getUnit());
        if (requireBaseQty) {
            BigDecimal baseQty = quantity.multiply(factor).setScale(4, RoundingMode.HALF_UP);
            if (baseQty.signum() <= 0) {
                throw new BusinessRuleException(zeroBaseQtyRule,
                        lineNo + "-сатр: base миқдор жуда кичик (нолга юмалоқланади): "
                        + quantity + " × " + factor);
            }
        }
        return factor;
    }

    /**
     * Ҳужжат сатри select'и учун бирликлар: base гуруҳидаги фаоллар
     * (base биринчи); base гуруҳсиз бўлса фақат ўзи.
     */
    @Transactional(readOnly = true)
    public List<Unit> selectableUnits(Unit baseUnit) {
        if (baseUnit == null) {
            return List.of();
        }
        if (baseUnit.getGroup() == null) {
            return List.of(baseUnit);
        }
        return repository.findByGroupIdAndActiveTrueOrderByBaseDescNameAsc(
                baseUnit.getGroup().getId());
    }

    // ---- инвариантлар ----

    /**
     * Гуруҳ майдонларини инвариантлар билан қўллайди. self null -
     * янги бирлик; мавжудда base мақомини йўқотиш/гуруҳдан чиқиш
     * гуруҳда бошқа бирликлар бор экан тақиқ (BR-UOM-004).
     */
    private void applyGrouping(Unit unit, UUID groupId, BigDecimal factor,
                               boolean base, Unit self) {
        // Base мақомидан/гуруҳдан чиқиш: қолганлар «етим» бўлиб қолмасин
        if (self != null && self.isBase() && self.getGroup() != null) {
            boolean leavingBase = groupId == null
                    || !groupId.equals(self.getGroup().getId()) || !base;
            if (leavingBase && repository
                    .findByGroupIdOrderByBaseDescNameAsc(self.getGroup().getId()).stream()
                    .anyMatch(other -> !other.getId().equals(self.getId()))) {
                throw new BusinessRuleException(BusinessRule.BR_UOM_004,
                        "Base бирлик гуруҳдан чиқмайди: гуруҳда бошқа бирликлар бор");
            }
        }

        if (groupId == null) {
            if (base || (factor != null && factor.compareTo(BigDecimal.ONE) != 0)) {
                throw new BusinessRuleException(BusinessRule.BR_UOM_005,
                        "Гуруҳсиз бирликка factor/base қўйилмайди");
            }
            unit.applyGrouping(null, BigDecimal.ONE, false);
            return;
        }

        UnitGroup group = getGroup(groupId);
        List<Unit> others = repository.findByGroupIdOrderByBaseDescNameAsc(groupId).stream()
                .filter(other -> self == null || !other.getId().equals(self.getId()))
                .toList();
        boolean hasBase = others.stream().anyMatch(Unit::isBase);

        if (base) {
            if (factor != null && factor.compareTo(BigDecimal.ONE) != 0) {
                throw new BusinessRuleException(BusinessRule.BR_UOM_003,
                        "Base бирликнинг factor'и айнан 1 бўлиши керак: " + factor);
            }
            if (hasBase) {
                throw new BusinessRuleException(BusinessRule.BR_UOM_004,
                        "Гуруҳда base бирлик аллақачон бор: «" + group.getName() + "»");
            }
            unit.applyGrouping(group, BigDecimal.ONE, true);
            return;
        }

        if (!hasBase) {
            throw new BusinessRuleException(BusinessRule.BR_UOM_004,
                    "Гуруҳга биринчи кирган бирлик base бўлиши керак: «"
                    + group.getName() + "»");
        }
        if (factor == null || factor.signum() <= 0) {
            throw new BusinessRuleException(BusinessRule.BR_UOM_002,
                    "Конверсия factor'и мусбат бўлиши керак: " + factor);
        }
        unit.applyGrouping(group, factor, false);
    }

    /** Бирлик номи бандлигини текширади (BR-UNT-001). */
    private void requireFreeName(String name, UUID selfId) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException(BusinessRule.BR_UNT_001, "Бирлик номи бўш бўлиши мумкин эмас");
        }
        repository.findByName(name.strip())
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw new BusinessRuleException(BusinessRule.BR_UNT_001, "Бу ном банд: " + name.strip());
                });
    }

    /** Гуруҳ номи бандлигини текширади (BR-UOM-001). */
    private void requireFreeGroupName(String name, UUID selfId) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException(BusinessRule.BR_UOM_001, "Гуруҳ номи бўш бўлиши мумкин эмас");
        }
        groupRepository.findByName(name.strip())
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw new BusinessRuleException(BusinessRule.BR_UOM_001, "Бу ном банд: " + name.strip());
                });
    }
}
