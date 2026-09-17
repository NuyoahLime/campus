package com.campusguinness.result.internal.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.time.Instant;
import java.util.UUID;

/**
 * ActivityResult aggregate root (ADR-004: independent aggregate).
 *
 * <p>Dual state machines:
 *
 * <p><b>result_internal_status</b> (3 states):
 * <pre>
 *   DRAFT → INTERNAL_PUBLISHED → INTERNAL_WITHDRAWN → DRAFT
 * </pre>
 *
 * <p><b>result_public_status</b> (7 states):
 * <pre>
 *   NOT_SUBMITTED → PENDING_PUBLIC_REVIEW → PLATFORM_APPROVED → PUBLIC
 *                                         → PLATFORM_REJECTED → NOT_SUBMITTED
 *                                                       PUBLIC → ANOMALY_PENDING → PUBLIC
 *                                                              → PLATFORM_TAKEDOWN → NOT_SUBMITTED
 *                                              ANOMALY_PENDING → NOT_SUBMITTED
 *                                              ANOMALY_PENDING → PLATFORM_TAKEDOWN
 * </pre>
 *
 * <p>Cross-machine (ADR-004 §6):
 * <ul>
 *   <li>Only INTERNAL_PUBLISHED can submit for public review
 *   <li>INTERNAL_WITHDRAWN + PUBLIC → auto PLATFORM_TAKEDOWN (atomic within aggregate)
 *   <li>INTERNAL_WITHDRAWN → DRAFT does NOT auto-restore public status
 * </ul>
 *
 * <p>ResultVersion is an immutable snapshot referenced through candidate, internal, and public pointers.
 */
public final class ActivityResult {

    private final ActivityResultId id;
    private final UUID schoolId;
    private final UUID activityId;
    private ResultInternalStatus internalStatus;
    private ResultPublicStatus publicStatus;
    private UUID currentCandidateVersionId;
    private UUID currentInternalVersionId;
    private UUID currentPublicVersionId;
    private boolean publicVisibilityBlocked;
    private final Instant createdAt;
    private Instant updatedAt;
    private final int persistenceVersion;
    private final List<Object> domainEvents;

    private ActivityResult(Builder b, ResultInternalStatus internalStatus, ResultPublicStatus publicStatus,
                           UUID candidateVersionId, UUID internalVersionId, UUID publicVersionId,
                           boolean publicVisibilityBlocked, Instant createdAt, Instant updatedAt,
                           int persistenceVersion) {
        this.id = b.id;
        this.schoolId = b.schoolId;
        this.activityId = b.activityId;
        this.internalStatus = internalStatus;
        this.publicStatus = publicStatus;
        this.currentCandidateVersionId = candidateVersionId;
        this.currentInternalVersionId = internalVersionId;
        this.currentPublicVersionId = publicVersionId;
        this.publicVisibilityBlocked = publicVisibilityBlocked;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.persistenceVersion = persistenceVersion;
        this.domainEvents = new ArrayList<>();
    }

    public static ActivityResult create(Builder builder) {
        validate(builder);
        Instant now = Instant.now();
        return new ActivityResult(builder, ResultInternalStatus.DRAFT, ResultPublicStatus.NOT_SUBMITTED,
                null, null, null, false, now, now, 0);
    }

    public static ActivityResult reconstitute(Builder builder,
            ResultInternalStatus internalStatus, ResultPublicStatus publicStatus,
            UUID candidateVersionId, UUID internalVersionId, UUID publicVersionId,
            boolean publicVisibilityBlocked, Instant createdAt, Instant updatedAt,
            int persistenceVersion) {
        validate(builder);
        if (internalStatus == null) throw new IllegalArgumentException("internalStatus required");
        if (publicStatus == null) throw new IllegalArgumentException("publicStatus required");
        if (createdAt == null) throw new IllegalArgumentException("createdAt required");
        if (updatedAt == null) throw new IllegalArgumentException("updatedAt required");
        if (persistenceVersion < 0) throw new IllegalArgumentException("persistenceVersion must not be negative");
        return new ActivityResult(builder, internalStatus, publicStatus,
                candidateVersionId, internalVersionId, publicVersionId,
                publicVisibilityBlocked, createdAt, updatedAt, persistenceVersion);
    }

    private static void validate(Builder b) {
        if (b.id == null) throw new IllegalArgumentException("id required");
        if (b.schoolId == null) throw new IllegalArgumentException("schoolId required");
        if (b.activityId == null) throw new IllegalArgumentException("activityId required");
    }

    // ── result_internal_status transitions ──

    public void createFirstCandidate(UUID candidateVersionId) {
        requireVersionId(candidateVersionId, "candidateVersionId");
        if (currentCandidateVersionId != null) {
            throw new IllegalStateException("ActivityResult already has a candidate version");
        }
        this.currentCandidateVersionId = candidateVersionId;
        this.internalStatus = ResultInternalStatus.DRAFT;
        this.publicStatus = ResultPublicStatus.NOT_SUBMITTED;
        this.publicVisibilityBlocked = false;
        touch();
    }

    public void replaceCandidateAfterCoreEdit(UUID candidateVersionId) {
        requireVersionId(candidateVersionId, "candidateVersionId");
        if (internalStatus == ResultInternalStatus.INTERNAL_WITHDRAWN) {
            throw new InvalidResultStateTransitionException(internalStatus, "edit core content");
        }
        if (publicStatus == ResultPublicStatus.PENDING_PUBLIC_REVIEW
                || publicStatus == ResultPublicStatus.PLATFORM_APPROVED) {
            throw new InvalidResultStateTransitionException(publicStatus, "edit core content");
        }
        this.currentCandidateVersionId = candidateVersionId;
        this.internalStatus = ResultInternalStatus.DRAFT;
        if (publicStatus != ResultPublicStatus.PLATFORM_TAKEDOWN) {
            this.publicStatus = ResultPublicStatus.NOT_SUBMITTED;
        }
        touch();
    }

    /** DRAFT → INTERNAL_PUBLISHED */
    public void publishInternal(UUID candidateVersionId) {
        if (internalStatus != ResultInternalStatus.DRAFT) {
            throw new InvalidResultStateTransitionException(internalStatus, "publish internal");
        }
        requireVersionId(candidateVersionId, "candidateVersionId");
        if (!candidateVersionId.equals(currentCandidateVersionId)) {
            throw new IllegalStateException("Internal publication must use the current candidate version");
        }
        this.currentInternalVersionId = candidateVersionId;
        this.internalStatus = ResultInternalStatus.INTERNAL_PUBLISHED;
        touch();
        domainEvents.add(new ResultInternalPublished(id));
    }

    /** @deprecated use publishInternal(UUID) so the exact candidate pointer is explicit. */
    @Deprecated(forRemoval = false)
    public void publishInternal() {
        if (currentCandidateVersionId == null) {
            throw new IllegalStateException("currentCandidateVersionId required");
        }
        publishInternal(currentCandidateVersionId);
    }

    /** INTERNAL_PUBLISHED → INTERNAL_WITHDRAWN.
     *  Cross-machine: if PUBLIC → auto PLATFORM_TAKEDOWN (ADR-004 §6.3). */
    public void withdrawInternal() {
        if (internalStatus != ResultInternalStatus.INTERNAL_PUBLISHED) {
            throw new InvalidResultStateTransitionException(internalStatus, "withdraw internal");
        }
        this.internalStatus = ResultInternalStatus.INTERNAL_WITHDRAWN;
        touch();
        domainEvents.add(new ResultInternalWithdrawn(id));
        if (currentPublicVersionId != null && !publicVisibilityBlocked) {
            currentCandidateVersionId = null;
            this.publicStatus = ResultPublicStatus.PLATFORM_TAKEDOWN;
            this.publicVisibilityBlocked = true;
            domainEvents.add(new ResultPlatformTakenDown(id));
        }
    }

    /** INTERNAL_WITHDRAWN → DRAFT. Does NOT auto-restore public status (ADR-004 §6.4). */
    public void returnToDraft() {
        if (internalStatus != ResultInternalStatus.INTERNAL_WITHDRAWN) {
            throw new InvalidResultStateTransitionException(internalStatus, "return to draft");
        }
        this.internalStatus = ResultInternalStatus.DRAFT;
        touch();
    }

    // ── result_public_status transitions ──

    /** NOT_SUBMITTED → PENDING_PUBLIC_REVIEW. Precondition: internal must be INTERNAL_PUBLISHED. */
    public void submitForReview(UUID candidateVersionId) {
        if (publicStatus != ResultPublicStatus.NOT_SUBMITTED) {
            throw new InvalidResultStateTransitionException(publicStatus, "submit for review");
        }
        if (internalStatus != ResultInternalStatus.INTERNAL_PUBLISHED) {
            throw new InvalidResultStateTransitionException(internalStatus, "submit for review");
        }
        requireVersionId(candidateVersionId, "candidateVersionId");
        if (!candidateVersionId.equals(currentCandidateVersionId)) {
            throw new IllegalStateException("Public review submission must use the current candidate version");
        }
        if (!candidateVersionId.equals(currentInternalVersionId)) {
            throw new IllegalStateException("Public review candidate must be the current internal version");
        }
        this.publicStatus = ResultPublicStatus.PENDING_PUBLIC_REVIEW;
        touch();
        domainEvents.add(new ResultSubmittedForReview(id));
    }

    public void submitForReview() {
        submitForReview(currentCandidateVersionId);
    }

    /** PENDING_PUBLIC_REVIEW → PLATFORM_APPROVED */
    public void platformApprove(UUID candidateVersionId) {
        if (publicStatus != ResultPublicStatus.PENDING_PUBLIC_REVIEW) {
            throw new InvalidResultStateTransitionException(publicStatus, "platform approve");
        }
        requireCurrentCandidate(candidateVersionId, "approve public review");
        this.publicStatus = ResultPublicStatus.PLATFORM_APPROVED;
        touch();
        domainEvents.add(new ResultPlatformApproved(id));
    }

    public void platformApprove() {
        platformApprove(currentCandidateVersionId);
    }

    /** PENDING_PUBLIC_REVIEW → PLATFORM_REJECTED */
    public void platformReject(UUID candidateVersionId) {
        if (publicStatus != ResultPublicStatus.PENDING_PUBLIC_REVIEW) {
            throw new InvalidResultStateTransitionException(publicStatus, "platform reject");
        }
        requireCurrentCandidate(candidateVersionId, "reject public review");
        this.publicStatus = ResultPublicStatus.PLATFORM_REJECTED;
        touch();
    }

    public void platformReject() {
        platformReject(currentCandidateVersionId);
    }

    /** PLATFORM_APPROVED → PUBLIC */
    public void makePublic() {
        if (publicStatus != ResultPublicStatus.PLATFORM_APPROVED) {
            throw new InvalidResultStateTransitionException(publicStatus, "make public");
        }
        this.publicStatus = ResultPublicStatus.PUBLIC;
        touch();
        domainEvents.add(new ResultMadePublic(id));
    }

    /** PUBLIC → ANOMALY_PENDING (referenced media taken down) */
    public void markAnomaly() {
        if (publicStatus != ResultPublicStatus.PUBLIC) {
            throw new InvalidResultStateTransitionException(publicStatus, "mark anomaly");
        }
        this.publicStatus = ResultPublicStatus.ANOMALY_PENDING;
        touch();
    }

    /** ANOMALY_PENDING → PUBLIC (anomaly resolved) */
    public void resolveAnomaly() {
        if (publicStatus != ResultPublicStatus.ANOMALY_PENDING) {
            throw new InvalidResultStateTransitionException(publicStatus, "resolve anomaly");
        }
        this.publicStatus = ResultPublicStatus.PUBLIC;
        touch();
    }

    /** PLATFORM_REJECTED / PLATFORM_TAKEDOWN → NOT_SUBMITTED */
    public void returnToNotSubmitted() {
        if (publicStatus != ResultPublicStatus.PLATFORM_REJECTED
                && publicStatus != ResultPublicStatus.PLATFORM_TAKEDOWN
                && publicStatus != ResultPublicStatus.ANOMALY_PENDING) {
            throw new InvalidResultStateTransitionException(publicStatus, "return to not submitted");
        }
        this.publicStatus = ResultPublicStatus.NOT_SUBMITTED;
        touch();
    }

    /** PUBLIC or ANOMALY_PENDING → PLATFORM_TAKEDOWN */
    public void platformTakedown() {
        if (publicStatus != ResultPublicStatus.PUBLIC
                && publicStatus != ResultPublicStatus.ANOMALY_PENDING) {
            throw new InvalidResultStateTransitionException(publicStatus, "platform takedown");
        }
        this.publicStatus = ResultPublicStatus.PLATFORM_TAKEDOWN;
        touch();
        domainEvents.add(new ResultPlatformTakenDown(id));
    }

    private void touch() { updatedAt = Instant.now(); }

    private static void requireVersionId(UUID value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " required");
    }

    private void requireCurrentCandidate(UUID candidateVersionId, String operation) {
        requireVersionId(candidateVersionId, "candidateVersionId");
        if (!candidateVersionId.equals(currentCandidateVersionId)) {
            throw new IllegalStateException(operation + " must use the current candidate version");
        }
    }

    public void clearDomainEvents() { domainEvents.clear(); }

    // ── Getters ──

    public ActivityResultId id() { return id; }
    public UUID schoolId() { return schoolId; }
    public UUID activityId() { return activityId; }
    public ResultInternalStatus internalStatus() { return internalStatus; }
    public ResultPublicStatus publicStatus() { return publicStatus; }
    public UUID currentCandidateVersionId() { return currentCandidateVersionId; }
    public UUID currentInternalVersionId() { return currentInternalVersionId; }
    public UUID currentPublicVersionId() { return currentPublicVersionId; }
    public boolean publicVisibilityBlocked() { return publicVisibilityBlocked; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public int persistenceVersion() { return persistenceVersion; }

    public List<Object> domainEvents() { return Collections.unmodifiableList(domainEvents); }

    public static class Builder {
        private ActivityResultId id;
        private UUID schoolId, activityId;

        public Builder id(ActivityResultId v) { this.id = v; return this; }
        public Builder schoolId(UUID v) { this.schoolId = v; return this; }
        public Builder activityId(UUID v) { this.activityId = v; return this; }
    }
}
