package com.campusguinness.project.internal.domain;

import java.time.Instant;

/** Domain event: a new challenge project was created. */
public record ChallengeProjectCreated(ChallengeProjectId projectId, Instant occurredAt) {
    public ChallengeProjectCreated(ChallengeProjectId projectId) {
        this(projectId, Instant.now());
    }
}
