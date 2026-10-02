package com.finova.transaction.config;

import com.finova.common.domain.Currency;
import com.finova.common.domain.TransactionStatus;
import com.finova.common.domain.TransactionType;
import com.finova.common.support.Money;
import com.finova.transaction.client.AccountLookupResponse;
import com.finova.transaction.client.AccountServiceClient;
import com.finova.transaction.domain.LedgerAccount;
import com.finova.transaction.domain.LedgerEvent;
import com.finova.transaction.domain.Transaction;
import com.finova.transaction.dto.TransferRequest;
import com.finova.transaction.mapper.AccountNumbers;
import com.finova.transaction.repository.LedgerAccountRepository;
import com.finova.transaction.repository.LedgerEventRepository;
import com.finova.transaction.repository.TransactionRepository;
import com.finova.transaction.service.ReferenceGenerator;
import com.finova.transaction.service.RequestFingerprint;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Realistic demo ledger for the local stack (profile {@code dev} only).
 * <p>
 * Demo accounts are configured by account number and resolved through the
 * beneficiary lookup, the same single directory call the transfer flow uses. The
 * directory is never asked to map an email to accounts: that endpoint is
 * owner-scoped, so it could not enumerate the demo holders' accounts in the first
 * place.
 * <p>
 * If the directory is unreachable, or the demo numbers are not configured, the
 * seeder logs a warning and leaves the ledger empty - it must never stop this
 * service from starting.
 * <p>
 * The money is internally consistent. Every completed seed moves real money and
 * writes both double-entry legs, the walk runs in chronological order, and the
 * opening balance is back-solved so the closing checking and savings balances
 * land exactly on the figures the demo dashboard shows.
 */
@Configuration
@Profile("dev")
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String PRIMARY_EMAIL = "takwa@finova.dev";
    private static final String DEMO_CURRENCY = Currency.TND.name();
    private static final BigDecimal CHECKING_TARGET = new BigDecimal("12450.750");
    private static final BigDecimal SAVINGS_TARGET = new BigDecimal("5800.000");
    private static final LocalTime SEED_TIME = LocalTime.of(9, 15);

    private final AccountServiceClient accountServiceClient;
    private final LedgerAccountRepository ledgerRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEventRepository ledgerEventRepository;
    private final ReferenceGenerator referenceGenerator;
    private final RequestFingerprint fingerprint;
    private final AccountNumbers accountNumbers;
    private final TransactionProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public DataSeeder(AccountServiceClient accountServiceClient,
                      LedgerAccountRepository ledgerRepository,
                      TransactionRepository transactionRepository,
                      LedgerEventRepository ledgerEventRepository,
                      ReferenceGenerator referenceGenerator,
                      RequestFingerprint fingerprint,
                      AccountNumbers accountNumbers,
                      TransactionProperties properties,
                      Clock clock,
                      PlatformTransactionManager transactionManager) {
        this.accountServiceClient = accountServiceClient;
        this.ledgerRepository = ledgerRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEventRepository = ledgerEventRepository;
        this.referenceGenerator = referenceGenerator;
        this.fingerprint = fingerprint;
        this.accountNumbers = accountNumbers;
        this.properties = properties;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Bean
    CommandLineRunner seedDemoData() {
        return args -> {
            try {
                transactions.executeWithoutResult(status -> seed());
            } catch (RuntimeException ex) {
                log.warn("Demo data could not be seeded; the service continues with an empty ledger: {}",
                        ex.getMessage());
            }
        };
    }

    public void seed() {
        if (transactionRepository.count() > 0) {
            log.info("Ledger already holds {} transactions, demo data not seeded", transactionRepository.count());
            return;
        }
        LedgerAccount checking = resolveDemoAccount(properties.getDemo().getPrimaryChecking(), "primary-checking");
        LedgerAccount savings = resolveDemoAccount(properties.getDemo().getPrimarySavings(), "primary-savings");
        if (checking == null || savings == null) {
            log.warn("Demo account numbers are not configured; ledger left empty. "
                    + "Set finova.transactions.demo.primary-checking and .primary-savings.");
            return;
        }
        List<LedgerAccount> counterparties = new ArrayList<>();
        for (String number : properties.getDemo().getCounterparties()) {
            LedgerAccount counterparty = resolveDemoAccount(number, "counterparty");
            if (counterparty != null) {
                counterparties.add(counterparty);
            }
        }
        if (counterparties.isEmpty()) {
            log.warn("No counterparty accounts resolved; ledger left empty. "
                    + "Set finova.transactions.demo.counterparties.");
            return;
        }

        List<Seed> seeds = seeds(LocalDate.now(clock.withZone(ZoneOffset.UTC)), counterparties.size());
        checking.setBalance(Money.scale(CHECKING_TARGET.subtract(netEffect(seeds, false))));
        savings.setBalance(Money.scale(SAVINGS_TARGET.subtract(netEffect(seeds, true))));
        ledgerRepository.save(checking);
        ledgerRepository.save(savings);

        int completed = 0;
        int failed = 0;
        int flagged = 0;
        for (Seed seed : seeds) {
            switch (seed.status()) {
                case COMPLETED -> completed += settle(checking, savings, counterparties, seed);
                case FAILED -> {
                    recordTerminal(checking, savings, counterparties, seed, TransactionStatus.FAILED);
                    failed++;
                }
                default -> {
                    recordTerminal(checking, savings, counterparties, seed, TransactionStatus.FLAGGED);
                    flagged++;
                }
            }
        }
        report(checking, savings, completed, failed, flagged);
    }

    private int settle(LedgerAccount checking, LedgerAccount savings, List<LedgerAccount> counterparties, Seed seed) {
        LedgerAccount own = seed.savings() ? savings : checking;
        LedgerAccount other = counterparties.get(seed.partyIndex());
        LedgerAccount sender = seed.outgoing() ? own : other;
        LedgerAccount receiver = seed.outgoing() ? other : own;
        if (sender.getBalance().compareTo(seed.amount()) < 0) {
            log.warn("Demo seed {} skipped, {} has no funds for {}", seed.description(),
                    accountNumbers.mask(sender.getAccountNumber()), seed.amount());
            return 0;
        }
        Instant at = seed.at();
        BigDecimal senderBefore = sender.getBalance();
        BigDecimal receiverBefore = receiver.getBalance();
        sender.setBalance(Money.scale(senderBefore.subtract(seed.amount())));
        receiver.setBalance(Money.scale(receiverBefore.add(seed.amount())));

        Transaction transaction = transaction(seed, sender, receiver, at, TransactionStatus.COMPLETED);
        transaction.setSettledSenderBalance(sender.getBalance());
        transaction.setSettledReceiverBalance(receiver.getBalance());
        transactionRepository.save(transaction);
        ledgerEventRepository.save(leg(transaction, sender, LedgerEvent.DIRECTION_DEBIT, seed.amount(),
                senderBefore, sender.getBalance(), at));
        ledgerEventRepository.save(leg(transaction, receiver, LedgerEvent.DIRECTION_CREDIT, seed.amount(),
                receiverBefore, receiver.getBalance(), at));
        return 1;
    }

    private void recordTerminal(LedgerAccount checking, LedgerAccount savings, List<LedgerAccount> counterparties,
                                Seed seed, TransactionStatus status) {
        LedgerAccount own = seed.savings() ? savings : checking;
        LedgerAccount other = counterparties.get(seed.partyIndex());
        LedgerAccount sender = seed.outgoing() ? own : other;
        LedgerAccount receiver = seed.outgoing() ? other : own;
        Transaction transaction = transaction(seed, sender, receiver, seed.at(), status);
        transaction.setFailureReason(seed.failureReason());
        if (status == TransactionStatus.FLAGGED) {
            transaction.setRiskScore(88);
            transaction.setRiskLevel("HIGH");
            transaction.setRiskReasons(List.of("Unusual amount for this counterparty",
                    "First transfer to this beneficiary",
                    "Velocity rule: 4 transfers in 10 minutes"));
        }
        transactionRepository.save(transaction);
    }

    private Transaction transaction(Seed seed, LedgerAccount sender, LedgerAccount receiver, Instant at,
                                    TransactionStatus status) {
        TransferRequest request = new TransferRequest(sender.getAccountId(), receiver.getAccountNumber(),
                seed.amount(), DEMO_CURRENCY, seed.description());
        Transaction transaction = new Transaction();
        transaction.setId(UUID.randomUUID().toString());
        transaction.setReference(referenceGenerator.nextFor(at.atZone(ZoneOffset.UTC).toLocalDate()));
        transaction.setIdempotencyKey("seed-" + UUID.randomUUID());
        transaction.setRequestFingerprint(fingerprint.of(request));
        transaction.setSenderAccountId(sender.getAccountId());
        transaction.setReceiverAccountId(receiver.getAccountId());
        transaction.setSenderAccountNumber(sender.getAccountNumber());
        transaction.setReceiverAccountNumber(receiver.getAccountNumber());
        transaction.setSenderUserId(sender.getUserId());
        transaction.setReceiverUserId(receiver.getUserId());
        transaction.setAmount(seed.amount());
        transaction.setCurrency(DEMO_CURRENCY);
        transaction.setFee(Money.ZERO);
        transaction.setDescription(seed.description());
        transaction.setType(TransactionType.TRANSFER.name());
        transaction.setStatus(status.name());
        transaction.setCreatedAt(at);
        transaction.setCorrelationId("seed-" + at.toEpochMilli());
        transaction.setRequestedByUserId(sender.getUserId());
        return transactionRepository.save(transaction);
    }

    private LedgerEvent leg(Transaction transaction, LedgerAccount account, String direction, BigDecimal amount,
                            BigDecimal before, BigDecimal after, Instant at) {
        LedgerEvent event = new LedgerEvent();
        event.setId(UUID.randomUUID().toString());
        event.setTransactionId(transaction.getId());
        event.setLedgerAccountId(account.getId());
        event.setAccountId(account.getAccountId());
        event.setDirection(direction);
        event.setAmount(amount);
        event.setBalanceBefore(before);
        event.setBalanceAfter(after);
        event.setCurrency(DEMO_CURRENCY);
        event.setCreatedAt(at);
        event.setReference(transaction.getReference());
        return event;
    }

    /**
     * Sum of every completed seed that touches the chosen own account, signed by
     * direction. Back-solving this from the closing target is what makes the
     * dashboard totals and the ledger balance agree.
     */
    private BigDecimal netEffect(List<Seed> seeds, boolean savingsLeg) {
        BigDecimal net = Money.ZERO;
        for (Seed seed : seeds) {
            if (seed.status() != TransactionStatus.COMPLETED || seed.savings() != savingsLeg) {
                continue;
            }
            BigDecimal signed = seed.outgoing() ? seed.amount().negate() : seed.amount();
            net = Money.scale(net.add(signed));
        }
        return net;
    }

    /**
     * Resolves one configured demo account number through the beneficiary lookup and
     * mirrors it onto the ledger.
     * <p>
     * Every failure is swallowed into a warning: the seeder must never stop the
     * service from starting, and an unreachable directory simply means no demo
     * data. The opening balance is irrelevant here because {@link #seed()}
     * overwrites it with the back-solved value that makes the closing figures add
     * up.
     */
    private LedgerAccount resolveDemoAccount(String rawNumber, String role) {
        String number = accountNumbers.normalise(rawNumber);
        if (number == null || number.isEmpty()) {
            return null;
        }
        try {
            AccountLookupResponse lookup = accountServiceClient.lookupBeneficiary(number);
            if (lookup == null || lookup.accountId() == null || lookup.accountId().isBlank()) {
                log.warn("Demo {} account {} is not in the account directory", role, number);
                return null;
            }
            return ledgerRepository.findByAccountId(lookup.accountId()).orElseGet(() -> {
                LedgerAccount created = new LedgerAccount();
                created.setId(UUID.randomUUID().toString());
                created.setAccountId(lookup.accountId());
                created.setAccountNumber(resolveNumber(lookup, number));
                created.setUserId(lookup.userId());
                created.setAccountType(lookup.accountType());
                created.setCurrency(lookup.currency());
                created.setBalance(Money.ZERO);
                created.setStatus(lookup.status());
                return ledgerRepository.save(created);
            });
        } catch (FeignException ex) {
            log.warn("Account directory unreachable while resolving demo {} account {}: {}",
                    role, number, ex.getMessage());
            return null;
        }
    }

    private String resolveNumber(AccountLookupResponse lookup, String requestedNumber) {
        String returned = accountNumbers.normalise(lookup.accountNumber());
        return returned == null || returned.isEmpty() ? requestedNumber : returned;
    }

    private List<Seed> seeds(LocalDate today, int counterpartyCount) {
        List<Seed> seeds = new ArrayList<>();
        int party = 0;
        int[] history = {1, 2, 5, 9, 5, 3, 7, 12, 20, 26, 0, 4, 1, 2, 3, 4, 8, 11, 17, 21, 24, 28, 33, 38, 42, 43, 44, 7, 30, 41};
        String[] descriptions = {
                "Netflix", "Coffee Shop", "Electricity bill", "Grocery shopping", "Restaurant — La Marsa",
                "Fuel", "Insurance premium", "University tuition", "Rent payment", "Monthly salary",
                "Family transfer", "Transfer", "Family transfer", "Coffee Shop", "Restaurant — La Marsa",
                "Electricity bill", "Monthly salary", "Grocery shopping", "Transfer", "Fuel", "Insurance premium",
                "Rent payment", "Restaurant — La Marsa", "Netflix", "Grocery shopping", "Transfer", "Transfer",
                "Transfer", "Transfer", "Transfer"};
        String[] amounts = {
                "15.990", "8.500", "93.400", "147.850", "64.000", "52.300", "118.000", "1600.000", "750.000",
                "2500.000", "200.000", "250.000", "120.000", "11.200", "48.750", "88.100", "2500.000", "96.450",
                "350.000", "48.900", "118.000", "750.000", "39.900", "15.990", "71.300", "5000.000", "15.000",
                "3200.000", "980.000", "240.000"};
        boolean[] outgoing = {
                true, true, true, true, true, true, true, true, true, false, false, true, false, true, true, true,
                false, true, false, true, true, true, true, true, true, true, true, true, true, true};
        boolean[] savingsLeg = {
                false, false, false, false, false, false, false, false, false, false, false, false, false, false,
                false, false, false, false, true, false, false, false, false, false, false, false, true, false,
                false, false};

        for (int index = 0; index < descriptions.length; index++) {
            party = Math.floorMod(party + 1 + index, counterpartyCount);
            int daysAgo = history[index];
            int minuteOffset = (index * 7) % 60;
            Instant at = today.minusDays(daysAgo).atTime(SEED_TIME).plusMinutes(minuteOffset)
                    .toInstant(ZoneOffset.UTC);
            TransactionStatus status = TransactionStatus.COMPLETED;
            String failureReason = null;
            if (index == 27) {
                status = TransactionStatus.FAILED;
                failureReason = "Sender balance was insufficient at settlement time.";
            } else if (index == 28) {
                status = TransactionStatus.FAILED;
                failureReason = "An account involved in this transfer is not active.";
            } else if (index == 29) {
                status = TransactionStatus.FLAGGED;
                failureReason = "Held for fraud review: Unusual amount for this counterparty";
            }
            seeds.add(new Seed(descriptions[index], new BigDecimal(amounts[index]), at, outgoing[index],
                    savingsLeg[index], party, status, failureReason));
        }
        return seeds;
    }

    private void report(LedgerAccount checking, LedgerAccount savings, int completed, int failed, int flagged) {
        log.warn("""

                === FINOVA TRANSACTION DEMO DATA ===
                  demo customer    : {}
                  checking account : {} -> {} {}
                  savings account  : {} -> {} {}
                  transfers        : {} completed, {} failed, {} flagged
                ================================
                """,
                PRIMARY_EMAIL,
                accountNumbers.mask(checking.getAccountNumber()), Money.scale(checking.getBalance()), DEMO_CURRENCY,
                accountNumbers.mask(savings.getAccountNumber()), Money.scale(savings.getBalance()), DEMO_CURRENCY,
                completed, failed, flagged);
    }

    /** One demo transfer instruction with the instant it is booked at. */
    private record Seed(
            String description,
            BigDecimal amount,
            Instant at,
            boolean outgoing,
            boolean savings,
            int partyIndex,
            TransactionStatus status,
            String failureReason
    ) {
    }
}