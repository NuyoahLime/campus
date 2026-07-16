package com.campusguinness.activity.internal.domain;

import java.time.Instant;

/** Domain event: activity submitted for platform public review. */
public record ActivitySubmittedForReview(ActivityId activityId, Instant occurredAt) {
    public ActivitySubmittedForReview(ActivityId id) { this(id, Instant.now()); }
}
