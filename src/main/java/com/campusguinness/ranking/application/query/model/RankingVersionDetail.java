package com.campusguinness.ranking.application.query.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RankingVersionDetail(
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
        String createdReason,
        UUID activityProjectId,
        String activityTitle,
        String projectName,
        String scoreStorageType,
        String scoreUnit,
        String comparisonDirection,
        String effectiveScoreRule,
        TiePolicy tiePolicy,
        String gradeOrder,
        boolean allowTie,
        Integer decimalPlaces,
        UUID currentRuleVersionId,
        String sourceFingerprint,
        List<RankingEntryItem> entries) {
}
