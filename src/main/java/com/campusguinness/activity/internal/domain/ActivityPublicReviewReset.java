package com.campusguinness.activity.internal.domain;

import java.time.Instant;

/** Domain event: activity public review state reset to NOT_SUBMITTED (after reject/withdraw/takedown). */
public record ActivityPublicReviewReset(ActivityId activityId, Instant occurredAt) {
    public ActivityPublicReviewReset(ActivityId id) { this(id, Instant.now()); }
}
