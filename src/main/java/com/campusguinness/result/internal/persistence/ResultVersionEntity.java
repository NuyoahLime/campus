package com.campusguinness.result.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "result_versions")
public class ResultVersionEntity {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "result_id", nullable = false, updatable = false) private UUID resultId;
    @Column(name = "version_number", nullable = false, updatable = false) private int versionNumber;
    @Column(name = "title", nullable = false, length = 200, updatable = false) private String title;
    @Column(name = "summary_text", nullable = false, updatable = false) private String summaryText;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "score_highlights", columnDefinition = "jsonb", updatable = false) private String scoreHighlights;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "media_refs", columnDefinition = "jsonb", updatable = false) private String mediaRefs;
    @Column(name = "is_core_content_modified", nullable = false, updatable = false) private boolean coreContentModified;
    @Column(name = "format_change_log", updatable = false) private String formatChangeLog;
    @Column(name = "published_internally_at", updatable = false) private Instant publishedInternallyAt;
    @Column(name = "published_publicly_at", updatable = false) private Instant publishedPubliclyAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected ResultVersionEntity() {}

    void setId(UUID v) { id = v; } public UUID getId() { return id; }
    void setResultId(UUID v) { resultId = v; } public UUID getResultId() { return resultId; }
    void setVersionNumber(int v) { versionNumber = v; } public int getVersionNumber() { return versionNumber; }
    void setTitle(String v) { title = v; } public String getTitle() { return title; }
    void setSummaryText(String v) { summaryText = v; } public String getSummaryText() { return summaryText; }
    void setScoreHighlights(String v) { scoreHighlights = v; } public String getScoreHighlights() { return scoreHighlights; }
    void setMediaRefs(String v) { mediaRefs = v; } public String getMediaRefs() { return mediaRefs; }
    void setCoreContentModified(boolean v) { coreContentModified = v; } public boolean isCoreContentModified() { return coreContentModified; }
    void setFormatChangeLog(String v) { formatChangeLog = v; } public String getFormatChangeLog() { return formatChangeLog; }
    void setPublishedInternallyAt(Instant v) { publishedInternallyAt = v; } public Instant getPublishedInternallyAt() { return publishedInternallyAt; }
    void setPublishedPubliclyAt(Instant v) { publishedPubliclyAt = v; } public Instant getPublishedPubliclyAt() { return publishedPubliclyAt; }
    void setCreatedAt(Instant v) { createdAt = v; } public Instant getCreatedAt() { return createdAt; }
}
