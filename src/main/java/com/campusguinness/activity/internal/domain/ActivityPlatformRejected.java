package com.campusguinness.activity.internal.domain;

import java.time.Instant;

/** Domain event: platform admin rejected the public review submission. */
public record ActivityPlatformRejected(ActivityId activityId, String rejectReason, Instant occurredAt) {
    public ActivityPlatformRejected(ActivityId id, String rejectReason) {
        this(id, rejectReason, Instant.now());
    }
}
