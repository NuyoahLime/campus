package com.campusguinness.score.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record TeacherScoreAttemptItem(
        UUID attemptId,
        UUID activityProjectId,
        UUID activityId,
        String activityTitle,
        UUID schoolId,
        String schoolName,
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
        Instant submittedAt,
        Instant createdAt,
        Instant updatedAt,
        boolean currentEffective) {
}
