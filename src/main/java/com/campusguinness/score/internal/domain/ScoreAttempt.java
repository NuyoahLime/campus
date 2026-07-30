package com.campusguinness.score.internal.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * ScoreAttempt aggregate root.
 *
 * <p>Each attempt is an independent aggregate instance (ADR-002).
 * State machine (CG-SCORE-001~005):
 * <pre>
 *   DRAFT → PENDING_REVIEW → APPROVED → INVALIDATED (terminal)
 *     ↑         ↓
 *     └─ REJECTED ←┘
 * </pre>
 *
 * <p>Score value uses {@link ScoreValue} sealed interface with variants matching
 * {@link ScoreStorageType}: INTEGER→IntegerScore, DECIMAL→DecimalScore,
 * DURATION→DurationScore, GRADE→GradeScore.
 *
 * <p>Invariants:
 * <ul>
 *   <li>Only APPROVED scores can be isCurrentEffective=true
 *   <li>Approved scores cannot be directly overwritten — only through correction
 *   <li>INVALIDATED is terminal; all historical attempts are preserved
 *   <li>Score value type must match scoreStorageType
 * </ul>
 */
public final class ScoreAttempt {

    private final ScoreAttemptId id;
    private final UUID schoolId;
    private final UUID activityProjectId;
    private final UUID studentId;
    private final int attemptNumber;
    private final ScoreStorageType scoreStorageType;
    private ScoreValue scoreValue;
    private Instant scoreBusinessTime;
    private String timeSource;
    private boolean currentEffective;
    private final UUID replacesId;
    private AttemptStatus status;
    private final UUID enteredBy;
    private Instant submittedAt;
    private final boolean manualMakeup;
    private final List<Object> domainEvents;

    private ScoreAttempt(Builder b, AttemptStatus status, boolean currentEffective,
                         Instant submittedAt, boolean manualMakeup) {
        this.id = b.id;
        this.schoolId = b.schoolId;
        this.activityProjectId = b.activityProjectId;
        this.studentId = b.studentId;
        this.attemptNumber = b.attemptNumber;
        this.scoreStorageType = b.scoreStorageType;
        this.scoreValue = b.scoreValue;
        this.scoreBusinessTime = b.scoreBusinessTime;
        this.timeSource = b.timeSource;
        this.currentEffective = currentEffective;
        this.replacesId = b.replacesId;
        this.status = status;
        this.enteredBy = b.enteredBy;
        this.submittedAt = submittedAt;
        this.manualMakeup = manualMakeup;
        this.domainEvents = new ArrayList<>();
    }

    /** Create a new ScoreAttempt in DRAFT status. */
    public static ScoreAttempt create(Builder builder) {
        validate(builder);
        return new ScoreAttempt(builder, AttemptStatus.DRAFT, false, null, false);
    }

    /** Reconstitute from persistence — all fields explicit, no domain events. */
    public static ScoreAttempt reconstitute(Builder builder, AttemptStatus status,
            boolean currentEffective, Instant submittedAt, boolean manualMakeup) {
        validate(builder);
        if (status == null) throw new IllegalArgumentException("status required for reconstitute");
        return new ScoreAttempt(builder, status, currentEffective, submittedAt, manualMakeup);
    }

    private static void validate(Builder b) {
        if (b.id == null) throw new IllegalArgumentException("id required");
        if (b.schoolId == null) throw new IllegalArgumentException("schoolId required");
        if (b.activityProjectId == null) throw new IllegalArgumentException("activityProjectId required");
        if (b.studentId == null) throw new IllegalArgumentException("studentId required");
        if (b.enteredBy == null) throw new IllegalArgumentException("enteredBy required");
        if (b.attemptNumber <= 0) throw new IllegalArgumentException("attemptNumber must be > 0");
        if (b.scoreStorageType == null) throw new IllegalArgumentException("scoreStorageType required");
        if (b.scoreValue == null) throw new IllegalArgumentException("scoreValue required");
        validateScoreConsistency(b.scoreStorageType, b.scoreValue);
    }

    /** CG-SCORE-011: ensure ScoreValue variant matches ScoreStorageType discriminator. */
    private static void validateScoreConsistency(ScoreStorageType type, ScoreValue value) {
        boolean valid = switch (type) {
            case INTEGER  -> value instanceof ScoreValue.IntegerScore;
            case DECIMAL  -> value instanceof ScoreValue.DecimalScore;
            case DURATION -> value instanceof ScoreValue.DurationScore;
            case GRADE    -> value instanceof ScoreValue.GradeScore;
        };
        if (!valid) {
            throw new IllegalArgumentException(
                    "scoreValue type " + value.getClass().getSimpleName()
                    + " does not match scoreStorageType " + type);
        }
    }

    // ── Field mutation (only in DRAFT) ──

    /** CG-SCORE-014: update score value — only allowed in DRAFT. */
    public void updateScoreValue(ScoreValue newValue) {
        if (status != AttemptStatus.DRAFT) {
            throw new InvalidScoreAttemptStateTransitionException(status, "update score value");
        }
        if (newValue == null) throw new IllegalArgumentException("scoreValue required");
        validateScoreConsistency(scoreStorageType, newValue);
        this.scoreValue = newValue;
    }

    /**
     * Updates the mutable draft fields without changing ownership, identity, status, or
     * effective-score selection.
     */
    public void updateDraft(
            ScoreValue newValue,
            Instant newBusinessTime,
            String newTimeSource) {
        if (status != AttemptStatus.DRAFT) {
            throw new InvalidScoreAttemptStateTransitionException(status, "update draft");
        }
        if (newValue == null) throw new IllegalArgumentException("scoreValue required");
        if (newBusinessTime == null) {
            throw new IllegalArgumentException("scoreBusinessTime required");
        }
        String normalizedTimeSource = newTimeSource == null ? null : newTimeSource.trim();
        if (normalizedTimeSource == null || normalizedTimeSource.isEmpty()) {
            throw new IllegalArgumentException("timeSource required");
        }
        if (normalizedTimeSource.length() > 32) {
            throw new IllegalArgumentException("timeSource must not exceed 32 characters");
        }
        validateScoreConsistency(scoreStorageType, newValue);
        this.scoreValue = newValue;
        this.scoreBusinessTime = newBusinessTime;
        this.timeSource = normalizedTimeSource;
    }

    // ── State transitions ──

    /** CG-SCORE-001: DRAFT → PENDING_REVIEW */
    public void submit() {
        if (status != AttemptStatus.DRAFT) {
            throw new InvalidScoreAttemptStateTransitionException(status, "submit");
        }
        this.status = AttemptStatus.PENDING_REVIEW;
        this.submittedAt = Instant.now();
        domainEvents.add(new ScoreAttemptSubmitted(id));
    }

    /** CG-SCORE-002: PENDING_REVIEW → APPROVED. Kept for backwards compatibility. */
    public void approve() {
        approve(true);
    }

    /** CG-SCORE-002: PENDING_REVIEW → APPROVED with an explicitly selected effective flag. */
    public void approve(boolean currentEffective) {
        if (status != AttemptStatus.PENDING_REVIEW) {
            throw new InvalidScoreAttemptStateTransitionException(status, "approve");
        }
        this.status = AttemptStatus.APPROVED;
        this.currentEffective = currentEffective;
        domainEvents.add(new ScoreAttemptApproved(id));
    }

    /**
     * Changes effective-score selection without invalidating an approved historical attempt.
     */
    public void changeCurrentEffective(boolean currentEffective) {
        if (status != AttemptStatus.APPROVED) {
            throw new InvalidScoreAttemptStateTransitionException(status, "change current effective");
        }
        this.currentEffective = currentEffective;
    }

    /** CG-SCORE-003: PENDING_REVIEW → REJECTED. Reason is mandatory. */
    public void reject(String reason) {
        if (status != AttemptStatus.PENDING_REVIEW) {
            throw new InvalidScoreAttemptStateTransitionException(status, "reject");
        }
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reject reason required");
        this.status = AttemptStatus.REJECTED;
        this.currentEffective = false;
        domainEvents.add(new ScoreAttemptRejected(id, reason));
    }

    /** CG-SCORE-004: REJECTED → DRAFT (revise and resubmit). */
    public void returnToDraft() {
        if (status != AttemptStatus.REJECTED) {
            throw new InvalidScoreAttemptStateTransitionException(status, "return to draft");
        }
        this.status = AttemptStatus.DRAFT;
        this.submittedAt = null;
        domainEvents.add(new ScoreAttemptReturnedToDraft(id));
    }

    /** CG-SCORE-005: APPROVED → INVALIDATED (terminal). Clears isCurrentEffective.
     *  Called when a correction replaces this score. */
    public void invalidate(UUID replacedById) {
        if (status != AttemptStatus.APPROVED) {
            throw new InvalidScoreAttemptStateTransitionException(status, "invalidate");
        }
        this.status = AttemptStatus.INVALIDATED;
        this.currentEffective = false;
        domainEvents.add(new ScoreAttemptInvalidated(id, replacedById));
    }

    public void clearDomainEvents() { domainEvents.clear(); }

    // ── Getters ──

    public ScoreAttemptId id() { return id; }
    public UUID schoolId() { return schoolId; }
    public UUID activityProjectId() { return activityProjectId; }
    public UUID studentId() { return studentId; }
    public int attemptNumber() { return attemptNumber; }
    public ScoreStorageType scoreStorageType() { return scoreStorageType; }
    public ScoreValue scoreValue() { return scoreValue; }
    public Instant scoreBusinessTime() { return scoreBusinessTime; }
    public String timeSource() { return timeSource; }
    public boolean isCurrentEffective() { return currentEffective; }
    public UUID replacesId() { return replacesId; }
    public AttemptStatus status() { return status; }
    public UUID enteredBy() { return enteredBy; }
    public Instant submittedAt() { return submittedAt; }
    public boolean isManualMakeup() { return manualMakeup; }

    public List<Object> domainEvents() { return Collections.unmodifiableList(domainEvents); }

    /** Builder for ScoreAttempt. */
    public static class Builder {
        private ScoreAttemptId id;
        private UUID schoolId;
        private UUID activityProjectId;
        private UUID studentId;
        private int attemptNumber;
        private ScoreStorageType scoreStorageType;
        private ScoreValue scoreValue;
        private Instant scoreBusinessTime;
        private String timeSource;
        private UUID replacesId;
        private UUID enteredBy;
        private boolean manualMakeup;

        public Builder id(ScoreAttemptId v) { this.id = v; return this; }
        public Builder schoolId(UUID v) { this.schoolId = v; return this; }
        public Builder activityProjectId(UUID v) { this.activityProjectId = v; return this; }
        public Builder studentId(UUID v) { this.studentId = v; return this; }
        public Builder attemptNumber(int v) { this.attemptNumber = v; return this; }
        public Builder scoreStorageType(ScoreStorageType v) { this.scoreStorageType = v; return this; }
        public Builder scoreValue(ScoreValue v) { this.scoreValue = v; return this; }
        public Builder scoreBusinessTime(Instant v) { this.scoreBusinessTime = v; return this; }
        public Builder timeSource(String v) { this.timeSource = v; return this; }
        public Builder replacesId(UUID v) { this.replacesId = v; return this; }
        public Builder enteredBy(UUID v) { this.enteredBy = v; return this; }
        public Builder manualMakeup(boolean v) { this.manualMakeup = v; return this; }
    }
}
