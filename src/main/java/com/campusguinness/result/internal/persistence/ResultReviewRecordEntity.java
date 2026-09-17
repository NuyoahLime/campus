package com.campusguinness.result.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "result_review_records")
class ResultReviewRecordEntity {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "result_id", nullable = false, updatable = false) private UUID resultId;
    @Column(name = "result_version_id", nullable = false, updatable = false) private UUID resultVersionId;
    @Column(name = "action", nullable = false, length = 32, updatable = false) private String action;
    @Column(name = "submitted_by", updatable = false) private UUID submittedBy;
    @Column(name = "submitted_at", updatable = false) private Instant submittedAt;
    @Column(name = "reviewer_id", updatable = false) private UUID reviewerId;
    @Column(name = "reviewed_at", updatable = false) private Instant reviewedAt;
    @Column(name = "reason", updatable = false) private String reason;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected ResultReviewRecordEntity() {}

    void setId(UUID value) { id = value; }
    void setResultId(UUID value) { resultId = value; }
    void setResultVersionId(UUID value) { resultVersionId = value; }
    void setAction(String value) { action = value; }
    void setSubmittedBy(UUID value) { submittedBy = value; }
    void setSubmittedAt(Instant value) { submittedAt = value; }
    void setReviewerId(UUID value) { reviewerId = value; }
    void setReviewedAt(Instant value) { reviewedAt = value; }
    void setReason(String value) { reason = value; }
    void setCreatedAt(Instant value) { createdAt = value; }

    UUID getId() { return id; }
    UUID getResultId() { return resultId; }
    UUID getResultVersionId() { return resultVersionId; }
    String getAction() { return action; }
    UUID getSubmittedBy() { return submittedBy; }
    Instant getSubmittedAt() { return submittedAt; }
    UUID getReviewerId() { return reviewerId; }
    Instant getReviewedAt() { return reviewedAt; }
    String getReason() { return reason; }
    Instant getCreatedAt() { return createdAt; }
}
