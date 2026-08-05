package com.averpo.erp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA auditing'ни ёқади - BaseEntity'даги @CreatedDate/@LastModifiedDate
 * майдонлари шу конфигурациясиз тўлдирилмай қолади.
 *
 * @author Zafar
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
