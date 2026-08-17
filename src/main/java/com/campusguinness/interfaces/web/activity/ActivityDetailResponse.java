package com.campusguinness.interfaces.web.activity;

import com.campusguinness.activity.application.query.model.ActivityDetailResult;
import com.campusguinness.activity.application.query.model.ActivityProjectResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ActivityDetailResponse(
        UUID id,
        UUID schoolId,
        String schoolName,
        String schoolRegion,
        String title,
        String description,
        Instant startTime,
        Instant endTime,
        String location,
        String executionStatus,
        List<ActivityProjectResponse> projects) {

    static ActivityDetailResponse from(ActivityDetailResult result) {
        return new ActivityDetailResponse(result.id(), result.schoolId(), result.schoolName(),
                result.schoolRegion(), result.title(), result.description(), result.startTime(),
                result.endTime(), result.location(), result.executionStatus(),
                result.projects().stream().map(ActivityProjectResponse::from).toList());
    }

    public record ActivityProjectResponse(
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
            boolean allowTie) {
        static ActivityProjectResponse from(ActivityProjectResult result) {
            return new ActivityProjectResponse(result.projectId(), result.projectName(), result.category(),
                    result.ruleVersionId(), result.ruleVersionNumber(), result.rulesText(),
                    result.scoreStorageType(), result.scoreIndicatorType(), result.comparisonDirection(),
                    result.scoreUnit(), result.allowTie());
        }
    }
}
