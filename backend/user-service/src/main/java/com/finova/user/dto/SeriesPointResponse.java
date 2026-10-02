package com.finova.user.dto;

/** One point of a chart series: a display label and a count. */
public record SeriesPointResponse(String label, long count) {
}
