package com.campusguinness.activity.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityProjectPort {
    record ProjectRecord(UUID id, UUID activityId, UUID projectId) {}

    ProjectRecord add(UUID activityId, UUID projectId, UUID ruleVersionId);
    List<ProjectRecord> findByActivity(UUID activityId);
    Optional<ProjectRecord> findByActivityAndProject(UUID activityId, UUID projectId);
    Optional<ProjectRecord> findById(UUID id);
    void remove(UUID activityId, UUID projectId);
    boolean existsByActivityAndProject(UUID activityId, UUID projectId);
}
