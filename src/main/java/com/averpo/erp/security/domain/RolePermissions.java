package com.averpo.erp.security.domain;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Роль → рухсат матрицаси - docs/modules/user-roles.md жадвалининг
 * коддаги ЯГОНА манбаси (v1: қатъий тўплам, DB жадвали ЙЎҚ - 2-босқич
 * per-user созлаш шу модел устига DB билан қурилади).
 *
 * <p>Продакшн ҳам ({@code JpaUserDetailsService} authority'ларни шу
 * ердан беради), тестлар ҳам (custom {@code @WithMockRole}) айнан шу
 * матрицадан ўтади - жадвал билан хулқ ҳеч қачон ажралмайди.
 */
public final class RolePermissions {

    private RolePermissions() {
    }

    /** Матрица катакларини қисқа ёзиш учун алиас (спецдаги E). */
    private static final PermissionLevel E = PermissionLevel.EDIT;

    /** Матрица катакларини қисқа ёзиш учун алиас (спецдаги V). */
    private static final PermissionLevel V = PermissionLevel.VIEW;

    /** Матрица катакларини қисқа ёзиш учун алиас (спецдаги «-»). */
    private static final PermissionLevel N = PermissionLevel.NONE;

    /** Роль → (соҳа → даража). EnumMap - тартиб ва тезлик учун. */
    private static final Map<UserRole, Map<Permission, PermissionLevel>> MATRIX =
            new EnumMap<>(UserRole.class);

    /** Роль → boolean имкониятлар тўплами. */
    private static final Map<UserRole, Set<Capability>> CAPABILITIES =
            new EnumMap<>(UserRole.class);

    static {
        // Параметр тартиби = Permission enum тартиби (спец матрица қаторлари):
        // SALES, PURCHASE, INVENTORY, BANKING, GL, PAYROLL, FIN_REPORTS, SETTINGS, USERS
        row(UserRole.SUPER_ADMIN,       E, E, E, E, E, E, E, E, E);
        row(UserRole.DIRECTOR_ADMIN,    V, V, V, V, V, V, V, N, N);
        row(UserRole.CHIEF_ACCOUNTANT,  E, E, E, E, E, E, V, N, N);
        row(UserRole.ACCOUNTANT,        E, E, V, E, N, N, N, N, N);
        row(UserRole.SALES_MANAGER,     E, N, N, N, N, N, N, N, N);
        row(UserRole.PURCHASE_MANAGER,  N, E, V, N, N, N, N, N, N);
        row(UserRole.WAREHOUSE_MANAGER, N, N, E, N, N, N, N, N, N);
        row(UserRole.VIEWER_AUDITOR,    V, V, V, V, V, V, V, N, N);

        // Имкониятлар: PERIOD_CLOSE фақат SUPER_ADMIN + CHIEF; EXPORT
        // VIEWER_AUDITOR'дан бошқа ҳаммада (спецда «ихт.» - v1да берилмайди).
        caps(UserRole.SUPER_ADMIN, Capability.PERIOD_CLOSE, Capability.EXPORT);
        caps(UserRole.DIRECTOR_ADMIN, Capability.EXPORT);
        caps(UserRole.CHIEF_ACCOUNTANT, Capability.PERIOD_CLOSE, Capability.EXPORT);
        caps(UserRole.ACCOUNTANT, Capability.EXPORT);
        caps(UserRole.SALES_MANAGER, Capability.EXPORT);
        caps(UserRole.PURCHASE_MANAGER, Capability.EXPORT);
        caps(UserRole.WAREHOUSE_MANAGER, Capability.EXPORT);
        caps(UserRole.VIEWER_AUDITOR);
    }

    /** Матрица қаторини позицион тўлдиради - тартиб Permission enum'иники. */
    private static void row(UserRole role, PermissionLevel... levels) {
        Permission[] areas = Permission.values();
        if (levels.length != areas.length) {
            // Янги соҳа қўшилганда ҳар қатор янгиланиши шарт - жим NONE
            // бўлиб қолмасин (fail-fast, класс юкланишида йиқилади)
            throw new IllegalStateException("Матрица қатори тўлиқ эмас: " + role);
        }
        Map<Permission, PermissionLevel> rowMap = new EnumMap<>(Permission.class);
        for (int i = 0; i < areas.length; i++) {
            rowMap.put(areas[i], levels[i]);
        }
        MATRIX.put(role, Collections.unmodifiableMap(rowMap));
    }

    /** Роль имкониятларини тўплам қилиб сақлайди (бўш ҳам мумкин). */
    private static void caps(UserRole role, Capability... capabilities) {
        Set<Capability> set = EnumSet.noneOf(Capability.class);
        Collections.addAll(set, capabilities);
        CAPABILITIES.put(role, Collections.unmodifiableSet(set));
    }

    /** Ролнинг соҳадаги даражаси (матрица катаги). */
    public static PermissionLevel level(UserRole role, Permission area) {
        return MATRIX.get(role).get(area);
    }

    /** Роль соҳада камида шу даражага эгами (EDIT ⊃ VIEW занжири). */
    public static boolean hasAtLeast(UserRole role, Permission area, PermissionLevel required) {
        return level(role, area).atLeast(required);
    }

    /** Рольда boolean имконият борми (PERIOD_CLOSE/EXPORT). */
    public static boolean hasCapability(UserRole role, Capability capability) {
        return CAPABILITIES.get(role).contains(capability);
    }

    /** Соҳанинг кўриш authority номи - SecurityConfig GET қоидалари шуни текширади. */
    public static String viewAuthority(Permission area) {
        return area.name() + "_VIEW";
    }

    /** Соҳанинг ёзиш authority номи - SecurityConfig POST қоидалари шуни текширади. */
    public static String editAuthority(Permission area) {
        return area.name() + "_EDIT";
    }

    /**
     * Барча соҳаларнинг EDIT authority'лари - «харитага кирмаган POST
     * камида битта соҳада EDIT талаб қилади» глобал қоидаси учун (эски
     * VIEWER POST-блоки мероси: ёза олмайдиган роль ҳеч қаерга POST
     * қила олмасин).
     */
    public static String[] allEditAuthorities() {
        Permission[] areas = Permission.values();
        String[] result = new String[areas.length];
        for (int i = 0; i < areas.length; i++) {
            result[i] = editAuthority(areas[i]);
        }
        return result;
    }

    /**
     * Роль учун тўлиқ Spring Security authority тўплами:
     * {@code ROLE_<роль>} (isAdmin каби роль-текширувлар учун) + ҳар
     * соҳа даражасига мос {@code <СОҲА>_VIEW}/{@code <СОҲА>_EDIT}
     * (EDIT'да иккаласи - hasAuthority(VIEW) текшируви EDIT эгасига ҳам
     * ўтсин) + имконият номлари. JpaUserDetailsService login'да,
     * @WithMockRole тестда айнан шуни беради.
     */
    public static Set<GrantedAuthority> authorities(UserRole role) {
        Set<GrantedAuthority> result = new LinkedHashSet<>();
        result.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        for (Permission area : Permission.values()) {
            PermissionLevel level = level(role, area);
            if (level.atLeast(PermissionLevel.VIEW)) {
                result.add(new SimpleGrantedAuthority(viewAuthority(area)));
            }
            if (level == PermissionLevel.EDIT) {
                result.add(new SimpleGrantedAuthority(editAuthority(area)));
            }
        }
        for (Capability capability : Capability.values()) {
            if (hasCapability(role, capability)) {
                result.add(new SimpleGrantedAuthority(capability.name()));
            }
        }
        return result;
    }
}
