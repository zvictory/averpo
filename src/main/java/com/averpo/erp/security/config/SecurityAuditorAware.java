package com.averpo.erp.security.config;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * BaseEntity'даги {@code @CreatedBy} учун жорий фойдаланувчи id'си
 * (user-management.md): SecurityContext'даги username → app_user.id.
 * {@code @EnableJpaAuditing} ягона AuditorAware bean'ни автоматик
 * топади - қўшимча конфигурация керак эмас.
 *
 * <p>Lookup атайлаб JdbcClient билан (Hibernate эмас): auditor
 * {@code @PrePersist} пайтида, баъзан flush ЎРТАСИДА чақирилади
 * (cascade persist) - у ерда JPA query юритиш flush рекурсиясига олиб
 * келиши мумкин; тоза SQL эса хавфсиз. Кэшсиз оддий lookup (spec
 * қарори - user жадвали кичик).
 *
 * <p>Auth контекстисиз (scheduler, bootstrap) ёки anonymous'да бўш
 * Optional қайтади - created_by NULL қолади (сохта атрибуция йўқ).
 */
@Component
@RequiredArgsConstructor
public class SecurityAuditorAware implements AuditorAware<UUID> {

    /** app_user'дан id ўқиш учун - JPA session'га тегмайдиган йўл. */
    private final JdbcClient jdbc;

    /** {@inheritDoc} */
    @Override
    public Optional<UUID> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return jdbc.sql("SELECT id FROM app_user WHERE username = :username")
                .param("username", auth.getName())
                .query(UUID.class)
                .optional();
    }
}
