package com.campusguinness.activity.internal.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activity_projects")
public class ActivityProjectEntity {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "activity_id", nullable = false) private UUID activityId;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "rule_version_id", nullable = false) private UUID ruleVersionId;
    @Column(name = "config", columnDefinition = "jsonb") private String config;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected ActivityProjectEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID v) { id = v; }
    public UUID getActivityId() { return activityId; }
    public void setActivityId(UUID v) { activityId = v; }
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID v) { projectId = v; }
    public UUID getRuleVersionId() { return ruleVersionId; }
    public void setRuleVersionId(UUID v) { ruleVersionId = v; }
    public String getConfig() { return config; }
    public void setConfig(String v) { config = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { createdAt = v; }
}
