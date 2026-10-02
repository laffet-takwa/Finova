package com.finova.account.repository;

import com.finova.account.domain.Account;
import com.finova.account.domain.BalanceProjection;
import com.finova.account.support.AccountFixtures;
import com.finova.common.domain.AccountStatus;
import com.finova.common.domain.AccountType;
import com.finova.common.domain.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the real Flyway migration against PostgreSQL. {@code ddl-auto=validate}
 * means the context only starts when {@code V1__init.sql} matches the entity
 * mapping exactly, so context loading is itself an assertion.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class AccountRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private BalanceProjectionRepository balanceProjectionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        balanceProjectionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void shouldCreateEveryTableAndIndexDeclaredByTheMigration() {
        List<String> tables = jdbcTemplate.queryForList(
            "select table_name from information_schema.tables where table_schema = 'public'", String.class);
        assertTrue(tables.contains("accounts"), "accounts table missing, got " + tables);
        assertTrue(tables.contains("balance_projection"), "balance_projection table missing, got " + tables);

        List<String> indexes = jdbcTemplate.queryForList("select indexname from pg_indexes where schemaname = 'public'",
            String.class);
        assertTrue(indexes.contains("idx_accounts_user_id"), "user_id index missing, got " + indexes);
        assertTrue(indexes.contains("idx_accounts_account_number"), "account_number index missing, got " + indexes);
        assertTrue(indexes.contains("idx_accounts_status"), "status index missing, got " + indexes);
        assertTrue(indexes.contains("idx_accounts_created_at"), "created_at index missing, got " + indexes);
    }

    @Test
    void shouldExposeTheAccountColumnsExpectedByTheEntity() {
        List<String> columns = jdbcTemplate.queryForList(
            "select column_name from information_schema.columns where table_name = 'accounts'", String.class);

        assertTrue(columns.containsAll(List.of("id", "user_id", "account_number", "account_type", "currency",
            "balance", "status", "iban", "nickname", "created_at", "updated_at", "version")), columns.toString());
    }

    @Test
    void shouldEnforceAUniqueAccountNumber() {
        insertRow("acc-1", "user-1", "TN58100001234567890111", "CHECKING", "TND", new BigDecimal("10.000"), "ACTIVE");

        assertThrows(DataIntegrityViolationException.class,
            () -> insertRow("acc-2", "user-2", "TN58100001234567890111", "CHECKING", "TND", new BigDecimal("20.000"), "ACTIVE"));
    }

    @Test
    void shouldEnforceOneAccountPerTypeAndCurrencyForAUser() {
        insertRow("acc-1", "user-1", "TN58100001234567890111", "CHECKING", "TND", new BigDecimal("10.000"), "ACTIVE");

        assertThrows(DataIntegrityViolationException.class,
            () -> insertRow("acc-2", "user-1", "TN58100001234567890112", "CHECKING", "TND", new BigDecimal("20.000"), "ACTIVE"));
    }

    @Test
    void shouldAllowTheSameAccountTypeInAnotherCurrency() {
        insertRow("acc-1", "user-1", "TN58100001234567890111", "CHECKING", "TND", new BigDecimal("10.000"), "ACTIVE");

        insertRow("acc-2", "user-1", "EU58200003456789012345", "CHECKING", "EUR", new BigDecimal("20.000"), "ACTIVE");

        assertEquals(2, accountRepository.count());
    }

    @Test
    void shouldRejectANegativeBalanceThroughTheCheckConstraint() {
        insertRow("acc-1", "user-1", "TN58100001234567890111", "CHECKING", "TND", new BigDecimal("10.000"), "ACTIVE");

        assertThrows(DataIntegrityViolationException.class,
            () -> insertRow("acc-2", "user-2", "TN58100001234567890112", "CHECKING", "TND", new BigDecimal("-1.000"), "ACTIVE"));
    }

    @Test
    void shouldRejectValuesOutsideTheAllowedDomains() {
        insertRow("acc-1", "user-1", "TN58100001234567890111", "CHECKING", "TND", new BigDecimal("10.000"), "ACTIVE");

        assertThrows(DataIntegrityViolationException.class,
            () -> jdbcTemplate.update("update accounts set status = 'FROZEN' where id = ?", "acc-1"));
        assertThrows(DataIntegrityViolationException.class,
            () -> jdbcTemplate.update("update accounts set currency = 'GBP' where id = ?", "acc-1"));
        assertThrows(DataIntegrityViolationException.class,
            () -> jdbcTemplate.update("update accounts set account_type = 'CURRENT' where id = ?", "acc-1"));
    }


    @Test
    void shouldKeepTheIdempotencyMarkerTableKeyedByTransactionId() {
        balanceProjectionRepository.save(new BalanceProjection("tx-1", Instant.now()));
        balanceProjectionRepository.save(new BalanceProjection("tx-1", Instant.now()));

        assertEquals(1, balanceProjectionRepository.count());
    }

    @Test
    void shouldAggregateBalancesPerCurrencyAndCountsPerType() {
        accountRepository.save(AccountFixtures.account("acc-1", "user-1", "TN58100001234567890111",
            AccountType.CHECKING, Currency.TND, new BigDecimal("100.000"), AccountStatus.ACTIVE));
        accountRepository.save(AccountFixtures.account("acc-2", "user-2", "EU58200003456789012345",
            AccountType.SAVINGS, Currency.EUR, new BigDecimal("40.500"), AccountStatus.ACTIVE));
        accountRepository.save(AccountFixtures.account("acc-3", "user-3", "US58300009876543212345",
            AccountType.SAVINGS, Currency.USD, new BigDecimal("0.000"), AccountStatus.BLOCKED));
        accountRepository.flush();

        assertEquals(3, accountRepository.count());
        assertEquals(1, accountRepository.countByStatus(AccountStatus.ACTIVE));
        assertEquals(1, accountRepository.countByStatus(AccountStatus.BLOCKED));
        assertEquals(0, accountRepository.countByStatus(AccountStatus.CLOSED));
        assertEquals(new BigDecimal("100.000"), accountRepository.totalBalanceByCurrency(Currency.TND));
        assertEquals(2, accountRepository.countGroupedByType().stream()
            .filter(row -> row.getAccountType() == AccountType.SAVINGS).findFirst().orElseThrow().getTotal());
        assertEquals(3, accountRepository.totalBalanceGroupedByCurrency().size());
        assertEquals(3, accountRepository.accountsCreatedSince(Instant.now().minusSeconds(3600)).size());
    }

    @Test
    void shouldFindAnAccountByUserIdNewestFirst() {
        accountRepository.save(AccountFixtures.account("acc-1", "user-1", "TN58100001234567890111",
            AccountType.CHECKING, Currency.TND, new BigDecimal("10.000"), AccountStatus.ACTIVE));
        accountRepository.save(AccountFixtures.account("acc-2", "user-1", "TN58100001234567890112",
            AccountType.SAVINGS, Currency.TND, new BigDecimal("20.000"), AccountStatus.ACTIVE));
        accountRepository.flush();

        List<Account> accounts = accountRepository.findByUserIdOrderByCreatedAtDesc("user-1");

        assertEquals(2, accounts.size());
        assertEquals(1, accountRepository.countByUserId("user-1"));
        assertTrue(accountRepository.existsByAccountNumber("TN58100001234567890111"));
        assertTrue(accountRepository.existsByUserIdAndAccountTypeAndCurrency("user-1", AccountType.SAVINGS,
            Currency.TND));
    }

    private void insertRow(String id, String userId, String accountNumber, String accountType,
                           String currency, BigDecimal balance, String status) {
        jdbcTemplate.update("insert into accounts (id, user_id, account_number, account_type, currency, balance, "
                + "status, created_at, updated_at, version) values (?, ?, ?, ?, ?, ?, ?, now(), now(), 0)",
            id, userId, accountNumber, accountType, currency, balance, status);
    }
}
