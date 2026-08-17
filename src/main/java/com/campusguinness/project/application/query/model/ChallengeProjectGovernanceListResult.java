package com.campusguinness.project.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record ChallengeProjectGovernanceListResult(
        UUID id, String name, String category, String projectStatus,
        String scoreStorageType, String scoreIndicatorType, String comparisonDirection,
        String scoreUnit, Integer currentRuleVersionNumber, Instant createdAt,
        Instant updatedAt) {}
