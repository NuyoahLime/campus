package com.campusguinness.ranking.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record StudentRankingProjectItem(
        UUID activityProjectId,
        UUID activityId,
        String activityTitle,
        UUID schoolId,
        String schoolName,
        String executionStatus,
        UUID projectId,
        String projectName,
        String scoreStorageType,
        String scoreUnit,
        String comparisonDirection,
        StudentRankingAvailability rankingAvailability,
        Integer currentVersionNumber,
        Instant publishedAt,
        Long totalRanked,
        Integer myRank,
        String myScoreDisplayValue) {
}
