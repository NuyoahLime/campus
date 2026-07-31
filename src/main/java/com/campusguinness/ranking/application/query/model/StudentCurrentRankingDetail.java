package com.campusguinness.ranking.application.query.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StudentCurrentRankingDetail(
        UUID activityProjectId,
        UUID activityId,
        String activityTitle,
        String schoolName,
        UUID projectId,
        String projectName,
        String scoreStorageType,
        String scoreUnit,
        String comparisonDirection,
        String effectiveScoreRule,
        TiePolicy tiePolicy,
        int versionNumber,
        Instant publishedAt,
        long totalRanked,
        Integer myRank,
        String myScoreDisplayValue,
        List<StudentRankingEntry> entries) {
}
