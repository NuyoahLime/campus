package com.campusguinness.media.internal.persistence;

import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name = "media")
public class MediaEntity {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "activity_id", nullable = false) private UUID activityId;
    @Column(name = "uploader_id", nullable = false) private UUID uploaderId;
    @Column(name = "file_key", nullable = false, length = 500) private String fileKey;
    @Column(name = "file_name", nullable = false, length = 300) private String fileName;
    @Column(name = "file_type", nullable = false, length = 16) private String fileType;
    @Column(name = "file_format", nullable = false, length = 16) private String fileFormat;
    @Column(name = "file_size_bytes", nullable = false) private long fileSizeBytes;
    @Column(name = "checksum", length = 128) private String checksum;
    @Column(name = "internal_status", nullable = false, length = 32) private String internalStatus;
    @Column(name = "public_status", nullable = false, length = 32) private String publicStatus;
    @Column(name = "description", columnDefinition = "text") private String description;
    @Column(name = "uploaded_at", nullable = false) private Instant uploadedAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(name = "version", nullable = false) private int version;
    protected MediaEntity() {}

    void setId(UUID v) { id = v; } public UUID getId() { return id; }
    void setSchoolId(UUID v) { schoolId = v; } public UUID getSchoolId() { return schoolId; }
    void setActivityId(UUID v) { activityId = v; } public UUID getActivityId() { return activityId; }
    void setUploaderId(UUID v) { uploaderId = v; } public UUID getUploaderId() { return uploaderId; }
    void setFileKey(String v) { fileKey = v; } public String getFileKey() { return fileKey; }
    void setFileName(String v) { fileName = v; } public String getFileName() { return fileName; }
    void setFileType(String v) { fileType = v; } public String getFileType() { return fileType; }
    void setFileFormat(String v) { fileFormat = v; } public String getFileFormat() { return fileFormat; }
    void setFileSizeBytes(long v) { fileSizeBytes = v; } public long getFileSizeBytes() { return fileSizeBytes; }
    void setChecksum(String v) { checksum = v; } public String getChecksum() { return checksum; }
    void setInternalStatus(String v) { internalStatus = v; } public String getInternalStatus() { return internalStatus; }
    void setPublicStatus(String v) { publicStatus = v; } public String getPublicStatus() { return publicStatus; }
    void setDescription(String v) { description = v; } public String getDescription() { return description; }
    void setUploadedAt(Instant v) { uploadedAt = v; } public Instant getUploadedAt() { return uploadedAt; }
    void setUpdatedAt(Instant v) { updatedAt = v; } public Instant getUpdatedAt() { return updatedAt; }
    void setVersion(int v) { version = v; } public int getVersion() { return version; }
}
