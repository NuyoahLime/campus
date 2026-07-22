package com.campusguinness.ranking.internal.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ranking_versions")
public class RankingVersionEntity {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "definition_id", nullable = false) private UUID definitionId;
    @Column(name = "version_number", nullable = false) private int versionNumber;
    @Column(name = "comparison_direction", nullable = false) private String comparisonDirection;
    @Column(name = "tie_policy", length = 32) private String tiePolicy;
    @Column(name = "effective_score_rule", nullable = false) private String effectiveScoreRule;
    @Column(name = "ranked_student_count", nullable = false) private int rankedStudentCount;
    @Column(name = "version_status", nullable = false) private String versionStatus = "PUBLISHED";
    @Column(name = "published_by", nullable = false) private UUID publishedBy;
    @Column(name = "published_at", nullable = false) private Instant publishedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Version @Column(name = "version", nullable = false) private int version;
    protected RankingVersionEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID v) { id = v; }
    public UUID getDefinitionId() { return definitionId; }
    public void setDefinitionId(UUID v) { definitionId = v; }
    public int getVersionNumber() { return versionNumber; }
    public void setVersionNumber(int v) { versionNumber = v; }
    public String getComparisonDirection() { return comparisonDirection; }
    public void setComparisonDirection(String v) { comparisonDirection = v; }
    public String getTiePolicy() { return tiePolicy; }
    public void setTiePolicy(String v) { tiePolicy = v; }
    public String getEffectiveScoreRule() { return effectiveScoreRule; }
    public void setEffectiveScoreRule(String v) { effectiveScoreRule = v; }
    public int getRankedStudentCount() { return rankedStudentCount; }
    public void setRankedStudentCount(int v) { rankedStudentCount = v; }
    public String getVersionStatus() { return versionStatus; }
    public void setVersionStatus(String v) { versionStatus = v; }
    public UUID getPublishedBy() { return publishedBy; }
    public void setPublishedBy(UUID v) { publishedBy = v; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant v) { publishedAt = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { createdAt = v; }
}
