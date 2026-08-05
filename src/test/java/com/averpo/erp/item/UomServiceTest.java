package com.averpo.erp.item;

import com.averpo.erp.item.domain.Item;
import com.averpo.erp.item.domain.ItemType;
import com.averpo.erp.item.domain.Unit;
import com.averpo.erp.item.domain.UnitGroup;
import com.averpo.erp.item.service.ItemService;
import com.averpo.erp.item.service.ItemService.ItemData;
import com.averpo.erp.item.service.UnitService;
import com.averpo.erp.ledger.service.AccountService;
import com.averpo.erp.shared.exception.BusinessRule;
import com.averpo.erp.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UoM гуруҳ/конверсия инвариантлари (BR-UOM-001..006) ва item default
 * бирликлари (BR-ITM-012) - docs/modules/uom.md.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UomServiceTest {

    @Autowired UnitService unitService;
    @Autowired ItemService itemService;
    @Autowired AccountService accountService;

    /** «Оғирлик» тест гуруҳи. */
    private UnitGroup weight;

    /** Гуруҳнинг base бирлиги (кг). */
    private Unit kg;

    /** Ҳар тест олдидан гуруҳ + base тайёрланади (rollback тозалайди). */
    @BeforeEach
    void setUp() {
        weight = unitService.createGroup("Оғирлик (тест)");
        kg = unitService.create("кг (тест)", weight.getId(), null, true);
    }

    @Test
    void group_firstUnitIsBase_factorOne() {
        assertThat(kg.isBase()).isTrue();
        assertThat(kg.getFactor()).isEqualByComparingTo("1");

        // Иккинчиси factor билан киради
        Unit gram = unitService.create("гр (тест)", weight.getId(),
                new BigDecimal("0.001"), false);
        assertThat(gram.isBase()).isFalse();
        assertThat(gram.getFactor()).isEqualByComparingTo("0.001");
        assertThat(unitService.groupUnits(weight.getId())).hasSize(2);
    }

    @Test
    void group_invariants_rejected() {
        // BR-UOM-001: гуруҳ номи банд
        assertThatThrownBy(() -> unitService.createGroup("Оғирлик (тест)"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-UOM-001"));

        // BR-UOM-004: иккинчи base рад
        assertThatThrownBy(() -> unitService.create("яна base", weight.getId(), null, true))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-UOM-004"));

        // BR-UOM-004: base'сиз гуруҳга оддий бирлик биринчи бўлиб кирмайди
        UnitGroup empty = unitService.createGroup("Бўш гуруҳ (тест)");
        assertThatThrownBy(() -> unitService.create("етим", empty.getId(),
                new BigDecimal("2"), false))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-UOM-004"));

        // BR-UOM-002: factor мусбат эмас
        assertThatThrownBy(() -> unitService.create("нол", weight.getId(),
                BigDecimal.ZERO, false))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-UOM-002"));

        // BR-UOM-003: base factor 1 эмас
        assertThatThrownBy(() -> unitService.create("қинғир base", empty.getId(),
                new BigDecimal("5"), true))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-UOM-003"));

        // BR-UOM-005: гуруҳсиз бирликка factor
        assertThatThrownBy(() -> unitService.create("гуруҳсиз", null,
                new BigDecimal("3"), false))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-UOM-005"));
    }

    @Test
    void base_cannotLeaveGroup_whileMembersExist() {
        unitService.create("гр (тест)", weight.getId(), new BigDecimal("0.001"), false);

        // Base гуруҳдан чиқмоқчи - гуруҳда бошқа бирлик бор
        assertThatThrownBy(() -> unitService.update(kg.getId(), "кг (тест)", true,
                null, null, false))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-UOM-004"));

        // Base мақомини йўқотмоқчи (гуруҳда қолиб) - ҳам рад
        assertThatThrownBy(() -> unitService.update(kg.getId(), "кг (тест)", true,
                weight.getId(), new BigDecimal("2"), false))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-UOM-004"));
    }

    @Test
    void addUnitToGroup_simpleFlow() {
        // Бўш гуруҳга биринчи бирлик - автоматик base (factor эътиборсиз)
        UnitGroup volume = unitService.createGroup("Ҳажм (тест)");
        Unit litr = unitService.addUnitToGroup(volume.getId(), "литр (қўшиш тест)",
                new BigDecimal("55"));
        assertThat(litr.isBase()).isTrue();
        assertThat(litr.getFactor()).isEqualByComparingTo("1");

        // Янги ном - бирлик яратилиб гуруҳга киради (factor билан)
        Unit ml = unitService.addUnitToGroup(volume.getId(), "мл (қўшиш тест)",
                new BigDecimal("0.001"));
        assertThat(ml.isBase()).isFalse();
        assertThat(ml.getFactor()).isEqualByComparingTo("0.001");

        // Мавжуд гуруҳсиз бирлик номи - ўша бирлик гуруҳга киради
        Unit existing = unitService.create("челак (қўшиш тест)", null, null, false);
        Unit joined = unitService.addUnitToGroup(volume.getId(), "челак (қўшиш тест)",
                new BigDecimal("10"));
        assertThat(joined.getId()).isEqualTo(existing.getId());
        assertThat(joined.getFactor()).isEqualByComparingTo("10");

        // Бошқа гуруҳдаги бирлик номи - жимгина кўчирилмайди, аниқ хато
        assertThatThrownBy(() -> unitService.addUnitToGroup(volume.getId(),
                "кг (тест)", new BigDecimal("2")))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-UOM-006"));

        // Иккинчи бирликка factor киритилмаса - BR-UOM-002
        assertThatThrownBy(() -> unitService.addUnitToGroup(volume.getId(),
                "томчи (қўшиш тест)", null))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-UOM-002"));
    }

    @Test
    void assignGroup_joinChangeFactorLeave() {
        // Гуруҳсиз бирлик гуруҳга киради (ном/фаолликка тегилмайди)
        Unit litr = unitService.create("литр (тест)", null, null, false);
        Unit joined = unitService.assignGroup(litr.getId(), weight.getId(),
                new BigDecimal("0.9"), false);
        assertThat(joined.getGroup().getId()).isEqualTo(weight.getId());
        assertThat(joined.getFactor()).isEqualByComparingTo("0.9");
        assertThat(joined.getName()).isEqualTo("литр (тест)");

        // Factor'ини ўзгартириш - гуруҳда қолиб
        unitService.assignGroup(litr.getId(), weight.getId(),
                new BigDecimal("0.95"), false);
        assertThat(unitService.get(litr.getId()).getFactor())
                .isEqualByComparingTo("0.95");

        // Гуруҳдан чиқариш - factor 1 га қайтади
        Unit left = unitService.assignGroup(litr.getId(), null, null, false);
        assertThat(left.getGroup()).isNull();
        assertThat(left.getFactor()).isEqualByComparingTo("1");

        // Base эса аъзолар турганда чиқа олмайди (BR-UOM-004)
        unitService.create("гр2 (тест)", weight.getId(), new BigDecimal("0.001"), false);
        assertThatThrownBy(() -> unitService.assignGroup(kg.getId(), null, null, false))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-UOM-004"));
    }

    @Test
    void conversion_toBase_factorBetween_selectable() {
        Unit gram = unitService.create("гр (тест)", weight.getId(),
                new BigDecimal("0.001"), false);
        Unit ton = unitService.create("тонна (тест)", weight.getId(),
                new BigDecimal("1000"), false);

        // toBase: 2500 гр = 2.5 кг; 3 тонна = 3000 кг
        assertThat(unitService.toBase(gram, new BigDecimal("2500")))
                .isEqualByComparingTo("2.5");
        assertThat(unitService.toBase(ton, new BigDecimal("3")))
                .isEqualByComparingTo("3000");

        // factorBetween: 1 тонна = 1 000 000 гр; тескариси 0.000001
        assertThat(unitService.factorBetween(ton, gram))
                .isEqualByComparingTo("1000000");
        assertThat(unitService.factorBetween(gram, ton))
                .isEqualByComparingTo("0.000001");
        assertThat(unitService.factorBetween(kg, kg)).isEqualByComparingTo("1");

        // selectableUnits: base биринчи, гуруҳдаги фаоллар
        assertThat(unitService.selectableUnits(kg))
                .extracting(Unit::getName)
                .containsExactly("кг (тест)", "гр (тест)", "тонна (тест)");

        // BR-UOM-006: гуруҳлараро конверсия рад
        Unit dona = unitService.create("дона (тест)", null, null, false);
        assertThatThrownBy(() -> unitService.factorBetween(kg, dona))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-UOM-006"));
        // Гуруҳсиз base'да фақат ўзи танланади
        assertThat(unitService.selectableUnits(dona)).containsExactly(dona);
    }

    @Test
    void item_defaultUnits_sameGroupRequired() {
        accountService.importDefaultChart();
        ItemService.DefaultAccounts defaults = itemService.defaultsFor(ItemType.INVENTORY);
        Unit gram = unitService.create("гр (тест)", weight.getId(),
                new BigDecimal("0.001"), false);
        Unit dona = unitService.create("дона (тест)", null, null, false);

        // Тўғри: base кг, default харид бирлиги гр (бир гуруҳ)
        Item item = itemService.create(ItemType.INVENTORY, data(defaults,
                "UoM товар (тест)", kg.getId(), gram.getId(), null));
        assertThat(item.getPurchaseUnit().getName()).isEqualTo("гр (тест)");
        assertThat(item.getSalesUnit()).isNull();

        // BR-ITM-012: бошқа гуруҳдаги (гуруҳсиз) default рад
        assertThatThrownBy(() -> itemService.create(ItemType.INVENTORY, data(defaults,
                "UoM товар 2 (тест)", kg.getId(), dona.getId(), null)))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-ITM-012"));

        // BR-ITM-012: base танланмаган ҳолда default рад
        assertThatThrownBy(() -> itemService.create(ItemType.INVENTORY, data(defaults,
                "UoM товар 3 (тест)", null, null, gram.getId())))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-ITM-012"));

        // Эски 12 майдонли ItemData имзоси ишлайверади (default'ларсиз)
        Item legacy = itemService.create(ItemType.INVENTORY, new ItemData(
                "UoM legacy (тест)", null, null, kg.getId(), null, null,
                defaults.income(), null, null, defaults.expense(),
                defaults.inventoryAsset(), null));
        assertThat(legacy.getPurchaseUnit()).isNull();
        assertThat(legacy.getSalesUnit()).isNull();
    }

    /**
     * Xorazmiy-041: UnitService.lineFactor ягона helper'нинг ҳар тармоғи
     * (6 нусха ўрнига). null бирлик/base/конверсия/BR-UOM-006/нол baseQty/
     * requireBaseQty гейти айнан эски unitFactorSnapshot'лар хулқи.
     */
    @Test
    void lineFactor_allBranches() {
        accountService.importDefaultChart();
        ItemService.DefaultAccounts defaults = itemService.defaultsFor(ItemType.INVENTORY);
        Unit gram = unitService.create("гр (тест)", weight.getId(),
                new BigDecimal("0.001"), false);
        Unit dona = unitService.create("дона (тест)", null, null, false); // гуруҳсиз
        Item item = itemService.create(ItemType.INVENTORY,
                data(defaults, "Factor товар (тест)", kg.getId(), null, null));
        Item noUnit = itemService.create(ItemType.INVENTORY, new ItemData(
                "Бирликсиз товар (тест)", null, null, null, null, null,
                defaults.income(), null, null, defaults.expense(),
                defaults.inventoryAsset(), null));

        // Бирлик танланмаган → null (factor 1)
        assertThat(unitService.lineFactor(1, item, null, BigDecimal.TEN,
                true, BusinessRule.BR_SINV_003)).isNull();
        // Танланган бирлик base'нинг ўзи → null
        assertThat(unitService.lineFactor(1, item, kg.getId(), BigDecimal.TEN,
                true, BusinessRule.BR_SINV_003)).isNull();
        // Бошқа бирлик (гр): 1 гр = 0.001 кг → factor 0.001
        assertThat(unitService.lineFactor(1, item, gram.getId(), BigDecimal.TEN,
                true, BusinessRule.BR_SINV_003)).isEqualByComparingTo("0.001");
        // Item'да base бирлик йўқ → BR-UOM-006
        assertThatThrownBy(() -> unitService.lineFactor(1, noUnit, gram.getId(),
                BigDecimal.ONE, true, BusinessRule.BR_SINV_003))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-UOM-006"));
        // Гуруҳлараро конверсия → BR-UOM-006 (factorBetween)
        assertThatThrownBy(() -> unitService.lineFactor(1, item, dona.getId(),
                BigDecimal.ONE, true, BusinessRule.BR_SINV_003))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-UOM-006"));
        // requireBaseQty=true: base миқдор нолга юмалоқланади → zeroBaseQtyRule
        assertThatThrownBy(() -> unitService.lineFactor(3, item, gram.getId(),
                new BigDecimal("0.0001"), true, BusinessRule.BR_SR_001))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BR-SR-001"));
        // requireBaseQty=FALSE: нол baseQty текширилмайди (SERVICE сатр гейти) - factor қайтади
        assertThat(unitService.lineFactor(3, item, gram.getId(),
                new BigDecimal("0.0001"), false, BusinessRule.BR_SR_001))
                .isEqualByComparingTo("0.001");
    }

    /** INVENTORY item маълумоти ясагич - бирликлар билан. */
    private static ItemData data(ItemService.DefaultAccounts defaults, String name,
                                 UUID unitId, UUID purchaseUnitId, UUID salesUnitId) {
        return new ItemData(name, null, null, unitId, null, null,
                defaults.income(), null, null, defaults.expense(),
                defaults.inventoryAsset(), null, purchaseUnitId, salesUnitId);
    }
}
