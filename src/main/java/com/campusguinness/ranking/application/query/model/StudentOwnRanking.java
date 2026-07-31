package com.campusguinness.ranking.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record StudentOwnRanking(
        UUID activityProjectId,
        int versionNumber,
        int rankPosition,
        String scoreDisplayValue,
        long totalRanked,
        Instant publishedAt) {
}
