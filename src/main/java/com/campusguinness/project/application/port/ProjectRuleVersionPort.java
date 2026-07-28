package com.campusguinness.project.application.port;

import com.campusguinness.project.internal.domain.ScoreConfig;

import java.util.Optional;
import java.util.UUID;

/** Creates and queries project rule versions — project module concern. */
public interface ProjectRuleVersionPort {

    /** Create the initial rule version (version_number=1) from the project's score config
     *  and set it as current_rule_version_id. Returns the new rule version ID. */
    UUID createInitialRuleVersion(UUID projectId, ScoreConfig scoreConfig, UUID createdBy);

    /** Read current_rule_version_id from challenge_projects. */
    Optional<UUID> findCurrentRuleVersionId(UUID projectId);
}
