package com.campusguinness.activity.internal.domain;

import java.time.Instant;

/** Domain event: school admin withdrew the activity from public listing. */
public record ActivityWithdrawnBySchool(ActivityId activityId, Instant occurredAt) {
    public ActivityWithdrawnBySchool(ActivityId id) { this(id, Instant.now()); }
}
