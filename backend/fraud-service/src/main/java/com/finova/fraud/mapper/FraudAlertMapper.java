package com.finova.fraud.mapper;

import com.finova.fraud.domain.FraudAlert;
import com.finova.fraud.domain.TimelineStep;
import com.finova.fraud.dto.FraudAlertResponse;
import com.finova.fraud.dto.TimelineStepResponse;

import java.util.List;

public final class FraudAlertMapper {

    private FraudAlertMapper() {
    }

    public static FraudAlertResponse toResponse(FraudAlert alert) {
        return new FraudAlertResponse(
                alert.getId(),
                alert.getTransactionId(),
                alert.getReference(),
                alert.getSenderAccountId(),
                alert.getReceiverAccountId(),
                alert.getSenderAccountNumber(),
                alert.getSenderUserId(),
                alert.getAmount(),
                alert.getCurrency(),
                alert.getRiskScore(),
                alert.getRiskLevel(),
                alert.getReasons() == null ? List.of() : List.copyOf(alert.getReasons()),
                alert.getTriggeredRules() == null ? List.of() : List.copyOf(alert.getTriggeredRules()),
                alert.getStatus(),
                alert.getCreatedAt(),
                alert.getUpdatedAt(),
                alert.getReviewedAt(),
                alert.getReviewedBy(),
                alert.getReviewNote(),
                alert.timelineOrEmpty().stream().map(FraudAlertMapper::toTimelineResponse).toList());
    }

    public static List<FraudAlertResponse> toResponses(List<FraudAlert> alerts) {
        return alerts.stream().map(FraudAlertMapper::toResponse).toList();
    }

    private static TimelineStepResponse toTimelineResponse(TimelineStep step) {
        return new TimelineStepResponse(step.getKey(), step.getLabel(), step.getDescription(), step.getAt());
    }
}
