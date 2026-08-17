package com.campusguinness.project.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record ProjectRuleVersionResult(
        UUID id, int versionNumber, String scoreStorageType, String scoreIndicatorType,
        String comparisonDirection, String scoreUnit, Integer decimalPlaces,
        String gradeOrder, boolean allowTie, String effectiveScoreRule, String rulesText,
        String venueRequirements, String equipmentRequirements, String changeReason,
        UUID createdBy, Instant createdAt) {}
