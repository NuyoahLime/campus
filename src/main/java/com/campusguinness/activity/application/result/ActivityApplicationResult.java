package com.campusguinness.activity.application.result;

import java.time.Instant;
import java.util.UUID;

public record ActivityApplicationResult(UUID applicationId, UUID schoolId, String title,
        String description, String status, UUID createdActivityId, Instant reviewedAt,
        String reviewComment, String rejectReason, int applicationVersion) {

    /** Lightweight result used in lists — omits description and rejectReason. */
    public static ActivityApplicationResult fromDomain(
            com.campusguinness.activity.internal.domain.ActivityApplication a) {
        return new ActivityApplicationResult(
                a.id().value(), a.schoolId(), a.title(),
                a.description(), a.status().name(), a.createdActivityId(),
                a.reviewedAt(), a.reviewComment(), a.rejectReason(),
                a.applicationVersion());
    }

    /** Minimal constructor for backward compat — use fromDomain() for full result. */
    public ActivityApplicationResult(UUID applicationId, String status, UUID createdActivityId) {
        this(applicationId, null, null, null, status, createdActivityId, null, null, null, 0);
    }
}
