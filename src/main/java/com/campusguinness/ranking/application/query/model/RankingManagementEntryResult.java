package com.campusguinness.ranking.application.query.model;

public record RankingManagementEntryResult(
        int rankPosition,
        String studentDisplayName,
        String schoolName,
        String scoreDisplayValue
) {}
