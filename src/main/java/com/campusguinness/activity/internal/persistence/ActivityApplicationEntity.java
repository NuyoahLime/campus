package com.campusguinness.activity.internal.persistence;

import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name = "activity_applications")
public class ActivityApplicationEntity {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "applicant_id", nullable = false) private UUID applicantId;
    @Column(name = "title", nullable = false, length = 200) private String title;
    @Column(name = "description", columnDefinition = "text") private String description;
    @Column(name = "application_status", nullable = false, length = 32) private String applicationStatus;
    @Column(name = "created_activity_id") private UUID createdActivityId;
    @Column(name = "reviewed_by") private UUID reviewedBy;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "review_comment", columnDefinition = "text") private String reviewComment;
    @Column(name = "reject_reason", columnDefinition = "text") private String rejectReason;
    @Column(name = "application_version", nullable = false) private int applicationVersion;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(name = "version", nullable = false) private int version;
    protected ActivityApplicationEntity() {}

    void setId(UUID v) { id = v; } public UUID getId() { return id; }
    void setSchoolId(UUID v) { schoolId = v; } public UUID getSchoolId() { return schoolId; }
    void setApplicantId(UUID v) { applicantId = v; } public UUID getApplicantId() { return applicantId; }
    void setTitle(String v) { title = v; } public String getTitle() { return title; }
    void setDescription(String v) { description = v; } public String getDescription() { return description; }
    void setApplicationStatus(String v) { applicationStatus = v; } public String getApplicationStatus() { return applicationStatus; }
    void setCreatedActivityId(UUID v) { createdActivityId = v; } public UUID getCreatedActivityId() { return createdActivityId; }
    void setReviewedBy(UUID v) { reviewedBy = v; } public UUID getReviewedBy() { return reviewedBy; }
    void setReviewedAt(Instant v) { reviewedAt = v; } public Instant getReviewedAt() { return reviewedAt; }
    void setReviewComment(String v) { reviewComment = v; } public String getReviewComment() { return reviewComment; }
    void setRejectReason(String v) { rejectReason = v; } public String getRejectReason() { return rejectReason; }
    void setApplicationVersion(int v) { applicationVersion = v; } public int getApplicationVersion() { return applicationVersion; }
    void setCreatedAt(Instant v) { createdAt = v; } public Instant getCreatedAt() { return createdAt; }
    void setUpdatedAt(Instant v) { updatedAt = v; } public Instant getUpdatedAt() { return updatedAt; }
    void setVersion(int v) { version = v; } public int getVersion() { return version; }
}
