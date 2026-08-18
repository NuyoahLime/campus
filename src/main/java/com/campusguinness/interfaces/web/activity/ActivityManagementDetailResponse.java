package com.campusguinness.interfaces.web.activity;

import com.campusguinness.activity.application.query.model.ActivityProjectResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ActivityManagementDetailResponse(UUID id, UUID schoolId, String schoolName,
        String title, String description, Instant startTime, Instant endTime, String location,
        String executionStatus, String publicStatus, Instant createdAt, Instant updatedAt,
        List<ActivityProjectResult> projects) {}
