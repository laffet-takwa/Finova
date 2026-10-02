package com.finova.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Service-level result of an accepted transfer: the mapped response plus whether
 * it came from an idempotent replay. The controller needs the flag to answer 201
 * for a first write and 200 for a replay.
 */
@Schema(hidden = true)
public record TransferOutcome(TransactionResponse response, boolean replay) {
}