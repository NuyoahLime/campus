package com.campusguinness.activity.internal.domain;

import java.time.Instant;

/** Domain event: activity was cancelled (terminal state, public visibility auto-stopped). */
public record ActivityCancelled(ActivityId activityId, Instant occurredAt) {
    public ActivityCancelled(ActivityId id) { this(id, Instant.now()); }
}
