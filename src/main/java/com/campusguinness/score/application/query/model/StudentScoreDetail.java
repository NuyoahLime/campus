package com.campusguinness.score.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record StudentScoreDetail(
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
        Instant createdAt,
        String scoreValue,
        Long scoreDurationMs,
        String scoreGrade,
        String timeSource,
        String enteredByDisplayName,
        String reviewComment,
        String rejectReason,
        Instant reviewedAt
) {}
