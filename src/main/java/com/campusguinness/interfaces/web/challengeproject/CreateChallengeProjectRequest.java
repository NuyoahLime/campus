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
        String equipmentRequirements) {}
