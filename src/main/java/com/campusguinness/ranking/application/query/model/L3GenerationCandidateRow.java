package com.campusguinness.ranking.application.query.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record L3GenerationCandidateRow(
        UUID scoreAttemptId,
        UUID studentId,
        UUID schoolId,
        String schoolName,
        UUID activityId,
        String activityTitle,
        Instant activityStart,
        Instant activityEnd,
        String studentGrade,
        String studentClassName,
        BigDecimal numericValue,
        Long durationMs,
        String scoreGrade,
        UUID activityProjectId,
        UUID ruleVersionId) {
}
