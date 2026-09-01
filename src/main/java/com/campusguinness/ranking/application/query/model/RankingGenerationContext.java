package com.campusguinness.ranking.application.query.model;

import java.util.UUID;

public record RankingGenerationContext(
        UUID activityProjectId,
        UUID activityId,
        String activityTitle,
        UUID schoolId,
        String schoolName,
        UUID projectId,
        String projectName,
        UUID ruleVersionId,
        int ruleVersionNumber,
        String scoreStorageType,
        String comparisonDirection,
        Integer decimalPlaces,
        String gradeOrder
) {}
