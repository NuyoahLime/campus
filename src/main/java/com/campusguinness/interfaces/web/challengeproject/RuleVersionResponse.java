package com.campusguinness.interfaces.web.challengeproject;

import com.campusguinness.project.application.query.model.ProjectRuleVersionResult;

import java.time.Instant;
import java.util.UUID;

public record RuleVersionResponse(
        UUID id, int versionNumber, String scoreStorageType, String scoreIndicatorType,
        String comparisonDirection, String scoreUnit, Integer decimalPlaces,
        String gradeOrder, boolean allowTie, String effectiveScoreRule, String rulesText,
        String venueRequirements, String equipmentRequirements, String changeReason,
        UUID createdBy, Instant createdAt) {
    public static RuleVersionResponse from(ProjectRuleVersionResult value) {
        return new RuleVersionResponse(value.id(), value.versionNumber(), value.scoreStorageType(),
                value.scoreIndicatorType(), value.comparisonDirection(), value.scoreUnit(),
                value.decimalPlaces(), value.gradeOrder(), value.allowTie(), value.effectiveScoreRule(),
                value.rulesText(), value.venueRequirements(), value.equipmentRequirements(),
                value.changeReason(), value.createdBy(), value.createdAt());
    }
}
