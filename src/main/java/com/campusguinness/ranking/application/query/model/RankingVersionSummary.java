package com.campusguinness.ranking.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record RankingVersionSummary(
        UUID versionId,
        int versionNumber,
        RankingVersionStatus versionStatus,
        long entryCount,
        UUID publishedBy,
        String publishedByName,
        Instant publishedAt,
        UUID withdrawnBy,
        String withdrawnByName,
        Instant withdrawnAt,
        String withdrawalReason,
        String createdReason) {
}
