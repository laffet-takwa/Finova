package com.finova.transaction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import com.finova.transaction.client.AccountLookupResponse;
import com.finova.transaction.client.AccountResponse;
import com.finova.transaction.client.AccountServiceClient;
import com.finova.transaction.domain.LedgerAccount;
import com.finova.transaction.mapper.AccountNumbers;
import com.finova.transaction.repository.LedgerAccountRepository;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@DisplayName("LedgerProjectionService - one directory call per leg, and what its failures mean")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LedgerProjectionServiceTest {

    private static final String NUMBER = "TN5800000000000002";
    private static final String LOOKUP_ACCOUNT_ID = "acct-receiver-0002";
    private static final String LOOKUP_USER_ID = "user-receiver";
    private static final String SENDER_ID = "acct-sender-0001";
    private static final int FEIGN_NO_HTTP_RESPONSE = -1;

    @Mock
    private AccountServiceClient accountServiceClient;
    @Mock
    private LedgerAccountRepository repository;

    private LedgerProjectionService service;

    @BeforeEach
    void setUp() {
        service = new LedgerProjectionService(accountServiceClient, repository, new AccountNumbers());
    }

    private AccountLookupResponse lookup() {
        return new AccountLookupResponse(LOOKUP_ACCOUNT_ID, LOOKUP_USER_ID, NUMBER, "•••• 0002", "CHECKING",
                "TND", "ACTIVE", "Ines B.", "Finova");
    }

    private ErrorCode codeOf(Runnable action) {
        BusinessException exception = assertThrows(BusinessException.class, action::run);
        return exception.getErrorCode();
    }

    /**
     * Lets a test drive {@code FeignException.status()} directly, including the
     * negative status Feign reports when the call never reached the server.
     */
    private static final class StubFeignException extends FeignException {

        StubFeignException(int status, String message) {
            super(status, message);
        }
    }

    private static FeignException feignFailure(int status) {
        return new StubFeignException(status, "stubbed directory failure");
    }

    @Test
    void shouldResolveTheReceiverThroughLookupBeneficiaryOnly() {
        when(accountServiceClient.lookupBeneficiary(NUMBER)).thenReturn(lookup());
        when(repository.findByAccountId(LOOKUP_ACCOUNT_ID)).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> call.getArgument(0));

        service.resolveByAccountNumber(NUMBER);

        verify(accountServiceClient).lookupBeneficiary(NUMBER);
        verifyNoMoreInteractions(accountServiceClient);
    }

    @Test
    void shouldConsultTheDirectoryEvenWhenTheProjectionAlreadyExists() {
        LedgerAccount existing = existingProjection();
        when(accountServiceClient.lookupBeneficiary(NUMBER)).thenReturn(lookup());
        when(repository.findByAccountId(LOOKUP_ACCOUNT_ID)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        service.resolveByAccountNumber(NUMBER);

        verify(accountServiceClient).lookupBeneficiary(NUMBER);
        verifyNoMoreInteractions(accountServiceClient);
    }

    @Test
    void shouldTakeTheReceiverUserIdFromTheLookup() {
        when(accountServiceClient.lookupBeneficiary(NUMBER)).thenReturn(lookup());
        when(repository.findByAccountId(LOOKUP_ACCOUNT_ID)).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> call.getArgument(0));

        LedgerAccount receiver = service.resolveByAccountNumber(NUMBER);

        assertEquals(LOOKUP_USER_ID, receiver.getUserId());
        assertEquals(LOOKUP_ACCOUNT_ID, receiver.getAccountId());
        assertEquals(NUMBER, receiver.getAccountNumber());
        assertEquals("TND", receiver.getCurrency());
        assertEquals("ACTIVE", receiver.getStatus());
    }

    @Test
    void shouldRefreshTheHolderOnAnExistingProjectionWithoutTouchingTheBalance() {
        LedgerAccount existing = existingProjection();
        existing.setUserId("stale-user");
        existing.setStatus("BLOCKED");
        when(accountServiceClient.lookupBeneficiary(NUMBER)).thenReturn(lookup());
        when(repository.findByAccountId(LOOKUP_ACCOUNT_ID)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        LedgerAccount receiver = service.resolveByAccountNumber(NUMBER);

        assertEquals(LOOKUP_USER_ID, receiver.getUserId());
        assertEquals("ACTIVE", receiver.getStatus());
        assertEquals(0, receiver.getBalance().compareTo(new BigDecimal("321.500")));
    }

    @Test
    void shouldOpenAFirstSeenReceiverAtZeroBecauseTheLookupCarriesNoBalance() {
        when(accountServiceClient.lookupBeneficiary(NUMBER)).thenReturn(lookup());
        when(repository.findByAccountId(LOOKUP_ACCOUNT_ID)).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> call.getArgument(0));

        LedgerAccount receiver = service.resolveByAccountNumber(NUMBER);

        assertEquals(0, receiver.getBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    void shouldStillResolveTheSenderThroughGetAccountById() {
        AccountResponse account = new AccountResponse(SENDER_ID, "TN5800000000000001", "user-owner", "CHECKING",
                "TND", new BigDecimal("900.000"), "ACTIVE", "Everyday", "Finova", null);
        when(accountServiceClient.getAccount(SENDER_ID)).thenReturn(account);
        when(repository.findByAccountId(SENDER_ID)).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> call.getArgument(0));

        LedgerAccount sender = service.resolveByAccountId(SENDER_ID);

        verify(accountServiceClient).getAccount(SENDER_ID);
        verifyNoMoreInteractions(accountServiceClient);
        assertEquals("user-owner", sender.getUserId());
        assertEquals(0, sender.getBalance().compareTo(new BigDecimal("900.000")));
    }

    @Test
    void shouldSurfaceALookupThatRejectsTheCallersOwnAccountAsSenderReceiverIdentical() {
        when(accountServiceClient.lookupBeneficiary(NUMBER))
                .thenThrow(feignFailure(HttpStatus.BAD_REQUEST.value()));

        assertEquals(ErrorCode.SENDER_RECEIVER_IDENTICAL, codeOf(() -> service.resolveByAccountNumber(NUMBER)));
    }

    @Test
    void shouldSurfaceAnUnknownNumberAsAccountNotFound() {
        when(accountServiceClient.lookupBeneficiary(NUMBER))
                .thenThrow(feignFailure(HttpStatus.NOT_FOUND.value()));

        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, codeOf(() -> service.resolveByAccountNumber(NUMBER)));
    }

    @Test
    void shouldSurfaceADirectoryOutageAsServiceUnavailable() {
        when(accountServiceClient.lookupBeneficiary(NUMBER))
                .thenThrow(feignFailure(HttpStatus.INTERNAL_SERVER_ERROR.value()));

        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, codeOf(() -> service.resolveByAccountNumber(NUMBER)));
    }

    @Test
    void shouldSurfaceAConnectionFailureAsServiceUnavailable() {
        when(accountServiceClient.lookupBeneficiary(anyString()))
                .thenThrow(feignFailure(FEIGN_NO_HTTP_RESPONSE));

        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, codeOf(() -> service.resolveByAccountNumber(NUMBER)));
    }

    @Test
    void shouldSurfaceADirectoryOutageOnTheSenderPathAsServiceUnavailable() {
        when(accountServiceClient.getAccount(SENDER_ID)).thenThrow(feignFailure(503));

        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, codeOf(() -> service.resolveByAccountId(SENDER_ID)));
    }

    @Test
    void shouldRejectABlankReceiverNumberWithoutCallingTheDirectory() {
        assertEquals(ErrorCode.VALIDATION_ERROR, codeOf(() -> service.resolveByAccountNumber("   ")));

        verifyNoMoreInteractions(accountServiceClient);
    }

    @Test
    void shouldNormaliseTheTypedNumberBeforeLookingItUp() {
        when(accountServiceClient.lookupBeneficiary(NUMBER)).thenReturn(lookup());
        when(repository.findByAccountId(LOOKUP_ACCOUNT_ID)).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> call.getArgument(0));

        service.resolveByAccountNumber("tn58 00000000000002");

        verify(accountServiceClient).lookupBeneficiary(NUMBER);
    }

    @Test
    void shouldBindTheLookupResponseIncludingAccountIdAndUserId() throws Exception {
        String json = """
                {
                  "accountId": "acct-receiver-0002",
                  "userId": "user-receiver",
                  "accountNumber": "TN5800000000000002",
                  "maskedAccountNumber": "•••• 0002",
                  "accountType": "CHECKING",
                  "currency": "TND",
                  "status": "ACTIVE",
                  "holderDisplayName": "Ines B.",
                  "bankName": "Finova"
                }
                """;

        AccountLookupResponse bound = new ObjectMapper().readValue(json, AccountLookupResponse.class);

        assertEquals(LOOKUP_ACCOUNT_ID, bound.accountId());
        assertEquals(LOOKUP_USER_ID, bound.userId());
        assertEquals(NUMBER, bound.accountNumber());
        assertEquals("•••• 0002", bound.maskedAccountNumber());
        assertEquals("TND", bound.currency());
        assertEquals("ACTIVE", bound.status());
        assertEquals("Ines B.", bound.holderDisplayName());
        assertEquals("Finova", bound.bankName());
    }

    @Test
    void shouldBindALookupResponseThatOmitsTheHolderDisplayName() throws Exception {
        String json = """
                {"accountId":"acct-r","userId":"user-r","accountNumber":"TN58","currency":"TND","status":"ACTIVE"}
                """;

        AccountLookupResponse bound = new ObjectMapper().readValue(json, AccountLookupResponse.class);

        assertEquals("acct-r", bound.accountId());
        assertEquals("user-r", bound.userId());
        assertEquals(null, bound.holderDisplayName());
    }

    @Test
    void shouldExposeOnlyTwoDirectoryEndpoints() {
        assertEquals(2, AccountServiceClient.class.getDeclaredMethods().length);
        assertTrue(java.util.Arrays.stream(AccountServiceClient.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().startsWith("find")));
    }

    private LedgerAccount existingProjection() {
        LedgerAccount existing = new LedgerAccount();
        existing.setId("ledger-0002");
        existing.setAccountId(LOOKUP_ACCOUNT_ID);
        existing.setAccountNumber(NUMBER);
        existing.setUserId("stale-user");
        existing.setAccountType("CHECKING");
        existing.setCurrency("TND");
        existing.setBalance(new BigDecimal("321.500"));
        existing.setStatus("BLOCKED");
        return existing;
    }

}