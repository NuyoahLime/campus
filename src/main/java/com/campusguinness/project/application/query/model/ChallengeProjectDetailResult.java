package com.campusguinness.project.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record ChallengeProjectDetailResult(
        UUID id, String name, String category, String description,
        String venueRequirements, String equipmentRequirements, String rulesText,
        String scoreStorageType, String scoreIndicatorType, String comparisonDirection,
        String scoreUnit, Integer decimalPlaces, String gradeOrder, boolean allowTie,
        String effectiveScoreRule, String projectStatus, UUID currentRuleVersionId,
        Integer currentRuleVersionNumber, Instant createdAt, Instant updatedAt) {}
