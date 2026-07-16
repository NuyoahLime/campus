package com.campusguinness.activity.internal.domain;

import java.time.Instant;

/** Domain event: activity entered IN_PROGRESS execution status. */
public record ActivityExecutionStarted(ActivityId activityId, Instant occurredAt) {
    public ActivityExecutionStarted(ActivityId id) { this(id, Instant.now()); }
}
