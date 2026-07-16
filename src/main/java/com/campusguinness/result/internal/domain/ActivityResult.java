package com.campusguinness.result.internal.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
 * <p>ResultVersion: IMMUTABLE_VERSION_SNAPSHOT (deferred, V1 not modeled in domain).
 */
public final class ActivityResult {

    private final ActivityResultId id;
    private final UUID schoolId;
    private final UUID activityId;
    private ResultInternalStatus internalStatus;
    private ResultPublicStatus publicStatus;
    private UUID currentInternalVersionId;
    private UUID currentPublicVersionId;
    private final List<Object> domainEvents;

    private ActivityResult(Builder b, ResultInternalStatus internalStatus, ResultPublicStatus publicStatus,
                           UUID internalVersionId, UUID publicVersionId) {
        this.id = b.id;
        this.schoolId = b.schoolId;
        this.activityId = b.activityId;
        this.internalStatus = internalStatus;
        this.publicStatus = publicStatus;
        this.currentInternalVersionId = internalVersionId;
        this.currentPublicVersionId = publicVersionId;
        this.domainEvents = new ArrayList<>();
    }

    public static ActivityResult create(Builder builder) {
        validate(builder);
        return new ActivityResult(builder, ResultInternalStatus.DRAFT, ResultPublicStatus.NOT_SUBMITTED, null, null);
    }

    public static ActivityResult reconstitute(Builder builder,
            ResultInternalStatus internalStatus, ResultPublicStatus publicStatus,
            UUID internalVersionId, UUID publicVersionId) {
        validate(builder);
        return new ActivityResult(builder, internalStatus, publicStatus, internalVersionId, publicVersionId);
    }

    private static void validate(Builder b) {
        if (b.id == null) throw new IllegalArgumentException("id required");
        if (b.schoolId == null) throw new IllegalArgumentException("schoolId required");
        if (b.activityId == null) throw new IllegalArgumentException("activityId required");
    }

    // ── result_internal_status transitions ──

    /** DRAFT → INTERNAL_PUBLISHED */
    public void publishInternal() {
        if (internalStatus != ResultInternalStatus.DRAFT) {
            throw new InvalidResultStateTransitionException(internalStatus, "publish internal");
        }
        this.internalStatus = ResultInternalStatus.INTERNAL_PUBLISHED;
        domainEvents.add(new ResultInternalPublished(id));
    }

    /** INTERNAL_PUBLISHED → INTERNAL_WITHDRAWN.
     *  Cross-machine: if PUBLIC → auto PLATFORM_TAKEDOWN (ADR-004 §6.3). */
    public void withdrawInternal() {
        if (internalStatus != ResultInternalStatus.INTERNAL_PUBLISHED) {
            throw new InvalidResultStateTransitionException(internalStatus, "withdraw internal");
        }
        this.internalStatus = ResultInternalStatus.INTERNAL_WITHDRAWN;
        domainEvents.add(new ResultInternalWithdrawn(id));
        if (publicStatus == ResultPublicStatus.PUBLIC
                || publicStatus == ResultPublicStatus.ANOMALY_PENDING) {
            this.publicStatus = ResultPublicStatus.PLATFORM_TAKEDOWN;
            domainEvents.add(new ResultPlatformTakenDown(id));
        }
    }

    /** INTERNAL_WITHDRAWN → DRAFT. Does NOT auto-restore public status (ADR-004 §6.4). */
    public void returnToDraft() {
        if (internalStatus != ResultInternalStatus.INTERNAL_WITHDRAWN) {
            throw new InvalidResultStateTransitionException(internalStatus, "return to draft");
        }
        this.internalStatus = ResultInternalStatus.DRAFT;
    }

    // ── result_public_status transitions ──

    /** NOT_SUBMITTED → PENDING_PUBLIC_REVIEW. Precondition: internal must be INTERNAL_PUBLISHED. */
    public void submitForReview() {
        if (publicStatus != ResultPublicStatus.NOT_SUBMITTED) {
            throw new InvalidResultStateTransitionException(publicStatus, "submit for review");
        }
        if (internalStatus != ResultInternalStatus.INTERNAL_PUBLISHED) {
            throw new InvalidResultStateTransitionException(internalStatus, "submit for review");
        }
        this.publicStatus = ResultPublicStatus.PENDING_PUBLIC_REVIEW;
        domainEvents.add(new ResultSubmittedForReview(id));
    }

    /** PENDING_PUBLIC_REVIEW → PLATFORM_APPROVED */
    public void platformApprove() {
        if (publicStatus != ResultPublicStatus.PENDING_PUBLIC_REVIEW) {
            throw new InvalidResultStateTransitionException(publicStatus, "platform approve");
        }
        this.publicStatus = ResultPublicStatus.PLATFORM_APPROVED;
        domainEvents.add(new ResultPlatformApproved(id));
    }

    /** PENDING_PUBLIC_REVIEW → PLATFORM_REJECTED */
    public void platformReject() {
        if (publicStatus != ResultPublicStatus.PENDING_PUBLIC_REVIEW) {
            throw new InvalidResultStateTransitionException(publicStatus, "platform reject");
        }
        this.publicStatus = ResultPublicStatus.PLATFORM_REJECTED;
    }

    /** PLATFORM_APPROVED → PUBLIC */
    public void makePublic() {
        if (publicStatus != ResultPublicStatus.PLATFORM_APPROVED) {
            throw new InvalidResultStateTransitionException(publicStatus, "make public");
        }
        this.publicStatus = ResultPublicStatus.PUBLIC;
        domainEvents.add(new ResultMadePublic(id));
    }

    /** PUBLIC → ANOMALY_PENDING (referenced media taken down) */
    public void markAnomaly() {
        if (publicStatus != ResultPublicStatus.PUBLIC) {
            throw new InvalidResultStateTransitionException(publicStatus, "mark anomaly");
        }
        this.publicStatus = ResultPublicStatus.ANOMALY_PENDING;
    }

    /** ANOMALY_PENDING → PUBLIC (anomaly resolved) */
    public void resolveAnomaly() {
        if (publicStatus != ResultPublicStatus.ANOMALY_PENDING) {
            throw new InvalidResultStateTransitionException(publicStatus, "resolve anomaly");
        }
        this.publicStatus = ResultPublicStatus.PUBLIC;
    }

    /** PLATFORM_REJECTED / PLATFORM_TAKEDOWN → NOT_SUBMITTED */
    public void returnToNotSubmitted() {
        if (publicStatus != ResultPublicStatus.PLATFORM_REJECTED
                && publicStatus != ResultPublicStatus.PLATFORM_TAKEDOWN
                && publicStatus != ResultPublicStatus.ANOMALY_PENDING) {
            throw new InvalidResultStateTransitionException(publicStatus, "return to not submitted");
        }
        this.publicStatus = ResultPublicStatus.NOT_SUBMITTED;
    }

    /** PUBLIC or ANOMALY_PENDING → PLATFORM_TAKEDOWN */
    public void platformTakedown() {
        if (publicStatus != ResultPublicStatus.PUBLIC
                && publicStatus != ResultPublicStatus.ANOMALY_PENDING) {
            throw new InvalidResultStateTransitionException(publicStatus, "platform takedown");
        }
        this.publicStatus = ResultPublicStatus.PLATFORM_TAKEDOWN;
        domainEvents.add(new ResultPlatformTakenDown(id));
    }

    public void clearDomainEvents() { domainEvents.clear(); }

    // ── Getters ──

    public ActivityResultId id() { return id; }
    public UUID schoolId() { return schoolId; }
    public UUID activityId() { return activityId; }
    public ResultInternalStatus internalStatus() { return internalStatus; }
    public ResultPublicStatus publicStatus() { return publicStatus; }
    public UUID currentInternalVersionId() { return currentInternalVersionId; }
    public UUID currentPublicVersionId() { return currentPublicVersionId; }

    public List<Object> domainEvents() { return Collections.unmodifiableList(domainEvents); }

    public static class Builder {
        private ActivityResultId id;
        private UUID schoolId, activityId;

        public Builder id(ActivityResultId v) { this.id = v; return this; }
        public Builder schoolId(UUID v) { this.schoolId = v; return this; }
        public Builder activityId(UUID v) { this.activityId = v; return this; }
    }
}
