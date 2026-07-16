package com.campusguinness.project.internal.domain;

/**
 * Immutable score configuration for a challenge project.
 * All fields enforced at construction; no setters.
 */
public record ScoreConfig(
        ScoreStorageType storageType,
        ScoreIndicatorType indicatorType,
        ComparisonDirection comparisonDirection,
        String scoreUnit,
        Integer decimalPlaces,
        String effectiveScoreRule,
        String gradeOrder,
        String rulesText,
        boolean allowTie) {

    public ScoreConfig {
        if (storageType == null) {
            throw new IllegalArgumentException("scoreStorageType must not be null");
        }
        if (indicatorType == null) {
            throw new IllegalArgumentException("scoreIndicatorType must not be null");
        }
        if (comparisonDirection == null) {
            throw new IllegalArgumentException("comparisonDirection must not be null");
        }
        if (effectiveScoreRule == null || effectiveScoreRule.isBlank()) {
            throw new IllegalArgumentException("effectiveScoreRule must not be null or blank");
        }
    }
}
