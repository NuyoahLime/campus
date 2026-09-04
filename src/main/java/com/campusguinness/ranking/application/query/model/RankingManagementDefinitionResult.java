package com.campusguinness.ranking.application.query.model;

import java.util.UUID;
import java.time.Instant;

public record RankingManagementDefinitionResult(
        UUID id,
        String name,
        String layer,
        boolean enabled,
        UUID schoolId,
        String schoolName,
        UUID projectId,
        String projectName,
        UUID activityId,
        String activityTitle,
        UUID activityProjectId,
        String dimensionFilters,
        String selectionPolicy,
        String grade,
        String className,
        Instant activityPeriodStart,
        Instant activityPeriodEnd,
        RankingManagementVersionResult latestGeneratedVersion,
        RankingManagementVersionResult currentPublishedVersion
) {}
