package com.finova.account.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.account.config.SecurityConfig;
import com.finova.account.config.WebSupportConfig;
import com.finova.account.domain.Account;
import com.finova.account.dto.AccountListFilter;
import com.finova.account.dto.AccountResponse;
import com.finova.account.dto.AccountSearchCriteria;
import com.finova.account.dto.StatusUpdateRequest;
import com.finova.account.mapper.AccountMapperImpl;
import com.finova.account.service.AccountService;
import com.finova.account.service.AccountStatsService;
import com.finova.account.support.AccountFixtures;
import com.finova.common.domain.AccountStatus;
import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import com.finova.common.security.AuthenticatedUser;
import com.finova.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@Import({SecurityConfig.class, WebSupportConfig.class, AccountMapperImpl.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AccountControllerTest {

    private static final String CUSTOMER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER_CUSTOMER_ID = "22222222-2222-2222-2222-222222222222";
    private static final String ADMIN_ID = "99999999-9999-9999-9999-999999999999";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AccountService accountService;

    @MockBean
    private AccountStatsService accountStatsService;

    @Test
    void shouldReturn401WhenNoTokenIsPresent() throws Exception {
        mockMvc.perform(get("/api/accounts"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHENTICATED.name()));
    }

    @Test
    void shouldReturnOwnAccountsOnlyWhenCustomerListsAccounts() throws Exception {
        when(accountService.listForUser(eq(CUSTOMER_ID), any(AccountListFilter.class))).thenReturn(List.of(ownAccount()));

        mockMvc.perform(get("/api/accounts").with(jwt(CUSTOMER_ID, AuthenticatedUser.ROLE_CUSTOMER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].accountNumber").value("TN58 1000 0123 4567 8901 23"))
            .andExpect(jsonPath("$[0].userId").value(CUSTOMER_ID));

        verify(accountService).listForUser(eq(CUSTOMER_ID), any(AccountListFilter.class));
        verify(accountService, never()).listForAdmin(any(AccountListFilter.class));
    }

    @Test
    void shouldReturn200WhenCustomerReadsOwnAccount() throws Exception {
        when(accountService.getOwned(CUSTOMER_ID, "acc-1")).thenReturn(ownAccount());

        mockMvc.perform(get("/api/accounts/acc-1").with(jwt(CUSTOMER_ID, AuthenticatedUser.ROLE_CUSTOMER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("acc-1"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldReturn403WhenCustomerReadsAnotherCustomersAccount() throws Exception {
        when(accountService.getOwned(CUSTOMER_ID, "acc-2")).thenThrow(new BusinessException(ErrorCode.ACCESS_DENIED));

        mockMvc.perform(get("/api/accounts/acc-2").with(jwt(CUSTOMER_ID, AuthenticatedUser.ROLE_CUSTOMER)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.name()));
    }

    @Test
    void shouldReturn404WhenAccountDoesNotExist() throws Exception {
        when(accountService.getOwned(CUSTOMER_ID, "missing"))
            .thenThrow(BusinessException.notFound(ErrorCode.ACCOUNT_NOT_FOUND, "Account", "missing"));

        mockMvc.perform(get("/api/accounts/missing").with(jwt(CUSTOMER_ID, AuthenticatedUser.ROLE_CUSTOMER)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(ErrorCode.ACCOUNT_NOT_FOUND.name()));
    }

    @Test
    void shouldReturn403WhenCustomerAttemptsStatusChange() throws Exception {
        mockMvc.perform(put("/api/accounts/acc-1/status")
                .with(jwt(CUSTOMER_ID, AuthenticatedUser.ROLE_CUSTOMER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StatusUpdateRequest(AccountStatus.BLOCKED, "nope"))))
            .andExpect(status().isForbidden());

        verify(accountService, never()).updateStatus(anyString(), anyString(), any());
    }

    @Test
    void shouldReturn200WhenAdminChangesStatus() throws Exception {
        when(accountService.updateStatus(eq(ADMIN_ID), eq("acc-1"), any()))
            .thenReturn(blockedAccount());

        mockMvc.perform(put("/api/accounts/acc-1/status")
                .with(jwt(ADMIN_ID, AuthenticatedUser.ROLE_ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StatusUpdateRequest(AccountStatus.BLOCKED, "fraud"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("BLOCKED"));

        verify(accountService).updateStatus(eq(ADMIN_ID), eq("acc-1"), any());
    }

    @Test
    void shouldSucceedWhenAdminBlocksAnAlreadyBlockedAccount() throws Exception {
        Account entity = AccountFixtures.account("acc-1", CUSTOMER_ID, AccountStatus.BLOCKED,
            new BigDecimal("250.000"));
        when(accountService.updateStatus(eq(ADMIN_ID), eq("acc-1"), any()))
            .thenReturn(new AccountMapperImpl().toResponse(entity));

        mockMvc.perform(put("/api/accounts/acc-1/status")
                .with(jwt(ADMIN_ID, AuthenticatedUser.ROLE_ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new StatusUpdateRequest(AccountStatus.BLOCKED, "Fraud review: transfer TX-1 assessed at 88/100."))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("BLOCKED"));

        verify(accountService).updateStatus(eq(ADMIN_ID), eq("acc-1"), any());
    }

    @Test
    void shouldReturn400WhenTheStatusIsMissing() throws Exception {
        mockMvc.perform(put("/api/accounts/acc-1/status")
                .with(jwt(ADMIN_ID, AuthenticatedUser.ROLE_ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());

        verify(accountService, never()).updateStatus(anyString(), anyString(), any());
    }

    @Test
    void shouldNormaliseTheAccountNumberAndEmailFiltersAndForwardThem() throws Exception {
        ArgumentCaptor<AccountListFilter> filter = ArgumentCaptor.forClass(AccountListFilter.class);
        when(accountService.listForUser(eq(CUSTOMER_ID), any(AccountListFilter.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/accounts")
                .param("accountNumber", "  tn58 1000 0123 4567 8901 23 ")
                .param("email", "  TAKWA@Finova.DEV ")
                .with(jwt(CUSTOMER_ID, AuthenticatedUser.ROLE_CUSTOMER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

        verify(accountService).listForUser(eq(CUSTOMER_ID), filter.capture());
        assertEquals("TN58100001234567890123", filter.getValue().accountNumber());
        assertEquals("takwa@finova.dev", filter.getValue().email());
    }

    @Test
    void shouldLetAnAdminFilterTheListByAccountNumber() throws Exception {
        Account entity = AccountFixtures.account("acc-2", OTHER_CUSTOMER_ID, AccountStatus.ACTIVE,
            new BigDecimal("1000.000"));
        when(accountService.listForAdmin(any(AccountListFilter.class)))
            .thenReturn(List.of(new AccountMapperImpl().toResponse(entity)));

        mockMvc.perform(get("/api/accounts")
                .param("accountNumber", "TN58100001234567890123")
                .with(jwt(ADMIN_ID, AuthenticatedUser.ROLE_ADMIN)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].userId").value(OTHER_CUSTOMER_ID))
            .andExpect(jsonPath("$[0].bankName").value("Finova Bank"));

        verify(accountService, never()).listForUser(anyString(), any(AccountListFilter.class));
    }

    @Test
    void shouldReturnOnlyOpaqueIdentifiersForABeneficiaryLookup() throws Exception {
        Account beneficiary = AccountFixtures.account("acc-2", OTHER_CUSTOMER_ID, AccountStatus.ACTIVE,
            new BigDecimal("1000.000"));
        when(accountService.lookupBeneficiary("TN58 1000 0123 4567 8901 23"))
            .thenReturn(new AccountMapperImpl().toLookup(beneficiary));

        String body = mockMvc.perform(get("/api/accounts/lookup")
                .param("accountNumber", "TN58 1000 0123 4567 8901 23")
                .with(jwt(CUSTOMER_ID, AuthenticatedUser.ROLE_CUSTOMER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value("acc-2"))
            .andExpect(jsonPath("$.userId").value(OTHER_CUSTOMER_ID))
            .andExpect(jsonPath("$.holderDisplayName").value("TN58 •••• •••• 8901 23"))
            .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        List<String> fields = new ArrayList<>();
        json.fieldNames().forEachRemaining(fields::add);
        assertEquals(List.of("accountId", "userId", "accountNumber", "maskedAccountNumber", "accountType",
            "currency", "status", "holderDisplayName", "bankName"), fields,
            "the lookup response must not gain a money or profile field");

        assertFalse(body.contains("balance"), body);
        assertFalse(body.contains("availableBalance"), body);
        assertFalse(body.contains("firstName"), body);
        assertFalse(body.contains("lastName"), body);
        assertFalse(body.contains("email"), body);
        assertFalse(body.contains("phone"), body);
        assertFalse(body.toLowerCase().contains("takwa"), body);
        assertFalse(body.toLowerCase().contains("ben salah"), body);
    }

    @Test
    void shouldRejectAnonymousBeneficiaryLookupWithoutCallingTheService() throws Exception {
        mockMvc.perform(get("/api/accounts/lookup").param("accountNumber", "TN58 1000 0123 4567 8901 23"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(accountService, never()).lookupBeneficiary(anyString());
    }

    @Test
    void shouldRejectLookingUpYourOwnAccount() throws Exception {
        when(accountService.lookupBeneficiary("TN58 1000 0123 4567 8901 23"))
            .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR,
                "A transfer destination must be an account you do not hold yourself."));

        mockMvc.perform(get("/api/accounts/lookup")
                .param("accountNumber", "TN58 1000 0123 4567 8901 23")
                .with(jwt(CUSTOMER_ID, AuthenticatedUser.ROLE_CUSTOMER)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturn404ForAnUnknownBeneficiary() throws Exception {
        when(accountService.lookupBeneficiary(AccountFixtures.ACCOUNT_NUMBER))
            .thenThrow(BusinessException.notFound(ErrorCode.ACCOUNT_NOT_FOUND, "Account", "TN58100001234567890123"));

        mockMvc.perform(get("/api/accounts/lookup")
                .param("accountNumber", AccountFixtures.ACCOUNT_NUMBER)
                .with(jwt(CUSTOMER_ID, AuthenticatedUser.ROLE_CUSTOMER)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void shouldReturn403WhenCustomerRequestsAdminStats() throws Exception {
        mockMvc.perform(get("/api/accounts/stats/summary").with(jwt(CUSTOMER_ID, AuthenticatedUser.ROLE_CUSTOMER)))
            .andExpect(status().isForbidden());

        verify(accountStatsService, never()).summary();
    }

    @Test
    void shouldReturnForbiddenForCustomerOnAdminSearch() throws Exception {
        mockMvc.perform(get("/api/accounts/admin/all").with(jwt(CUSTOMER_ID, AuthenticatedUser.ROLE_CUSTOMER)))
            .andExpect(status().isForbidden());

        verify(accountService, never()).search(any(AccountSearchCriteria.class), any());
    }

    @Test
    void shouldReturnPageWhenAdminSearches() throws Exception {
        Account entity = AccountFixtures.account("acc-1", CUSTOMER_ID, AccountStatus.ACTIVE,
            new BigDecimal("1000.000"));
        when(accountService.search(any(AccountSearchCriteria.class), any()))
            .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 5), 1));

        mockMvc.perform(get("/api/accounts/admin/all")
                .param("status", "ACTIVE")
                .param("size", "5")
                .with(jwt(ADMIN_ID, AuthenticatedUser.ROLE_ADMIN)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value("acc-1"))
            .andExpect(jsonPath("$.size").value(5));
    }

    private static AccountResponse ownAccount() {
        Account account = AccountFixtures.account("acc-1", CUSTOMER_ID, AccountStatus.ACTIVE,
            new BigDecimal("1000.000"));
        return new AccountMapperImpl().toResponse(account);
    }

    private static AccountResponse blockedAccount() {
        Account account = AccountFixtures.account("acc-1", OTHER_CUSTOMER_ID, AccountStatus.BLOCKED,
            new BigDecimal("1000.000"));
        return new AccountMapperImpl().toResponse(account);
    }

    /**
     * Installs the same {@link AuthenticatedUser} principal the real JWT filter
     * produces, so {@code CurrentUser} resolves exactly as it does in production.
     */
    private static RequestPostProcessor jwt(String userId, String role) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, userId + "@finova.dev", role, "test-correlation-id");
        return SecurityMockMvcRequestPostProcessors.authentication(new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority(principal.authority()))));
    }
}
