package com.campusguinness.achievement.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record SchoolAdminAchievementStatus(
        UUID rankingEntryId,
        UUID achievementRecordId,
        AchievementStatus achievementStatus,
        String verificationCode,
        Instant issuedAt) {
}
