package com.campusguinness.score.internal.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "score_attempts")
public class ScoreAttemptEntity {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "activity_project_id", nullable = false) private UUID activityProjectId;
    @Column(name = "student_id", nullable = false) private UUID studentId;
    @Column(name = "attempt_number", nullable = false) private int attemptNumber;
    @Column(name = "score_storage_type", nullable = false, length = 32) private String scoreStorageType;
    @Column(name = "score_value", precision = 18, scale = 4) private BigDecimal scoreValue;
    @Column(name = "score_duration_ms") private Long scoreDurationMs;
    @Column(name = "score_grade", length = 32) private String scoreGrade;
    @Column(name = "score_business_time") private Instant scoreBusinessTime;
    @Column(name = "time_source", length = 32) private String timeSource;
    @Column(name = "is_current_effective", nullable = false) private boolean currentEffective;
    @Column(name = "replaces_id") private UUID replacesId;
    @Column(name = "score_status", nullable = false, length = 32) private String scoreStatus;
    @Column(name = "entered_by", nullable = false) private UUID enteredBy;
    @Column(name = "submitted_at") private Instant submittedAt;
    @Column(name = "is_manual_makeup", nullable = false) private boolean manualMakeup;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(name = "version", nullable = false) private int version;
    protected ScoreAttemptEntity() {}

    void setId(UUID v) { id = v; } public UUID getId() { return id; }
    void setSchoolId(UUID v) { schoolId = v; } public UUID getSchoolId() { return schoolId; }
    void setActivityProjectId(UUID v) { activityProjectId = v; } public UUID getActivityProjectId() { return activityProjectId; }
    void setStudentId(UUID v) { studentId = v; } public UUID getStudentId() { return studentId; }
    void setAttemptNumber(int v) { attemptNumber = v; } public int getAttemptNumber() { return attemptNumber; }
    void setScoreStorageType(String v) { scoreStorageType = v; } public String getScoreStorageType() { return scoreStorageType; }
    void setScoreValue(java.math.BigDecimal v) { scoreValue = v; } public java.math.BigDecimal getScoreValue() { return scoreValue; }
    void setScoreDurationMs(Long v) { scoreDurationMs = v; } public Long getScoreDurationMs() { return scoreDurationMs; }
    void setScoreGrade(String v) { scoreGrade = v; } public String getScoreGrade() { return scoreGrade; }
    void setScoreBusinessTime(Instant v) { scoreBusinessTime = v; } public Instant getScoreBusinessTime() { return scoreBusinessTime; }
    void setTimeSource(String v) { timeSource = v; } public String getTimeSource() { return timeSource; }
    void setCurrentEffective(boolean v) { currentEffective = v; } public boolean isCurrentEffective() { return currentEffective; }
    void setReplacesId(UUID v) { replacesId = v; } public UUID getReplacesId() { return replacesId; }
    void setScoreStatus(String v) { scoreStatus = v; } public String getScoreStatus() { return scoreStatus; }
    void setEnteredBy(UUID v) { enteredBy = v; } public UUID getEnteredBy() { return enteredBy; }
    void setSubmittedAt(Instant v) { submittedAt = v; } public Instant getSubmittedAt() { return submittedAt; }
    void setManualMakeup(boolean v) { manualMakeup = v; } public boolean isManualMakeup() { return manualMakeup; }
    void setCreatedAt(Instant v) { createdAt = v; } public Instant getCreatedAt() { return createdAt; }
    void setUpdatedAt(Instant v) { updatedAt = v; } public Instant getUpdatedAt() { return updatedAt; }
    void setVersion(int v) { version = v; } public int getVersion() { return version; }
}
