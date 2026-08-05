package com.averpo.erp.testsupport;

import com.averpo.erp.security.domain.RolePermissions;
import com.averpo.erp.security.domain.UserRole;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

/**
 * MockMvc сўровига роль қўшишнинг inline варианти -
 * {@code .with(TestRoles.as("nomi", UserRole.X))}. {@link WithMockRole}
 * билан бир хил манба: authority'лар продакшн RolePermissions
 * матрицасидан ({@code user(...).roles("X")} эски услуби фақат ROLE_X
 * беради ва соҳа қоидаларида 403 олади).
 *
 * @author Zafar
 */
public final class TestRoles {

    private TestRoles() {
    }

    /** Берилган роль authority'лари билан request post-processor. */
    public static SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor as(
            String username, UserRole role) {
        return SecurityMockMvcRequestPostProcessors.user(username)
                .authorities(RolePermissions.authorities(role));
    }
}
