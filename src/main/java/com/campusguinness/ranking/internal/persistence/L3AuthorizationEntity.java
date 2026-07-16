package com.campusguinness.ranking.internal.persistence;

import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name = "l3_authorizations")
public class L3AuthorizationEntity {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "rule_version_id", nullable = false) private UUID ruleVersionId;
    @Column(name = "data_scope", columnDefinition = "jsonb") private String dataScope;
    @Column(name = "allow_school_name", nullable = false) private boolean allowSchoolName;
    @Column(name = "allow_student_name", nullable = false) private boolean allowStudentName;
    @Column(name = "authorization_status", nullable = false, length = 32) private String authorizationStatus;
    @Column(name = "submitted_at") private Instant submittedAt;
    @Column(name = "reviewed_by") private UUID reviewedBy;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "review_comment", columnDefinition = "text") private String reviewComment;
    @Column(name = "reject_reason", columnDefinition = "text") private String rejectReason;
    @Column(name = "paused_at") private Instant pausedAt;
    @Column(name = "withdrawn_at") private Instant withdrawnAt;
    @Column(name = "withdraw_reason", columnDefinition = "text") private String withdrawReason;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(name = "version", nullable = false) private int version;
    protected L3AuthorizationEntity() {}

    void setId(UUID v) { id = v; } public UUID getId() { return id; }
    void setSchoolId(UUID v) { schoolId = v; } public UUID getSchoolId() { return schoolId; }
    void setProjectId(UUID v) { projectId = v; } public UUID getProjectId() { return projectId; }
    void setRuleVersionId(UUID v) { ruleVersionId = v; } public UUID getRuleVersionId() { return ruleVersionId; }
    void setDataScope(String v) { dataScope = v; } public String getDataScope() { return dataScope; }
    void setAllowSchoolName(boolean v) { allowSchoolName = v; } public boolean isAllowSchoolName() { return allowSchoolName; }
    void setAllowStudentName(boolean v) { allowStudentName = v; } public boolean isAllowStudentName() { return allowStudentName; }
    void setAuthorizationStatus(String v) { authorizationStatus = v; } public String getAuthorizationStatus() { return authorizationStatus; }
    void setSubmittedAt(Instant v) { submittedAt = v; } public Instant getSubmittedAt() { return submittedAt; }
    void setReviewedBy(UUID v) { reviewedBy = v; } public UUID getReviewedBy() { return reviewedBy; }
    void setReviewedAt(Instant v) { reviewedAt = v; } public Instant getReviewedAt() { return reviewedAt; }
    void setReviewComment(String v) { reviewComment = v; } public String getReviewComment() { return reviewComment; }
    void setRejectReason(String v) { rejectReason = v; } public String getRejectReason() { return rejectReason; }
    void setPausedAt(Instant v) { pausedAt = v; } public Instant getPausedAt() { return pausedAt; }
    void setWithdrawnAt(Instant v) { withdrawnAt = v; } public Instant getWithdrawnAt() { return withdrawnAt; }
    void setWithdrawReason(String v) { withdrawReason = v; } public String getWithdrawReason() { return withdrawReason; }
    void setCreatedAt(Instant v) { createdAt = v; } public Instant getCreatedAt() { return createdAt; }
    void setUpdatedAt(Instant v) { updatedAt = v; } public Instant getUpdatedAt() { return updatedAt; }
    void setVersion(int v) { version = v; } public int getVersion() { return version; }
}
