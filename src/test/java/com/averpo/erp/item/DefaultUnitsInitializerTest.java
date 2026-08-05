package com.averpo.erp.item;

import com.averpo.erp.item.config.DefaultUnitsInitializer;
import com.averpo.erp.item.domain.Unit;
import com.averpo.erp.item.domain.UnitGroup;
import com.averpo.erp.item.service.UnitService;
import com.averpo.erp.shared.service.CompanySettingsService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Arbitr-147: янги ўрнатишда стандарт UOM гуруҳлари автоматик ўрнатилиши
 * ва seed бирликлар билан ярашуви.
 *
 * <p>{@link DefaultUnitsInitializer} тест профилида {@code @Profile("!test")}
 * туфайли КЎТАРИЛМАЙДИ (акс ҳолда ҳар контекстда гуруҳлар commit бўлиб
 * умумий базани ифлослар, «seed бирлик гуруҳсиз» инвариантини бузарди) -
 * initializer JavaDoc'ига қаранг. Bean'ни қўлда қуриб ҳақиқий
 * {@link UnitService} билан текширамиз, @Transactional (rollback). Seed
 * бирликлар (дона/кг/литр/метр/соат/хизмат) changeset 008'дан тест
 * базасида ҳам бор - идемпотентлик айнан шу ҳолатда исботланади (хотира
 * тузоғи: тест DB seed'ида units бор).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DefaultUnitsInitializerTest {

    /** Кутилган стандарт гуруҳлар (Arbitr-147 тўплами). */
    private static final List<String> STANDARD_GROUPS =
            List.of("Дона", "Оғирлик", "Узунлик", "Ҳажм", "Юза", "Вақт");

    @Autowired UnitService unitService;
    @Autowired CompanySettingsService settingsService;
    @Autowired JdbcClient jdbcClient;

    @PersistenceContext EntityManager entityManager;

    /** Жорий гуруҳ номлари. */
    private List<String> groupNames() {
        return unitService.groups().stream().map(UnitGroup::getName).toList();
    }

    /** Ном бўйича ягона бирлик (unique шарти - топилмаса/дубл бўлса аниқ хато). */
    private Unit unitByName(String name) {
        List<Unit> hits = unitService.all().stream()
                .filter(u -> u.getName().equals(name)).toList();
        assertThat(hits).as("бирлик «%s» ягона", name).hasSize(1);
        return hits.get(0);
    }

    @Test
    void install_createsStandardGroups_adoptsSeedUnits() {
        unitService.installDefaultUnits();

        assertThat(groupNames()).containsAll(STANDARD_GROUPS);

        // Seed бирликлар ном бўйича гуруҳга ЮТИЛДИ (дубликат эмас - ягона),
        // base мақомида; хизмат гуруҳсиз қолди (ўлчов гуруҳига кирмайди)
        Unit kg = unitByName("кг");
        assertThat(kg.getGroup().getName()).isEqualTo("Оғирлик");
        assertThat(kg.isBase()).isTrue();
        assertThat(unitByName("дона").getGroup().getName()).isEqualTo("Дона");
        assertThat(unitByName("литр").getGroup().getName()).isEqualTo("Ҳажм");
        assertThat(unitByName("метр").getGroup().getName()).isEqualTo("Узунлик");
        assertThat(unitByName("соат").getGroup().getName()).isEqualTo("Вақт");
        assertThat(unitByName("хизмат").getGroup()).isNull();

        // Гуруҳ ичи: base биринчи, factor base'га нисбатан (уом.md)
        UnitGroup weight = unitService.groups().stream()
                .filter(g -> g.getName().equals("Оғирлик")).findFirst().orElseThrow();
        assertThat(unitService.groupUnits(weight.getId()).stream().map(Unit::getName))
                .containsExactlyInAnyOrder("кг", "г", "тонна");
        assertThat(unitByName("г").getFactor()).isEqualByComparingTo("0.001");
        assertThat(unitByName("тонна").getFactor()).isEqualByComparingTo("1000");
        assertThat(unitByName("см²").getFactor()).isEqualByComparingTo("0.0001");
    }

    @Test
    void install_isIdempotent_secondRunAddsNothing() {
        unitService.installDefaultUnits();
        int groups = unitService.groups().size();
        int units = unitService.all().size();

        unitService.installDefaultUnits();

        // Гуруҳ НОМИ бўйича ўтказиб юборилди - дубл гуруҳ ҳам, дубл бирлик ҳам йўқ
        assertThat(unitService.groups()).hasSize(groups);
        assertThat(unitService.all()).hasSize(units);
    }

    @Test
    void install_respectsExistingGroupByName() {
        // Фойдаланувчи «Оғирлик»ни ЎЗИЧА тузган (фақат кг base) - installer
        // уни ном бўйича ўтказиб юборади, г/тонна қўшмайди (бузмайди)
        UnitGroup mine = unitService.createGroup("Оғирлик");
        unitService.addUnitToGroup(mine.getId(), "кг", null);

        unitService.installDefaultUnits();

        // Оғирлик қайта тузилмади - фақат кг қолди
        assertThat(unitService.groupUnits(mine.getId()).stream().map(Unit::getName))
                .containsExactly("кг");
        // Қолган стандарт гуруҳлар ўрнатилди
        assertThat(groupNames()).containsAll(STANDARD_GROUPS);
    }

    @Test
    void initializer_freshInstall_installs() {
        // Fresh install ҳолатини детерминистик тиклаймиз (умумий тест базаси
        // ноаниқ бўлиши мумкин): гуруҳларни тозалаб (унит'ларни detach қилиб)
        // ва company_settings'ни ўчириб (get() lazy setupDone=false яратади).
        jdbcClient.sql("UPDATE unit SET group_id = NULL, is_base = false").update();
        jdbcClient.sql("DELETE FROM unit_group").update();
        jdbcClient.sql("DELETE FROM company_settings").update();
        entityManager.clear(); // JdbcClient ёзувларидан кейин stale JPA detach

        assertThat(unitService.groups()).isEmpty();
        assertThat(settingsService.isSetupDone()).isFalse();

        new DefaultUnitsInitializer(unitService, settingsService).run(null);

        assertThat(groupNames()).containsExactlyInAnyOrderElementsOf(STANDARD_GROUPS);
    }

    @Test
    void initializer_setupDone_skips() {
        // Мавжуд ўрнатилган база (setupDone=true) - карта #3: авто тегилмайди
        settingsService.update("Тест компания", "UZS", "Asia/Tashkent", null, null);
        assertThat(settingsService.isSetupDone()).isTrue();
        boolean hadWeight = groupNames().contains("Оғирлик");

        new DefaultUnitsInitializer(unitService, settingsService).run(null);

        // Стандарт гуруҳ қўшилмади (олдин бўлмаган бўлса кейин ҳам йўқ)
        assertThat(groupNames().contains("Оғирлик")).isEqualTo(hadWeight);
    }

    @Test
    void initializer_existingGroups_skips() {
        // Гуруҳ аллақачон бор (фойдаланувчи тузган) - идемпотент, тегилмайди
        unitService.createGroup("Мавжуд гуруҳ");
        int before = unitService.groups().size();

        new DefaultUnitsInitializer(unitService, settingsService).run(null);

        // Гуруҳ бўш эмас эди - installer умуман ишламади (стандарт қўшилмади)
        assertThat(unitService.groups()).hasSize(before);
        assertThat(groupNames()).doesNotContain("Оғирлик");
    }
}
