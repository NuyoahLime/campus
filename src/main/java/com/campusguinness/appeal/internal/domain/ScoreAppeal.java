package com.campusguinness.appeal.internal.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * ScoreAppeal aggregate root (13-state machine, most complex in the system).
 *
 * <pre>
 *   SUBMITTED → PROCESSING → REJECTED (terminal)
 *                          → ACCEPTED_PENDING_CORRECTION → SCORE_CORRECTING → RESOLVED (terminal)
 *                          → RANK_CHECKING → RANK_FIXING → RESOLVED
 *                                         → REJECTED / WITHDRAWN
 *                          → ESCALATED → PLATFORM_PROCESSING → PLATFORM_DECIDED → RESOLVED
 *                                                             → RETURNED_TO_SCHOOL → PROCESSING
 *                          → WITHDRAWN (terminal)
 *   SUBMITTED → WITHDRAWN (terminal)
 * </pre>
 *
 * <p>appeal_records: IMMUTABLE_HISTORY_RECORD (deferred, V1 not modeled).
 */
public final class ScoreAppeal {

    private final ScoreAppealId id;
    private final UUID schoolId;
    private final UUID scoreAttemptId;
    private final UUID studentId;
    private final String appealType;
    private final String appealReason;
    private final String evidenceFileKeys;
    private AppealStatus status;
    private UUID handlerId;
    private UUID escalatedTo;
    private String resolution;
    private Instant resolvedAt;
    private final List<Object> domainEvents;

    private ScoreAppeal(Builder b, AppealStatus status, UUID handlerId, UUID escalatedTo,
                         String resolution, Instant resolvedAt) {
        this.id = b.id; this.schoolId = b.schoolId; this.scoreAttemptId = b.scoreAttemptId;
        this.studentId = b.studentId; this.appealType = b.appealType; this.appealReason = b.appealReason;
        this.evidenceFileKeys = b.evidenceFileKeys;
        this.status = status; this.handlerId = handlerId; this.escalatedTo = escalatedTo;
        this.resolution = resolution; this.resolvedAt = resolvedAt;
        this.domainEvents = new ArrayList<>();
    }

    public static ScoreAppeal create(Builder builder) {
        validate(builder);
        return new ScoreAppeal(builder, AppealStatus.SUBMITTED, null, null, null, null);
    }

    public static ScoreAppeal reconstitute(Builder builder, AppealStatus status,
            UUID handlerId, UUID escalatedTo, String resolution, Instant resolvedAt) {
        validate(builder);
        return new ScoreAppeal(builder, status, handlerId, escalatedTo, resolution, resolvedAt);
    }

    private static void validate(Builder b) {
        if (b.id == null) throw new IllegalArgumentException("id required");
        if (b.schoolId == null) throw new IllegalArgumentException("schoolId required");
        if (b.scoreAttemptId == null) throw new IllegalArgumentException("scoreAttemptId required");
        if (b.studentId == null) throw new IllegalArgumentException("studentId required");
        if (b.appealType == null || b.appealType.isBlank()) throw new IllegalArgumentException("appealType required");
        if (b.appealReason == null || b.appealReason.isBlank()) throw new IllegalArgumentException("appealReason required");
    }

    // ── State transitions ──

    /** SUBMITTED → PROCESSING */
    public void beginProcessing(UUID handlerId) {
        if (status != AppealStatus.SUBMITTED && status != AppealStatus.RETURNED_TO_SCHOOL) {
            throw new InvalidAppealStateTransitionException(status, "begin processing");
        }
        this.status = AppealStatus.PROCESSING;
        this.handlerId = handlerId;
    }

    /** SUBMITTED / PROCESSING / RANK_CHECKING → WITHDRAWN (terminal) */
    public void withdraw() {
        if (status != AppealStatus.SUBMITTED
                && status != AppealStatus.PROCESSING
                && status != AppealStatus.RANK_CHECKING) {
            throw new InvalidAppealStateTransitionException(status, "withdraw");
        }
        this.status = AppealStatus.WITHDRAWN;
        domainEvents.add(new ScoreAppealWithdrawn(id));
    }

    /** PROCESSING / RANK_CHECKING → REJECTED (terminal) */
    public void reject(String resolution) {
        if (status != AppealStatus.PROCESSING && status != AppealStatus.RANK_CHECKING) {
            throw new InvalidAppealStateTransitionException(status, "reject");
        }
        this.status = AppealStatus.REJECTED;
        this.resolution = resolution;
        this.resolvedAt = Instant.now();
        domainEvents.add(new ScoreAppealRejected(id));
    }

    /** PROCESSING → ACCEPTED_PENDING_CORRECTION */
    public void acceptPendingCorrection() {
        if (status != AppealStatus.PROCESSING) {
            throw new InvalidAppealStateTransitionException(status, "accept pending correction");
        }
        this.status = AppealStatus.ACCEPTED_PENDING_CORRECTION;
    }

    /** ACCEPTED_PENDING_CORRECTION → SCORE_CORRECTING */
    public void beginScoreCorrecting() {
        if (status != AppealStatus.ACCEPTED_PENDING_CORRECTION) {
            throw new InvalidAppealStateTransitionException(status, "begin score correcting");
        }
        this.status = AppealStatus.SCORE_CORRECTING;
    }

    /** PROCESSING → RANK_CHECKING */
    public void beginRankChecking() {
        if (status != AppealStatus.PROCESSING) {
            throw new InvalidAppealStateTransitionException(status, "begin rank checking");
        }
        this.status = AppealStatus.RANK_CHECKING;
    }

    /** RANK_CHECKING → RANK_FIXING */
    public void beginRankFixing() {
        if (status != AppealStatus.RANK_CHECKING) {
            throw new InvalidAppealStateTransitionException(status, "begin rank fixing");
        }
        this.status = AppealStatus.RANK_FIXING;
    }

    /** PROCESSING → ESCALATED */
    public void escalate(UUID escalatedTo) {
        if (status != AppealStatus.PROCESSING) {
            throw new InvalidAppealStateTransitionException(status, "escalate");
        }
        this.status = AppealStatus.ESCALATED;
        this.escalatedTo = escalatedTo;
    }

    /** ESCALATED → PLATFORM_PROCESSING */
    public void beginPlatformProcessing() {
        if (status != AppealStatus.ESCALATED) {
            throw new InvalidAppealStateTransitionException(status, "begin platform processing");
        }
        this.status = AppealStatus.PLATFORM_PROCESSING;
    }

    /** PLATFORM_PROCESSING → RETURNED_TO_SCHOOL */
    public void returnToSchool() {
        if (status != AppealStatus.PLATFORM_PROCESSING) {
            throw new InvalidAppealStateTransitionException(status, "return to school");
        }
        this.status = AppealStatus.RETURNED_TO_SCHOOL;
    }

    /** PLATFORM_PROCESSING → PLATFORM_DECIDED */
    public void platformDecide() {
        if (status != AppealStatus.PLATFORM_PROCESSING) {
            throw new InvalidAppealStateTransitionException(status, "platform decide");
        }
        this.status = AppealStatus.PLATFORM_DECIDED;
    }

    /** SCORE_CORRECTING / RANK_FIXING / PLATFORM_DECIDED → RESOLVED (terminal) */
    public void resolve(String resolution) {
        if (status != AppealStatus.SCORE_CORRECTING
                && status != AppealStatus.RANK_FIXING
                && status != AppealStatus.PLATFORM_DECIDED) {
            throw new InvalidAppealStateTransitionException(status, "resolve");
        }
        this.status = AppealStatus.RESOLVED;
        this.resolution = resolution;
        this.resolvedAt = Instant.now();
        domainEvents.add(new ScoreAppealResolved(id));
    }

    public void clearDomainEvents() { domainEvents.clear(); }

    // ── Getters ──

    public ScoreAppealId id() { return id; }
    public UUID schoolId() { return schoolId; }
    public UUID scoreAttemptId() { return scoreAttemptId; }
    public UUID studentId() { return studentId; }
    public String appealType() { return appealType; }
    public String appealReason() { return appealReason; }
    public String evidenceFileKeys() { return evidenceFileKeys; }
    public AppealStatus status() { return status; }
    public UUID handlerId() { return handlerId; }
    public UUID escalatedTo() { return escalatedTo; }
    public String resolution() { return resolution; }
    public Instant resolvedAt() { return resolvedAt; }

    public List<Object> domainEvents() { return Collections.unmodifiableList(domainEvents); }

    public static class Builder {
        private ScoreAppealId id;
        private UUID schoolId, scoreAttemptId, studentId;
        private String appealType, appealReason, evidenceFileKeys;

        public Builder id(ScoreAppealId v) { this.id = v; return this; }
        public Builder schoolId(UUID v) { this.schoolId = v; return this; }
        public Builder scoreAttemptId(UUID v) { this.scoreAttemptId = v; return this; }
        public Builder studentId(UUID v) { this.studentId = v; return this; }
        public Builder appealType(String v) { this.appealType = v; return this; }
        public Builder appealReason(String v) { this.appealReason = v; return this; }
        public Builder evidenceFileKeys(String v) { this.evidenceFileKeys = v; return this; }
    }
}
