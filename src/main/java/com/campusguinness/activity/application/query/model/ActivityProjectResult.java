package com.campusguinness.activity.application.query.model;

import java.util.UUID;

public record ActivityProjectResult(
        UUID id,
        UUID projectId,
        String projectName,
        String category,
        UUID ruleVersionId,
        int ruleVersionNumber,
        String rulesText,
        String scoreStorageType,
        String scoreIndicatorType,
        String comparisonDirection,
        String scoreUnit,
        boolean allowTie) {}
