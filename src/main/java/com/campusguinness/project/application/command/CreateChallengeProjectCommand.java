package com.campusguinness.project.application.command;

/** Immutable command for creating a new ChallengeProject. */
public record CreateChallengeProjectCommand(
        String name,
        String category,
        String scoreStorageType,
        String scoreIndicatorType,
        String comparisonDirection,
        String effectiveScoreRule,
        boolean allowTie,
        String scoreUnit,
        Integer decimalPlaces,
        String gradeOrder,
        String rulesText,
        String description,
        String venueRequirements,
        String equipmentRequirements) {
    public CreateChallengeProjectCommand(
            String name, String category, String scoreStorageType,
            String scoreIndicatorType, String comparisonDirection,
            String effectiveScoreRule, boolean allowTie, String scoreUnit,
            Integer decimalPlaces, String gradeOrder, String rulesText,
            String description) {
        this(name, category, scoreStorageType, scoreIndicatorType, comparisonDirection,
                effectiveScoreRule, allowTie, scoreUnit, decimalPlaces, gradeOrder,
                rulesText, description, null, null);
    }

    public CreateChallengeProjectCommand {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        if (category == null || category.isBlank()) throw new IllegalArgumentException("category required");
        if (scoreStorageType == null) throw new IllegalArgumentException("scoreStorageType required");
        if (scoreIndicatorType == null) throw new IllegalArgumentException("scoreIndicatorType required");
        if (comparisonDirection == null) throw new IllegalArgumentException("comparisonDirection required");
        if (effectiveScoreRule == null || effectiveScoreRule.isBlank())
            throw new IllegalArgumentException("effectiveScoreRule required");
    }
}
