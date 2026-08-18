package com.campusguinness.interfaces.web.activity;

import java.time.Instant;
import java.util.UUID;

public record ActivityManagementListItem(UUID id, String title, String projectName,
        Integer ruleVersionNumber, String executionStatus, String publicStatus,
        Instant startTime, Instant endTime, Instant updatedAt) {}
