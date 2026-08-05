package com.campusguinness.identity.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "student_identity_applications")
public class StudentIdentityApplicationEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "real_name", nullable = false, length = 100)
    private String realName;

    @Column(name = "student_number", nullable = false, length = 64)
    private String studentNumber;

    @Column(name = "grade", nullable = false, length = 32)
    private String grade;

    @Column(name = "class_name", nullable = false, length = 64)
    private String className;

    @Column(name = "evidence_file_key", length = 500)
    private String evidenceFileKey;

    @Column(name = "application_status", nullable = false, length = 32)
    private String applicationStatus;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected StudentIdentityApplicationEntity() {}

    void setId(UUID v) { id = v; }
    void setUserId(UUID v) { userId = v; }
    void setSchoolId(UUID v) { schoolId = v; }
    void setRealName(String v) { realName = v; }
    void setStudentNumber(String v) { studentNumber = v; }
    void setGrade(String v) { grade = v; }
    void setClassName(String v) { className = v; }
    void setEvidenceFileKey(String v) { evidenceFileKey = v; }
    void setApplicationStatus(String v) { applicationStatus = v; }
    void setReviewedBy(UUID v) { reviewedBy = v; }
    void setReviewedAt(Instant v) { reviewedAt = v; }
    void setRejectionReason(String v) { rejectionReason = v; }
    void setCreatedAt(Instant v) { createdAt = v; }
    void setUpdatedAt(Instant v) { updatedAt = v; }
    void setVersion(int v) { version = v; }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getSchoolId() { return schoolId; }
    public String getRealName() { return realName; }
    public String getStudentNumber() { return studentNumber; }
    public String getGrade() { return grade; }
    public String getClassName() { return className; }
    public String getEvidenceFileKey() { return evidenceFileKey; }
    public String getApplicationStatus() { return applicationStatus; }
    public UUID getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public int getVersion() { return version; }
}
