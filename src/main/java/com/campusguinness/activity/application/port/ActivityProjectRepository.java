package com.campusguinness.activity.application.port;

import java.util.UUID;

public interface ActivityProjectRepository {
    void save(ActivityProjectSnapshot snapshot);

    record ActivityProjectSnapshot(UUID id, UUID activityId, UUID projectId, UUID ruleVersionId) {}
}
