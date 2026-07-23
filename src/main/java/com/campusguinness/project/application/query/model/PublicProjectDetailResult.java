package com.campusguinness.project.application.query.model;

import java.util.UUID;

public record PublicProjectDetailResult(UUID projectId, String name, String category,
        String description, String venueRequirements, String equipmentRequirements,
        String rulesText, String scoreStorageType, String scoreIndicatorType,
        String comparisonDirection, String effectiveScoreRule, boolean allowTie,
        String scoreUnit, Integer decimalPlaces, String gradeOrder) {}
