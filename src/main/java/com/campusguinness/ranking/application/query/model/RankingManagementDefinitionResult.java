package com.campusguinness.ranking.application.query.model;

import java.util.UUID;

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
        RankingManagementVersionResult latestGeneratedVersion,
        RankingManagementVersionResult currentPublishedVersion
) {}
