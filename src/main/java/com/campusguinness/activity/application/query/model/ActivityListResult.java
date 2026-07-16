package com.campusguinness.activity.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record ActivityListResult(UUID id, UUID schoolId, String title, Instant startTime,
        Instant endTime, String location, String executionStatus) {}
