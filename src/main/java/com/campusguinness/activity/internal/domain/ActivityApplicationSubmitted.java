package com.campusguinness.activity.internal.domain;

import java.time.Instant;

/** Domain event: an activity application was submitted for school admin review. */
public record ActivityApplicationSubmitted(ActivityApplicationId applicationId, Instant occurredAt) {
    public ActivityApplicationSubmitted(ActivityApplicationId id) {
        this(id, Instant.now());
    }
}
