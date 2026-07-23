package com.campusguinness.activity.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityProjectParticipantPort {
    record ProjectParticipantRecord(UUID id, UUID activityProjectId, UUID activityParticipantId,
            UUID assignedBy, java.time.Instant assignedAt) {}

    ProjectParticipantRecord assign(UUID activityProjectId, UUID activityParticipantId, UUID assignedBy);
    List<ProjectParticipantRecord> findByProject(UUID activityProjectId);
    Optional<ProjectParticipantRecord> findByProjectAndParticipant(UUID activityProjectId, UUID activityParticipantId);
    void unassign(UUID activityProjectId, UUID activityParticipantId);
    boolean existsByProjectAndParticipant(UUID activityProjectId, UUID activityParticipantId);
    List<ProjectParticipantRecord> findByParticipantId(UUID activityParticipantId);
    List<ProjectParticipantRecord> findByParticipantIds(List<UUID> participantIds);
    boolean existsByParticipantId(UUID activityParticipantId);
}
