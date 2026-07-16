package com.campusguinness.project.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record ChallengeProjectListResult(UUID id, String name, String category,
        String scoreStorageType, String comparisonDirection, String projectStatus, Instant createdAt) {}
