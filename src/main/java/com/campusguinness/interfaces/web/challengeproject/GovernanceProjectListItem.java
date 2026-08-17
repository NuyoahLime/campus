package com.campusguinness.interfaces.web.challengeproject;

import java.time.Instant;
import java.util.UUID;

public record GovernanceProjectListItem(
        UUID id, String name, String category, String status,
        String scoreStorageType, String scoreIndicatorType,
        String comparisonDirection, String scoreUnit,
        Integer currentRuleVersionNumber, Instant createdAt, Instant updatedAt) {}
