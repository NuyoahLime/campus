package com.campusguinness.school.internal.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.time.Instant;
import java.util.UUID;

/**
 * SchoolRegistration aggregate root.
 *
 * <p>State machine (CG-SCHOOL-REG-001~007):
 * <pre>
 *   DRAFT → SUBMITTED → APPROVED (terminal)
 *                    → REJECTED (terminal)
 *                    → WITHDRAWN (terminal)
 *                    → NEED_SUPPLEMENT → SUBMITTED
 *                                     → WITHDRAWN (terminal)
 * </pre>
 */
public final class SchoolRegistration {

    private final SchoolRegistrationId id;
    private final String schoolName;
    private final String unifiedCodeType;
    private final String unifiedCode;
    private final String schoolType;
    private final String region;
    private final String address;
    private final String contactName;
    private final String contactPhone;
    private final String contactEmail;
    private final String description;
    private final String evidenceFileKey;
    private RegistrationStatus status;
    private UUID createdSchoolId;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private String reviewComment;
    private String rejectReason;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final int version;
    private final List<Object> domainEvents;

    private SchoolRegistration(Builder b) {
        this.id = b.id;
        this.schoolName = b.schoolName;
        this.unifiedCodeType = b.unifiedCodeType;
        this.unifiedCode = b.unifiedCode;
        this.schoolType = b.schoolType;
        this.region = b.region;
        this.address = b.address;
        this.contactName = b.contactName;
        this.contactPhone = b.contactPhone;
        this.contactEmail = b.contactEmail;
        this.description = b.description;
        this.evidenceFileKey = b.evidenceFileKey;
        this.status = b.status != null ? b.status : RegistrationStatus.DRAFT;
        this.createdSchoolId = b.createdSchoolId;
        this.reviewedBy = b.reviewedBy;
        this.reviewedAt = b.reviewedAt;
        this.reviewComment = b.reviewComment;
        this.rejectReason = b.rejectReason;
        this.createdAt = b.createdAt;
        this.updatedAt = b.updatedAt;
        this.version = b.version;
        this.domainEvents = new ArrayList<>();
    }

    /** Create a new registration in DRAFT status. */
    public static SchoolRegistration create(Builder builder) {
        validate(builder);
        return new SchoolRegistration(builder);
    }

    /** Reconstitute from persistence — takes final status and audit fields, no domain events. */
    public static SchoolRegistration reconstitute(Builder builder) {
        validate(builder);
        if (builder.status == null) throw new IllegalArgumentException("status required for reconstitute");
        return new SchoolRegistration(builder);
    }

    private static void validate(Builder b) {
        if (b.id == null) throw new IllegalArgumentException("id required");
        if (b.schoolName == null || b.schoolName.isBlank()) throw new IllegalArgumentException("schoolName required");
        if (b.schoolName.length() > 200) throw new IllegalArgumentException("schoolName max 200 chars");
        if (b.unifiedCodeType == null || b.unifiedCodeType.isBlank()) throw new IllegalArgumentException("unifiedCodeType required");
        if (b.schoolType == null || b.schoolType.isBlank()) throw new IllegalArgumentException("schoolType required");
        if (b.region == null || b.region.isBlank()) throw new IllegalArgumentException("region required");
        if (b.address == null || b.address.isBlank()) throw new IllegalArgumentException("address required");
        if (b.contactName == null || b.contactName.isBlank()) throw new IllegalArgumentException("contactName required");
        if (b.contactPhone == null || b.contactPhone.isBlank()) throw new IllegalArgumentException("contactPhone required");
        if (b.contactEmail == null || b.contactEmail.isBlank()) throw new IllegalArgumentException("contactEmail required");
    }

    // ── State transitions ──

    /** CG-SCHOOL-REG-001: DRAFT → SUBMITTED */
    public void submit() {
        if (status != RegistrationStatus.DRAFT) {
            throw new InvalidRegistrationStateTransitionException(status, "submit");
        }
        this.status = RegistrationStatus.SUBMITTED;
        domainEvents.add(new SchoolRegistrationSubmitted(id));
    }

    /** CG-SCHOOL-REG-002: SUBMITTED → NEED_SUPPLEMENT */
    public void requestSupplement(UUID reviewerId, String comment) {
        if (status != RegistrationStatus.SUBMITTED) {
            throw new InvalidRegistrationStateTransitionException(status, "request supplement");
        }
        this.status = RegistrationStatus.NEED_SUPPLEMENT;
        recordReview(reviewerId);
        this.reviewComment = requireText(comment, "comment");
        this.rejectReason = null;
        domainEvents.add(new SchoolRegistrationSupplementRequested(id));
    }

    /** CG-SCHOOL-REG-003: SUBMITTED → APPROVED */
    public void approve(UUID reviewerId, String comment, UUID schoolId) {
        if (status != RegistrationStatus.SUBMITTED) {
            throw new InvalidRegistrationStateTransitionException(status, "approve");
        }
        if (schoolId == null) throw new IllegalArgumentException("schoolId required for approval");
        this.status = RegistrationStatus.APPROVED;
        recordReview(reviewerId);
        this.reviewComment = normalize(comment);
        this.rejectReason = null;
        this.createdSchoolId = schoolId;
        domainEvents.add(new SchoolRegistrationApproved(id));
    }

    /** CG-SCHOOL-REG-004: SUBMITTED → REJECTED */
    public void reject(UUID reviewerId, String reason) {
        if (status != RegistrationStatus.SUBMITTED) {
            throw new InvalidRegistrationStateTransitionException(status, "reject");
        }
        this.status = RegistrationStatus.REJECTED;
        recordReview(reviewerId);
        this.reviewComment = null;
        this.rejectReason = requireText(reason, "reason");
        domainEvents.add(new SchoolRegistrationRejected(id));
    }

    /** CG-SCHOOL-REG-005/007: SUBMITTED or NEED_SUPPLEMENT → WITHDRAWN */
    public void withdraw() {
        if (status != RegistrationStatus.SUBMITTED && status != RegistrationStatus.NEED_SUPPLEMENT) {
            throw new InvalidRegistrationStateTransitionException(status, "withdraw");
        }
        this.status = RegistrationStatus.WITHDRAWN;
        domainEvents.add(new SchoolRegistrationWithdrawn(id));
    }

    /** CG-SCHOOL-REG-006: NEED_SUPPLEMENT → SUBMITTED */
    public void resubmit() {
        if (status != RegistrationStatus.NEED_SUPPLEMENT) {
            throw new InvalidRegistrationStateTransitionException(status, "resubmit");
        }
        this.status = RegistrationStatus.SUBMITTED;
        domainEvents.add(new SchoolRegistrationSubmitted(id));
    }

    public void clearDomainEvents() { domainEvents.clear(); }

    // ── Getters ──

    public SchoolRegistrationId id() { return id; }
    public String schoolName() { return schoolName; }
    public String unifiedCodeType() { return unifiedCodeType; }
    public String unifiedCode() { return unifiedCode; }
    public String schoolType() { return schoolType; }
    public String region() { return region; }
    public String address() { return address; }
    public String contactName() { return contactName; }
    public String contactPhone() { return contactPhone; }
    public String contactEmail() { return contactEmail; }
    public String description() { return description; }
    public String evidenceFileKey() { return evidenceFileKey; }
    public RegistrationStatus status() { return status; }
    public UUID createdSchoolId() { return createdSchoolId; }
    public UUID reviewedBy() { return reviewedBy; }
    public Instant reviewedAt() { return reviewedAt; }
    public String reviewComment() { return reviewComment; }
    public String rejectReason() { return rejectReason; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public int version() { return version; }

    public List<Object> domainEvents() { return Collections.unmodifiableList(domainEvents); }

    /** Builder for registration. Audit fields used only during reconstitute. */
    public static class Builder {
        private SchoolRegistrationId id;
        private String schoolName, unifiedCodeType, unifiedCode, schoolType, region, address;
        private String contactName, contactPhone, contactEmail, description, evidenceFileKey;
        RegistrationStatus status;
        UUID createdSchoolId, reviewedBy;
        Instant reviewedAt, createdAt, updatedAt;
        String reviewComment, rejectReason;
        int version;

        public Builder id(SchoolRegistrationId v) { this.id = v; return this; }
        public Builder schoolName(String v) { this.schoolName = v; return this; }
        public Builder unifiedCodeType(String v) { this.unifiedCodeType = v; return this; }
        public Builder unifiedCode(String v) { this.unifiedCode = v; return this; }
        public Builder schoolType(String v) { this.schoolType = v; return this; }
        public Builder region(String v) { this.region = v; return this; }
        public Builder address(String v) { this.address = v; return this; }
        public Builder contactName(String v) { this.contactName = v; return this; }
        public Builder contactPhone(String v) { this.contactPhone = v; return this; }
        public Builder contactEmail(String v) { this.contactEmail = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder evidenceFileKey(String v) { this.evidenceFileKey = v; return this; }
        public Builder status(RegistrationStatus v) { this.status = v; return this; }
        public Builder createdSchoolId(UUID v) { this.createdSchoolId = v; return this; }
        public Builder reviewedBy(UUID v) { this.reviewedBy = v; return this; }
        public Builder reviewedAt(Instant v) { this.reviewedAt = v; return this; }
        public Builder reviewComment(String v) { this.reviewComment = v; return this; }
        public Builder rejectReason(String v) { this.rejectReason = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v) { this.updatedAt = v; return this; }
        public Builder version(int v) { this.version = v; return this; }
    }

    private void recordReview(UUID reviewerId) {
        if (reviewerId == null) throw new IllegalArgumentException("reviewerId required");
        this.reviewedBy = reviewerId;
        this.reviewedAt = Instant.now();
    }

    private static String requireText(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) throw new IllegalArgumentException(field + " required");
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
