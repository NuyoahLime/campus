package com.campusguinness.activity.internal.domain;

import java.time.Instant;

/** Domain event: an activity application was rejected by school admin. */
public record ActivityApplicationRejected(
        ActivityApplicationId applicationId,
        String rejectReason,
        Instant occurredAt) {
    public ActivityApplicationRejected(ActivityApplicationId id, String rejectReason) {
        this(id, rejectReason, Instant.now());
    }
}
