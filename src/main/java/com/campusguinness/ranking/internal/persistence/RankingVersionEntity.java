package com.campusguinness.ranking.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-model mapping aligned with the V007/V017/V022 ranking version schema.
 */
@Entity
@Table(name = "ranking_versions")
public class RankingVersionEntity {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "definition_id", nullable = false)
    private UUID definitionId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "previous_version_id")
    private UUID previousVersionId;

    @Column(name = "version_status", nullable = false)
    private String versionStatus;

    @Column(name = "calculation_params", columnDefinition = "jsonb")
    private String calculationParams;

    @Column(name = "data_scope_snapshot", columnDefinition = "jsonb")
    private String dataScopeSnapshot;

    @Column(name = "authorization_ids_snapshot", columnDefinition = "jsonb")
    private String authorizationIdsSnapshot;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by")
    private UUID publishedBy;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "withdrawn_by")
    private UUID withdrawnBy;

    @Column(name = "withdrawal_reason")
    private String withdrawalReason;

    @Column(name = "created_reason")
    private String createdReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RankingVersionEntity() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getDefinitionId() {
        return definitionId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public String getVersionStatus() {
        return versionStatus;
    }

    public UUID getPublishedBy() {
        return publishedBy;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
