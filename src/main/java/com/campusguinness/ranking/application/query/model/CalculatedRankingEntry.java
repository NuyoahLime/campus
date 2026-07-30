package com.campusguinness.ranking.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record CalculatedRankingEntry(
        int rankPosition,
        UUID studentId,
        String studentDisplayName,
        String schoolName,
        UUID scoreAttemptId,
        String scoreDisplayValue,
        Instant scoreBusinessTime,
        UUID ruleVersionId) {
}
