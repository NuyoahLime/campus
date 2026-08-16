package com.campusguinness.project.application.command;

public record UpdateChallengeProjectCommand(
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
}
