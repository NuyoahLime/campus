package com.campusguinness.interfaces.web.activity;

import java.time.Instant;
import java.util.UUID;

public record ActivityListItem(UUID id, UUID schoolId, String title, Instant startTime,
        Instant endTime, String location, String executionStatus, String publicStatus) {}
