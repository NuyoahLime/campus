package com.campusguinness.activity.application.query.port;

import com.campusguinness.project.application.query.model.QueryPage;
import java.util.Optional;
import java.util.UUID;

public interface StudentProjectQueryPort {
    QueryPage<StudentProjectItem> findMine(UUID studentId, String executionStatus,
            String scoreStatus, String keyword, int page, int size);
    Optional<StudentProjectDetail> findMineById(UUID studentId, UUID activityProjectId);

    record StudentProjectItem(UUID activityProjectId, UUID activityId, String activityTitle,
            UUID projectId, String projectName, String category,
            String scoreStorageType, String comparisonDirection, String scoreUnit,
            int attemptCount, UUID latestAttemptId, String latestAttemptStatus,
            String latestScoreDisplay, boolean hasApprovedScore, java.time.Instant assignedAt) {}

    record StudentProjectDetail(UUID activityProjectId, UUID activityId, String activityTitle,
            UUID projectId, String projectName, String category,
            String scoreStorageType, String comparisonDirection, String scoreUnit,
            int attemptCount, UUID latestAttemptId, String latestAttemptStatus,
            String latestScoreDisplay, boolean hasApprovedScore, java.time.Instant assignedAt,
            String activityDescription, java.time.Instant activityStartTime,
            java.time.Instant activityEndTime, String location,
            String projectDescription, String rulesText, String venueRequirements,
            String equipmentRequirements, String effectiveScoreRule,
            Boolean allowTie, Integer decimalPlaces, String gradeOrder) {}
}
