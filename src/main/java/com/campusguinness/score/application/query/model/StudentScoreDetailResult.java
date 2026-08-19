package com.campusguinness.score.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record StudentScoreDetailResult(
        UUID scoreAttemptId,
        UUID activityProjectId,
        UUID activityId,
        String activityName,
        String challengeProjectName,
        int attemptNumber,
        String scoreStorageType,
        String scoreValue,
        String scoreUnit,
        Instant scoreBusinessTime,
        String status,
        UUID ruleVersionId,
        int ruleVersionNumber,
        String rulesText) {
}
