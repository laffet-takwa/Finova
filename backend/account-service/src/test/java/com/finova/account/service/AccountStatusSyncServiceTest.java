package com.finova.account.service;

import com.finova.account.domain.Account;
import com.finova.account.repository.AccountRepository;
import com.finova.account.support.AccountFixtures;
import com.finova.common.domain.AccountStatus;
import com.finova.common.event.AccountBlockedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountStatusSyncServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private SettlementAccountResolver settlementAccountResolver;

    @InjectMocks
    private AccountStatusSyncService syncService;

    @Test
    void shouldBlockAnActiveAccountFromTheEvent() {
        Account account = AccountFixtures.account("acc-1", "user-1", AccountStatus.ACTIVE, new BigDecimal("10.000"));
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean applied = syncService.blockLocally(event("acc-1", "Fraud review: 88/100"));

        assertTrue(applied);
        assertEquals(AccountStatus.BLOCKED, account.getStatus());
    }

    @Test
    void shouldIgnoreItsOwnReplayedEvent() {
        Account account = AccountFixtures.account("acc-1", "user-1", AccountStatus.BLOCKED, new BigDecimal("10.000"));
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(account));

        boolean applied = syncService.blockLocally(event("acc-1", "Fraud review: 88/100"));

        assertFalse(applied);
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void shouldNotReopenAnAccountThatWasBlockedElsewhere() {
        Account account = AccountFixtures.account("acc-1", "user-1", AccountStatus.CLOSED, new BigDecimal("0.000"));
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(account));

        assertFalse(syncService.blockLocally(event("acc-1", "Fraud review: 88/100")));

        assertEquals(AccountStatus.CLOSED, account.getStatus());
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void shouldResolveTheAccountByNumberWhenTheEventCarriesNoId() {
        Account account = AccountFixtures.account("acc-2", "user-2", AccountStatus.ACTIVE, new BigDecimal("10.000"));
        when(settlementAccountResolver.findByAccountNumber(AccountFixtures.ACCOUNT_NUMBER))
            .thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertTrue(syncService.blockLocally(new AccountBlockedEvent(null, "user-2", AccountFixtures.ACCOUNT_NUMBER,
            "ACTIVE", "BLOCKED", "Fraud review", "fraud-1", new BigDecimal("10.000"), "TND", Instant.now())));
    }

    @Test
    void shouldSkipAnEventForAnUnknownAccount() {
        when(accountRepository.findById("missing")).thenReturn(Optional.empty());

        assertFalse(syncService.blockLocally(event("missing", "Fraud review")));
        assertFalse(syncService.blockLocally(null));
    }

    private static AccountBlockedEvent event(String accountId, String reason) {
        return new AccountBlockedEvent(accountId, "user-1", AccountFixtures.ACCOUNT_NUMBER,
            "ACTIVE", "BLOCKED", reason, "fraud-1", new BigDecimal("10.000"), "TND", Instant.now());
    }
}