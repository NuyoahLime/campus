package com.campusguinness.ranking.internal.persistence;

import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name = "ranking_definitions")
public class RankingDefinitionEntity {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "layer", nullable = false, length = 8) private String layer;
    @Column(name = "name", nullable = false, length = 200) private String name;
    @Column(name = "school_id") private UUID schoolId;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "activity_project_id") private UUID activityProjectId;
    @Column(name = "dimension_filters", columnDefinition = "jsonb") private String dimensionFilters;
    @Column(name = "tie_break_rule", length = 32) private String tieBreakRule;
    @Column(name = "is_enabled", nullable = false) private boolean enabled;
    @Column(name = "current_version_id") private UUID currentVersionId;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(name = "version", nullable = false) private int version;
    protected RankingDefinitionEntity() {}

    void setId(UUID v) { id = v; } public UUID getId() { return id; }
    void setLayer(String v) { layer = v; } public String getLayer() { return layer; }
    void setName(String v) { name = v; } public String getName() { return name; }
    void setSchoolId(UUID v) { schoolId = v; } public UUID getSchoolId() { return schoolId; }
    void setProjectId(UUID v) { projectId = v; } public UUID getProjectId() { return projectId; }
    void setDimensionFilters(String v) { dimensionFilters = v; } public String getDimensionFilters() { return dimensionFilters; }
    void setTieBreakRule(String v) { tieBreakRule = v; } public String getTieBreakRule() { return tieBreakRule; }
    void setEnabled(boolean v) { enabled = v; } public boolean isEnabled() { return enabled; }
    void setCurrentVersionId(UUID v) { currentVersionId = v; } public UUID getCurrentVersionId() { return currentVersionId; }
    void setCreatedBy(UUID v) { createdBy = v; } public UUID getCreatedBy() { return createdBy; }
    void setCreatedAt(Instant v) { createdAt = v; } public Instant getCreatedAt() { return createdAt; }
    void setUpdatedAt(Instant v) { updatedAt = v; } public Instant getUpdatedAt() { return updatedAt; }
    void setVersion(int v) { version = v; } public int getVersion() { return version; }
}
