package com.campusguinness.interfaces.web.activity;

import java.time.Instant;
import java.util.UUID;

public record ActivityListItem(UUID id, UUID schoolId, String schoolName, String schoolRegion,
        String title, Instant startTime, Instant endTime, String location, String executionStatus) {
    public ActivityListItem(UUID id, UUID schoolId, String title, Instant startTime,
            Instant endTime, String location, String executionStatus) {
        this(id, schoolId, null, null, title, startTime, endTime, location, executionStatus);
    }
}
