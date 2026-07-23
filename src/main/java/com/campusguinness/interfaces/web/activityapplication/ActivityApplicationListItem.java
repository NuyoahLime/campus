package com.campusguinness.interfaces.web.activityapplication;

import java.time.Instant;
import java.util.UUID;

public record ActivityApplicationListItem(UUID applicationId, UUID schoolId, String title,
        String status, UUID createdActivityId, Instant reviewedAt,
        String reviewComment, String rejectReason, int applicationVersion) {}
