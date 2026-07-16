package com.campusguinness.result.internal.persistence;

import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name = "activity_results")
public class ActivityResultEntity {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "activity_id", nullable = false) private UUID activityId;
    @Column(name = "result_internal_status", nullable = false, length = 32) private String resultInternalStatus;
    @Column(name = "result_public_status", nullable = false, length = 32) private String resultPublicStatus;
    @Column(name = "current_internal_version_id") private UUID currentInternalVersionId;
    @Column(name = "current_public_version_id") private UUID currentPublicVersionId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(name = "version", nullable = false) private int version;
    protected ActivityResultEntity() {}

    void setId(UUID v) { id = v; } public UUID getId() { return id; }
    void setSchoolId(UUID v) { schoolId = v; } public UUID getSchoolId() { return schoolId; }
    void setActivityId(UUID v) { activityId = v; } public UUID getActivityId() { return activityId; }
    void setResultInternalStatus(String v) { resultInternalStatus = v; } public String getResultInternalStatus() { return resultInternalStatus; }
    void setResultPublicStatus(String v) { resultPublicStatus = v; } public String getResultPublicStatus() { return resultPublicStatus; }
    void setCurrentInternalVersionId(UUID v) { currentInternalVersionId = v; } public UUID getCurrentInternalVersionId() { return currentInternalVersionId; }
    void setCurrentPublicVersionId(UUID v) { currentPublicVersionId = v; } public UUID getCurrentPublicVersionId() { return currentPublicVersionId; }
    void setCreatedAt(Instant v) { createdAt = v; } public Instant getCreatedAt() { return createdAt; }
    void setUpdatedAt(Instant v) { updatedAt = v; } public Instant getUpdatedAt() { return updatedAt; }
    void setVersion(int v) { version = v; } public int getVersion() { return version; }
}
