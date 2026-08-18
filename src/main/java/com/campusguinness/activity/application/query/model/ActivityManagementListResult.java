package com.campusguinness.activity.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record ActivityManagementListResult(
        UUID id, String title, String projectName, Integer ruleVersionNumber,
        String executionStatus, String publicStatus, Instant startTime,
        Instant endTime, Instant updatedAt) {}
