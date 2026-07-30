package com.campusguinness.score.application.query.model;

import java.util.UUID;

public record ScoreEntryProjectOption(
        UUID activityProjectId,
        UUID activityId,
        String activityTitle,
        String executionStatus,
        UUID projectId,
        String projectName,
        String scoreStorageType,
        String scoreUnit,
        Integer decimalPlaces,
        String gradeOrder,
        String comparisonDirection,
        String effectiveScoreRule) {
}
