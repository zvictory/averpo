package com.averpo.erp.security;

import com.averpo.erp.security.domain.Capability;
import com.averpo.erp.security.domain.Permission;
import com.averpo.erp.security.domain.PermissionLevel;
import com.averpo.erp.security.domain.RolePermissions;
import com.averpo.erp.security.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;
import java.util.stream.Collectors;

import static com.averpo.erp.security.domain.PermissionLevel.EDIT;
import static com.averpo.erp.security.domain.PermissionLevel.NONE;
import static com.averpo.erp.security.domain.PermissionLevel.VIEW;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * RolePermissions матрицаси unit тести (user-roles.md «Тестлар» 1-банд):
 * ҳар роль учун кутилган соҳа даражалари спец жадвалига айнан мос
 * бўлиши. Матрица кодда ўзгарса (янги роль/соҳа) - бу тест спец билан
 * солиштиришга мажбурлайди.
 */
class RolePermissionsTest {

    /** Спец жадвали қатори: роль бўйича 9 соҳа даражаси (enum тартибида). */
    private void assertRow(UserRole role, PermissionLevel... expected) {
        Permission[] areas = Permission.values();
        assertThat(expected).hasSize(areas.length);
        for (int i = 0; i < areas.length; i++) {
            assertThat(RolePermissions.level(role, areas[i]))
                    .as("%s / %s", role, areas[i])
                    .isEqualTo(expected[i]);
        }
    }

    @Test
    void matrix_matchesSpecTable() {
        // SALES, PURCHASE, INVENTORY, BANKING, GL, PAYROLL, FIN_REPORTS, SETTINGS, USERS
        assertRow(UserRole.SUPER_ADMIN,       EDIT, EDIT, EDIT, EDIT, EDIT, EDIT, EDIT, EDIT, EDIT);
        assertRow(UserRole.DIRECTOR_ADMIN,    VIEW, VIEW, VIEW, VIEW, VIEW, VIEW, VIEW, NONE, NONE);
        assertRow(UserRole.CHIEF_ACCOUNTANT,  EDIT, EDIT, EDIT, EDIT, EDIT, EDIT, VIEW, NONE, NONE);
        assertRow(UserRole.ACCOUNTANT,        EDIT, EDIT, VIEW, EDIT, NONE, NONE, NONE, NONE, NONE);
        assertRow(UserRole.SALES_MANAGER,     EDIT, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE);
        assertRow(UserRole.PURCHASE_MANAGER,  NONE, EDIT, VIEW, NONE, NONE, NONE, NONE, NONE, NONE);
        assertRow(UserRole.WAREHOUSE_MANAGER, NONE, NONE, EDIT, NONE, NONE, NONE, NONE, NONE, NONE);
        assertRow(UserRole.VIEWER_AUDITOR,    VIEW, VIEW, VIEW, VIEW, VIEW, VIEW, VIEW, NONE, NONE);
    }

    @Test
    void capabilities_matchSpec() {
        // PERIOD_CLOSE фақат SUPER_ADMIN + CHIEF_ACCOUNTANT
        for (UserRole role : UserRole.values()) {
            boolean expected = role == UserRole.SUPER_ADMIN || role == UserRole.CHIEF_ACCOUNTANT;
            assertThat(RolePermissions.hasCapability(role, Capability.PERIOD_CLOSE))
                    .as("PERIOD_CLOSE / %s", role).isEqualTo(expected);
        }
        // EXPORT: VIEWER_AUDITOR'дан бошқа ҳаммада (спецда «ихт.» - v1да йўқ)
        for (UserRole role : UserRole.values()) {
            assertThat(RolePermissions.hasCapability(role, Capability.EXPORT))
                    .as("EXPORT / %s", role)
                    .isEqualTo(role != UserRole.VIEWER_AUDITOR);
        }
    }

    @Test
    void authorities_containRoleMarker_andRespectLevels() {
        Set<String> salesManager = names(UserRole.SALES_MANAGER);
        assertThat(salesManager).contains("ROLE_SALES_MANAGER", "SALES_VIEW", "SALES_EDIT", "EXPORT");
        assertThat(salesManager).doesNotContain("PURCHASE_VIEW", "INVENTORY_VIEW",
                "GL_VIEW", "SETTINGS_VIEW", "USERS_VIEW", "PERIOD_CLOSE");

        // VIEW даражада EDIT authority берилмайди (ACCOUNTANT / INVENTORY)
        Set<String> accountant = names(UserRole.ACCOUNTANT);
        assertThat(accountant).contains("INVENTORY_VIEW");
        assertThat(accountant).doesNotContain("INVENTORY_EDIT", "GL_VIEW", "PERIOD_CLOSE");

        // EDIT доим VIEW'ни ҳам беради - hasAuthority(VIEW) текширувлари
        // EDIT эгасига ўтиши учун (SecurityConfig GET қоидалари)
        for (UserRole role : UserRole.values()) {
            Set<String> authorities = names(role);
            for (Permission area : Permission.values()) {
                if (authorities.contains(RolePermissions.editAuthority(area))) {
                    assertThat(authorities)
                            .as("%s EDIT бор жойда VIEW ҳам бўлиши шарт: %s", role, area)
                            .contains(RolePermissions.viewAuthority(area));
                }
            }
        }
    }

    /** Роль authority номлари тўплами (қисқа ёрдамчи). */
    private Set<String> names(UserRole role) {
        return RolePermissions.authorities(role).stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}
