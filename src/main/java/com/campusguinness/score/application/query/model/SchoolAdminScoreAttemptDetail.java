package com.campusguinness.score.application.query.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SchoolAdminScoreAttemptDetail(
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
        String comparisonDirection,
        Long integerValue,
        BigDecimal decimalValue,
        Long durationMs,
        String grade,
        Integer decimalPlaces,
        String gradeOrder,
        boolean allowTie,
        List<ScoreReviewHistoryItem> reviewHistory) {
}
