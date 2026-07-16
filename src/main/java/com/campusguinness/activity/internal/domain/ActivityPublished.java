package com.campusguinness.activity.internal.domain;

import java.time.Instant;

/** Domain event: activity execution status changed from DRAFT to PUBLISHED. */
public record ActivityPublished(ActivityId activityId, Instant occurredAt) {
    public ActivityPublished(ActivityId id) { this(id, Instant.now()); }
}
