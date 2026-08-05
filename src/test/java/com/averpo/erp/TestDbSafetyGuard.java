package com.averpo.erp;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Тест муҳити хавфсизлик қулфи: Liquibase drop-first фақат номи
 * {@code _test} билан тугайдиган базада ишлашига кафолат. URL адашиб
 * dev/production базага кўрсатса - контекст умуман кўтарилмайди,
 * schema ўчиб кетиш эҳтимоли ёпилади.
 *
 * <p>Фақат test classpath'да туради - production jar'га кирмайди.
 */
@Component
public class TestDbSafetyGuard {

    /** Тест datasource URL'и. */
    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    /** Контекст кўтарилишида URL текширилади. */
    @PostConstruct
    void verify() {
        String withoutParams = datasourceUrl.split("\\?")[0];
        if (!withoutParams.endsWith("_test")) {
            throw new IllegalStateException(
                    "ХАВФ: тест datasource '_test' билан тугамайди: " + datasourceUrl
                    + " - drop-first нотўғри базани ўчириб юбормаслиги учун тўхтатилди");
        }
    }
}
