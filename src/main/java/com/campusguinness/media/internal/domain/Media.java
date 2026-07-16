package com.campusguinness.media.internal.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Media aggregate root (ADR-003: single aggregate with dual state).
 *
 * <p>Dual state machines:
 *
 * <p><b>internal_status</b> (5 states):
 * <pre>
 *   DRAFT → PENDING_INTERNAL_REVIEW → INTERNAL_APPROVED ⇄ INTERNAL_DISABLED
 *             ↓
 *        INTERNAL_REJECTED → DRAFT
 * </pre>
 *
 * <p><b>public_status</b> (6 states):
 * <pre>
 *   NOT_SUBMITTED → PENDING_PUBLIC_REVIEW → PLATFORM_APPROVED → PUBLIC
 *                                         → PLATFORM_REJECTED → NOT_SUBMITTED
 *                                         → PLATFORM_REJECTED → PENDING_PUBLIC_REVIEW
 *                                                       PUBLIC → PLATFORM_TAKEDOWN → NOT_SUBMITTED
 * </pre>
 *
 * <p>Cross-machine (ADR-003 §6):
 * <ul>
 *   <li>Only INTERNAL_APPROVED can submit for public review
 *   <li>INTERNAL_DISABLED + PUBLIC → auto PLATFORM_TAKEDOWN (atomic)
 *   <li>Re-enable INTERNAL_APPROVED does NOT auto-restore public
 *   <li>INTERNAL_REJECTED clears public to NOT_SUBMITTED
 * </ul>
 *
 * <p>File binary excluded from domain (ADR-003 §7.7): Media holds file_key/file_name only.
 * MediaReviewRecord: IMMUTABLE_HISTORY_RECORD (deferred, V1 not modeled in domain).
 */
public final class Media {

    private final MediaId id;
    private final UUID schoolId;
    private final UUID activityId;
    private final UUID uploaderId;
    private final String fileKey;
    private final String fileName;
    private final String fileType;
    private final String fileFormat;
    private final long fileSizeBytes;
    private final String checksum;
    private MediaInternalStatus internalStatus;
    private MediaPublicStatus publicStatus;
    private String description;
    private final List<Object> domainEvents;

    private Media(Builder b, MediaInternalStatus internalStatus, MediaPublicStatus publicStatus) {
        this.id = b.id; this.schoolId = b.schoolId; this.activityId = b.activityId;
        this.uploaderId = b.uploaderId; this.fileKey = b.fileKey; this.fileName = b.fileName;
        this.fileType = b.fileType; this.fileFormat = b.fileFormat; this.fileSizeBytes = b.fileSizeBytes;
        this.checksum = b.checksum; this.description = b.description;
        this.internalStatus = internalStatus; this.publicStatus = publicStatus;
        this.domainEvents = new ArrayList<>();
    }

    public static Media create(Builder builder) {
        validate(builder);
        return new Media(builder, MediaInternalStatus.DRAFT, MediaPublicStatus.NOT_SUBMITTED);
    }

    public static Media reconstitute(Builder builder,
            MediaInternalStatus internalStatus, MediaPublicStatus publicStatus) {
        validate(builder);
        return new Media(builder, internalStatus, publicStatus);
    }

    private static void validate(Builder b) {
        if (b.id == null) throw new IllegalArgumentException("id required");
        if (b.schoolId == null) throw new IllegalArgumentException("schoolId required");
        if (b.activityId == null) throw new IllegalArgumentException("activityId required");
        if (b.uploaderId == null) throw new IllegalArgumentException("uploaderId required");
        if (b.fileKey == null || b.fileKey.isBlank()) throw new IllegalArgumentException("fileKey required");
        if (b.fileName == null || b.fileName.isBlank()) throw new IllegalArgumentException("fileName required");
        if (b.fileType == null || b.fileType.isBlank()) throw new IllegalArgumentException("fileType required");
        if (b.fileSizeBytes <= 0) throw new IllegalArgumentException("fileSizeBytes must be > 0");
    }

    // ── internal_status transitions ──

    /** DRAFT → PENDING_INTERNAL_REVIEW */
    public void submitForInternalReview() {
        if (internalStatus != MediaInternalStatus.DRAFT) {
            throw new InvalidMediaStateTransitionException(internalStatus, "submit for internal review");
        }
        this.internalStatus = MediaInternalStatus.PENDING_INTERNAL_REVIEW;
        domainEvents.add(new MediaInternalReviewSubmitted(id));
    }

    /** PENDING_INTERNAL_REVIEW → INTERNAL_APPROVED */
    public void approveInternal() {
        if (internalStatus != MediaInternalStatus.PENDING_INTERNAL_REVIEW) {
            throw new InvalidMediaStateTransitionException(internalStatus, "approve internal");
        }
        this.internalStatus = MediaInternalStatus.INTERNAL_APPROVED;
        domainEvents.add(new MediaInternalApproved(id));
    }

    /** PENDING_INTERNAL_REVIEW → INTERNAL_REJECTED. Also resets public_status to NOT_SUBMITTED. */
    public void rejectInternal() {
        if (internalStatus != MediaInternalStatus.PENDING_INTERNAL_REVIEW) {
            throw new InvalidMediaStateTransitionException(internalStatus, "reject internal");
        }
        this.internalStatus = MediaInternalStatus.INTERNAL_REJECTED;
        this.publicStatus = MediaPublicStatus.NOT_SUBMITTED;
        domainEvents.add(new MediaInternalRejected(id));
    }

    /** INTERNAL_REJECTED → DRAFT */
    public void returnToDraft() {
        if (internalStatus != MediaInternalStatus.INTERNAL_REJECTED) {
            throw new InvalidMediaStateTransitionException(internalStatus, "return to draft");
        }
        this.internalStatus = MediaInternalStatus.DRAFT;
    }

    /** INTERNAL_APPROVED → INTERNAL_DISABLED.
     *  Cross-machine: if PUBLIC → auto PLATFORM_TAKEDOWN (ADR-003 §6.4). */
    public void disableInternal() {
        if (internalStatus != MediaInternalStatus.INTERNAL_APPROVED) {
            throw new InvalidMediaStateTransitionException(internalStatus, "disable internal");
        }
        this.internalStatus = MediaInternalStatus.INTERNAL_DISABLED;
        boolean takedown = (publicStatus == MediaPublicStatus.PUBLIC);
        if (takedown) {
            this.publicStatus = MediaPublicStatus.PLATFORM_TAKEDOWN;
            domainEvents.add(new MediaPlatformTakedown(id, true));
        }
        domainEvents.add(new MediaInternalDisabled(id, takedown));
    }

    /** INTERNAL_DISABLED → INTERNAL_APPROVED. Does NOT auto-restore public_status (ADR-003 §6.5). */
    public void reEnableInternal() {
        if (internalStatus != MediaInternalStatus.INTERNAL_DISABLED) {
            throw new InvalidMediaStateTransitionException(internalStatus, "re-enable internal");
        }
        this.internalStatus = MediaInternalStatus.INTERNAL_APPROVED;
    }

    // ── public_status transitions ──

    /** NOT_SUBMITTED → PENDING_PUBLIC_REVIEW. Precondition: internal must be INTERNAL_APPROVED. */
    public void submitForPublicReview() {
        if (publicStatus != MediaPublicStatus.NOT_SUBMITTED) {
            throw new InvalidMediaStateTransitionException(publicStatus, "submit for public review");
        }
        if (internalStatus != MediaInternalStatus.INTERNAL_APPROVED) {
            throw new InvalidMediaStateTransitionException(internalStatus, "submit for public review");
        }
        this.publicStatus = MediaPublicStatus.PENDING_PUBLIC_REVIEW;
    }

    /** PENDING_PUBLIC_REVIEW → PLATFORM_APPROVED */
    public void platformApprove() {
        if (publicStatus != MediaPublicStatus.PENDING_PUBLIC_REVIEW) {
            throw new InvalidMediaStateTransitionException(publicStatus, "platform approve");
        }
        this.publicStatus = MediaPublicStatus.PLATFORM_APPROVED;
    }

    /** PENDING_PUBLIC_REVIEW → PLATFORM_REJECTED */
    public void platformReject() {
        if (publicStatus != MediaPublicStatus.PENDING_PUBLIC_REVIEW) {
            throw new InvalidMediaStateTransitionException(publicStatus, "platform reject");
        }
        this.publicStatus = MediaPublicStatus.PLATFORM_REJECTED;
    }

    /** PLATFORM_REJECTED → NOT_SUBMITTED */
    public void returnToNotSubmitted() {
        if (publicStatus != MediaPublicStatus.PLATFORM_REJECTED
                && publicStatus != MediaPublicStatus.PLATFORM_TAKEDOWN) {
            throw new InvalidMediaStateTransitionException(publicStatus, "return to not submitted");
        }
        this.publicStatus = MediaPublicStatus.NOT_SUBMITTED;
    }

    /** PLATFORM_REJECTED → PENDING_PUBLIC_REVIEW (direct resubmit) */
    public void resubmitForPublicReview() {
        if (publicStatus != MediaPublicStatus.PLATFORM_REJECTED) {
            throw new InvalidMediaStateTransitionException(publicStatus, "resubmit for public review");
        }
        this.publicStatus = MediaPublicStatus.PENDING_PUBLIC_REVIEW;
    }

    /** PLATFORM_APPROVED → PUBLIC */
    public void makePublic() {
        if (publicStatus != MediaPublicStatus.PLATFORM_APPROVED) {
            throw new InvalidMediaStateTransitionException(publicStatus, "make public");
        }
        this.publicStatus = MediaPublicStatus.PUBLIC;
        domainEvents.add(new MediaMadePublic(id));
    }

    /** PUBLIC → PLATFORM_TAKEDOWN (normal/forced takedown) */
    public void platformTakedown() {
        if (publicStatus != MediaPublicStatus.PUBLIC) {
            throw new InvalidMediaStateTransitionException(publicStatus, "platform takedown");
        }
        this.publicStatus = MediaPublicStatus.PLATFORM_TAKEDOWN;
        domainEvents.add(new MediaPlatformTakedown(id, false));
    }

    public void clearDomainEvents() { domainEvents.clear(); }

    // ── Getters ──

    public MediaId id() { return id; }
    public UUID schoolId() { return schoolId; }
    public UUID activityId() { return activityId; }
    public UUID uploaderId() { return uploaderId; }
    public String fileKey() { return fileKey; }
    public String fileName() { return fileName; }
    public String fileType() { return fileType; }
    public String fileFormat() { return fileFormat; }
    public long fileSizeBytes() { return fileSizeBytes; }
    public String checksum() { return checksum; }
    public MediaInternalStatus internalStatus() { return internalStatus; }
    public MediaPublicStatus publicStatus() { return publicStatus; }
    public String description() { return description; }

    public List<Object> domainEvents() { return Collections.unmodifiableList(domainEvents); }

    public static class Builder {
        private MediaId id;
        private UUID schoolId;
        private UUID activityId;
        private UUID uploaderId;
        private String fileKey;
        private String fileName;
        private String fileType;
        private String fileFormat;
        private long fileSizeBytes;
        private String checksum;
        private String description;

        public Builder id(MediaId v) { this.id = v; return this; }
        public Builder schoolId(UUID v) { this.schoolId = v; return this; }
        public Builder activityId(UUID v) { this.activityId = v; return this; }
        public Builder uploaderId(UUID v) { this.uploaderId = v; return this; }
        public Builder fileKey(String v) { this.fileKey = v; return this; }
        public Builder fileName(String v) { this.fileName = v; return this; }
        public Builder fileType(String v) { this.fileType = v; return this; }
        public Builder fileFormat(String v) { this.fileFormat = v; return this; }
        public Builder fileSizeBytes(long v) { this.fileSizeBytes = v; return this; }
        public Builder checksum(String v) { this.checksum = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
    }
}
