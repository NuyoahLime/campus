package com.campusguinness.project.application.port;

import com.campusguinness.project.internal.domain.ScoreConfig;

import java.time.Instant;
import java.util.UUID;

/** Immutable snapshot of the rule inputs used by a project version. */
public record ProjectRuleVersionSnapshot(
        UUID id,
        UUID projectId,
        int versionNumber,
        ScoreConfig scoreConfig,
        String venueRequirements,
        String equipmentRequirements,
        String changeReason,
        UUID createdBy,
        Instant createdAt) {
}
