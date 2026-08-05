package com.averpo.erp.testsupport;

import com.averpo.erp.security.domain.RolePermissions;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

/**
 * {@link WithMockRole} учун SecurityContext қурувчиси: authority'лар
 * продакшн {@code RolePermissions.authorities()} дан - JpaUserDetailsService
 * жонли login'да берадиган тўплам билан АЙНАН бир хил (тест ва продакшн
 * хулқи ажралмасин).
 */
public class WithMockRoleSecurityContextFactory implements WithSecurityContextFactory<WithMockRole> {

    @Override
    public SecurityContext createSecurityContext(WithMockRole annotation) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                annotation.username(), "password",
                RolePermissions.authorities(annotation.value())));
        return context;
    }
}
