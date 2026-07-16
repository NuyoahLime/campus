package com.campusguinness.ranking.internal.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * RankingDefinition aggregate root.
 * <p>A configuration that defines how rankings are computed for a ChallengeProject.
 * Has no complex state machine — managed via enable/disable.
 * RankingVersion, RankingEntry, and ranking_entry_score_sources are IMMUTABLE_VERSION_SNAPSHOT
 * or RELATION_ENTITY and are deferred (V1 domain model does not include them).
 */
public final class RankingDefinition {

    private final RankingDefinitionId id;
    private final RankingLayer layer;
    private final String name;
    private final UUID schoolId;
    private final UUID projectId;
    private final String dimensionFilters;
    private final String tieBreakRule;
    private boolean enabled;
    private UUID currentVersionId;
    private final UUID createdBy;
    private final List<Object> domainEvents;

    private RankingDefinition(Builder b, boolean enabled) {
        this.id = b.id; this.layer = b.layer; this.name = b.name;
        this.schoolId = b.schoolId; this.projectId = b.projectId;
        this.dimensionFilters = b.dimensionFilters; this.tieBreakRule = b.tieBreakRule;
        this.enabled = enabled; this.createdBy = b.createdBy;
        this.domainEvents = new ArrayList<>();
    }

    public static RankingDefinition create(Builder builder) {
        validate(builder);
        return new RankingDefinition(builder, true);
    }

    public static RankingDefinition reconstitute(Builder builder, boolean enabled, UUID currentVersionId) {
        validate(builder);
        var r = new RankingDefinition(builder, enabled);
        r.currentVersionId = currentVersionId;
        return r;
    }

    private static void validate(Builder b) {
        if (b.id == null) throw new IllegalArgumentException("id required");
        if (b.layer == null) throw new IllegalArgumentException("layer required");
        if (b.name == null || b.name.isBlank()) throw new IllegalArgumentException("name required");
        if (b.name.length() > 200) throw new IllegalArgumentException("name max 200 chars");
        if (b.projectId == null) throw new IllegalArgumentException("projectId required");
        if (b.createdBy == null) throw new IllegalArgumentException("createdBy required");
    }

    /** Disable this ranking definition. */
    public void disable() {
        if (!enabled) throw new IllegalStateException("already disabled");
        this.enabled = false;
    }

    /** Re-enable this ranking definition. */
    public void enable() {
        if (enabled) throw new IllegalStateException("already enabled");
        this.enabled = true;
    }

    /** Set the current version (called by application layer after RankingVersion is generated). */
    public void setCurrentVersionId(UUID versionId) {
        this.currentVersionId = versionId;
    }

    public void clearDomainEvents() { domainEvents.clear(); }

    // ── Getters ──

    public RankingDefinitionId id() { return id; }
    public RankingLayer layer() { return layer; }
    public String name() { return name; }
    public UUID schoolId() { return schoolId; }
    public UUID projectId() { return projectId; }
    public String dimensionFilters() { return dimensionFilters; }
    public String tieBreakRule() { return tieBreakRule; }
    public boolean isEnabled() { return enabled; }
    public UUID currentVersionId() { return currentVersionId; }
    public UUID createdBy() { return createdBy; }

    public List<Object> domainEvents() { return Collections.unmodifiableList(domainEvents); }

    public static class Builder {
        private RankingDefinitionId id;
        private RankingLayer layer;
        private String name;
        private UUID schoolId;
        private UUID projectId;
        private String dimensionFilters;
        private String tieBreakRule;
        private UUID createdBy;

        public Builder id(RankingDefinitionId v) { this.id = v; return this; }
        public Builder layer(RankingLayer v) { this.layer = v; return this; }
        public Builder name(String v) { this.name = v; return this; }
        public Builder schoolId(UUID v) { this.schoolId = v; return this; }
        public Builder projectId(UUID v) { this.projectId = v; return this; }
        public Builder dimensionFilters(String v) { this.dimensionFilters = v; return this; }
        public Builder tieBreakRule(String v) { this.tieBreakRule = v; return this; }
        public Builder createdBy(UUID v) { this.createdBy = v; return this; }
    }
}
