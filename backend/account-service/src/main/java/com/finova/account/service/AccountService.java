package com.finova.account.service;

import com.finova.account.domain.Account;
import com.finova.account.dto.AccountListFilter;
import com.finova.account.dto.AccountLookupResponse;
import com.finova.account.dto.AccountResponse;
import com.finova.account.dto.AccountSearchCriteria;
import com.finova.account.dto.BalanceResponse;
import com.finova.account.dto.CreateAccountRequest;
import com.finova.account.dto.StatusUpdateRequest;
import com.finova.account.event.EventPublisher;
import com.finova.account.mapper.AccountMapper;
import com.finova.account.repository.AccountRepository;
import com.finova.account.support.AccountNumberGenerator;
import com.finova.account.support.AccountNumbers;
import com.finova.account.support.IbanGenerator;
import com.finova.common.audit.AuditAction;
import com.finova.common.event.AuditRecordEvent;
import com.finova.common.domain.AccountStatus;
import com.finova.common.domain.AccountType;
import com.finova.common.domain.Currency;
import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import com.finova.common.event.AccountBlockedEvent;
import com.finova.common.event.AccountOpenedEvent;
import com.finova.common.event.EventType;
import com.finova.common.event.Topics;
import com.finova.common.security.CurrentUser;
import com.finova.common.support.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the account product: creation, reads, and the status lifecycle.
 * <p>
 * Balance ownership rule (EVENT-FLOW.md): this service never moves money because
 * of a user request. The only balance writes here are the opening balance at
 * creation; afterwards the balance is a projection fed by {@code transaction.completed}.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    public static final int MAX_ACCOUNTS_PER_USER = 3;
    public static final int NUMBER_GENERATION_ATTEMPTS = 5;
    public static final BigDecimal MAX_OPENING_BALANCE = new BigDecimal("50000.000");
    public static final BigDecimal DEFAULT_CHECKING_BALANCE = new BigDecimal("2500.000");
    public static final BigDecimal DEFAULT_SAVINGS_BALANCE = new BigDecimal("5000.000");

private final AccountRepository accountRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final IbanGenerator ibanGenerator;
    private final EventPublisher eventPublisher;
    private final AccountMapper accountMapper;
    private final UserEmailResolver userEmailResolver;

    public AccountService(AccountRepository accountRepository,
                          AccountNumberGenerator accountNumberGenerator,
                          IbanGenerator ibanGenerator,
                          EventPublisher eventPublisher,
                          AccountMapper accountMapper,
                          UserEmailResolver userEmailResolver) {
        this.accountRepository = accountRepository;
        this.accountNumberGenerator = accountNumberGenerator;
        this.ibanGenerator = ibanGenerator;
        this.eventPublisher = eventPublisher;
        this.accountMapper = accountMapper;
        this.userEmailResolver = userEmailResolver;
    }

    @Transactional
    public AccountResponse create(String userId, CreateAccountRequest request) {
        CurrentUser.checkOwnershipOrAdmin(userId);
        if (request.userId() != null && !request.userId().isBlank() && !CurrentUser.isAdmin()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                "Only an administrator can open an account on behalf of another customer.");
        }
        if (accountRepository.existsByUserIdAndAccountTypeAndCurrency(userId, request.accountType(), request.currency())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
                "This customer already holds a " + request.accountType() + " account in " + request.currency() + ".");
        }
        if (accountRepository.countByUserId(userId) >= MAX_ACCOUNTS_PER_USER) {
            throw new BusinessException(ErrorCode.ACCOUNT_LIMIT_REACHED,
                "A customer may hold at most " + MAX_ACCOUNTS_PER_USER + " accounts.");
        }

        String accountNumber = generateUniqueAccountNumber(request.currency());
        Account account = new Account();
        account.setId(UUID.randomUUID().toString());
        account.setUserId(userId);
        account.setAccountNumber(accountNumber);
        account.setAccountType(request.accountType());
        account.setCurrency(request.currency());
        account.setIban(ibanGenerator.generate(accountNumber, request.currency()));
        account.setNickname(normaliseNickname(request.nickname()));
        account.setBalance(resolveOpeningBalance(request));
        account.setStatus(AccountStatus.ACTIVE);
        Account saved = accountRepository.save(account);

        publishOpened(saved, false);
        eventPublisher.publishAudit(AuditAction.ACCOUNT_CREATED, userId, "ACCOUNT", saved.getId(), null,
            AuditRecordEvent.RESULT_SUCCESS, "Account opened",
            Map.of("accountNumber", accountNumber, "accountType", request.accountType().name(),
                "currency", request.currency().name(), "openingBalance", saved.getBalance().toPlainString()));
        return accountMapper.toResponse(saved);
    }

/**
     * The caller's own accounts. The owner scope is forced to {@code callerId}, so no
     * filter a caller supplies can widen it: a CUSTOMER filtering by someone else's
     * account number or email gets an empty list rather than the other holder's account.
     */
    @Transactional(readOnly = true)
    public List<AccountResponse> listForUser(String callerId, AccountListFilter filter) {
        CurrentUser.checkOwnershipOrAdmin(callerId);
        return find(filter.scopedTo(callerId));
    }

    /** ADMIN scope: {@code userId}, {@code accountNumber} and {@code email} may cross customers. */
    @Transactional(readOnly = true)
    public List<AccountResponse> listForAdmin(AccountListFilter filter) {
        return find(filter);
    }

    private List<AccountResponse> find(AccountListFilter filter) {
        if (filter.accountNumber() != null) {
            return accountRepository.findByAccountNumber(filter.accountNumber())
                .filter(account -> matchesOwnerFilter(account, filter))
                .map(accountMapper::toResponse)
                .map(List::of)
                .orElseGet(List::of);
        }
        String userId = filter.userId();
        if (filter.email() != null) {
            Optional<String> ownerId = userEmailResolver.resolveUserId(filter.email());
            if (ownerId.isEmpty() || (userId != null && !userId.equals(ownerId.get()))) {
                return List.of();
            }
            userId = ownerId.get();
        }
        List<Account> accounts = userId == null
            ? accountRepository.findAll()
            : accountRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return accounts.stream().map(accountMapper::toResponse).toList();
    }

    private boolean matchesOwnerFilter(Account account, AccountListFilter filter) {
        if (filter.userId() != null && !filter.userId().equals(account.getUserId())) {
            return false;
        }
        if (filter.email() == null) {
            return true;
        }
        return userEmailResolver.resolveUserId(filter.email())
            .map(account.getUserId()::equals)
            .orElse(false);
    }

    @Transactional(readOnly = true)
    public AccountResponse getOwned(String userId, String accountId) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> BusinessException.notFound(ErrorCode.ACCOUNT_NOT_FOUND, "Account", accountId));
        CurrentUser.checkOwnershipOrAdmin(account.getUserId());
        return accountMapper.toResponse(account);
    }

    @Transactional(readOnly = true)
    public BalanceResponse balance(String userId, String accountId) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> BusinessException.notFound(ErrorCode.ACCOUNT_NOT_FOUND, "Account", accountId));
        CurrentUser.checkOwnershipOrAdmin(account.getUserId());
        return accountMapper.toBalance(account);
    }

    /**
     * Minimal receiver information for the transfer review screen. The holder
     * name is never exposed, and a customer may not look up their own account
     * as a beneficiary.
     */
    @Transactional(readOnly = true)
    public AccountLookupResponse lookupBeneficiary(String accountNumber) {
        String normalised = AccountNumbers.normalise(accountNumber);
        if (normalised == null || !AccountNumbers.isCanonical(normalised)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "The account number format is not recognised.");
        }
        Account account = accountRepository.findByAccountNumber(normalised)
            .orElseThrow(() -> BusinessException.notFound(ErrorCode.ACCOUNT_NOT_FOUND, "Account", normalised));
        if (account.getUserId().equals(CurrentUser.userId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "A transfer destination must be an account you do not hold yourself.");
        }
        return accountMapper.toLookup(account);
    }

    @Transactional
    public AccountResponse updateStatus(String actorId, String accountId, StatusUpdateRequest request) {
        if (!CurrentUser.isAdmin()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                "Only an administrator can change an account status.");
        }
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> BusinessException.notFound(ErrorCode.ACCOUNT_NOT_FOUND, "Account", accountId));
        AccountStatus previous = account.getStatus();
        AccountStatus target = request.status();

if (previous == AccountStatus.CLOSED) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "A closed account cannot change status.");
        }
        if (previous == target) {
            // Idempotent no-op: the fraud-service blocks accounts from an automated
            // review that can retry the same decision, and replaying it must not fail.
            log.info("Account {} is already {}, nothing to change actorId={}", accountId, target, actorId);
            return accountMapper.toResponse(account);
        }
        if (target == AccountStatus.CLOSED && Money.scale(account.getBalance()).signum() != 0) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED,
                "An account with a non-zero balance cannot be closed.");
        }
        account.setStatus(target);
        Account saved = accountRepository.save(account);

        if (target == AccountStatus.BLOCKED) {
            eventPublisher.publish(Topics.ACCOUNT_BLOCKED, EventType.ACCOUNT_BLOCKED, new AccountBlockedEvent(
                saved.getId(), saved.getUserId(), saved.getAccountNumber(), previous.name(), target.name(),
                request.reason(), actorId, saved.getBalance(), saved.getCurrency().name(), Instant.now()));
            eventPublisher.publishAudit(AuditAction.ACCOUNT_BLOCKED, saved.getUserId(), "ACCOUNT", saved.getId(), null,
                AuditRecordEvent.RESULT_SUCCESS, reasonOrDefault(request.reason(), "Account blocked"),
                Map.of("previousStatus", previous.name(), "status", target.name()));
        } else {
            if (previous == AccountStatus.BLOCKED) {
                publishOpened(saved, true);
            }
            eventPublisher.publishAudit(AuditAction.ACCOUNT_STATUS_CHANGED, saved.getUserId(), "ACCOUNT",
                saved.getId(), null, AuditRecordEvent.RESULT_SUCCESS,
                reasonOrDefault(request.reason(), "Account status changed to " + target),
                Map.of("previousStatus", previous.name(), "status", target.name()));
        }
        return accountMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<Account> search(AccountSearchCriteria criteria, Pageable pageable) {
        return accountRepository.findAll(toSpecification(criteria), pageable);
    }

    /**
     * Built with the criteria API rather than a JPQL string because every filter
     * is optional: binding an unset enum or {@code Instant} as a named parameter
     * makes the parameter type ambiguous, while a predicate per filter stays type safe.
     */
    private Specification<Account> toSpecification(AccountSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.search() != null) {
                String pattern = "%" + criteria.search().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                    builder.like(builder.lower(root.get("accountNumber")), pattern),
                    builder.like(builder.lower(root.get("userId")), pattern)));
            }
            if (criteria.status() != null) {
                predicates.add(builder.equal(root.get("status"), enumFilter(AccountStatus.class, criteria.status(), "status")));
            }
            if (criteria.accountType() != null) {
                predicates.add(builder.equal(root.get("accountType"),
                    enumFilter(AccountType.class, criteria.accountType(), "accountType")));
            }
            if (criteria.currency() != null) {
                predicates.add(builder.equal(root.get("currency"),
                    enumFilter(Currency.class, criteria.currency(), "currency")));
            }
            if (criteria.userId() != null) {
                predicates.add(builder.equal(root.get("userId"), criteria.userId()));
            }
            if (criteria.minBalance() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("balance"), criteria.minBalance()));
            }
            if (criteria.maxBalance() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("balance"), criteria.maxBalance()));
            }
            if (criteria.createdFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), criteria.createdFrom()));
            }
            if (criteria.createdTo() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), criteria.createdTo()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void publishOpened(Account account, boolean restatement) {
        eventPublisher.publish(Topics.ACCOUNT_OPENED, EventType.ACCOUNT_OPENED, new AccountOpenedEvent(
            account.getId(), account.getUserId(), account.getAccountNumber(), account.getAccountType().name(),
            account.getCurrency().name(), account.getStatus().name(), Money.scale(account.getBalance()), restatement));
    }

    private String generateUniqueAccountNumber(Currency currency) {
        for (int attempt = 1; attempt <= NUMBER_GENERATION_ATTEMPTS; attempt++) {
            String candidate = accountNumberGenerator.generate(currency);
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
            log.warn("Account number collision on attempt {} for currency {}", attempt, currency);
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR,
            "Could not allocate a unique account number after " + NUMBER_GENERATION_ATTEMPTS + " attempts.");
    }

    private BigDecimal resolveOpeningBalance(CreateAccountRequest request) {
        if (request.openingBalance() == null) {
            return request.accountType() == AccountType.SAVINGS
                ? DEFAULT_SAVINGS_BALANCE
                : DEFAULT_CHECKING_BALANCE;
        }
        BigDecimal requested = Money.scale(request.openingBalance());
        if (requested.signum() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "The opening balance must not be negative.");
        }
        if (requested.compareTo(MAX_OPENING_BALANCE) > 0) {
            log.warn("Opening balance {} clamped to {}", requested.toPlainString(), MAX_OPENING_BALANCE.toPlainString());
            return MAX_OPENING_BALANCE;
        }
        return requested;
    }

    private String normaliseNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return null;
        }
        return nickname.trim();
    }

    private String reasonOrDefault(String reason, String fallback) {
        return reason == null || reason.isBlank() ? fallback : reason;
    }

    private static <E extends Enum<E>> E enumFilter(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Unsupported " + field + " filter: " + value);
        }
    }
}
