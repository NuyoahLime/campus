package com.campusguinness.score.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record StudentScoreItem(
        UUID attemptId,
        UUID activityId,
        String activityTitle,
        UUID activityProjectId,
        UUID projectId,
        String projectName,
        int attemptNumber,
        String scoreStorageType,
        String scoreDisplay,
        String status,
        boolean isCurrentEffective,
        Instant scoreBusinessTime,
        Instant submittedAt,
        Instant createdAt
) {}
