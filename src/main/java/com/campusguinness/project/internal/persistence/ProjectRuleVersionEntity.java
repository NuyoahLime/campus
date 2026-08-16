package com.campusguinness.project.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_rule_versions")
public class ProjectRuleVersionEntity {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "project_id", nullable = false, updatable = false) private UUID projectId;
    @Column(name = "version_number", nullable = false, updatable = false) private int versionNumber;
    @Column(name = "score_storage_type", nullable = false) private String scoreStorageType;
    @Column(name = "score_indicator_type", nullable = false) private String scoreIndicatorType;
    @Column(name = "comparison_direction", nullable = false) private String comparisonDirection;
    @Column(name = "score_unit") private String scoreUnit;
    @Column(name = "decimal_places") private Integer decimalPlaces;
    @Column(name = "grade_order") private String gradeOrder;
    @Column(name = "rules_text") private String rulesText;
    @Column(name = "venue_requirements") private String venueRequirements;
    @Column(name = "equipment_requirements") private String equipmentRequirements;
    @Column(name = "allow_tie", nullable = false) private boolean allowTie;
    @Column(name = "effective_score_rule", nullable = false) private String effectiveScoreRule;
    @Column(name = "change_reason") private String changeReason;
    @Column(name = "created_by", nullable = false, updatable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected ProjectRuleVersionEntity() {}

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public int getVersionNumber() { return versionNumber; }
    public String getScoreStorageType() { return scoreStorageType; }
    public String getScoreIndicatorType() { return scoreIndicatorType; }
    public String getComparisonDirection() { return comparisonDirection; }
    public String getScoreUnit() { return scoreUnit; }
    public Integer getDecimalPlaces() { return decimalPlaces; }
    public String getGradeOrder() { return gradeOrder; }
    public String getRulesText() { return rulesText; }
    public String getVenueRequirements() { return venueRequirements; }
    public String getEquipmentRequirements() { return equipmentRequirements; }
    public boolean isAllowTie() { return allowTie; }
    public String getEffectiveScoreRule() { return effectiveScoreRule; }
    public String getChangeReason() { return changeReason; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }

    void setId(UUID value) { id = value; }
    void setProjectId(UUID value) { projectId = value; }
    void setVersionNumber(int value) { versionNumber = value; }
    void setScoreStorageType(String value) { scoreStorageType = value; }
    void setScoreIndicatorType(String value) { scoreIndicatorType = value; }
    void setComparisonDirection(String value) { comparisonDirection = value; }
    void setScoreUnit(String value) { scoreUnit = value; }
    void setDecimalPlaces(Integer value) { decimalPlaces = value; }
    void setGradeOrder(String value) { gradeOrder = value; }
    void setRulesText(String value) { rulesText = value; }
    void setVenueRequirements(String value) { venueRequirements = value; }
    void setEquipmentRequirements(String value) { equipmentRequirements = value; }
    void setAllowTie(boolean value) { allowTie = value; }
    void setEffectiveScoreRule(String value) { effectiveScoreRule = value; }
    void setChangeReason(String value) { changeReason = value; }
    void setCreatedBy(UUID value) { createdBy = value; }
    void setCreatedAt(Instant value) { createdAt = value; }
}
