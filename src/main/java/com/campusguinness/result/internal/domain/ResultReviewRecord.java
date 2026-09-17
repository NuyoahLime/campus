package com.campusguinness.result.internal.domain;

import java.time.Instant;
import java.util.UUID;

public final class ResultReviewRecord {
    private static final int REASON_MAX = 2_000;
    private final ResultReviewRecordId id;
    private final ActivityResultId resultId;
    private final ResultVersionId resultVersionId;
    private final ResultReviewAction action;
    private final UUID submittedBy;
    private final Instant submittedAt;
    private final UUID reviewerId;
    private final Instant reviewedAt;
    private final String reason;
    private final Instant createdAt;

    private ResultReviewRecord(
            ResultReviewRecordId id,
            ActivityResultId resultId,
            ResultVersionId resultVersionId,
            ResultReviewAction action,
            UUID submittedBy,
            Instant submittedAt,
            UUID reviewerId,
            Instant reviewedAt,
            String reason,
            Instant createdAt) {
        this.id = id;
        this.resultId = resultId;
        this.resultVersionId = resultVersionId;
        this.action = action;
        this.submittedBy = submittedBy;
        this.submittedAt = submittedAt;
        this.reviewerId = reviewerId;
        this.reviewedAt = reviewedAt;
        this.reason = reason;
        this.createdAt = createdAt;
        validate();
    }

    public static ResultReviewRecord submitted(
            ResultReviewRecordId id,
            ActivityResultId resultId,
            ResultVersionId resultVersionId,
            UUID submittedBy,
            Instant submittedAt) {
        return new ResultReviewRecord(id, resultId, resultVersionId, ResultReviewAction.SUBMITTED,
                submittedBy, submittedAt, null, null, null, submittedAt);
    }

    public static ResultReviewRecord approved(
            ResultReviewRecordId id,
            ActivityResultId resultId,
            ResultVersionId resultVersionId,
            UUID reviewerId,
            Instant reviewedAt) {
        return new ResultReviewRecord(id, resultId, resultVersionId, ResultReviewAction.APPROVED,
                null, null, reviewerId, reviewedAt, null, reviewedAt);
    }

    public static ResultReviewRecord rejected(
            ResultReviewRecordId id,
            ActivityResultId resultId,
            ResultVersionId resultVersionId,
            UUID reviewerId,
            Instant reviewedAt,
            String reason) {
        return new ResultReviewRecord(id, resultId, resultVersionId, ResultReviewAction.REJECTED,
                null, null, reviewerId, reviewedAt, reason, reviewedAt);
    }

    public static ResultReviewRecord reconstitute(
            ResultReviewRecordId id,
            ActivityResultId resultId,
            ResultVersionId resultVersionId,
            ResultReviewAction action,
            UUID submittedBy,
            Instant submittedAt,
            UUID reviewerId,
            Instant reviewedAt,
            String reason,
            Instant createdAt) {
        return new ResultReviewRecord(id, resultId, resultVersionId, action,
                submittedBy, submittedAt, reviewerId, reviewedAt, reason, createdAt);
    }

    private void validate() {
        if (id == null) throw new IllegalArgumentException("id required");
        if (resultId == null) throw new IllegalArgumentException("resultId required");
        if (resultVersionId == null) throw new IllegalArgumentException("resultVersionId required");
        if (action == null) throw new IllegalArgumentException("action required");
        if (createdAt == null) throw new IllegalArgumentException("createdAt required");
        if (action == ResultReviewAction.SUBMITTED) {
            if (submittedBy == null || submittedAt == null) {
                throw new IllegalArgumentException("submission actor and timestamp required");
            }
            if (reviewerId != null || reviewedAt != null || reason != null) {
                throw new IllegalArgumentException("submitted record must not contain review fields");
            }
            return;
        }
        if (reviewerId == null || reviewedAt == null) {
            throw new IllegalArgumentException("review actor and timestamp required");
        }
        if (submittedBy != null || submittedAt != null) {
            throw new IllegalArgumentException("decision record must not contain submission fields");
        }
        if (action == ResultReviewAction.APPROVED && reason != null) {
            throw new IllegalArgumentException("approved record must not contain a reason");
        }
        if ((action == ResultReviewAction.REJECTED || action == ResultReviewAction.TAKEDOWN)
                && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("reason required");
        }
        if (reason != null && reason.length() > REASON_MAX) {
            throw new IllegalArgumentException("reason max 2000 chars");
        }
    }

    public ResultReviewRecordId id() { return id; }
    public ActivityResultId resultId() { return resultId; }
    public ResultVersionId resultVersionId() { return resultVersionId; }
    public ResultReviewAction action() { return action; }
    public UUID submittedBy() { return submittedBy; }
    public Instant submittedAt() { return submittedAt; }
    public UUID reviewerId() { return reviewerId; }
    public Instant reviewedAt() { return reviewedAt; }
    public String reason() { return reason; }
    public Instant createdAt() { return createdAt; }
}
