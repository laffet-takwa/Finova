package com.finova.fraud.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.common.domain.FraudStatus;
import com.finova.common.domain.RiskLevel;
import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import com.finova.common.security.JwtTokenProvider;
import com.finova.common.web.GlobalExceptionHandler;
import com.finova.fraud.config.SecurityConfig;
import com.finova.fraud.config.WebSupportConfig;
import com.finova.fraud.dto.FraudAlertResponse;
import com.finova.fraud.dto.FraudStatsResponse;
import com.finova.fraud.dto.ReviewAlertRequest;
import com.finova.fraud.dto.TimelineStepResponse;
import com.finova.fraud.service.FraudAlertService;
import com.finova.fraud.service.FraudStatsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.hamcrest.Matchers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FraudAlertController.class)
@Import({SecurityConfig.class, WebSupportConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "finova.jwt.secret=finova-fraud-controller-test-secret-32-bytes-long",
        "finova.jwt.issuer=finova"
})
@DisplayName("FraudAlertController")
class FraudAlertControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockBean
    private FraudAlertService alertService;
    @MockBean
    private FraudStatsService statsService;

    private static final FraudAlertResponse ALERT = new FraudAlertResponse(
            "alert-1", "txn-1", "TX-20260615-00042", "acc-sender-1", "acc-receiver-1",
            "TN12345678", "usr-sender-1", new BigDecimal("15000.000"), "TND", 88,
            RiskLevel.HIGH.name(), List.of("Large transaction amount"), List.of("LARGE_AMOUNT"),
            FraudStatus.OPEN.name(), Instant.parse("2026-06-15T12:00:00Z"),
            Instant.parse("2026-06-15T12:00:01Z"), null, null, null,
            List.of(new TimelineStepResponse("ALERT_CREATED", "Alert created", "Funds held", null)));

    @Test
    void shouldReturn401WhenNoTokenIsPresent() throws Exception {
        mockMvc.perform(get("/api/fraud/alerts"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} is rejected with 403")
    @CsvSource({
            "GET,/api/fraud/alerts",
            "GET,/api/fraud/alerts/unresolved",
            "GET,/api/fraud/alerts/alert-1",
            "GET,/api/fraud/alerts/transaction/txn-1",
            "PATCH,/api/fraud/alerts/alert-1/review",
            "PATCH,/api/fraud/alerts/alert-1/safe",
            "PATCH,/api/fraud/alerts/alert-1/confirm",
            "PATCH,/api/fraud/alerts/alert-1/block-account",
            "GET,/api/fraud/stats/summary"
    })
    void shouldReturn403ForCustomersOnEveryFraudPath(String method, String path) throws Exception {
        MockHttpServletRequestBuilder request = "GET".equals(method)
                ? get(path)
                : patch(path).contentType(MediaType.APPLICATION_JSON);
        mockMvc.perform(request.with(customerJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldReturnTheAlertPageForAnAdministrator() throws Exception {
        given(alertService.findAll(any())).willReturn(new PageImpl<>(
                List.of(ALERT), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/fraud/alerts").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("alert-1"))
                .andExpect(jsonPath("$.content[0].riskScore").value(88))
                .andExpect(jsonPath("$.content[0].riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldReturnUnresolvedAlertsForAnAdministrator() throws Exception {
        given(alertService.findUnresolved()).willReturn(List.of(ALERT));

        mockMvc.perform(get("/api/fraud/alerts/unresolved").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transactionId").value("txn-1"));
    }

    @Test
    void shouldReturnStatsForAnAdministrator() throws Exception {
        given(statsService.summary()).willReturn(new FraudStatsResponse(
                18, 7, 11, 3, 24, 6, 96, 47.3,
                Map.of("HIGH", 7L, "MEDIUM", 11L, "LOW", 3L),
                Map.of("OPEN", 18L, "UNDER_REVIEW", 4L, "SAFE", 20L, "CONFIRMED", 6L),
                List.of(new FraudStatsResponse.SeriesPointResponse("2026-06-15", 9)),
                List.of(new FraudStatsResponse.TopRiskyAccountResponse("acc-1", "TN111", 4, 96))));

        mockMvc.perform(get("/api/fraud/stats/summary").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openAlerts").value(18))
                .andExpect(jsonPath("$.highRisk").value(7))
                .andExpect(jsonPath("$.dailyAlerts[0].label").value("2026-06-15"))
                .andExpect(jsonPath("$.topRiskyAccounts[0].accountId").value("acc-1"));
    }

    @Test
    void shouldRejectMarkingSafeAnAlreadyConfirmedAlert() throws Exception {
        given(alertService.markSafe(eq("alert-1"), isNull())).willThrow(new BusinessException(
                ErrorCode.ALERT_ALREADY_REVIEWED,
                "Fraud alert alert-1 is already CONFIRMED and cannot be reviewed again."));

        mockMvc.perform(patch("/api/fraud/alerts/alert-1/safe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALERT_ALREADY_REVIEWED"));
    }

    @Test
    void shouldReturn503AndLeaveTheAlertUntouchedWhenTheAccountServiceIsDown() throws Exception {
        given(alertService.blockAccount(eq("alert-1"), any()))
                .willThrow(new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                        "The account service is unavailable, so account acc-sender-1 could not be blocked. "
                                + "The alert was not confirmed - please retry."));

        mockMvc.perform(patch("/api/fraud/alerts/alert-1/block-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewAlertRequest("possible fraud")))
                        .with(adminJwt()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(
                        Matchers.containsString("The alert was not confirmed")));
    }

    @Test
    void shouldMarkAnAlertSafeForAnAdministrator() throws Exception {
        given(alertService.markSafe(eq("alert-1"), eq("verified with the sender")))
                .willReturn(ALERT);

        mockMvc.perform(patch("/api/fraud/alerts/alert-1/safe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewAlertRequest("verified with the sender")))
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("alert-1"));
    }

    @Test
    void shouldStartAReviewWithoutABody() throws Exception {
        given(alertService.startReview(eq("alert-1"), isNull())).willReturn(ALERT);

        mockMvc.perform(patch("/api/fraud/alerts/alert-1/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(adminJwt()))
                .andExpect(status().isOk());
    }

    @Test
    void shouldSurfaceAnUnknownAlertAs404() throws Exception {
        given(alertService.findById("missing"))
                .willThrow(BusinessException.notFound(ErrorCode.FRAUD_ALERT_NOT_FOUND, "Fraud alert", "missing"));

        mockMvc.perform(get("/api/fraud/alerts/missing").with(adminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FRAUD_ALERT_NOT_FOUND"));
    }

    @Test
    void shouldPropagateAnAdministratorFailureAsServiceUnavailable() throws Exception {
        doThrow(new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "account service down"))
                .when(alertService).blockAccount(eq("alert-1"), isNull());

        mockMvc.perform(patch("/api/fraud/alerts/alert-1/block-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(adminJwt()))
                .andExpect(status().isServiceUnavailable());
    }

    private RequestPostProcessor adminJwt() {
        return request -> {
            request.addHeader("Authorization", bearer("usr-admin", "admin@finova.dev", "ADMIN"));
            return request;
        };
    }

    private RequestPostProcessor customerJwt() {
        return request -> {
            request.addHeader("Authorization", bearer("usr-customer", "customer@finova.dev", "CUSTOMER"));
            return request;
        };
    }

    /** Mints a real access token so the slice exercises the production authentication path. */
    private String bearer(String userId, String email, String role) {
        return "Bearer " + tokenProvider.createAccessToken(userId, email, role);
    }
}