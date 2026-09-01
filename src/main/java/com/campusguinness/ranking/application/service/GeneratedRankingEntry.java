package com.campusguinness.ranking.application.service;

import java.util.UUID;

public record GeneratedRankingEntry(
        UUID studentId,
        UUID scoreAttemptId,
        int rankPosition,
        String studentDisplayName,
        String schoolName,
        String scoreDisplayValue,
        UUID ruleVersionId
) {}
