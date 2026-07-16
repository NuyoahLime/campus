package com.campusguinness.feedback.internal.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Feedback aggregate root (5-state machine).
 * <pre>
 *   SUBMITTED → PROCESSING → RESOLVED → CLOSED (terminal)
 *             → CLOSED (terminal)
 *   PROCESSING → ESCALATED → PROCESSING (cycle back)
 *              → CLOSED (terminal)
 * </pre>
 *
 * <p>task_records: INFRASTRUCTURE_TASK — not related to Feedback aggregate.
 */
public final class Feedback {

    private final FeedbackId id;
    private final UUID schoolId;
    private final UUID submitterId;
    private final String feedbackType;
    private final String content;
    private FeedbackStatus status;
    private UUID handlerId;
    private String handlerLevel;
    private String reply;
    private String closeReason;
    private final List<Object> domainEvents;

    private Feedback(Builder b, FeedbackStatus status, UUID handlerId, String handlerLevel,
                     String reply, String closeReason) {
        this.id = b.id; this.schoolId = b.schoolId; this.submitterId = b.submitterId;
        this.feedbackType = b.feedbackType; this.content = b.content;
        this.status = status; this.handlerId = handlerId; this.handlerLevel = handlerLevel;
        this.reply = reply; this.closeReason = closeReason;
        this.domainEvents = new ArrayList<>();
    }

    public static Feedback create(Builder builder) {
        validate(builder);
        return new Feedback(builder, FeedbackStatus.SUBMITTED, null, null, null, null);
    }

    public static Feedback reconstitute(Builder builder, FeedbackStatus status,
            UUID handlerId, String handlerLevel, String reply, String closeReason) {
        validate(builder);
        return new Feedback(builder, status, handlerId, handlerLevel, reply, closeReason);
    }

    private static void validate(Builder b) {
        if (b.id == null) throw new IllegalArgumentException("id required");
        if (b.feedbackType == null || b.feedbackType.isBlank()) throw new IllegalArgumentException("feedbackType required");
        if (b.content == null || b.content.isBlank()) throw new IllegalArgumentException("content required");
    }

    // ── State transitions ──

    /** SUBMITTED → PROCESSING */
    public void beginProcessing(UUID handlerId) {
        if (status != FeedbackStatus.SUBMITTED && status != FeedbackStatus.ESCALATED) {
            throw new InvalidFeedbackStateTransitionException(status, "begin processing");
        }
        this.status = FeedbackStatus.PROCESSING;
        this.handlerId = handlerId;
    }

    /** PROCESSING → RESOLVED */
    public void resolve(String reply) {
        if (status != FeedbackStatus.PROCESSING) {
            throw new InvalidFeedbackStateTransitionException(status, "resolve");
        }
        this.status = FeedbackStatus.RESOLVED;
        this.reply = reply;
    }

    /** PROCESSING → ESCALATED */
    public void escalate() {
        if (status != FeedbackStatus.PROCESSING) {
            throw new InvalidFeedbackStateTransitionException(status, "escalate");
        }
        this.status = FeedbackStatus.ESCALATED;
    }

    /** SUBMITTED / PROCESSING / RESOLVED → CLOSED (terminal) */
    public void close(String reason) {
        if (status != FeedbackStatus.SUBMITTED
                && status != FeedbackStatus.PROCESSING
                && status != FeedbackStatus.RESOLVED) {
            throw new InvalidFeedbackStateTransitionException(status, "close");
        }
        this.status = FeedbackStatus.CLOSED;
        this.closeReason = reason;
    }

    // ── Field updates (PROCESSING only) ──

    public void setHandlerLevel(String level) {
        if (status != FeedbackStatus.PROCESSING) {
            throw new InvalidFeedbackStateTransitionException(status, "set handler level");
        }
        this.handlerLevel = level;
    }

    public void clearDomainEvents() { domainEvents.clear(); }

    // ── Getters ──

    public FeedbackId id() { return id; }
    public UUID schoolId() { return schoolId; }
    public UUID submitterId() { return submitterId; }
    public String feedbackType() { return feedbackType; }
    public String content() { return content; }
    public FeedbackStatus status() { return status; }
    public UUID handlerId() { return handlerId; }
    public String handlerLevel() { return handlerLevel; }
    public String reply() { return reply; }
    public String closeReason() { return closeReason; }

    public List<Object> domainEvents() { return Collections.unmodifiableList(domainEvents); }

    public static class Builder {
        private FeedbackId id;
        private UUID schoolId, submitterId;
        private String feedbackType, content;

        public Builder id(FeedbackId v) { this.id = v; return this; }
        public Builder schoolId(UUID v) { this.schoolId = v; return this; }
        public Builder submitterId(UUID v) { this.submitterId = v; return this; }
        public Builder feedbackType(String v) { this.feedbackType = v; return this; }
        public Builder content(String v) { this.content = v; return this; }
    }
}
