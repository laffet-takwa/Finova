package com.finova.user.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA auditing is only switched on when the service actually has a persistence
 * context.
 * <p>
 * {@code @EnableJpaAuditing} on the application class would be evaluated in every
 * slice test as well, and the auditing handler it registers needs the JPA metamodel,
 * so a {@code @WebMvcTest} would fail to start with "JPA metamodel must not be empty".
 * As an auto-configuration ordered after {@code HibernateJpaAutoConfiguration} the
 * condition is evaluated against the real context instead: auditing is active in the
 * running service and absent from the web slice.
 */
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@ConditionalOnBean(EntityManagerFactory.class)
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
