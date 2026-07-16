package com.campusguinness.activity.internal.domain;

import java.time.Instant;

/** Domain event: a rejected application was returned to DRAFT for revision. */
public record ActivityApplicationReturnedToDraft(
        ActivityApplicationId applicationId,
        int newVersion,
        Instant occurredAt) {
    public ActivityApplicationReturnedToDraft(ActivityApplicationId id, int newVersion) {
        this(id, newVersion, Instant.now());
    }
}
