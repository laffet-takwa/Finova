package com.finova.account.config;

import com.finova.account.client.UserReference;
import com.finova.account.client.UserServiceClient;
import com.finova.account.domain.Account;
import com.finova.account.dto.AccountLookupResponse;
import com.finova.account.repository.AccountRepository;
import com.finova.account.support.AccountNumberGenerator;
import com.finova.account.support.AccountNumbers;
import com.finova.account.support.IbanGenerator;
import com.finova.common.domain.AccountStatus;
import com.finova.common.domain.AccountType;
import com.finova.common.domain.Currency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Dev-only demo data.
 * <p>
 * The user-service owns the demo users and generates their ids randomly, so this
 * seeder resolves each id over Feign instead of hard-coding it. Every lookup is
 * best effort: when the user-service is down the seeder logs a warning and the
 * account-service still starts.
 */
@Configuration
@Profile("dev")
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String BANK_NAME = AccountLookupResponse.BANK_NAME;

    private final UserServiceClient userServiceClient;
    private final AccountRepository accountRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final IbanGenerator ibanGenerator;

    public DataSeeder(UserServiceClient userServiceClient,
                      AccountRepository accountRepository,
                      AccountNumberGenerator accountNumberGenerator,
                      IbanGenerator ibanGenerator) {
        this.userServiceClient = userServiceClient;
        this.accountRepository = accountRepository;
        this.accountNumberGenerator = accountNumberGenerator;
        this.ibanGenerator = ibanGenerator;
    }

    @Bean
    ApplicationRunner seedDemoAccounts() {
        return args -> seed();
    }

    /**
     * {@code balance_projection} is deliberately left empty: the seeded balances are
     * the starting point of the read projection, so the first {@code transaction.completed}
     * event must be applied normally.
     */
    @Transactional
    public void seed() {
        seedUser("takwa@finova.dev", List.of(
            new SeedAccount("Everyday Account", AccountType.CHECKING, Currency.TND, "12450.750", AccountStatus.ACTIVE),
            new SeedAccount("Savings Account", AccountType.SAVINGS, Currency.TND, "5800.000", AccountStatus.ACTIVE)));

        seedUser("ines.bouzid@finova.dev", List.of(
            new SeedAccount("Daily Spending", AccountType.CHECKING, Currency.TND, "3240.500", AccountStatus.ACTIVE)));

        seedUser("yassine.trabelsi@finova.dev", List.of(
            new SeedAccount("Everyday Account", AccountType.CHECKING, Currency.TND, "8915.000", AccountStatus.ACTIVE),
            new SeedAccount("Euro Savings", AccountType.SAVINGS, Currency.EUR, "6400.000", AccountStatus.ACTIVE)));

        seedUser("salma.gharbi@finova.dev", List.of(
            new SeedAccount("Savings Account", AccountType.SAVINGS, Currency.TND, "15000.000", AccountStatus.ACTIVE)));

        seedUser("sami.mejboud@finova.dev", List.of(
            new SeedAccount("Everyday Account", AccountType.CHECKING, Currency.TND, "1120.750", AccountStatus.BLOCKED)));

        printSummary();
    }

    private void seedUser(String email, List<SeedAccount> seeds) {
        Optional<String> userId = resolveUserId(email);
        if (userId.isEmpty()) {
            return;
        }
        if (!accountRepository.findByUserIdOrderByCreatedAtDesc(userId.get()).isEmpty()) {
            log.info("Demo accounts already present for {}", email);
            return;
        }
        for (SeedAccount seed : seeds) {
            accountRepository.save(toEntity(userId.get(), seed));
        }
        log.info("Seeded {} demo accounts for {}", seeds.size(), email);
    }

    private Account toEntity(String userId, SeedAccount seed) {
        String accountNumber = accountNumberGenerator.generate(seed.currency());
        Account account = new Account();
        account.setId(UUID.randomUUID().toString());
        account.setUserId(userId);
        account.setAccountNumber(accountNumber);
        account.setAccountType(seed.accountType());
        account.setCurrency(seed.currency());
        account.setIban(ibanGenerator.generate(accountNumber, seed.currency()));
        account.setNickname(seed.nickname());
        account.setBalance(new BigDecimal(seed.balance()));
        account.setStatus(seed.status());
        return account;
    }

    private Optional<String> resolveUserId(String email) {
        try {
            UserReference user = userServiceClient.findByEmail(email);
            if (user == null || user.id() == null || user.id().isBlank()) {
                log.warn("user-service returned no user for {}; skipping its demo accounts. "
                    + "The user-service must expose GET /api/users/by-email?email=", email);
                return Optional.empty();
            }
            return Optional.of(user.id());
        } catch (Exception ex) {
            log.warn("Could not resolve {} from the user-service ({}); its demo accounts were skipped. "
                + "Start the user-service and restart to populate them.", email, ex.getMessage());
            return Optional.empty();
        }
    }

    private void printSummary() {
        StringBuilder out = new StringBuilder();
        out.append(System.lineSeparator()).append("=== FINOVA ACCOUNT DEMO DATA ===");
        for (Account account : accountRepository.findAll()) {
            out.append(System.lineSeparator())
                .append("  ")
                .append(AccountNumbers.format(account.getAccountNumber()))
                .append("  ")
                .append(String.format("%-9s", account.getAccountType()))
                .append(String.format("%-4s", account.getCurrency()))
                .append(String.format("%14s", account.getBalance().toPlainString()))
                .append("  ")
                .append(account.getStatus())
                .append("  ")
                .append(account.getNickname() == null ? "" : account.getNickname())
                .append("  ")
                .append(account.getIban());
        }
        out.append(System.lineSeparator())
            .append("  bank: ").append(BANK_NAME)
            .append(" | accounts: ").append(accountRepository.count())
            .append(System.lineSeparator())
            .append("=== END FINOVA ACCOUNT DEMO DATA ===");
        log.info(out.toString());
    }

    private record SeedAccount(String nickname, AccountType accountType, Currency currency,
                               String balance, AccountStatus status) {
    }
}
