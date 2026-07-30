package com.campusguinness.ranking.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record RankingProjectDetail(
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
        boolean canPublish,
        Instant activityStartTime,
        Instant activityEndTime,
        String location,
        String projectDescription,
        String rulesText,
        String gradeOrder,
        Integer decimalPlaces,
        UUID currentRuleVersionId,
        UUID lastPublishedBy,
        String lastPublishedByName,
        String lastWithdrawalReason) {

    public TiePolicy tiePolicy() {
        return allowTie ? TiePolicy.COMPETITION : TiePolicy.EARLIER_BUSINESS_TIME;
    }
}
