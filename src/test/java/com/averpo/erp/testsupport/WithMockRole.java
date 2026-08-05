package com.averpo.erp.testsupport;

import com.averpo.erp.security.domain.UserRole;
import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Тест учун mock фойдаланувчи - authority'лари РОЛЬ НОМИДАН ЭМАС,
 * продакшндаги {@code RolePermissions} матрицасидан олинади (Arbitr-092).
 *
 * <p>Нега {@code @WithMockUser(roles=...)} эмас: у фақат ROLE_X
 * authority беради, SecurityConfig эса соҳа authority'ларини
 * (SALES_EDIT ва ҳ.к.) текширади - эски услубдаги mock ҳамма жойда
 * 403 оларди. Бу аннотация билан тест ҳам худди жонли login каби
 * матрицадан ўтади - жадвал ўзгарса тестлар ҳам бирга «кўради».
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Inherited
@WithSecurityContext(factory = WithMockRoleSecurityContextFactory.class)
public @interface WithMockRole {

    /** Тест фойдаланувчисининг роли (default - тўлиқ ҳуқуқли). */
    UserRole value() default UserRole.SUPER_ADMIN;

    /** Username - audit актор текширувлари учун созланади ({@code @WithMockUser} default'и билан бир хил). */
    String username() default "user";
}
