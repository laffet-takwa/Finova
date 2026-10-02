package com.finova.account.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA auditing is declared here rather than on the application class so that a
 * {@code @WebMvcTest} slice can start: the annotation pulls in a JPA mapping
 * context, which does not exist in a web slice and would fail the context.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
