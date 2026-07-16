package com.campusguinness.interfaces.web.challengeproject;

import java.time.Instant;
import java.util.UUID;

public record ChallengeProjectListItem(UUID id, String name, String category,
        String scoreStorageType, String comparisonDirection, String projectStatus, Instant createdAt) {}
