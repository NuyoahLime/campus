package com.campusguinness.project.application.port;

import java.util.Optional;
import java.util.UUID;

/** Creates and queries project rule versions — project module concern. */
public interface ProjectRuleVersionPort {

    /** Snapshot of project fields needed for an initial rule version. */
    record InitialRuleVersionSnapshot(
            String scoreStorageType, String scoreIndicatorType, String comparisonDirection,
            String effectiveScoreRule, String scoreUnit, Integer decimalPlaces,
            String gradeOrder, String rulesText,
            String venueRequirements, String equipmentRequirements) {}

    /** Create the initial rule version (version_number=1) and set it as current_rule_version_id.
     *  Returns the new rule version ID. */
    UUID createInitialRuleVersion(UUID projectId, InitialRuleVersionSnapshot snapshot, UUID createdBy);

    /** Read current_rule_version_id from challenge_projects. */
    Optional<UUID> findCurrentRuleVersionId(UUID projectId);
}
