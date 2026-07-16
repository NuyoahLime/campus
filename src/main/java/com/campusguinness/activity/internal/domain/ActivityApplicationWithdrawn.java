package com.campusguinness.activity.internal.domain;

import java.time.Instant;

/** Domain event: an activity application was withdrawn by the applicant. */
public record ActivityApplicationWithdrawn(ActivityApplicationId applicationId, Instant occurredAt) {
    public ActivityApplicationWithdrawn(ActivityApplicationId id) {
        this(id, Instant.now());
    }
}
