package com.campusguinness.ranking.application.query.model;

import java.util.List;
import java.util.UUID;

public record RankingPreviewResult(
        UUID activityProjectId,
        String activityTitle,
        String projectName,
        String scoreStorageType,
        String scoreUnit,
        String comparisonDirection,
        String effectiveScoreRule,
        TiePolicy tiePolicy,
        String sourceFingerprint,
        int totalRanked,
        long pendingReviewCount,
        boolean publishable,
        List<String> warnings,
        List<CalculatedRankingEntry> entries) {
}
