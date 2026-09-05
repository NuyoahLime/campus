package com.campusguinness.ranking.application.port;

import com.campusguinness.ranking.application.service.L3AuthorizationScope;

import java.util.UUID;

public interface L3AuthorizationValidationPort {
    void validateProjectRuleVersion(UUID projectId, UUID ruleVersionId);

    void validateSchoolScope(UUID schoolId, UUID projectId, UUID ruleVersionId, L3AuthorizationScope scope);

    void validateSchoolNormal(UUID schoolId);
}
