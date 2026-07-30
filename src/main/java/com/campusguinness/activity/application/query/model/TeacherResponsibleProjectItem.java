package com.campusguinness.activity.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record TeacherResponsibleProjectItem(
        UUID activityProjectId,
        UUID activityId,
        String activityTitle,
        UUID schoolId,
        String schoolName,
        String executionStatus,
        Instant startTime,
        Instant endTime,
        String location,
        UUID projectId,
        String projectName,
        String category,
        String scoreStorageType,
        String scoreUnit,
        Integer decimalPlaces,
        String gradeOrder,
        String comparisonDirection,
        String effectiveScoreRule,
        long participantCount,
        long enteredAttemptCount,
        long pendingReviewCount,
        long rejectedCount) {
}
