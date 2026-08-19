package com.campusguinness.appeal.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record ScoreAppealDetailResult(
        UUID appealId,
        UUID scoreAttemptId,
        String activityName,
        String challengeProjectName,
        String scoreStorageType,
        String scoreValue,
        String scoreUnit,
        String appealType,
        String appealReason,
        String status,
        String resolution,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt) {
}
