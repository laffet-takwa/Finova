package com.finova.transaction.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared Testcontainers harness for the integration tests.
 * <p>
 * Docker is optional: {@code disabledWithoutDocker = true} skips the whole class
 * when it is unavailable, so the build stays green on a machine without a
 * container runtime.
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresIntegrationTestBase {

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("finova_transactions")
                    .withUsername("finova")
                    .withPassword("finova");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}