package com.campusguinness.activity.internal.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Activity aggregate root.
 *
 * <p>Dual state machines:
 *
 * <p><b>execution_status</b> (CG-ACT-001~006):
 * <pre>
 *   DRAFT → PUBLISHED → IN_PROGRESS → ENDED (terminal)
 *     |         |
 *     └──→ CANCELLED (terminal) ←────┘
 * </pre>
 *
 * <p><b>public_status</b> (CG-ACT-007~013):
 * <pre>
 *   NOT_SUBMITTED → PENDING_PLATFORM_REVIEW → PLATFORM_APPROVED → PUBLIC
 *                                           → PLATFORM_REJECTED → NOT_SUBMITTED
 *                                                         PUBLIC → SCHOOL_WITHDRAWN → NOT_SUBMITTED
 *                                                         PUBLIC → PLATFORM_TAKEDOWN → NOT_SUBMITTED
 * </pre>
 *
 * <p>Cross-machine rules:
 * <ul>
 *   <li>Only PUBLISHED / IN_PROGRESS / ENDED can submit for public review
 *   <li>Cancellation auto-stops public visibility
 *   <li>DRAFT and CANCELLED cannot be submitted for public review
 * </ul>
 */
public final class Activity {

    private final ActivityId id;
    private final UUID schoolId;
    private String title;
    private String description;
    private Instant startTime;
    private Instant endTime;
    private String location;
    private ExecutionStatus executionStatus;
    private PublicStatus publicStatus;
    private final UUID createdBy;
    private final List<Object> domainEvents;

    private Activity(Builder b) {
        this.id = b.id;
        this.schoolId = b.schoolId;
        this.title = b.title;
        this.description = b.description;
        this.startTime = b.startTime;
        this.endTime = b.endTime;
        this.location = b.location;
        this.createdBy = b.createdBy;
        this.executionStatus = b.executionStatus != null ? b.executionStatus : ExecutionStatus.DRAFT;
        this.publicStatus = b.publicStatus != null ? b.publicStatus : PublicStatus.NOT_SUBMITTED;
        this.domainEvents = new ArrayList<>();
    }

    /** Create a new Activity in DRAFT execution and NOT_SUBMITTED public status. */
    public static Activity create(Builder builder) {
        validate(builder);
        return new Activity(builder);
    }

    /** Reconstitute from persistence — takes both states, no domain events. */
    public static Activity reconstitute(Builder builder) {
        validate(builder);
        if (builder.executionStatus == null) throw new IllegalArgumentException("executionStatus required for reconstitute");
        if (builder.publicStatus == null) throw new IllegalArgumentException("publicStatus required for reconstitute");
        return new Activity(builder);
    }

    private static void validate(Builder b) {
        if (b.id == null) throw new IllegalArgumentException("id required");
        if (b.schoolId == null) throw new IllegalArgumentException("schoolId required");
        if (b.createdBy == null) throw new IllegalArgumentException("createdBy required");
        if (b.title == null || b.title.isBlank()) throw new IllegalArgumentException("title required");
        if (b.title.length() > 200) throw new IllegalArgumentException("title max 200 chars");
    }

    // ── Field mutation (only in DRAFT) ──

    /** Update title — only allowed in DRAFT execution status. */
    public void updateTitle(String newTitle) {
        if (executionStatus != ExecutionStatus.DRAFT) {
            throw new InvalidActivityStateTransitionException(executionStatus, "update title");
        }
        if (newTitle == null || newTitle.isBlank()) throw new IllegalArgumentException("title required");
        if (newTitle.length() > 200) throw new IllegalArgumentException("title max 200 chars");
        this.title = newTitle;
    }

    /** Update description — only allowed in DRAFT execution status. */
    public void updateDescription(String newDescription) {
        if (executionStatus != ExecutionStatus.DRAFT) {
            throw new InvalidActivityStateTransitionException(executionStatus, "update description");
        }
        this.description = newDescription;
    }

    /** Update time range — only allowed in DRAFT execution status. */
    public void updateTimeRange(Instant newStartTime, Instant newEndTime) {
        if (executionStatus != ExecutionStatus.DRAFT) {
            throw new InvalidActivityStateTransitionException(executionStatus, "update time range");
        }
        if (newEndTime != null && newStartTime != null && newEndTime.isBefore(newStartTime)) {
            throw new IllegalArgumentException("endTime must not be before startTime");
        }
        this.startTime = newStartTime;
        this.endTime = newEndTime;
    }

    /** Update location — only allowed in DRAFT execution status. */
    public void updateLocation(String newLocation) {
        if (executionStatus != ExecutionStatus.DRAFT) {
            throw new InvalidActivityStateTransitionException(executionStatus, "update location");
        }
        this.location = newLocation;
    }

    // ── execution_status transitions ──

    /** CG-ACT-002: DRAFT → PUBLISHED */
    public void publish() {
        if (executionStatus != ExecutionStatus.DRAFT) {
            throw new InvalidActivityStateTransitionException(executionStatus, "publish");
        }
        this.executionStatus = ExecutionStatus.PUBLISHED;
        domainEvents.add(new ActivityPublished(id));
    }

    /** CG-ACT-003: PUBLISHED → IN_PROGRESS */
    public void beginExecution() {
        if (executionStatus != ExecutionStatus.PUBLISHED) {
            throw new InvalidActivityStateTransitionException(executionStatus, "begin execution");
        }
        this.executionStatus = ExecutionStatus.IN_PROGRESS;
        domainEvents.add(new ActivityExecutionStarted(id));
    }

    /** CG-ACT-004: IN_PROGRESS → ENDED (terminal) */
    public void end() {
        if (executionStatus != ExecutionStatus.IN_PROGRESS) {
            throw new InvalidActivityStateTransitionException(executionStatus, "end");
        }
        this.executionStatus = ExecutionStatus.ENDED;
        domainEvents.add(new ActivityEnded(id));
    }

    /** CG-ACT-005/006: DRAFT or PUBLISHED → CANCELLED (terminal).
     *  Also resets public_status to NOT_SUBMITTED (spec-04 line 146). */
    public void cancel() {
        if (executionStatus != ExecutionStatus.DRAFT && executionStatus != ExecutionStatus.PUBLISHED) {
            throw new InvalidActivityStateTransitionException(executionStatus, "cancel");
        }
        this.executionStatus = ExecutionStatus.CANCELLED;
        this.publicStatus = PublicStatus.NOT_SUBMITTED;
        domainEvents.add(new ActivityCancelled(id));
    }

    // ── public_status transitions ──

    /** CG-ACT-007: NOT_SUBMITTED → PENDING_PLATFORM_REVIEW.
     *  Precondition: execution must be PUBLISHED, IN_PROGRESS, or ENDED. */
    public void submitForReview() {
        if (publicStatus != PublicStatus.NOT_SUBMITTED) {
            throw new InvalidActivityStateTransitionException(publicStatus, "submit for review");
        }
        if (executionStatus != ExecutionStatus.PUBLISHED
                && executionStatus != ExecutionStatus.IN_PROGRESS
                && executionStatus != ExecutionStatus.ENDED) {
            throw new InvalidActivityStateTransitionException(executionStatus, "submit for review");
        }
        this.publicStatus = PublicStatus.PENDING_PLATFORM_REVIEW;
        domainEvents.add(new ActivitySubmittedForReview(id));
    }

    /** CG-ACT-008: PENDING_PLATFORM_REVIEW → PLATFORM_APPROVED */
    public void platformApprove() {
        if (publicStatus != PublicStatus.PENDING_PLATFORM_REVIEW) {
            throw new InvalidActivityStateTransitionException(publicStatus, "platform approve");
        }
        this.publicStatus = PublicStatus.PLATFORM_APPROVED;
        domainEvents.add(new ActivityPlatformApproved(id));
    }

    /** CG-ACT-009: PENDING_PLATFORM_REVIEW → PLATFORM_REJECTED */
    public void platformReject(String reason) {
        if (publicStatus != PublicStatus.PENDING_PLATFORM_REVIEW) {
            throw new InvalidActivityStateTransitionException(publicStatus, "platform reject");
        }
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reject reason required");
        this.publicStatus = PublicStatus.PLATFORM_REJECTED;
        domainEvents.add(new ActivityPlatformRejected(id, reason));
    }

    /** CG-ACT-010: PLATFORM_APPROVED → PUBLIC */
    public void makePublic() {
        if (publicStatus != PublicStatus.PLATFORM_APPROVED) {
            throw new InvalidActivityStateTransitionException(publicStatus, "make public");
        }
        this.publicStatus = PublicStatus.PUBLIC;
        domainEvents.add(new ActivityMadePublic(id));
    }

    /** CG-ACT-011: PUBLIC → SCHOOL_WITHDRAWN */
    public void schoolWithdraw() {
        if (publicStatus != PublicStatus.PUBLIC) {
            throw new InvalidActivityStateTransitionException(publicStatus, "school withdraw");
        }
        this.publicStatus = PublicStatus.SCHOOL_WITHDRAWN;
        domainEvents.add(new ActivityWithdrawnBySchool(id));
    }

    /** CG-ACT-012: PUBLIC → PLATFORM_TAKEDOWN */
    public void platformTakedown(String reason) {
        if (publicStatus != PublicStatus.PUBLIC) {
            throw new InvalidActivityStateTransitionException(publicStatus, "platform takedown");
        }
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("takedown reason required");
        this.publicStatus = PublicStatus.PLATFORM_TAKEDOWN;
        domainEvents.add(new ActivityTakenDownByPlatform(id, reason));
    }

    /** CG-ACT-013: PLATFORM_REJECTED / SCHOOL_WITHDRAWN / PLATFORM_TAKEDOWN → NOT_SUBMITTED */
    public void returnToNotSubmitted() {
        if (publicStatus != PublicStatus.PLATFORM_REJECTED
                && publicStatus != PublicStatus.SCHOOL_WITHDRAWN
                && publicStatus != PublicStatus.PLATFORM_TAKEDOWN) {
            throw new InvalidActivityStateTransitionException(publicStatus, "return to not submitted");
        }
        this.publicStatus = PublicStatus.NOT_SUBMITTED;
        domainEvents.add(new ActivityPublicReviewReset(id));
    }

    public void clearDomainEvents() { domainEvents.clear(); }

    // ── Getters ──

    public ActivityId id() { return id; }
    public UUID schoolId() { return schoolId; }
    public String title() { return title; }
    public String description() { return description; }
    public Instant startTime() { return startTime; }
    public Instant endTime() { return endTime; }
    public String location() { return location; }
    public ExecutionStatus executionStatus() { return executionStatus; }
    public PublicStatus publicStatus() { return publicStatus; }
    public UUID createdBy() { return createdBy; }

    public List<Object> domainEvents() { return Collections.unmodifiableList(domainEvents); }

    /** Builder for Activity. State fields used only during reconstitute. */
    public static class Builder {
        private ActivityId id;
        private UUID schoolId;
        private String title, description, location;
        private Instant startTime, endTime;
        private UUID createdBy;
        ExecutionStatus executionStatus;
        PublicStatus publicStatus;

        public Builder id(ActivityId v) { this.id = v; return this; }
        public Builder schoolId(UUID v) { this.schoolId = v; return this; }
        public Builder title(String v) { this.title = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder startTime(Instant v) { this.startTime = v; return this; }
        public Builder endTime(Instant v) { this.endTime = v; return this; }
        public Builder location(String v) { this.location = v; return this; }
        public Builder createdBy(UUID v) { this.createdBy = v; return this; }
        public Builder executionStatus(ExecutionStatus v) { this.executionStatus = v; return this; }
        public Builder publicStatus(PublicStatus v) { this.publicStatus = v; return this; }
    }
}
