package com.finova.fraud.dto;

/** Body of every {@code PATCH /api/fraud/alerts/{id}/...} action. */
public record ReviewAlertRequest(String note) {

    public String noteOrNull() {
        return note == null || note.isBlank() ? null : note.trim();
    }
}
