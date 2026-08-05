package com.averpo.erp.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Changeset 052 миграцияси тести (user-roles.md «Тестлар» 4-банд):
 * эски роль сатрлари янги номларга тўғри мапланиши. Liquibase тест
 * базасида аллақачон ўтган - шунинг учун айнан ЎША файлдаги SQL матни
 * classpath'дан ўқилиб қўлда бажарилади (нусха кўчирилмайди - файл
 * ўзгарса тест ҳам ўша матнни олади). Rollback UPDATE'лари ҳам
 * тескари йўналишда текширилади. Ҳаммаси @Transactional ичида -
 * умумий тест базасига из қолмайди.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRoleMigrationTest {

    @Autowired JdbcClient jdbc;

    /** Changeset файлидан ижро этиладиган SQL'ларни ажратади. */
    private List<String> statements(String content, boolean rollback) {
        List<String> result = new ArrayList<>();
        for (String line : content.split("\r?\n")) {
            String trimmed = line.strip();
            if (rollback) {
                // rollback қаторлари "--rollback " префикси билан туради
                if (trimmed.startsWith("--rollback ")) {
                    result.add(trimmed.substring("--rollback ".length())
                            .replace(";", "").strip());
                }
            } else if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                result.add(trimmed.replace(";", "").strip());
            }
        }
        return result;
    }

    /** Минимал app_user сатри - хом role сатри билан (энум четлаб ўтилади). */
    private void insertUser(String username, String role) {
        jdbc.sql("INSERT INTO app_user (id, username, password_hash, display_name, role) "
                        + "VALUES (?, ?, 'x', ?, ?)")
                .params(UUID.randomUUID(), username, username, role).update();
    }

    /** username бўйича хом role сатри. */
    private String roleOf(String username) {
        return jdbc.sql("SELECT role FROM app_user WHERE username = ?")
                .param(username).query(String.class).single();
    }

    @Test
    void changeset052_mapsOldRoles_rollbackReverses() throws Exception {
        String sql = new String(new ClassPathResource("db/changelog/052-user-roles-migration.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        // Эски роллардаги фойдаланувчилар (номлар бетакрор - умумий тест DB)
        insertUser("migr092adm", "ADMIN");
        insertUser("migr092acc", "ACCOUNTANT");
        insertUser("migr092view", "VIEWER");

        // Олдинга: ADMIN→SUPER_ADMIN, VIEWER→VIEWER_AUDITOR, ACCOUNTANT ўзгармас
        List<String> forward = statements(sql, false);
        assertThat(forward).isNotEmpty();
        forward.forEach(statement -> jdbc.sql(statement).update());
        assertThat(roleOf("migr092adm")).isEqualTo("SUPER_ADMIN");
        assertThat(roleOf("migr092acc")).isEqualTo("ACCOUNTANT");
        assertThat(roleOf("migr092view")).isEqualTo("VIEWER_AUDITOR");

        // Rollback: тескари UPDATE'лар эски номларни қайтаради
        List<String> rollback = statements(sql, true);
        assertThat(rollback).isNotEmpty();
        rollback.forEach(statement -> jdbc.sql(statement).update());
        assertThat(roleOf("migr092adm")).isEqualTo("ADMIN");
        assertThat(roleOf("migr092acc")).isEqualTo("ACCOUNTANT");
        assertThat(roleOf("migr092view")).isEqualTo("VIEWER");
    }
}
