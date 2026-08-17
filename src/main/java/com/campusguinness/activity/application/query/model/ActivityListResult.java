package com.campusguinness.activity.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record ActivityListResult(UUID id, UUID schoolId, String title, Instant startTime,
        Instant endTime, String location, String executionStatus,
        String schoolName, String schoolRegion, String description) {
    public ActivityListResult(UUID id, UUID schoolId, String title, Instant startTime,
            Instant endTime, String location, String executionStatus) {
        this(id, schoolId, title, startTime, endTime, location, executionStatus, null, null, null);
    }
}
