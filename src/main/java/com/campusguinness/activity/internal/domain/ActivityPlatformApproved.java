package com.campusguinness.activity.internal.domain;

import java.time.Instant;

/** Domain event: platform admin approved the activity for public listing. */
public record ActivityPlatformApproved(ActivityId activityId, Instant occurredAt) {
    public ActivityPlatformApproved(ActivityId id) { this(id, Instant.now()); }
}
