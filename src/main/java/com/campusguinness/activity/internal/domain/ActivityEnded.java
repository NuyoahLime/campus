package com.campusguinness.activity.internal.domain;

import java.time.Instant;

/** Domain event: activity execution ended (terminal state). */
public record ActivityEnded(ActivityId activityId, Instant occurredAt) {
    public ActivityEnded(ActivityId id) { this(id, Instant.now()); }
}
