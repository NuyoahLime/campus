package com.campusguinness.interfaces.web.activityapplication;

import java.time.Instant;
import java.util.UUID;

public record ActivityApplicationResponse(UUID applicationId, UUID schoolId, String title,
        String description, String status, UUID createdActivityId, Instant reviewedAt,
        String reviewComment, String rejectReason, int applicationVersion) {

    public static ActivityApplicationResponse from(
            com.campusguinness.activity.application.result.ActivityApplicationResult r) {
        return new ActivityApplicationResponse(r.applicationId(), r.schoolId(), r.title(),
                r.description(), r.status(), r.createdActivityId(), r.reviewedAt(),
                r.reviewComment(), r.rejectReason(), r.applicationVersion());
    }
}
