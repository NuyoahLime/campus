package com.campusguinness.activity.internal.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "responsible_teachers")
public class ResponsibleTeacherEntity {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "activity_project_id", nullable = false) private UUID activityProjectId;
    @Column(name = "teacher_membership_id", nullable = false) private UUID teacherMembershipId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected ResponsibleTeacherEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID v) { id = v; }
    public UUID getActivityProjectId() { return activityProjectId; }
    public void setActivityProjectId(UUID v) { activityProjectId = v; }
    public UUID getTeacherMembershipId() { return teacherMembershipId; }
    public void setTeacherMembershipId(UUID v) { teacherMembershipId = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { createdAt = v; }
}
