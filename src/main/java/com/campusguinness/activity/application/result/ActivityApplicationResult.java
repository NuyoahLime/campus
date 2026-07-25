package com.campusguinness.activity.application.result;

import java.time.Instant;
import java.util.UUID;

public record ActivityApplicationResult(UUID applicationId, UUID schoolId, String schoolName,
        String title, String description, String status, UUID createdActivityId, Instant reviewedAt,
        String reviewComment, String rejectReason, int applicationVersion,
        Instant createdAt, Instant updatedAt) {

    /** Full constructor — use fromDomain() or fromQuery(). */
    public ActivityApplicationResult(UUID applicationId, UUID schoolId, String schoolName,
            String title, String description, String status, UUID createdActivityId,
            Instant reviewedAt, String reviewComment, String rejectReason,
            int applicationVersion, Instant createdAt, Instant updatedAt) {
        this.applicationId = applicationId; this.schoolId = schoolId; this.schoolName = schoolName;
        this.title = title; this.description = description; this.status = status;
        this.createdActivityId = createdActivityId; this.reviewedAt = reviewedAt;
        this.reviewComment = reviewComment; this.rejectReason = rejectReason;
        this.applicationVersion = applicationVersion;
        this.createdAt = createdAt; this.updatedAt = updatedAt;
    }

    /** Lightweight result used in lists — omits description and rejectReason. */
    public static ActivityApplicationResult fromDomain(
            com.campusguinness.activity.internal.domain.ActivityApplication a) {
        return new ActivityApplicationResult(
                a.id().value(), a.schoolId(), null,
                a.title(), a.description(), a.status().name(), a.createdActivityId(),
                a.reviewedAt(), a.reviewComment(), a.rejectReason(),
                a.applicationVersion(), null, null);
    }
}
