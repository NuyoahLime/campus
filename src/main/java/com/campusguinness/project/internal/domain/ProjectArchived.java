package com.campusguinness.project.internal.domain;

import java.time.Instant;

/** Domain event: a project was archived (PUBLISHED → ARCHIVED). */
public record ProjectArchived(ChallengeProjectId projectId, Instant occurredAt) {
    public ProjectArchived(ChallengeProjectId projectId) {
        this(projectId, Instant.now());
    }
}
