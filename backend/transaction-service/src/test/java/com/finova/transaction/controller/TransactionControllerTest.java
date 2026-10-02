package com.finova.transaction.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import com.finova.common.security.AuthenticatedUser;
import com.finova.transaction.domain.Transaction;
import com.finova.transaction.dto.AdminTransactionStatsResponse;
import com.finova.transaction.dto.TransactionResponse;
import com.finova.transaction.dto.TransactionSummaryResponse;
import com.finova.transaction.dto.TransactionTimelineResponse;
import com.finova.transaction.dto.TransferOutcome;
import com.finova.transaction.dto.TransferRequest;
import com.finova.transaction.service.AdminTransactionStatsService;
import com.finova.transaction.service.TransactionQueryService;
import com.finova.transaction.service.TransactionSummaryService;
import com.finova.transaction.service.TransactionTimelineService;
import com.finova.transaction.service.TransferService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("TransactionController - auth, idempotency header, ownership and admin scope")
@WebMvcTest(TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
@EnableMethodSecurity
class TransactionControllerTest {

    private static final String CUSTOMER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String ADMIN_ID = "22222222-2222-2222-2222-222222222222";
    private static final String OTHER_ID = "33333333-3333-3333-3333-333333333333";
    private static final String KEY = "8f14e45f-ea5e-4c3f-9a6f-3b7d2c1e0a94";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransferService transferService;
    @MockBean
    private TransactionQueryService queryService;
    @MockBean
    private TransactionSummaryService summaryService;
    @MockBean
    private TransactionTimelineService timelineService;
    @MockBean
    private AdminTransactionStatsService statsService;
    @MockBean
    private com.finova.transaction.mapper.TransactionMapper mapper;

    @BeforeEach
    void setUp() {
        authenticateAs(CUSTOMER_ID, "CUSTOMER");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String userId, String role) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, userId + "@finova.dev", role, "corr-1");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(principal.authority()))));
    }

    private String body() throws Exception {
        return objectMapper.writeValueAsString(new TransferRequest("acct-sender", "TN5800000000000002",
                new BigDecimal("250.00"), "TND", "Monthly payment"));
    }

    private TransactionResponse response(String status) {
        return new TransactionResponse("tx-1", "TX-20261001-00001", "acct-sender", "•••• 0001", "acct-receiver",
                "•••• 0002", "Account •••• 01", "Account •••• 02", new BigDecimal("250.000"), "TND",
                new BigDecimal("0.000"), new BigDecimal("250.000"), "Monthly payment", "TRANSFER", status,
                12, "LOW", List.of(), null, Instant.parse("2026-10-01T09:15:00Z"), null, null, null, "corr-1");
    }

    @Test
    void shouldReturnUnauthorizedWhenNoTokenIsPresent() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/transactions").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void shouldCreateTransferAndReturn201() throws Exception {
        when(transferService.create(any(), eq(KEY), eq(CUSTOMER_ID), eq(false), anyString()))
                .thenReturn(new TransferOutcome(response("PENDING"), false));

        mockMvc.perform(post("/api/transactions")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("tx-1"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.reference").value("TX-20261001-00001"))
                .andExpect(jsonPath("$.correlationId").value("corr-1"))
                .andExpect(header().doesNotExist(TransactionController.IDEMPOTENCY_REPLAY_HEADER));
    }

    @Test
    void shouldReturn200AndReplayHeaderWhenTheRequestIsReplayed() throws Exception {
        when(transferService.create(any(), eq(KEY), eq(CUSTOMER_ID), eq(false), anyString()))
                .thenReturn(new TransferOutcome(response("COMPLETED"), true));

        mockMvc.perform(post("/api/transactions")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isOk())
                .andExpect(header().string(TransactionController.IDEMPOTENCY_REPLAY_HEADER, "true"))
                .andExpect(jsonPath("$.id").value("tx-1"));
    }

    @Test
    void shouldReturn400WithValidationErrorWhenIdempotencyKeyIsMissing() throws Exception {
        when(transferService.create(any(), any(), eq(CUSTOMER_ID), anyBoolean(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "The Idempotency-Key header is required for transfer creation."));

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturn400WhenTheBodyIsInvalid() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senderAccountId\":\"\",\"receiverAccountNumber\":\"\",\"currency\":\"TND\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturn403WhenTheCustomerCannotSeeAnotherUsersTransaction() throws Exception {
        when(queryService.requireVisible(eq("tx-9"), eq(CUSTOMER_ID), eq(false)))
                .thenThrow(new BusinessException(ErrorCode.ACCESS_DENIED));

        mockMvc.perform(get("/api/transactions/tx-9").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldReturnTransactionToItsHolder() throws Exception {
        when(queryService.requireVisible(eq("tx-9"), eq(CUSTOMER_ID), eq(false))).thenReturn(new Transaction());

        mockMvc.perform(get("/api/transactions/tx-9").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn404WhenTheTransactionDoesNotExist() throws Exception {
        when(queryService.requireVisible(eq("tx-9"), eq(CUSTOMER_ID), eq(false)))
                .thenThrow(BusinessException.notFound(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction", "tx-9"));

        mockMvc.perform(get("/api/transactions/tx-9").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));
    }

    @Test
    void shouldRejectAdminStatsForACustomer() throws Exception {
        mockMvc.perform(get("/api/transactions/stats/summary").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
        verify(statsService, never()).stats();
    }

    @Test
    void shouldRejectAdminListForACustomer() throws Exception {
        mockMvc.perform(get("/api/transactions/admin/all").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnAdminStatsForAnAdministrator() throws Exception {
        authenticateAs(ADMIN_ID, "ADMIN");
        when(statsService.stats()).thenReturn(new AdminTransactionStatsResponse(
                10L, 4L, 1L, 2L, 1L, new BigDecimal("80.00"), new BigDecimal("1500.000"),
                Map.of("TND", new BigDecimal("1500.000")),
                Map.of("COMPLETED", 6L),
                Map.of("TRANSFER", 10L),
                List.of(), List.of(),
                new BigDecimal("250.000"), new BigDecimal("900.000")));

        mockMvc.perform(get("/api/transactions/stats/summary").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions").value(10))
                .andExpect(jsonPath("$.flaggedCount").value(1));
    }

    @Test
    void shouldIgnoreTheUserIdFilterForACustomer() throws Exception {
        when(queryService.search(any(), eq(CUSTOMER_ID), eq(null))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/transactions").param("userId", OTHER_ID).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        verify(queryService).search(any(), eq(CUSTOMER_ID), eq(null));
    }

    @Test
    void shouldHonourTheUserIdFilterForAnAdministrator() throws Exception {
        authenticateAs(ADMIN_ID, "ADMIN");
        when(queryService.search(any(), eq(null), eq(OTHER_ID))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/transactions/admin/all").param("userId", OTHER_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        verify(queryService).search(any(), eq(null), eq(OTHER_ID));
    }

    @Test
    void shouldReturnTheDashboardSummaryForTheCaller() throws Exception {
        when(summaryService.summarise(CUSTOMER_ID)).thenReturn(new TransactionSummaryResponse(
                new BigDecimal("2500.000"), new BigDecimal("1245.500"), 12,
                new BigDecimal("-12.50"), new BigDecimal("-357.500"), List.of(), List.of()));

        mockMvc.perform(get("/api/transactions/summary").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.income").value(2500.000))
                .andExpect(jsonPath("$.expenses").value(1245.500))
                .andExpect(jsonPath("$.transactionCount").value(12));
    }

    @Test
    void shouldReturnTheDerivedTimeline() throws Exception {
        when(queryService.requireVisible(eq("tx-9"), eq(CUSTOMER_ID), eq(false))).thenReturn(new Transaction());
        when(timelineService.timeline(any())).thenReturn(new TransactionTimelineResponse("tx-9",
                "TX-20261001-00001", List.of(
                TransactionTimelineResponse.TimelineStep.of("CREATED", "Transfer created", "Accepted",
                        TransactionTimelineResponse.StepState.DONE, Instant.parse("2026-10-01T09:15:00Z")))));

        mockMvc.perform(get("/api/transactions/tx-9/timeline").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("tx-9"))
                .andExpect(jsonPath("$.steps[0].key").value("CREATED"));
    }

    @Test
    void shouldListOnlyTheCallersOwnTransactionsByDefault() throws Exception {
        when(queryService.search(any(), eq(CUSTOMER_ID), eq(null))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/transactions").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void shouldRejectAnUnsupportedSortFieldWithValidationError() throws Exception {
        when(queryService.search(any(), eq(CUSTOMER_ID), eq(null)))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Unsupported sort field 'password'. Allowed: createdAt."));

        mockMvc.perform(get("/api/transactions").param("sort", "password,asc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldNotCreateAnythingWhenTheCallerIsUnauthenticated() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(post("/api/transactions")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isUnauthorized());
        verify(transferService, never()).create(any(), anyString(), anyString(), anyBoolean(), anyString());
    }

    @Test
    void shouldReturnTransactionNotFoundForAnUnknownId() throws Exception {
        when(queryService.requireVisible(eq("missing"), eq(CUSTOMER_ID), eq(false)))
                .thenThrow(BusinessException.notFound(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction", "missing"));

        mockMvc.perform(get("/api/transactions/missing").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

}