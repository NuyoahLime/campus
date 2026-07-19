package com.campusguinness.appeal.internal.persistence;

import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name = "score_appeals")
public class ScoreAppealEntity {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "score_attempt_id", nullable = false) private UUID scoreAttemptId;
    @Column(name = "student_id", nullable = false) private UUID studentId;
    @Column(name = "appeal_type", nullable = false, length = 32) private String appealType;
    @Column(name = "appeal_reason", nullable = false, columnDefinition = "text") private String appealReason;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "evidence_file_keys", columnDefinition = "jsonb") private String evidenceFileKeys;
    @Column(name = "appeal_status", nullable = false, length = 32) private String appealStatus;
    @Column(name = "handler_id") private UUID handlerId;
    @Column(name = "escalated_to") private UUID escalatedTo;
    @Column(name = "resolution", columnDefinition = "text") private String resolution;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(name = "version", nullable = false) private int version;
    protected ScoreAppealEntity() {}

    void setId(UUID v) { id = v; } public UUID getId() { return id; }
    void setSchoolId(UUID v) { schoolId = v; } public UUID getSchoolId() { return schoolId; }
    void setScoreAttemptId(UUID v) { scoreAttemptId = v; } public UUID getScoreAttemptId() { return scoreAttemptId; }
    void setStudentId(UUID v) { studentId = v; } public UUID getStudentId() { return studentId; }
    void setAppealType(String v) { appealType = v; } public String getAppealType() { return appealType; }
    void setAppealReason(String v) { appealReason = v; } public String getAppealReason() { return appealReason; }
    void setEvidenceFileKeys(String v) { evidenceFileKeys = v; } public String getEvidenceFileKeys() { return evidenceFileKeys; }
    void setAppealStatus(String v) { appealStatus = v; } public String getAppealStatus() { return appealStatus; }
    void setHandlerId(UUID v) { handlerId = v; } public UUID getHandlerId() { return handlerId; }
    void setEscalatedTo(UUID v) { escalatedTo = v; } public UUID getEscalatedTo() { return escalatedTo; }
    void setResolution(String v) { resolution = v; } public String getResolution() { return resolution; }
    void setResolvedAt(Instant v) { resolvedAt = v; } public Instant getResolvedAt() { return resolvedAt; }
    void setCreatedAt(Instant v) { createdAt = v; } public Instant getCreatedAt() { return createdAt; }
    void setUpdatedAt(Instant v) { updatedAt = v; } public Instant getUpdatedAt() { return updatedAt; }
    void setVersion(int v) { version = v; } public int getVersion() { return version; }
}
