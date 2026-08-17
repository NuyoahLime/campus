package com.campusguinness.project.internal.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * ChallengeProject aggregate root.
 *
 * <p>State machine (CG-PROJECT-001):
 * <pre>
 *   DRAFT → PUBLISHED → ARCHIVED
 *                ↑          │
 *                └──────────┘
 * </pre>
 *
 * <p>Invariants:
 * - A project must be in DRAFT status to be initially published.
 * - A project must be in PUBLISHED status to be archived.
 * - A project in ARCHIVED status can be re-published.
 * - DRAFT projects cannot be directly archived.
 */
public final class ChallengeProject {

    private final ChallengeProjectId id;
    private ProjectName name;
    private ProjectCategory category;
    private ScoreConfig scoreConfig;
    private String description;
    private String venueRequirements;
    private String equipmentRequirements;
    private ProjectStatus status;
    private UUID currentRuleVersionId;
    private final List<Object> domainEvents;

    private ChallengeProject(ChallengeProjectId id, ProjectName name,
                             ProjectCategory category, ScoreConfig scoreConfig,
                             String description, String venueRequirements,
                             String equipmentRequirements, ProjectStatus status,
                             UUID currentRuleVersionId) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.scoreConfig = scoreConfig;
        this.description = description;
        this.venueRequirements = venueRequirements;
        this.equipmentRequirements = equipmentRequirements;
        this.status = status;
        this.currentRuleVersionId = currentRuleVersionId;
        this.domainEvents = new ArrayList<>();
    }

    /** Factory: create a new ChallengeProject in DRAFT status. */
    public static ChallengeProject create(ChallengeProjectId id, ProjectName name,
                                          ProjectCategory category, ScoreConfig scoreConfig,
                                          String description) {
        return create(id, name, category, scoreConfig, description, null, null);
    }

    public static ChallengeProject create(ChallengeProjectId id, ProjectName name,
                                          ProjectCategory category, ScoreConfig scoreConfig,
                                          String description, String venueRequirements,
                                          String equipmentRequirements) {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (name == null) throw new IllegalArgumentException("name must not be null");
        if (category == null) throw new IllegalArgumentException("category must not be null");
        if (scoreConfig == null) throw new IllegalArgumentException("scoreConfig must not be null");

        ChallengeProject project = new ChallengeProject(id, name, category, scoreConfig, description,
                venueRequirements, equipmentRequirements, ProjectStatus.DRAFT, null);
        project.domainEvents.add(new ChallengeProjectCreated(id));
        return project;
    }

    /** Reconstitute from persistence — takes final status, no domain events. */
    public static ChallengeProject reconstitute(ChallengeProjectId id, ProjectName name,
                                                ProjectCategory category, ScoreConfig scoreConfig,
                                                String description, ProjectStatus status) {
        return reconstitute(id, name, category, scoreConfig, description, null, null, status, null);
    }

    public static ChallengeProject reconstitute(ChallengeProjectId id, ProjectName name,
                                                ProjectCategory category, ScoreConfig scoreConfig,
                                                String description, String venueRequirements,
                                                String equipmentRequirements, ProjectStatus status,
                                                UUID currentRuleVersionId) {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (name == null) throw new IllegalArgumentException("name must not be null");
        if (category == null) throw new IllegalArgumentException("category must not be null");
        if (scoreConfig == null) throw new IllegalArgumentException("scoreConfig must not be null");
        if (status == null) throw new IllegalArgumentException("status must not be null");
        return new ChallengeProject(id, name, category, scoreConfig, description,
                venueRequirements, equipmentRequirements, status, currentRuleVersionId);
    }

    /** Publish: DRAFT → PUBLISHED, or ARCHIVED → PUBLISHED (re-publish). */
    public void publish() {
        if (status != ProjectStatus.DRAFT && status != ProjectStatus.ARCHIVED) {
            throw new InvalidProjectStateTransitionException(status, "publish");
        }
        this.status = ProjectStatus.PUBLISHED;
        this.domainEvents.add(new ProjectPublished(id));
    }

    /** Archive: PUBLISHED → ARCHIVED. */
    public void archive() {
        if (status != ProjectStatus.PUBLISHED) {
            throw new InvalidProjectStateTransitionException(status, "archive");
        }
        this.status = ProjectStatus.ARCHIVED;
        this.domainEvents.add(new ProjectArchived(id));
    }

    /** Updates editable project content and returns whether the frozen rule snapshot changed. */
    public boolean updateDetails(ProjectName name, ProjectCategory category, ScoreConfig scoreConfig,
                                 String description, String venueRequirements,
                                 String equipmentRequirements) {
        boolean rulesChanged = !Objects.equals(this.scoreConfig, scoreConfig)
                || !Objects.equals(this.venueRequirements, venueRequirements)
                || !Objects.equals(this.equipmentRequirements, equipmentRequirements);
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.category = Objects.requireNonNull(category, "category must not be null");
        this.scoreConfig = Objects.requireNonNull(scoreConfig, "scoreConfig must not be null");
        this.description = description;
        this.venueRequirements = venueRequirements;
        this.equipmentRequirements = equipmentRequirements;
        return rulesChanged;
    }

    public void assignCurrentRuleVersion(UUID ruleVersionId) {
        this.currentRuleVersionId = Objects.requireNonNull(ruleVersionId, "ruleVersionId must not be null");
    }

    /** Clear accumulated domain events (useful after publishing/handling). */
    public void clearDomainEvents() {
        this.domainEvents.clear();
    }

    // ── Getters (no public setters) ──

    public ChallengeProjectId id() { return id; }
    public ProjectName name() { return name; }
    public ProjectCategory category() { return category; }
    public ScoreConfig scoreConfig() { return scoreConfig; }
    public String description() { return description; }
    public String venueRequirements() { return venueRequirements; }
    public String equipmentRequirements() { return equipmentRequirements; }
    public ProjectStatus status() { return status; }
    public UUID currentRuleVersionId() { return currentRuleVersionId; }

    /** Returns unmodifiable view of accumulated domain events. */
    public List<Object> domainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }
}
