package com.campusguinness.activity.application.query.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ActivityDetailResult(
        UUID id,
        UUID schoolId,
        String schoolName,
        String schoolRegion,
        String title,
        String description,
        Instant startTime,
        Instant endTime,
        String location,
        String executionStatus,
        List<ActivityProjectResult> projects) {}
