package com.campusguinness.activity.internal.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activities")
public class ActivityEntity {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "title", nullable = false, length = 200) private String title;
    @Column(name = "description", columnDefinition = "text") private String description;
    @Column(name = "start_time") private Instant startTime;
    @Column(name = "end_time") private Instant endTime;
    @Column(name = "location", length = 300) private String location;
    @Column(name = "execution_status", nullable = false, length = 32) private String executionStatus;
    @Column(name = "public_status", nullable = false, length = 32) private String publicStatus;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(name = "version", nullable = false) private int version;
    protected ActivityEntity() {}

    void setId(UUID v) { id = v; } public UUID getId() { return id; }
    void setSchoolId(UUID v) { schoolId = v; } public UUID getSchoolId() { return schoolId; }
    void setTitle(String v) { title = v; } public String getTitle() { return title; }
    void setDescription(String v) { description = v; } public String getDescription() { return description; }
    void setStartTime(Instant v) { startTime = v; } public Instant getStartTime() { return startTime; }
    void setEndTime(Instant v) { endTime = v; } public Instant getEndTime() { return endTime; }
    void setLocation(String v) { location = v; } public String getLocation() { return location; }
    void setExecutionStatus(String v) { executionStatus = v; } public String getExecutionStatus() { return executionStatus; }
    void setPublicStatus(String v) { publicStatus = v; } public String getPublicStatus() { return publicStatus; }
    void setCreatedBy(UUID v) { createdBy = v; } public UUID getCreatedBy() { return createdBy; }
    void setCreatedAt(Instant v) { createdAt = v; } public Instant getCreatedAt() { return createdAt; }
    void setUpdatedAt(Instant v) { updatedAt = v; } public Instant getUpdatedAt() { return updatedAt; }
    void setVersion(int v) { version = v; } public int getVersion() { return version; }
}
