package com.campusguinness.ranking.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record RankingEntryItem(
        UUID rankingEntryId,
        int rankPosition,
        UUID studentId,
        String studentDisplayName,
        String schoolName,
        String scoreDisplayValue,
        UUID scoreAttemptId,
        Instant scoreBusinessTime) {
}
