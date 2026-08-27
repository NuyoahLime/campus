package com.campusguinness.score.application.port;

import java.util.Optional;
import java.util.UUID;

public interface ActivityProjectLockPort {
    Optional<Scope> lock(UUID activityProjectId);

    record Scope(UUID activityProjectId, UUID projectId, UUID ruleVersionId,
                 String effectiveScoreRule, String scoreStorageType,
                 String comparisonDirection, String gradeOrder, boolean allowTie) {}
}
