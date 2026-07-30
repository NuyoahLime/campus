package com.campusguinness.score.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record SchoolAdminScoreAttemptItem(
        UUID attemptId,
        UUID schoolId,
        UUID activityId,
        String activityTitle,
        UUID activityProjectId,
        UUID projectId,
        String projectName,
        UUID studentId,
        String studentName,
        int attemptNumber,
        String scoreStorageType,
        String displayValue,
        String scoreUnit,
        Instant scoreBusinessTime,
        String timeSource,
        String status,
        boolean currentEffective,
        UUID enteredBy,
        String enteredByName,
        Instant submittedAt,
        Instant createdAt,
        String effectiveScoreRule,
        String comparisonDirection) {
}
