package com.averpo.erp.shared;

import com.averpo.erp.shared.service.CompanySettingsService;
import com.averpo.erp.shared.service.FactoryResetService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Заводга қайтариш интеграцион тести (factory-reset.md, spec мажбурий
 * тести): тўлдирилган база (иккита user + ҳужжат + GL + каталог бузилиши)
 * → reset → ҳар жадвал айнан seed ҳолати ва фақат reset қилган admin
 * қолиши текширилади.
 *
 * <p>JPA/JdbcClient аралашуви: seed'нинг аксарияти JdbcClient билан
 * (уланишга дарҳол ёзилади); company_settings JPA орқали. reset'дан
 * ОЛДИН flush+clear (JPA holatini ulanishga tushirib stale entity'ни
 * detach) - reset'нинг JdbcClient DELETE'лари ҳақиқий ҳолатни кўради;
 * reset'дан КЕЙИН flush (JPA билан қайта ўрнатилган 51 счётни уланишга) -
 * JdbcClient count'лари уларни кўради.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FactoryResetServiceTest {

    /** Заводга қайтариш сервиси - синов объекти. */
    @Autowired
    private FactoryResetService resetService;

    /** company_settings'ни default'га қайтганини текшириш учун. */
    @Autowired
    private CompanySettingsService settingsService;

    /** Seed ва тасдиқ учун хом SQL (жадвал count'лари). */
    @Autowired
    private JdbcClient jdbcClient;

    /** JPA/JdbcClient аралашувида flush/clear учун. */
    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void reset_clearsUserData_keepsSeedAndCurrentAdmin() {
        // --- Тўлдирилган база ---
        UUID adminId = UUID.randomUUID();
        insertUser(adminId, "gayratrstadmin", "SUPER_ADMIN");
        insertUser(UUID.randomUUID(), "gayratrstacc", "ACCOUNTANT");

        jdbcClient.sql("INSERT INTO contact (id, type, display_name) "
                + "VALUES (?, 'CUSTOMER', 'Тест мижоз')").param(UUID.randomUUID()).update();
        jdbcClient.sql("INSERT INTO journal_entry (id, entry_number, entry_date, status) "
                + "VALUES (?, 'TEST-RST-1', DATE '2026-01-01', 'DRAFT')")
                .param(UUID.randomUUID()).update();
        // item: income/expense account - dimension (FK йўқ), тасодифий UUID етарли
        jdbcClient.sql("INSERT INTO item (id, type, name, income_account_id, expense_account_id) "
                + "VALUES (?, 'SERVICE', 'Тест товар', ?, ?)")
                .params(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()).update();

        // Каталог бузилиши: нофаол seed валюта (EUR) фаоллаштирилди,
        // seed'дан ташқари бирлик/омбор қўшилди, счётчик силжитилди.
        jdbcClient.sql("UPDATE currency SET active = true WHERE code = 'EUR'").update();
        jdbcClient.sql("INSERT INTO unit (id, name) VALUES (?, 'ортиқча бирлик')")
                .param(UUID.randomUUID()).update();
        jdbcClient.sql("INSERT INTO warehouse (id, name, code) VALUES (?, 'Ортиқча омбор', 'EXTRA')")
                .param(UUID.randomUUID()).update();
        jdbcClient.sql("UPDATE document_sequence SET next_number = 77").update();
        // Плагин ёқилган (Arbitr-113) - reset'дан кейин default (ўчиқ = қатор йўқ)
        jdbcClient.sql("INSERT INTO plugin_state (plugin_key, enabled) VALUES ('TELEGRAM', true)")
                .update();

        // Seed IDENTITY бузилиши (Arbitr-072): QQS12 коди/фоизи таҳрирланган
        // (аввалги код-бўйича DELETE уни «фойдаланувчи ставкаси» деб ўчирарди),
        // фойдаланувчи ставкаси қўшилган, seed бирлик номи ўзгартирилган.
        jdbcClient.sql("UPDATE tax_rate SET code = 'QQS15', name = 'ҚҚС 15%', rate = 15 "
                + "WHERE code = 'QQS12'").update();
        jdbcClient.sql("INSERT INTO tax_rate (id, code, name, rate) "
                + "VALUES (?, 'RST8', 'Reset тест ставкаси', 8)")
                .param(UUID.randomUUID()).update();
        jdbcClient.sql("UPDATE unit SET name = 'ўзгартирилган дона' WHERE name = 'дона'").update();

        // company_settings'ни default'дан узоқлаштирамиз (ном + setup_done=true)
        settingsService.update("Менинг компаниям", "UZS", "Asia/Tashkent", null, null);

        // Дастлабки ҳолат: seed'дан ташқари нарса бор
        assertThat(count("contact")).isEqualTo(1);
        assertThat(count("unit")).isEqualTo(7); // 6 seed + 1
        assertThat(count("warehouse")).isEqualTo(2); // 1 seed + 1

        // JPA holatini ulanishga tushiramiz, keyin kontekstni tozalaymiz -
        // reset'нинг хом SQL'и ҳақиқий ҳолатни кўради, stale entity қолмайди
        entityManager.flush();
        entityManager.clear();

        // --- RESET ---
        resetService.reset(adminId);
        // JPA билан қайта ўрнатилган chart'ни ulanishga (JdbcClient кўриши учун)
        entityManager.flush();

        // --- Seed ҳолати тасдиғи ---
        // Иш маълумоти тўлиқ тозаланди
        assertThat(count("contact")).isZero();
        assertThat(count("journal_entry")).isZero();
        assertThat(count("item")).isZero();
        assertThat(count("stock_movement")).isZero();
        assertThat(count("txn_class")).isZero();
        // Плагинлар default ҳолатига: қатор йўқ = ўчиқ (Arbitr-113)
        assertThat(count("plugin_state")).isZero();

        // Аудит (Arbitr-062): эски ёзувлар TRUNCATE бўлди, тоза журналда
        // ЯГОНА FACTORY_RESET - биринчи ёзув (UUIDv7 id тартиби), кейин
        // chart қайта ўрнатилиши CHART_IMPORTED бўлиб туради
        assertThat(jdbcClient.sql("SELECT event_type FROM audit_event ORDER BY id")
                .query(String.class).list())
                .containsExactly("FACTORY_RESET", "CHART_IMPORTED");

        // Seed каталоглар айнан seed сонида
        assertThat(count("currency")).isEqualTo(7);
        // UOM (Arbitr-147): reset'дан кейин 6 стандарт гуруҳ тикланади, seed
        // бирликлар (дона/кг/литр/метр/соат) гуруҳга ютилиб 9 янги бирлик
        // қўшилади (г,тонна,см,мм,мл,м³,м²,см²,кун) = 6+9=15; хизмат гуруҳсиз
        assertThat(count("unit_group")).isEqualTo(6);
        assertThat(count("unit")).isEqualTo(15);
        assertThat(count("tax_rate")).isEqualTo(2);
        assertThat(count("payment_term")).isEqualTo(4);
        assertThat(count("payment_method")).isEqualTo(3);
        assertThat(count("warehouse")).isEqualTo(1);

        // Seed IDENTITY реставрацияси (Arbitr-072): таҳрирланган QQS12 айнан
        // seed ҳолатига қайтди (код/фоиз/фаоллик), RST8 ўчди, бирлик номлари
        // ҳам seed'дагидек - import шаблони («дона»/«ҚҚС 12%») барқарор.
        assertThat(jdbcClient.sql("SELECT code FROM tax_rate ORDER BY code")
                .query(String.class).list())
                .containsExactly("NO_TAX", "QQS12");
        assertThat(jdbcClient.sql("SELECT rate::int FROM tax_rate WHERE code = 'QQS12'")
                .query(Integer.class).single()).isEqualTo(12);
        assertThat(jdbcClient.sql("SELECT active FROM tax_rate WHERE code = 'QQS12'")
                .query(Boolean.class).single()).isTrue();
        // Бирликлар seed номлари + стандарт гуруҳ бирликлари (Arbitr-147);
        // «ўзгартирилган дона» seed'га қайтди ва «Дона» гуруҳига ютилди
        assertThat(jdbcClient.sql("SELECT name FROM unit").query(String.class).list())
                .containsExactlyInAnyOrder("дона", "кг", "литр", "метр", "соат", "хизмат",
                        "г", "тонна", "см", "мм", "мл", "м³", "м²", "см²", "кун");
        // Seed бирлик гуруҳга ютилган (дубликат эмас): кг - «Оғирлик» base
        assertThat(jdbcClient.sql("""
                SELECT g.name FROM unit u JOIN unit_group g ON g.id = u.group_id
                WHERE u.name = 'кг' AND u.is_base
                """).query(String.class).single()).isEqualTo("Оғирлик");

        // Нофаол seed валюта қайта нофаол, home валюта фаол (BR-CUR-002)
        assertThat(active("EUR")).isFalse();
        assertThat(active("UZS")).isTrue();

        // Счётлар режаси қайта ўрнатилди (default-chart.csv, Arbitr-126:
        // 42 postable + 9 гуруҳ ота = 51 счёт, дарахт + кодлар билан)
        assertThat(count("account")).isEqualTo(51);
        assertThat(jdbcClient.sql("SELECT count(*) FROM account WHERE postable").query(Long.class).single())
                .isEqualTo(42);

        // Кодлар тўлиқ (NULL йўқ) ва бетакрор - uq_account_code'га мос
        assertThat(jdbcClient.sql("SELECT count(*) FROM account WHERE code IS NULL")
                .query(Long.class).single()).isZero();
        assertThat(jdbcClient.sql("SELECT count(DISTINCT code) FROM account")
                .query(Long.class).single()).isEqualTo(51);

        // Дарахт боғи: Касса (1030) → Пул маблағлари (1000, гуруҳ) -
        // import parentName'ни ном бўйича боғлагани исботи
        assertThat(jdbcClient.sql("""
                SELECT p.code FROM account c
                JOIN account p ON p.id = c.parent_id
                WHERE c.name = 'Касса' AND NOT p.postable
                """).query(String.class).single()).isEqualTo("1000");

        // Фақат reset қилган admin қолди
        assertThat(count("app_user")).isEqualTo(1);
        assertThat(jdbcClient.sql("SELECT username FROM app_user").query(String.class).single())
                .isEqualTo("gayratrstadmin");

        // Ҳужжат счётчиклари seed ҳолатига (кейинги рақам 1)
        assertThat(jdbcClient.sql("SELECT count(*) FROM document_sequence WHERE next_number <> 1")
                .query(Long.class).single()).isZero();

        // company_settings default'га (lazy qayta yaratildi), setup_done=false
        assertThat(settingsService.get().getName()).isEqualTo("Компания");
        assertThat(settingsService.isSetupDone()).isFalse();
    }

    /** Минимал app_user сатри (парол hash тестда керак эмас). */
    private void insertUser(UUID id, String username, String role) {
        jdbcClient.sql("INSERT INTO app_user (id, username, password_hash, display_name, role) "
                        + "VALUES (?, ?, 'x', ?, ?)")
                .params(id, username, username, role).update();
    }

    /** Жадвал сатрлари сони (жадвал номи - код константаси, инъекция йўқ). */
    private long count(String table) {
        return jdbcClient.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    /** Валюта фаолми (код бўйича). */
    private boolean active(String code) {
        return jdbcClient.sql("SELECT active FROM currency WHERE code = ?")
                .param(code).query(Boolean.class).single();
    }
}
