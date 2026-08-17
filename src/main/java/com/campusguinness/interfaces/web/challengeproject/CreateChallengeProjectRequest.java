package com.campusguinness.interfaces.web.challengeproject;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateChallengeProjectRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 64) String category,
        @NotBlank String scoreStorageType,
        @NotBlank String scoreIndicatorType,
        @NotBlank String comparisonDirection,
        @NotBlank String effectiveScoreRule,
        boolean allowTie,
        String scoreUnit,
        Integer decimalPlaces,
        String gradeOrder,
        String rulesText,
        String description,
        String venueRequirements,
        String equipmentRequirements) {
    public CreateChallengeProjectRequest(
            String name, String category, String scoreStorageType,
            String scoreIndicatorType, String comparisonDirection,
            String effectiveScoreRule, boolean allowTie, String scoreUnit,
            Integer decimalPlaces, String gradeOrder, String rulesText,
            String description) {
        this(name, category, scoreStorageType, scoreIndicatorType, comparisonDirection,
                effectiveScoreRule, allowTie, scoreUnit, decimalPlaces, gradeOrder,
                rulesText, description, null, null);
    }
}
