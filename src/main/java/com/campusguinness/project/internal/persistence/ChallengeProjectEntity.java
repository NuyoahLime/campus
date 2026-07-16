package com.campusguinness.project.internal.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "challenge_projects")
public class ChallengeProjectEntity {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "name", nullable = false, length = 200) private String name;
    @Column(name = "category", nullable = false, length = 64) private String category;
    @Column(name = "description", columnDefinition = "text") private String description;
    @Column(name = "venue_requirements", columnDefinition = "text") private String venueRequirements;
    @Column(name = "equipment_requirements", columnDefinition = "text") private String equipmentRequirements;
    @Column(name = "rules_text", columnDefinition = "text") private String rulesText;
    @Column(name = "score_storage_type", nullable = false, length = 32) private String scoreStorageType;
    @Column(name = "score_indicator_type", nullable = false, length = 32) private String scoreIndicatorType;
    @Column(name = "comparison_direction", nullable = false, length = 32) private String comparisonDirection;
    @Column(name = "score_unit", length = 32) private String scoreUnit;
    @Column(name = "decimal_places") private Integer decimalPlaces;
    @Column(name = "grade_order", columnDefinition = "text") private String gradeOrder;
    @Column(name = "allow_tie", nullable = false) private boolean allowTie;
    @Column(name = "effective_score_rule", nullable = false, length = 32) private String effectiveScoreRule;
    @Column(name = "project_status", nullable = false, length = 32) private String projectStatus;
    @Column(name = "current_rule_version_id") private UUID currentRuleVersionId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(name = "version", nullable = false) private int version;
    protected ChallengeProjectEntity() {}

    // ── package-private setters (for PersistenceMapper only) ──

    void setId(UUID v) { this.id = v; }
    void setName(String v) { this.name = v; }
    void setCategory(String v) { this.category = v; }
    void setDescription(String v) { this.description = v; }
    void setVenueRequirements(String v) { this.venueRequirements = v; }
    void setEquipmentRequirements(String v) { this.equipmentRequirements = v; }
    void setRulesText(String v) { this.rulesText = v; }
    void setScoreStorageType(String v) { this.scoreStorageType = v; }
    void setScoreIndicatorType(String v) { this.scoreIndicatorType = v; }
    void setComparisonDirection(String v) { this.comparisonDirection = v; }
    void setScoreUnit(String v) { this.scoreUnit = v; }
    void setDecimalPlaces(Integer v) { this.decimalPlaces = v; }
    void setGradeOrder(String v) { this.gradeOrder = v; }
    void setAllowTie(boolean v) { this.allowTie = v; }
    void setEffectiveScoreRule(String v) { this.effectiveScoreRule = v; }
    void setProjectStatus(String v) { this.projectStatus = v; }
    void setCurrentRuleVersionId(UUID v) { this.currentRuleVersionId = v; }
    void setCreatedAt(Instant v) { this.createdAt = v; }
    void setUpdatedAt(Instant v) { this.updatedAt = v; }
    void setVersion(int v) { this.version = v; }

    // ── public getters (read-only) ──

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public String getVenueRequirements() { return venueRequirements; }
    public String getEquipmentRequirements() { return equipmentRequirements; }
    public String getRulesText() { return rulesText; }
    public String getScoreStorageType() { return scoreStorageType; }
    public String getScoreIndicatorType() { return scoreIndicatorType; }
    public String getComparisonDirection() { return comparisonDirection; }
    public String getScoreUnit() { return scoreUnit; }
    public Integer getDecimalPlaces() { return decimalPlaces; }
    public String getGradeOrder() { return gradeOrder; }
    public boolean isAllowTie() { return allowTie; }
    public String getEffectiveScoreRule() { return effectiveScoreRule; }
    public String getProjectStatus() { return projectStatus; }
    public UUID getCurrentRuleVersionId() { return currentRuleVersionId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public int getVersion() { return version; }
}
