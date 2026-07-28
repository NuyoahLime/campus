package com.campusguinness.activity.application.port;

import java.util.Optional;
import java.util.UUID;

/** Read-only port for resolving a project's current rule version before adding it to an activity. */
public interface ProjectCurrentRuleVersionPort {
    Optional<UUID> findCurrentRuleVersionId(UUID projectId);
}
