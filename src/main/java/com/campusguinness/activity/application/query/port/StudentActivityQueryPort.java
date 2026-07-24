package com.campusguinness.activity.application.query.port;

import com.campusguinness.project.application.query.model.QueryPage;

import java.util.Optional;
import java.util.UUID;

public interface StudentActivityQueryPort {
    QueryPage<StudentActivityItem> findMine(UUID studentId, int page, int size);
    Optional<StudentActivityDetail> findMineById(UUID studentId, UUID activityId);

    record StudentActivityItem(UUID activityId, String title, String descriptionSummary,
            java.time.Instant startTime, java.time.Instant endTime, String location,
            String executionStatus, int assignedProjectCount) {}

    record StudentActivityDetail(UUID activityId, String title, String description,
            java.time.Instant startTime, java.time.Instant endTime, String location,
            String executionStatus, java.util.List<AssignedProjectItem> projects) {}

    record AssignedProjectItem(UUID activityProjectId, UUID projectId, String projectName,
            String category, String scoreStorageType, String scoreUnit,
            UUID latestAttemptId, String latestAttemptStatus, String latestScoreDisplay,
            boolean hasApprovedScore) {}
}
