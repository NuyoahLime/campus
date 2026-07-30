package com.campusguinness.ranking.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record RankingProjectItem(
        UUID activityProjectId,
        UUID activityId,
        String activityTitle,
        String executionStatus,
        UUID projectId,
        String projectName,
        String scoreStorageType,
        String scoreUnit,
        String comparisonDirection,
        String effectiveScoreRule,
        boolean allowTie,
        long approvedEffectiveScoreCount,
        long pendingReviewCount,
        RankingStatus rankingStatus,
        UUID currentVersionId,
        Integer currentVersionNumber,
        Long currentVersionEntryCount,
        Instant currentPublishedAt,
        RankingVersionStatus lastVersionStatus,
        boolean canPreview,
        boolean canPublish) {
}
