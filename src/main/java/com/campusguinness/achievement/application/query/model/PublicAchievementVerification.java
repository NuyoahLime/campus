package com.campusguinness.achievement.application.query.model;

import java.time.Instant;

public record PublicAchievementVerification(
        boolean valid,
        AchievementStatus status,
        String recordTitle,
        String schoolName,
        String activityTitle,
        String projectName,
        int rankingVersionNumber,
        int rankPosition,
        String scoreDisplayValue,
        String scoreStorageType,
        Instant issuedAt,
        Instant revokedAt) {
}
