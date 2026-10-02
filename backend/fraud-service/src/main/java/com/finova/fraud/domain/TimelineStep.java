package com.finova.fraud.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One entry of the fraud timeline shown to the administrator (client spec section 36). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimelineStep {

    private String key;
    private String label;
    private String description;
    private Instant at;

    public static TimelineStep at(String key, String label, String description, Instant at) {
        return new TimelineStep(key, label, description, at);
    }
}
