package com.campusguinness.score.application.query.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TeacherScoreAttemptDetail(
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
        Long integerValue,
        BigDecimal decimalValue,
        Long durationMs,
        String grade,
        Integer decimalPlaces,
        String gradeOrder,
        Instant scoreBusinessTime,
        String timeSource,
        String status,
        Instant submittedAt,
        Instant createdAt,
        Instant updatedAt,
        boolean currentEffective,
        List<ScoreReviewHistoryItem> reviewHistory) {
}
