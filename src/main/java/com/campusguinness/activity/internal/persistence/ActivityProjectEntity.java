package com.campusguinness.activity.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activity_projects")
public class ActivityProjectEntity {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "activity_id", nullable = false, updatable = false) private UUID activityId;
    @Column(name = "project_id", nullable = false, updatable = false) private UUID projectId;
    @Column(name = "rule_version_id", nullable = false, updatable = false) private UUID ruleVersionId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected ActivityProjectEntity() {}
    public UUID getId() { return id; }
    void setId(UUID value) { id = value; }
    public UUID getActivityId() { return activityId; }
    void setActivityId(UUID value) { activityId = value; }
    public UUID getProjectId() { return projectId; }
    void setProjectId(UUID value) { projectId = value; }
    public UUID getRuleVersionId() { return ruleVersionId; }
    void setRuleVersionId(UUID value) { ruleVersionId = value; }
    public Instant getCreatedAt() { return createdAt; }
    void setCreatedAt(Instant value) { createdAt = value; }
}
