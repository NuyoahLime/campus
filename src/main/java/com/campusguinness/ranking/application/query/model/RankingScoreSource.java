package com.campusguinness.ranking.application.query.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RankingScoreSource(
        UUID scoreAttemptId,
        UUID studentId,
        String studentDisplayName,
        String schoolName,
        String scoreStorageType,
        BigDecimal scoreValue,
        Long scoreDurationMs,
        String scoreGrade,
        Instant scoreBusinessTime,
        UUID currentRuleVersionId,
        Integer decimalPlaces) {

    public String canonicalScoreValue() {
        return switch (scoreStorageType) {
            case "INTEGER", "DECIMAL" -> scoreValue == null
                    ? ""
                    : scoreValue.stripTrailingZeros().toPlainString();
            case "DURATION" -> scoreDurationMs == null ? "" : scoreDurationMs.toString();
            case "GRADE" -> scoreGrade == null ? "" : scoreGrade;
            default -> "";
        };
    }
}
