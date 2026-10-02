package com.finova.account.service;

import com.finova.account.domain.Account;
import com.finova.account.domain.BalanceProjection;
import com.finova.account.repository.AccountRepository;
import com.finova.account.repository.BalanceProjectionRepository;
import com.finova.common.event.TransactionEvent;
import com.finova.common.support.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Applies settled transfers to the balance read projection.
 * <p>
 * Idempotency: every applied {@code transaction.completed} leaves a row in
 * {@code balance_projection} keyed by the transaction id. The marker check, both
 * balance updates and the marker insert share one transaction, so an at-least-once
 * Kafka delivery can never move money twice. Two consumers racing on the same
 * event both pass the check, but the second insert loses on the primary key and
 * its whole transaction rolls back.
 */
@Service
public class AccountProjectionService {

    private static final Logger log = LoggerFactory.getLogger(AccountProjectionService.class);

    private final AccountRepository accountRepository;
    private final BalanceProjectionRepository balanceProjectionRepository;
    private final SettlementAccountResolver settlementAccountResolver;

    public AccountProjectionService(AccountRepository accountRepository,
                                    BalanceProjectionRepository balanceProjectionRepository,
                                    SettlementAccountResolver settlementAccountResolver) {
        this.accountRepository = accountRepository;
        this.balanceProjectionRepository = balanceProjectionRepository;
        this.settlementAccountResolver = settlementAccountResolver;
    }

    /**
     * @return {@code true} when the event was applied, {@code false} when it was skipped
     */
    @Transactional
    public boolean applyCompleted(TransactionEvent transaction) {
        if (transaction == null || transaction.transactionId() == null) {
            log.warn("Discarding transaction.completed without a transaction id");
            return false;
        }
        if (balanceProjectionRepository.existsById(transaction.transactionId())) {
            log.info("Balance projection already applied for transactionId={}", transaction.transactionId());
            return false;
        }
        BigDecimal amount = Money.scale(transaction.amount());
        if (amount == null || amount.signum() <= 0) {
            log.warn("Discarding transaction.completed with a non positive amount transactionId={} amount={}",
                transaction.transactionId(), transaction.amount());
            return false;
        }

        credit(transaction.receiverAccountId(), transaction.receiverAccountNumber(), amount, transaction);
        debit(transaction.senderAccountId(), transaction.senderAccountNumber(), amount, transaction);
        balanceProjectionRepository.save(new BalanceProjection(transaction.transactionId(), Instant.now()));
        return true;
    }

    public void onTransactionFailed(TransactionEvent transaction) {        if (transaction == null) {
            return;
        }
        log.debug("No balance projection for failed transaction transactionId={} reason={}",
            transaction.transactionId(), transaction.failureReason());
    }

    private void credit(String accountId, String accountNumber, BigDecimal amount, TransactionEvent transaction) {
        Optional<Account> account = resolve(accountId, accountNumber);
        if (account.isEmpty()) {
            log.error("Receiver account not found for transactionId={} accountId={} accountNumber={}",
                transaction.transactionId(), accountId, accountNumber);
            return;
        }
        Account receiver = account.get();
        receiver.setBalance(Money.scale(receiver.getBalance().add(amount)));
        accountRepository.save(receiver);
    }

    private void debit(String accountId, String accountNumber, BigDecimal amount, TransactionEvent transaction) {
        Optional<Account> account = resolve(accountId, accountNumber);
        if (account.isEmpty()) {
            log.error("Sender account not found for transactionId={} accountId={} accountNumber={}",
                transaction.transactionId(), accountId, accountNumber);
            return;
        }
        Account sender = account.get();
        BigDecimal projected = Money.scale(sender.getBalance().subtract(amount));
        if (projected.signum() < 0) {
            log.warn("Projection would go negative for accountId={} balance={} amount={}; clamping to zero "
                    + "because the transaction-service owns the authoritative ledger",
                sender.getId(), sender.getBalance().toPlainString(), amount.toPlainString());
            projected = Money.ZERO;
        }
        sender.setBalance(projected);
        accountRepository.save(sender);
    }

    private Optional<Account> resolve(String accountId, String accountNumber) {
        if (accountId != null && !accountId.isBlank()) {
            return accountRepository.findById(accountId);
        }
        return settlementAccountResolver.findByAccountNumber(accountNumber);
    }
}
