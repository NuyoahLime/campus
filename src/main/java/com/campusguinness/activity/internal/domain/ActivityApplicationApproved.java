package com.campusguinness.activity.internal.domain;

import java.time.Instant;
import java.util.UUID;

/** Domain event: an activity application was approved and an Activity was created. */
public record ActivityApplicationApproved(
        ActivityApplicationId applicationId,
        UUID createdActivityId,
        Instant occurredAt) {
    public ActivityApplicationApproved(ActivityApplicationId id, UUID createdActivityId) {
        this(id, createdActivityId, Instant.now());
    }
}
