package com.campusguinness.project.internal.domain;

import java.time.Instant;

/** Domain event: a project was published (DRAFT → PUBLISHED or ARCHIVED → PUBLISHED). */
public record ProjectPublished(ChallengeProjectId projectId, Instant occurredAt) {
    public ProjectPublished(ChallengeProjectId projectId) {
        this(projectId, Instant.now());
    }
}
