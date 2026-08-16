package com.campusguinness.interfaces.web.challengeproject;

import java.time.Instant;
import java.util.UUID;

public record ChallengeProjectResponse(
        UUID id, String name, String category, String description,
        String venueRequirements, String equipmentRequirements, String rulesText,
        String scoreStorageType, String scoreIndicatorType, String comparisonDirection,
        String scoreUnit, Integer decimalPlaces, String gradeOrder, boolean allowTie,
        String effectiveScoreRule, String status, UUID currentRuleVersionId,
        Integer currentRuleVersionNumber, Instant createdAt, Instant updatedAt) {

    public ChallengeProjectResponse(UUID id, String name, String status) {
        this(id, name, null, null, null, null, null, null, null, null,
                null, null, null, false, null, status, null, null, null, null);
    }
}
