package com.campusguinness.activity.internal.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activity_project_participants")
public class ActivityProjectParticipantEntity {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "activity_project_id", nullable = false) private UUID activityProjectId;
    @Column(name = "activity_participant_id", nullable = false) private UUID activityParticipantId;
    @Column(name = "assigned_by", nullable = false) private UUID assignedBy;
    @Column(name = "assigned_at", nullable = false) private Instant assignedAt;
    protected ActivityProjectParticipantEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID v) { id = v; }
    public UUID getActivityProjectId() { return activityProjectId; }
    public void setActivityProjectId(UUID v) { activityProjectId = v; }
    public UUID getActivityParticipantId() { return activityParticipantId; }
    public void setActivityParticipantId(UUID v) { activityParticipantId = v; }
    public UUID getAssignedBy() { return assignedBy; }
    public void setAssignedBy(UUID v) { assignedBy = v; }
    public Instant getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Instant v) { assignedAt = v; }
}
