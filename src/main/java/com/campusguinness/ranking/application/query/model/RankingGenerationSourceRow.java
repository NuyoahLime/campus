package com.campusguinness.ranking.application.query.model;

import java.math.BigDecimal;
import java.util.UUID;

public record RankingGenerationSourceRow(
        UUID scoreAttemptId,
        UUID studentId,
        String studentDisplayName,
        BigDecimal numericValue,
        Long durationMs,
        String grade,
        UUID activityProjectId,
        UUID ruleVersionId,
        String schoolName
) {
    public RankingGenerationSourceRow(
            UUID scoreAttemptId,
            UUID studentId,
            String studentDisplayName,
            BigDecimal numericValue,
            Long durationMs,
            String grade) {
        this(scoreAttemptId, studentId, studentDisplayName, numericValue, durationMs, grade, null, null, null);
    }

    public RankingGenerationSourceRow(
            UUID scoreAttemptId,
            UUID studentId,
            String studentDisplayName,
            BigDecimal numericValue,
            Long durationMs,
            String grade,
            UUID activityProjectId,
            UUID ruleVersionId) {
        this(scoreAttemptId, studentId, studentDisplayName, numericValue, durationMs, grade, activityProjectId, ruleVersionId, null);
    }
}
