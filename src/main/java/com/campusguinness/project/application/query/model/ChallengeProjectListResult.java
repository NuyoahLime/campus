package com.campusguinness.project.application.query.model;

import java.util.UUID;

public record ChallengeProjectListResult(UUID projectId, String name, String category,
        String descriptionSummary, String scoreStorageType, String comparisonDirection,
        String scoreUnit, String projectStatus, java.time.Instant createdAt) {}
