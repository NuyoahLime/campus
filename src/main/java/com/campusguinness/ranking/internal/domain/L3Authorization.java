package com.campusguinness.ranking.internal.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * L3Authorization aggregate root (ADR-005: independent aggregate).
 *
 * <p>State machine (6 states):
 * <pre>
 *   DRAFT → PENDING_REVIEW → APPROVED ⇄ SUSPENDED
 *     ↓         ↓               ↓          ↓
 *     └──→ WITHDRAWN ←─────────┴──────────┘ (terminal)
 *   PENDING_REVIEW → REJECTED → DRAFT
 * </pre>
 *
 * <p>Cross-aggregate (ADR-005 §8-10):
 * <ul>
 *   <li>School pause → APPROVED auto-transitions to SUSPENDED (app-layer coordination)
 *   <li>School disable → auto WITHDRAWN (terminal, app-layer coordination)
 *   <li>School restore + super admin confirm → SUSPENDED → APPROVED
 *   <li>WITHDRAWN is terminal; new authorization must be created if school re-enabled
 * </ul>
 *
 * <p>Authorization scope: school_id + project_id + rule_version_id + data_scope JSONB.
 * Storage fields (allow_school_name, allow_student_name) are display-option booleans.
 */
public final class L3Authorization {

    private final L3AuthorizationId id;
    private final UUID schoolId;
    private final UUID projectId;
    private final UUID ruleVersionId;
    private String dataScope;
    private boolean allowSchoolName;
    private boolean allowStudentName;
    private AuthorizationStatus status;
    private Instant submittedAt;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private String reviewComment;
    private String rejectReason;
    private Instant pausedAt;
    private Instant withdrawnAt;
    private String withdrawReason;
    private final List<Object> domainEvents;

    private L3Authorization(Builder b, AuthorizationStatus status, Instant submittedAt,
            UUID reviewedBy, Instant reviewedAt, String reviewComment, String rejectReason,
            Instant pausedAt, Instant withdrawnAt, String withdrawReason) {
        this.id = b.id; this.schoolId = b.schoolId; this.projectId = b.projectId;
        this.ruleVersionId = b.ruleVersionId; this.dataScope = b.dataScope;
        this.allowSchoolName = b.allowSchoolName; this.allowStudentName = b.allowStudentName;
        this.status = status; this.submittedAt = submittedAt;
        this.reviewedBy = reviewedBy; this.reviewedAt = reviewedAt;
        this.reviewComment = reviewComment; this.rejectReason = rejectReason;
        this.pausedAt = pausedAt; this.withdrawnAt = withdrawnAt;
        this.withdrawReason = withdrawReason;
        this.domainEvents = new ArrayList<>();
    }

    public static L3Authorization create(Builder builder) {
        validate(builder);
        return new L3Authorization(builder, AuthorizationStatus.DRAFT,
                null, null, null, null, null, null, null, null);
    }

    public static L3Authorization reconstitute(Builder builder, AuthorizationStatus status,
            Instant submittedAt, UUID reviewedBy, Instant reviewedAt,
            String reviewComment, String rejectReason,
            Instant pausedAt, Instant withdrawnAt, String withdrawReason) {
        validate(builder);
        return new L3Authorization(builder, status, submittedAt, reviewedBy, reviewedAt,
                reviewComment, rejectReason, pausedAt, withdrawnAt, withdrawReason);
    }

    private static void validate(Builder b) {
        if (b.id == null) throw new IllegalArgumentException("id required");
        if (b.schoolId == null) throw new IllegalArgumentException("schoolId required");
        if (b.projectId == null) throw new IllegalArgumentException("projectId required");
        if (b.ruleVersionId == null) throw new IllegalArgumentException("ruleVersionId required");
    }

    // ── State transitions ──

    /** DRAFT → PENDING_REVIEW */
    public void submit() {
        if (status != AuthorizationStatus.DRAFT) {
            throw new InvalidAuthorizationStateTransitionException(status, "submit");
        }
        this.status = AuthorizationStatus.PENDING_REVIEW;
        this.submittedAt = Instant.now();
        domainEvents.add(new L3AuthorizationSubmitted(id));
    }

    public void editDraft(String dataScope, boolean allowSchoolName, boolean allowStudentName) {
        if (status != AuthorizationStatus.DRAFT) {
            throw new InvalidAuthorizationStateTransitionException(status, "edit");
        }
        this.dataScope = dataScope;
        this.allowSchoolName = allowSchoolName;
        this.allowStudentName = allowStudentName;
    }

    /** PENDING_REVIEW → APPROVED */
    public void approve(UUID reviewerId, String comment) {
        if (status != AuthorizationStatus.PENDING_REVIEW) {
            throw new InvalidAuthorizationStateTransitionException(status, "approve");
        }
        this.status = AuthorizationStatus.APPROVED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = Instant.now();
        this.reviewComment = comment;
        domainEvents.add(new L3AuthorizationApproved(id));
    }

    /** PENDING_REVIEW → REJECTED */
    public void reject(UUID reviewerId, String reason) {
        if (status != AuthorizationStatus.PENDING_REVIEW) {
            throw new InvalidAuthorizationStateTransitionException(status, "reject");
        }
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reject reason required");
        this.status = AuthorizationStatus.REJECTED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = Instant.now();
        this.rejectReason = reason;
        domainEvents.add(new L3AuthorizationRejected(id));
    }

    /** REJECTED → DRAFT */
    public void returnToDraft() {
        if (status != AuthorizationStatus.REJECTED) {
            throw new InvalidAuthorizationStateTransitionException(status, "return to draft");
        }
        this.status = AuthorizationStatus.DRAFT;
        this.submittedAt = null;
    }

    /** APPROVED → SUSPENDED (school paused, triggered by app layer) */
    public void suspend() {
        if (status != AuthorizationStatus.APPROVED) {
            throw new InvalidAuthorizationStateTransitionException(status, "suspend");
        }
        this.status = AuthorizationStatus.SUSPENDED;
        this.pausedAt = Instant.now();
        domainEvents.add(new L3AuthorizationSuspended(id));
    }

    /** SUSPENDED → APPROVED (school restored + super admin confirms) */
    public void resume() {
        if (status != AuthorizationStatus.SUSPENDED) {
            throw new InvalidAuthorizationStateTransitionException(status, "resume");
        }
        this.status = AuthorizationStatus.APPROVED;
        this.pausedAt = null;
        domainEvents.add(new L3AuthorizationResumed(id));
    }

    /** DRAFT / APPROVED / SUSPENDED → WITHDRAWN (terminal).
     *  School disable → auto withdraw (app-layer coordination). */
    public void withdraw(String reason) {
        if (status != AuthorizationStatus.DRAFT
                && status != AuthorizationStatus.APPROVED
                && status != AuthorizationStatus.SUSPENDED) {
            throw new InvalidAuthorizationStateTransitionException(status, "withdraw");
        }
        this.status = AuthorizationStatus.WITHDRAWN;
        this.withdrawnAt = Instant.now();
        this.withdrawReason = reason;
        domainEvents.add(new L3AuthorizationWithdrawn(id));
    }

    public void withdrawForSchoolDisable(String reason) {
        if (status == AuthorizationStatus.WITHDRAWN) {
            return;
        }
        this.status = AuthorizationStatus.WITHDRAWN;
        this.withdrawnAt = Instant.now();
        this.withdrawReason = reason;
        domainEvents.add(new L3AuthorizationWithdrawn(id));
    }

    // ── Query ──

    /** Check if this authorization is currently usable for L3 ranking generation.
     *  Must be APPROVED (not SUSPENDED, not WITHDRAWN, not DRAFT). */
    public boolean isUsable() {
        return status == AuthorizationStatus.APPROVED;
    }

    public void clearDomainEvents() { domainEvents.clear(); }

    // ── Getters ──

    public L3AuthorizationId id() { return id; }
    public UUID schoolId() { return schoolId; }
    public UUID projectId() { return projectId; }
    public UUID ruleVersionId() { return ruleVersionId; }
    public String dataScope() { return dataScope; }
    public boolean allowSchoolName() { return allowSchoolName; }
    public boolean allowStudentName() { return allowStudentName; }
    public AuthorizationStatus status() { return status; }
    public Instant submittedAt() { return submittedAt; }
    public UUID reviewedBy() { return reviewedBy; }
    public Instant reviewedAt() { return reviewedAt; }
    public String reviewComment() { return reviewComment; }
    public String rejectReason() { return rejectReason; }
    public Instant pausedAt() { return pausedAt; }
    public Instant withdrawnAt() { return withdrawnAt; }
    public String withdrawReason() { return withdrawReason; }

    public List<Object> domainEvents() { return Collections.unmodifiableList(domainEvents); }

    public static class Builder {
        private L3AuthorizationId id;
        private UUID schoolId;
        private UUID projectId;
        private UUID ruleVersionId;
        private String dataScope;
        private boolean allowSchoolName;
        private boolean allowStudentName;

        public Builder id(L3AuthorizationId v) { this.id = v; return this; }
        public Builder schoolId(UUID v) { this.schoolId = v; return this; }
        public Builder projectId(UUID v) { this.projectId = v; return this; }
        public Builder ruleVersionId(UUID v) { this.ruleVersionId = v; return this; }
        public Builder dataScope(String v) { this.dataScope = v; return this; }
        public Builder allowSchoolName(boolean v) { this.allowSchoolName = v; return this; }
        public Builder allowStudentName(boolean v) { this.allowStudentName = v; return this; }
    }
}
