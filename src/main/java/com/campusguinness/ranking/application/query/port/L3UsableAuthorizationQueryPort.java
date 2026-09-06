package com.campusguinness.ranking.application.query.port;

import com.campusguinness.ranking.application.query.model.L3UsableAuthorizationResult;

import java.util.List;
import java.util.UUID;

public interface L3UsableAuthorizationQueryPort {
    List<L3UsableAuthorizationResult> findUsableAuthorizations(UUID projectId, UUID ruleVersionId);
}
