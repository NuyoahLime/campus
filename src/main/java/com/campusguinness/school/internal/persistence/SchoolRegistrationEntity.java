package com.campusguinness.school.internal.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "school_registrations")
public class SchoolRegistrationEntity {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "school_name", nullable = false, length = 200) private String schoolName;
    @Column(name = "unified_code_type", nullable = false, length = 32) private String unifiedCodeType;
    @Column(name = "unified_code", length = 64) private String unifiedCode;
    @Column(name = "school_type", nullable = false, length = 32) private String schoolType;
    @Column(name = "region", nullable = false, length = 128) private String region;
    @Column(name = "address", nullable = false, columnDefinition = "text") private String address;
    @Column(name = "contact_name", nullable = false, length = 100) private String contactName;
    @Column(name = "contact_phone", nullable = false, length = 32) private String contactPhone;
    @Column(name = "contact_email", nullable = false, length = 200) private String contactEmail;
    @Column(name = "description", columnDefinition = "text") private String description;
    @Column(name = "evidence_file_key", length = 500) private String evidenceFileKey;
    @Column(name = "registration_status", nullable = false, length = 32) private String registrationStatus;
    @Column(name = "created_school_id") private UUID createdSchoolId;
    @Column(name = "reviewed_by") private UUID reviewedBy;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "review_comment", columnDefinition = "text") private String reviewComment;
    @Column(name = "reject_reason", columnDefinition = "text") private String rejectReason;
    @Column(name = "withdrawn_by", length = 100) private String withdrawnBy;
    @Column(name = "withdrawn_at") private Instant withdrawnAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(name = "version", nullable = false) private int version;

    protected SchoolRegistrationEntity() {}

    // ── package-private setters (for PersistenceMapper only) ──
    void setId(UUID v) { id = v; }
    void setSchoolName(String v) { schoolName = v; }
    void setUnifiedCodeType(String v) { unifiedCodeType = v; }
    void setUnifiedCode(String v) { unifiedCode = v; }
    void setSchoolType(String v) { schoolType = v; }
    void setRegion(String v) { region = v; }
    void setAddress(String v) { address = v; }
    void setContactName(String v) { contactName = v; }
    void setContactPhone(String v) { contactPhone = v; }
    void setContactEmail(String v) { contactEmail = v; }
    void setDescription(String v) { description = v; }
    void setEvidenceFileKey(String v) { evidenceFileKey = v; }
    void setRegistrationStatus(String v) { registrationStatus = v; }
    void setCreatedSchoolId(UUID v) { createdSchoolId = v; }
    void setReviewedBy(UUID v) { reviewedBy = v; }
    void setReviewedAt(Instant v) { reviewedAt = v; }
    void setReviewComment(String v) { reviewComment = v; }
    void setRejectReason(String v) { rejectReason = v; }
    void setWithdrawnBy(String v) { withdrawnBy = v; }
    void setWithdrawnAt(Instant v) { withdrawnAt = v; }
    void setCreatedAt(Instant v) { createdAt = v; }
    void setUpdatedAt(Instant v) { updatedAt = v; }
    void setVersion(int v) { version = v; }

    // ── public getters (read-only) ──
    public UUID getId() { return id; }
    public String getSchoolName() { return schoolName; }
    public String getUnifiedCodeType() { return unifiedCodeType; }
    public String getUnifiedCode() { return unifiedCode; }
    public String getSchoolType() { return schoolType; }
    public String getRegion() { return region; }
    public String getAddress() { return address; }
    public String getContactName() { return contactName; }
    public String getContactPhone() { return contactPhone; }
    public String getContactEmail() { return contactEmail; }
    public String getDescription() { return description; }
    public String getEvidenceFileKey() { return evidenceFileKey; }
    public String getRegistrationStatus() { return registrationStatus; }
    public UUID getCreatedSchoolId() { return createdSchoolId; }
    public UUID getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewComment() { return reviewComment; }
    public String getRejectReason() { return rejectReason; }
    public String getWithdrawnBy() { return withdrawnBy; }
    public Instant getWithdrawnAt() { return withdrawnAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public int getVersion() { return version; }
}
