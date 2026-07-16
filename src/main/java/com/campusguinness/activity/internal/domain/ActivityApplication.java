package com.campusguinness.activity.internal.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * ActivityApplication aggregate root.
 *
 * <p>State machine (CG-ACT-APP-001~005):
 * <pre>
 *   DRAFT → SUBMITTED → APPROVED (terminal)
 *                    → REJECTED → DRAFT (revise)
 *                    → WITHDRAWN (terminal)
 * </pre>
 *
 * <p>Invariants:
 * <ul>
 *   <li>One application produces at most one Activity (created_activity_id unique)
 *   <li>Approved or withdrawn applications cannot be modified
 *   <li>Unsubmitted drafts can be physically deleted; submitted cannot
 *   <li>Rejection preserves historical review records
 * </ul>
 *
 * <p>ADR-001: ActivityApplication is an independent aggregate root, separate from Activity.
 */
public final class ActivityApplication {

    private final ActivityApplicationId id;
    private final UUID schoolId;
    private final UUID applicantId;
    private String title;
    private String description;
    private ApplicationStatus status;
    private UUID createdActivityId;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private String reviewComment;
    private String rejectReason;
    private int applicationVersion;
    private final List<Object> domainEvents;

    private ActivityApplication(Builder b) {
        this.id = b.id;
        this.schoolId = b.schoolId;
        this.applicantId = b.applicantId;
        this.title = b.title;
        this.description = b.description;
        this.status = b.status != null ? b.status : ApplicationStatus.DRAFT;
        this.applicationVersion = b.applicationVersion > 0 ? b.applicationVersion : 1;
        this.createdActivityId = b.createdActivityId;
        this.reviewedBy = b.reviewedBy;
        this.reviewedAt = b.reviewedAt;
        this.reviewComment = b.reviewComment;
        this.rejectReason = b.rejectReason;
        this.domainEvents = new ArrayList<>();
    }

    /** Create a new ActivityApplication in DRAFT status. */
    public static ActivityApplication create(Builder builder) {
        validate(builder);
        return new ActivityApplication(builder);
    }

    /** Reconstitute from persistence — takes final status and audit fields, no domain events. */
    public static ActivityApplication reconstitute(Builder builder) {
        validate(builder);
        if (builder.status == null) throw new IllegalArgumentException("status required for reconstitute");
        return new ActivityApplication(builder);
    }

    private static void validate(Builder b) {
        if (b.id == null) throw new IllegalArgumentException("id required");
        if (b.schoolId == null) throw new IllegalArgumentException("schoolId required");
        if (b.applicantId == null) throw new IllegalArgumentException("applicantId required");
        if (b.title == null || b.title.isBlank()) throw new IllegalArgumentException("title required");
        if (b.title.length() > 200) throw new IllegalArgumentException("title max 200 chars");
    }

    // ── Field mutation (only in DRAFT) ──

    /** CG-ACT-APP-016: update title only allowed in DRAFT. */
    public void updateTitle(String newTitle) {
        if (status != ApplicationStatus.DRAFT) {
            throw new InvalidActivityApplicationStateTransitionException(status, "update title");
        }
        if (newTitle == null || newTitle.isBlank()) throw new IllegalArgumentException("title required");
        if (newTitle.length() > 200) throw new IllegalArgumentException("title max 200 chars");
        this.title = newTitle;
    }

    /** CG-ACT-APP-016: update description only allowed in DRAFT. */
    public void updateDescription(String newDescription) {
        if (status != ApplicationStatus.DRAFT) {
            throw new InvalidActivityApplicationStateTransitionException(status, "update description");
        }
        this.description = newDescription;
    }

    // ── State transitions ──

    /** CG-ACT-APP-001: DRAFT → SUBMITTED */
    public void submit() {
        if (status != ApplicationStatus.DRAFT) {
            throw new InvalidActivityApplicationStateTransitionException(status, "submit");
        }
        this.status = ApplicationStatus.SUBMITTED;
        domainEvents.add(new ActivityApplicationSubmitted(id));
    }

    /** CG-ACT-APP-002: SUBMITTED → APPROVED */
    public void approve(UUID reviewerId, UUID activityId) {
        if (status != ApplicationStatus.SUBMITTED) {
            throw new InvalidActivityApplicationStateTransitionException(status, "approve");
        }
        if (activityId == null) throw new IllegalArgumentException("activityId required for approval");
        this.status = ApplicationStatus.APPROVED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = Instant.now();
        this.createdActivityId = activityId;
        domainEvents.add(new ActivityApplicationApproved(id, activityId));
    }

    /** CG-ACT-APP-003: SUBMITTED → REJECTED */
    public void reject(UUID reviewerId, String reason) {
        if (status != ApplicationStatus.SUBMITTED) {
            throw new InvalidActivityApplicationStateTransitionException(status, "reject");
        }
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reject reason required");
        this.status = ApplicationStatus.REJECTED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = Instant.now();
        this.rejectReason = reason;
        domainEvents.add(new ActivityApplicationRejected(id, reason));
    }

    /** CG-ACT-APP-004: SUBMITTED → WITHDRAWN */
    public void withdraw() {
        if (status != ApplicationStatus.SUBMITTED) {
            throw new InvalidActivityApplicationStateTransitionException(status, "withdraw");
        }
        this.status = ApplicationStatus.WITHDRAWN;
        domainEvents.add(new ActivityApplicationWithdrawn(id));
    }

    /** CG-ACT-APP-005: REJECTED → DRAFT (revise and increment version) */
    public void returnToDraft() {
        if (status != ApplicationStatus.REJECTED) {
            throw new InvalidActivityApplicationStateTransitionException(status, "return to draft");
        }
        this.status = ApplicationStatus.DRAFT;
        this.applicationVersion++;
        domainEvents.add(new ActivityApplicationReturnedToDraft(id, applicationVersion));
    }

    public void clearDomainEvents() { domainEvents.clear(); }

    // ── Getters ──

    public ActivityApplicationId id() { return id; }
    public UUID schoolId() { return schoolId; }
    public UUID applicantId() { return applicantId; }
    public String title() { return title; }
    public String description() { return description; }
    public ApplicationStatus status() { return status; }
    public UUID createdActivityId() { return createdActivityId; }
    public UUID reviewedBy() { return reviewedBy; }
    public Instant reviewedAt() { return reviewedAt; }
    public String reviewComment() { return reviewComment; }
    public String rejectReason() { return rejectReason; }
    public int applicationVersion() { return applicationVersion; }

    public List<Object> domainEvents() { return Collections.unmodifiableList(domainEvents); }

    /** Builder for ActivityApplication. Audit fields used only during reconstitute. */
    public static class Builder {
        private ActivityApplicationId id;
        private UUID schoolId, applicantId;
        private String title, description;
        ApplicationStatus status;
        int applicationVersion;
        UUID createdActivityId, reviewedBy;
        Instant reviewedAt;
        String reviewComment, rejectReason;

        public Builder id(ActivityApplicationId v) { this.id = v; return this; }
        public Builder schoolId(UUID v) { this.schoolId = v; return this; }
        public Builder applicantId(UUID v) { this.applicantId = v; return this; }
        public Builder title(String v) { this.title = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder status(ApplicationStatus v) { this.status = v; return this; }
        public Builder applicationVersion(int v) { this.applicationVersion = v; return this; }
        public Builder createdActivityId(UUID v) { this.createdActivityId = v; return this; }
        public Builder reviewedBy(UUID v) { this.reviewedBy = v; return this; }
        public Builder reviewedAt(Instant v) { this.reviewedAt = v; return this; }
        public Builder reviewComment(String v) { this.reviewComment = v; return this; }
        public Builder rejectReason(String v) { this.rejectReason = v; return this; }
    }
}
