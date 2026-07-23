package com.campusguinness.activity.internal.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activity_participants")
public class ActivityParticipantEntity {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "activity_id", nullable = false) private UUID activityId;
    @Column(name = "student_membership_id", nullable = false) private UUID studentMembershipId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected ActivityParticipantEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID v) { id = v; }
    public UUID getActivityId() { return activityId; }
    public void setActivityId(UUID v) { activityId = v; }
    public UUID getStudentMembershipId() { return studentMembershipId; }
    public void setStudentMembershipId(UUID v) { studentMembershipId = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { createdAt = v; }
}
