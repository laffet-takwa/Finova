package com.finova.account.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SeriesPoint", description = "One point of a dashboard time series")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SeriesPoint(
    @Schema(example = "01/10")
    String label,
    @Schema(description = "Populated for count series such as account growth")
    Long count,
    @Schema(description = "Populated for amount series such as daily balances")
    java.math.BigDecimal total
) {

    public static SeriesPoint ofCount(String label, long count) {
        return new SeriesPoint(label, count, null);
    }

    public static SeriesPoint ofTotal(String label, java.math.BigDecimal total) {
        return new SeriesPoint(label, null, total);
    }
}
