package com.campusguinness.ranking.application.query.model;

public record RankingEntryReadResult(
        int rankPosition,
        String studentDisplayName,
        String schoolName,
        String scoreDisplayValue
) {}
