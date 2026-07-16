package com.campusguinness.project.internal.domain;

import java.util.UUID;

/**
 * Strongly-typed identifier for {@link ChallengeProject}.
 */
public record ChallengeProjectId(UUID value) {
    public ChallengeProjectId {
        if (value == null) {
            throw new IllegalArgumentException("ChallengeProjectId must not be null");
        }
    }
}
