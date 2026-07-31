package com.campusguinness.ranking.application.query.model;

public record StudentRankingEntry(
        int rankPosition,
        String studentDisplayName,
        String scoreDisplayValue,
        boolean isCurrentStudent) {
}
