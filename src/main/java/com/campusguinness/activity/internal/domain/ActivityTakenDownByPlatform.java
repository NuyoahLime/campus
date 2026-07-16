package com.campusguinness.activity.internal.domain;

import java.time.Instant;

/** Domain event: platform admin forcibly took down the activity from public listing. */
public record ActivityTakenDownByPlatform(ActivityId activityId, String reason, Instant occurredAt) {
    public ActivityTakenDownByPlatform(ActivityId id, String reason) {
        this(id, reason, Instant.now());
    }
}
