package com.campusguinness.activity.internal.domain;

import java.time.Instant;

/** Domain event: activity is now publicly visible on the platform. */
public record ActivityMadePublic(ActivityId activityId, Instant occurredAt) {
    public ActivityMadePublic(ActivityId id) { this(id, Instant.now()); }
}
