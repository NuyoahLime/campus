package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.application.port.ActivityProjectParticipantPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class ActivityProjectParticipantAdapter implements ActivityProjectParticipantPort {
    private final ActivityProjectParticipantJpaRepository jpa;

    ActivityProjectParticipantAdapter(ActivityProjectParticipantJpaRepository jpa) { this.jpa = jpa; }

    @Override @Transactional
    public ProjectParticipantRecord assign(UUID activityProjectId, UUID activityParticipantId, UUID assignedBy) {
        var e = new ActivityProjectParticipantEntity();
        e.setId(UUID.randomUUID());
        e.setActivityProjectId(activityProjectId);
        e.setActivityParticipantId(activityParticipantId);
        e.setAssignedBy(assignedBy);
        e.setAssignedAt(Instant.now());
        jpa.save(e);
        return toRecord(e);
    }

    @Override @Transactional(readOnly = true)
    public List<ProjectParticipantRecord> findByProject(UUID activityProjectId) {
        return jpa.findByActivityProjectId(activityProjectId).stream().map(this::toRecord).toList();
    }

    @Override @Transactional(readOnly = true)
    public Optional<ProjectParticipantRecord> findByProjectAndParticipant(UUID activityProjectId, UUID activityParticipantId) {
        return jpa.findByActivityProjectIdAndActivityParticipantId(activityProjectId, activityParticipantId).map(this::toRecord);
    }

    @Override @Transactional
    public void unassign(UUID activityProjectId, UUID activityParticipantId) {
        jpa.deleteByActivityProjectIdAndActivityParticipantId(activityProjectId, activityParticipantId);
    }

    @Override @Transactional(readOnly = true)
    public boolean existsByProjectAndParticipant(UUID activityProjectId, UUID activityParticipantId) {
        return jpa.existsByActivityProjectIdAndActivityParticipantId(activityProjectId, activityParticipantId);
    }

    @Override @Transactional(readOnly = true)
    public List<ProjectParticipantRecord> findByParticipantId(UUID activityParticipantId) {
        return jpa.findByActivityParticipantId(activityParticipantId).stream().map(this::toRecord).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<ProjectParticipantRecord> findByParticipantIds(List<UUID> participantIds) {
        return jpa.findByActivityParticipantIdIn(participantIds).stream().map(this::toRecord).toList();
    }

    @Override @Transactional(readOnly = true)
    public boolean existsByParticipantId(UUID activityParticipantId) {
        return !jpa.findByActivityParticipantId(activityParticipantId).isEmpty();
    }

    private ProjectParticipantRecord toRecord(ActivityProjectParticipantEntity e) {
        return new ProjectParticipantRecord(e.getId(), e.getActivityProjectId(),
                e.getActivityParticipantId(), e.getAssignedBy(), e.getAssignedAt());
    }
}
