package com.campusguinness.ranking.application.port;

import java.util.Optional;
import java.util.UUID;

public interface RankingDefinitionPort {

    LockedDefinition getOrCreateAndLock(
            UUID activityProjectId,
            UUID schoolId,
            UUID projectId,
            String name,
            String tieBreakRule,
            UUID createdBy);

    Optional<LockedDefinition> lockExisting(
            UUID schoolId, UUID activityProjectId);

    void pointToCurrentVersion(UUID definitionId, UUID versionId);

    void clearCurrentVersion(UUID definitionId, UUID expectedVersionId);

    record LockedDefinition(UUID definitionId, UUID currentVersionId) {
    }
}
