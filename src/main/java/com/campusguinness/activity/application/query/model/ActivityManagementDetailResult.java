package com.campusguinness.activity.application.query.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.campusguinness.activity.application.query.model.ActivityProjectResult;

public record ActivityManagementDetailResult(
        UUID id, UUID schoolId, String schoolName, String title, String description,
        Instant startTime, Instant endTime, String location, String executionStatus,
        String publicStatus, Instant createdAt, Instant updatedAt,
        List<ActivityProjectResult> projects) {}
