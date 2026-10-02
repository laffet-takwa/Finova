package com.finova.account.service;

import com.finova.account.domain.Account;
import com.finova.account.dto.AccountListFilter;
import com.finova.account.dto.AccountResponse;
import com.finova.account.dto.CreateAccountRequest;
import com.finova.account.dto.StatusUpdateRequest;
import com.finova.account.event.EventPublisher;
import com.finova.account.mapper.AccountMapperImpl;
import com.finova.account.repository.AccountRepository;
import com.finova.account.support.AccountFixtures;
import com.finova.account.support.AccountNumberGenerator;
import com.finova.account.support.IbanGenerator;
import com.finova.account.support.SecurityContextTestSupport;
import com.finova.common.audit.AuditAction;
import com.finova.common.domain.AccountStatus;
import com.finova.common.domain.AccountType;
import com.finova.common.domain.Currency;
import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import com.finova.common.event.AccountBlockedEvent;
import com.finova.common.event.AccountOpenedEvent;
import com.finova.common.event.EventType;
import com.finova.common.event.Topics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String CUSTOMER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER_CUSTOMER_ID = "22222222-2222-2222-2222-222222222222";
    private static final String ADMIN_ID = "99999999-9999-9999-9999-999999999999";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountNumberGenerator accountNumberGenerator;

    @Mock
    private IbanGenerator ibanGenerator;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private UserEmailResolver userEmailResolver;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, accountNumberGenerator, ibanGenerator,
            eventPublisher, new AccountMapperImpl(), userEmailResolver);
        SecurityContextTestSupport.asCustomer(CUSTOMER_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextTestSupport.clear();
    }

    @Test
    void shouldCreateAccountAndPublishOpenedEventWhenRequestIsValid() {
        CreateAccountRequest request = new CreateAccountRequest(AccountType.CHECKING, Currency.TND,
            "Everyday Account", null, null);
        stubHappyPathCreation();

        AccountResponse response = accountService.create(CUSTOMER_ID, request);

        assertEquals("TN58 1000 0123 4567 8901 23", response.accountNumber());
        assertEquals("TN58 •••• •••• 8901 23", response.maskedAccountNumber());
        assertEquals(CUSTOMER_ID, response.userId());
        assertEquals(new BigDecimal("2500.000"), response.balance());
        assertEquals(response.balance(), response.availableBalance());
        assertEquals(AccountStatus.ACTIVE, response.status());
        assertEquals(AccountFixtures.IBAN, response.iban());
        assertEquals("Everyday Account", response.nickname());

        ArgumentCaptor<AccountOpenedEvent> opened = ArgumentCaptor.forClass(AccountOpenedEvent.class);
        verify(eventPublisher).publish(eq(Topics.ACCOUNT_OPENED), eq(EventType.ACCOUNT_OPENED), opened.capture());
        assertEquals(new BigDecimal("2500.000"), opened.getValue().openingBalance());
        assertEquals(AccountStatus.ACTIVE.name(), opened.getValue().status());
        assertTrue(!opened.getValue().restatement(), "creation is not a restatement");
        verify(eventPublisher).publishAudit(eq(AuditAction.ACCOUNT_CREATED), eq(CUSTOMER_ID), eq("ACCOUNT"),
            anyString(), any(), anyString(), anyString(), any(Map.class));
    }

    @Test
    void shouldDefaultSavingsOpeningBalanceToFiveThousand() {
        stubHappyPathCreation();
        CreateAccountRequest request = new CreateAccountRequest(AccountType.SAVINGS, Currency.TND, null, null, null);

        AccountResponse response = accountService.create(CUSTOMER_ID, request);

        assertEquals(new BigDecimal("5000.000"), response.balance());
    }

    @Test
    void shouldClampOpeningBalanceToFiftyThousand() {
        stubHappyPathCreation();
        CreateAccountRequest request = new CreateAccountRequest(AccountType.CHECKING, Currency.TND, null,
            new BigDecimal("120000.000"), null);

        AccountResponse response = accountService.create(CUSTOMER_ID, request);

        assertEquals(new BigDecimal("50000.000"), response.balance());
    }

    @Test
    void shouldRejectNegativeOpeningBalance() {
        CreateAccountRequest request = new CreateAccountRequest(AccountType.CHECKING, Currency.TND, null,
            new BigDecimal("-1.000"), null);
        when(accountRepository.existsByUserIdAndAccountTypeAndCurrency(CUSTOMER_ID, AccountType.CHECKING, Currency.TND))
            .thenReturn(false);
        when(accountRepository.countByUserId(CUSTOMER_ID)).thenReturn(0L);

        BusinessException thrown = assertThrows(BusinessException.class,
            () -> accountService.create(CUSTOMER_ID, request));

        assertEquals(ErrorCode.VALIDATION_ERROR, thrown.getErrorCode());
    }

    @Test
    void shouldThrowDuplicateResourceWhenSameTypeAndCurrencyAlreadyExists() {
        CreateAccountRequest request = new CreateAccountRequest(AccountType.CHECKING, Currency.TND, null, null, null);
        when(accountRepository.existsByUserIdAndAccountTypeAndCurrency(CUSTOMER_ID, AccountType.CHECKING, Currency.TND))
            .thenReturn(true);

        BusinessException thrown = assertThrows(BusinessException.class,
            () -> accountService.create(CUSTOMER_ID, request));

        assertEquals(ErrorCode.DUPLICATE_RESOURCE, thrown.getErrorCode());
        verify(eventPublisher, never()).publish(anyString(), any(EventType.class), any());
    }

    @Test
    void shouldThrowAccountLimitReachedOnTheFourthAccount() {
        CreateAccountRequest request = new CreateAccountRequest(AccountType.SAVINGS, Currency.EUR, null, null, null);
        when(accountRepository.existsByUserIdAndAccountTypeAndCurrency(CUSTOMER_ID, AccountType.SAVINGS, Currency.EUR))
            .thenReturn(false);
        when(accountRepository.countByUserId(CUSTOMER_ID)).thenReturn(3L);

        BusinessException thrown = assertThrows(BusinessException.class,
            () -> accountService.create(CUSTOMER_ID, request));

        assertEquals(ErrorCode.ACCOUNT_LIMIT_REACHED, thrown.getErrorCode());
    }

    @Test
    void shouldThrowAccessDeniedWhenReadingAnotherCustomersAccount() {
        Account account = AccountFixtures.account("acc-1", OTHER_CUSTOMER_ID, AccountStatus.ACTIVE,
            new BigDecimal("10.000"));
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(account));

        BusinessException thrown = assertThrows(BusinessException.class,
            () -> accountService.getOwned(CUSTOMER_ID, "acc-1"));

        assertEquals(ErrorCode.ACCESS_DENIED, thrown.getErrorCode());
    }

    @Test
    void shouldLetAdminReadAnotherCustomersAccount() {
        SecurityContextTestSupport.asAdmin(ADMIN_ID);
        Account account = AccountFixtures.account("acc-1", OTHER_CUSTOMER_ID, AccountStatus.ACTIVE,
            new BigDecimal("10.000"));
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(account));

        AccountResponse response = accountService.getOwned(ADMIN_ID, "acc-1");

        assertEquals(OTHER_CUSTOMER_ID, response.userId());
    }

    @Test
    void shouldThrowAccountNotFoundWhenAccountDoesNotExist() {
        when(accountRepository.findById("missing")).thenReturn(Optional.empty());

        BusinessException thrown = assertThrows(BusinessException.class,
            () -> accountService.getOwned(CUSTOMER_ID, "missing"));

        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, thrown.getErrorCode());
    }

    @Test
    void shouldThrowOperationNotAllowedWhenClosingANonZeroBalanceAccount() {
        SecurityContextTestSupport.asAdmin(ADMIN_ID);
        Account account = AccountFixtures.account("acc-1", CUSTOMER_ID, AccountStatus.ACTIVE,
            new BigDecimal("25.500"));
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(account));

        BusinessException thrown = assertThrows(BusinessException.class,
            () -> accountService.updateStatus(ADMIN_ID, "acc-1", new StatusUpdateRequest(AccountStatus.CLOSED, null)));

        assertEquals(ErrorCode.OPERATION_NOT_ALLOWED, thrown.getErrorCode());
        assertEquals("An account with a non-zero balance cannot be closed.", thrown.getMessage());
    }

    @Test
    void shouldPublishAccountBlockedWhenStatusBecomesBlocked() {
        SecurityContextTestSupport.asAdmin(ADMIN_ID);
        Account account = AccountFixtures.account("acc-1", CUSTOMER_ID, AccountStatus.ACTIVE,
            new BigDecimal("25.500"));
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountService.updateStatus(ADMIN_ID, "acc-1",
            new StatusUpdateRequest(AccountStatus.BLOCKED, "Suspicious activity"));

        ArgumentCaptor<AccountBlockedEvent> blocked = ArgumentCaptor.forClass(AccountBlockedEvent.class);
        verify(eventPublisher).publish(eq(Topics.ACCOUNT_BLOCKED), eq(EventType.ACCOUNT_BLOCKED), blocked.capture());
        assertEquals(AccountStatus.ACTIVE.name(), blocked.getValue().previousStatus());
        assertEquals(AccountStatus.BLOCKED.name(), blocked.getValue().status());
        assertEquals("Suspicious activity", blocked.getValue().reason());
        assertEquals(ADMIN_ID, blocked.getValue().blockedBy());
        verify(eventPublisher).publishAudit(eq(AuditAction.ACCOUNT_BLOCKED), eq(CUSTOMER_ID), eq("ACCOUNT"),
            eq("acc-1"), any(), anyString(), anyString(), any(Map.class));
    }

    @Test
    void shouldPublishOpenedRestatementWhenLeavingBlocked() {
        SecurityContextTestSupport.asAdmin(ADMIN_ID);
        Account account = AccountFixtures.account("acc-1", CUSTOMER_ID, AccountStatus.BLOCKED,
            new BigDecimal("25.500"));
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountService.updateStatus(ADMIN_ID, "acc-1", new StatusUpdateRequest(AccountStatus.ACTIVE, null));

        ArgumentCaptor<AccountOpenedEvent> opened = ArgumentCaptor.forClass(AccountOpenedEvent.class);
        verify(eventPublisher).publish(eq(Topics.ACCOUNT_OPENED), eq(EventType.ACCOUNT_OPENED), opened.capture());
        assertTrue(opened.getValue().restatement(), "leaving BLOCKED must restate the projection");
        assertEquals(AccountStatus.ACTIVE.name(), opened.getValue().status());
        verify(eventPublisher).publishAudit(eq(AuditAction.ACCOUNT_STATUS_CHANGED), eq(CUSTOMER_ID), eq("ACCOUNT"),
            eq("acc-1"), any(), anyString(), anyString(), any(Map.class));
    }

    @Test
    void shouldTreatBlockingAnAlreadyBlockedAccountAsAnIdempotentNoOp() {
        SecurityContextTestSupport.asAdmin(ADMIN_ID);
        Account account = AccountFixtures.account("acc-1", CUSTOMER_ID, AccountStatus.BLOCKED,
            new BigDecimal("25.500"));
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(account));

        AccountResponse response = accountService.updateStatus(ADMIN_ID, "acc-1",
            new StatusUpdateRequest(AccountStatus.BLOCKED, "Fraud review: replayed decision"));

        assertEquals(AccountStatus.BLOCKED, response.status());
        verify(eventPublisher, never()).publish(eq(Topics.ACCOUNT_BLOCKED), any(EventType.class), any());
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void shouldReturnEmptyWhenACustomerFiltersByAnotherCustomersAccountNumber() {
        Account someoneElse = AccountFixtures.account("acc-2", OTHER_CUSTOMER_ID, AccountStatus.ACTIVE,
            new BigDecimal("1000.000"));
        when(accountRepository.findByAccountNumber(AccountFixtures.ACCOUNT_NUMBER)).thenReturn(Optional.of(someoneElse));

        List<AccountResponse> result = accountService.listForUser(CUSTOMER_ID,
            AccountListFilter.of(null, "TN58 1000 0123 4567 8901 23", null));

        assertTrue(result.isEmpty(), "a CUSTOMER must never receive an account they do not hold");
    }

    @Test
    void shouldReturnTheCustomersOwnAccountWhenTheyFilterByItsNumber() {
        Account own = AccountFixtures.account("acc-1", CUSTOMER_ID, AccountStatus.ACTIVE, new BigDecimal("1000.000"));
        when(accountRepository.findByAccountNumber(AccountFixtures.ACCOUNT_NUMBER)).thenReturn(Optional.of(own));

        List<AccountResponse> result = accountService.listForUser(CUSTOMER_ID,
            AccountListFilter.of(OTHER_CUSTOMER_ID, "TN58 1000 0123 4567 8901 23", null));

        assertEquals(1, result.size());
        assertEquals(CUSTOMER_ID, result.get(0).userId());
    }

    @Test
    void shouldMatchAFormattedAndANormalisedAccountNumberIdentically() {
        SecurityContextTestSupport.asAdmin(ADMIN_ID);
        Account account = AccountFixtures.account("acc-1", OTHER_CUSTOMER_ID, AccountStatus.ACTIVE,
            new BigDecimal("1000.000"));
        when(accountRepository.findByAccountNumber(AccountFixtures.ACCOUNT_NUMBER)).thenReturn(Optional.of(account));

        List<AccountResponse> formatted = accountService.listForAdmin(
            AccountListFilter.of(null, "TN58 1000 0123 4567 8901 23", null));
        List<AccountResponse> normalised = accountService.listForAdmin(
            AccountListFilter.of(null, "tn58100001234567890123", null));

        assertEquals(1, formatted.size());
        assertEquals(1, normalised.size());
        assertEquals(formatted.get(0).id(), normalised.get(0).id());
        assertEquals("TN58 1000 0123 4567 8901 23", normalised.get(0).accountNumber());
        assertEquals("Finova Bank", normalised.get(0).bankName());
    }

    @Test
    void shouldLetAnAdminResolveAnotherCustomersAccountNumber() {
        SecurityContextTestSupport.asAdmin(ADMIN_ID);
        Account account = AccountFixtures.account("acc-2", OTHER_CUSTOMER_ID, AccountStatus.ACTIVE,
            new BigDecimal("1000.000"));
        when(accountRepository.findByAccountNumber(AccountFixtures.ACCOUNT_NUMBER)).thenReturn(Optional.of(account));

        List<AccountResponse> result = accountService.listForAdmin(
            AccountListFilter.of(null, "TN58100001234567890123", null));

        assertEquals(1, result.size());
        assertEquals(OTHER_CUSTOMER_ID, result.get(0).userId());
    }

    @Test
    void shouldReturnEmptyWhenTheAccountNumberFilterCombinesWithAContradictingOwner() {
        SecurityContextTestSupport.asAdmin(ADMIN_ID);
        Account account = AccountFixtures.account("acc-2", OTHER_CUSTOMER_ID, AccountStatus.ACTIVE,
            new BigDecimal("1000.000"));
        when(accountRepository.findByAccountNumber(AccountFixtures.ACCOUNT_NUMBER)).thenReturn(Optional.of(account));

        List<AccountResponse> result = accountService.listForAdmin(
            AccountListFilter.of(CUSTOMER_ID, "TN58100001234567890123", null));

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenNoAccountMatchesTheNumber() {
        when(accountRepository.findByAccountNumber("TN58000000000000000000")).thenReturn(Optional.empty());

        assertTrue(accountService.listForUser(CUSTOMER_ID,
            AccountListFilter.of(null, "TN58000000000000000000", null)).isEmpty());
    }

    @Test
    void shouldFilterByOwnerEmailForACustomer() {
        when(userEmailResolver.resolveUserId("takwa@finova.dev")).thenReturn(Optional.of(CUSTOMER_ID));
        when(accountRepository.findByUserIdOrderByCreatedAtDesc(CUSTOMER_ID))
            .thenReturn(List.of(AccountFixtures.account("acc-1", CUSTOMER_ID, AccountStatus.ACTIVE,
                new BigDecimal("1000.000"))));

        List<AccountResponse> result = accountService.listForUser(CUSTOMER_ID,
            AccountListFilter.of(null, null, "  TAKWA@Finova.DEV "));

        assertEquals(1, result.size());
        assertEquals(CUSTOMER_ID, result.get(0).userId());
    }

    @Test
    void shouldReturnEmptyWhenACustomerFiltersByAnotherCustomersEmail() {
        when(userEmailResolver.resolveUserId("ines.bouzid@finova.dev")).thenReturn(Optional.of(OTHER_CUSTOMER_ID));

        List<AccountResponse> result = accountService.listForUser(CUSTOMER_ID,
            AccountListFilter.of(null, null, "ines.bouzid@finova.dev"));

        assertTrue(result.isEmpty());
        verify(accountRepository, never()).findByUserIdOrderByCreatedAtDesc(anyString());
        verify(accountRepository, never()).findAll();
    }

    @Test
    void shouldReturnEmptyWhenTheEmailFilterMatchesNoUser() {
        when(userEmailResolver.resolveUserId("ghost@finova.dev")).thenReturn(Optional.empty());

        assertTrue(accountService.listForAdmin(AccountListFilter.of(null, null, "ghost@finova.dev")).isEmpty());
    }

    @Test
    void shouldKeepTheUnfilteredListingUnchanged() {
        when(accountRepository.findByUserIdOrderByCreatedAtDesc(CUSTOMER_ID)).thenReturn(List.of());

        assertTrue(accountService.listForUser(CUSTOMER_ID, AccountListFilter.of(null, null, null)).isEmpty());
        verify(accountRepository).findByUserIdOrderByCreatedAtDesc(CUSTOMER_ID);

        when(accountRepository.findAll()).thenReturn(List.of());
        assertTrue(accountService.listForAdmin(AccountListFilter.of(null, null, null)).isEmpty());
        verify(accountRepository).findAll();
    }

    @Test
    void shouldRejectAnyTransitionOutOfClosed() {
        SecurityContextTestSupport.asAdmin(ADMIN_ID);
        Account account = AccountFixtures.account("acc-1", CUSTOMER_ID, AccountStatus.CLOSED, new BigDecimal("0.000"));
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(account));

        BusinessException thrown = assertThrows(BusinessException.class,
            () -> accountService.updateStatus(ADMIN_ID, "acc-1", new StatusUpdateRequest(AccountStatus.ACTIVE, null)));

        assertEquals(ErrorCode.OPERATION_NOT_ALLOWED, thrown.getErrorCode());
    }

    @Test
    void shouldRejectStatusChangeByCustomer() {
        BusinessException thrown = assertThrows(BusinessException.class,
            () -> accountService.updateStatus(CUSTOMER_ID, "acc-1", new StatusUpdateRequest(AccountStatus.CLOSED, null)));

        assertEquals(ErrorCode.ACCESS_DENIED, thrown.getErrorCode());
        verify(accountRepository, never()).findById(anyString());
    }

    @Test
    void shouldRejectLookupOfYourOwnAccountAsBeneficiary() {
        Account account = AccountFixtures.account("acc-1", CUSTOMER_ID, AccountStatus.ACTIVE, new BigDecimal("1.000"));
        when(accountRepository.findByAccountNumber(AccountFixtures.ACCOUNT_NUMBER)).thenReturn(Optional.of(account));

        BusinessException thrown = assertThrows(BusinessException.class,
            () -> accountService.lookupBeneficiary("TN58 1000 0123 4567 8901 23"));

        assertEquals(ErrorCode.VALIDATION_ERROR, thrown.getErrorCode());
    }

    @Test
    void shouldNotLeakTheHolderNameWhenLookingUpABeneficiary() {
        Account account = AccountFixtures.account("acc-1", OTHER_CUSTOMER_ID, AccountStatus.ACTIVE,
            new BigDecimal("1.000"));
        when(accountRepository.findByAccountNumber(AccountFixtures.ACCOUNT_NUMBER)).thenReturn(Optional.of(account));

        var lookup = accountService.lookupBeneficiary("tn58 1000 0123 4567 8901 23");

        assertEquals("TN58 •••• •••• 8901 23", lookup.holderDisplayName());
        assertEquals(lookup.maskedAccountNumber(), lookup.holderDisplayName());
        assertEquals("Finova Bank", lookup.bankName());
    }

    @Test
    void shouldRejectMalformedBeneficiaryAccountNumber() {
        BusinessException thrown = assertThrows(BusinessException.class,
            () -> accountService.lookupBeneficiary("not-an-account"));

        assertEquals(ErrorCode.VALIDATION_ERROR, thrown.getErrorCode());
    }

    private void stubHappyPathCreation() {
        when(accountRepository.existsByUserIdAndAccountTypeAndCurrency(anyString(), any(), any()))
            .thenReturn(false);
        when(accountRepository.countByUserId(CUSTOMER_ID)).thenReturn(0L);
        when(accountRepository.existsByAccountNumber(AccountFixtures.ACCOUNT_NUMBER)).thenReturn(false);
        when(accountNumberGenerator.generate(Currency.TND)).thenReturn(AccountFixtures.ACCOUNT_NUMBER);
        when(ibanGenerator.generate(AccountFixtures.ACCOUNT_NUMBER, Currency.TND))
            .thenReturn(AccountFixtures.IBAN);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account saved = invocation.getArgument(0);
            saved.setCreatedAt(java.time.Instant.parse("2026-01-15T10:00:00Z"));
            saved.setUpdatedAt(java.time.Instant.parse("2026-01-15T10:00:00Z"));
            return saved;
        });
    }
}
