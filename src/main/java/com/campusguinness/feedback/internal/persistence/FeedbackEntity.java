package com.campusguinness.feedback.internal.persistence;

import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name = "feedbacks")
public class FeedbackEntity {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "school_id") private UUID schoolId;
    @Column(name = "submitter_id") private UUID submitterId;
    @Column(name = "feedback_type", nullable = false, length = 32) private String feedbackType;
    @Column(name = "content", nullable = false, columnDefinition = "text") private String content;
    @Column(name = "feedback_status", nullable = false, length = 32) private String feedbackStatus;
    @Column(name = "handler_id") private UUID handlerId;
    @Column(name = "handler_level", length = 32) private String handlerLevel;
    @Column(name = "reply", columnDefinition = "text") private String reply;
    @Column(name = "close_reason", columnDefinition = "text") private String closeReason;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(name = "version", nullable = false) private int version;
    protected FeedbackEntity() {}

    void setId(UUID v) { id = v; } public UUID getId() { return id; }
    void setSchoolId(UUID v) { schoolId = v; } public UUID getSchoolId() { return schoolId; }
    void setSubmitterId(UUID v) { submitterId = v; } public UUID getSubmitterId() { return submitterId; }
    void setFeedbackType(String v) { feedbackType = v; } public String getFeedbackType() { return feedbackType; }
    void setContent(String v) { content = v; } public String getContent() { return content; }
    void setFeedbackStatus(String v) { feedbackStatus = v; } public String getFeedbackStatus() { return feedbackStatus; }
    void setHandlerId(UUID v) { handlerId = v; } public UUID getHandlerId() { return handlerId; }
    void setHandlerLevel(String v) { handlerLevel = v; } public String getHandlerLevel() { return handlerLevel; }
    void setReply(String v) { reply = v; } public String getReply() { return reply; }
    void setCloseReason(String v) { closeReason = v; } public String getCloseReason() { return closeReason; }
    void setCreatedAt(Instant v) { createdAt = v; } public Instant getCreatedAt() { return createdAt; }
    void setUpdatedAt(Instant v) { updatedAt = v; } public Instant getUpdatedAt() { return updatedAt; }
    void setVersion(int v) { version = v; } public int getVersion() { return version; }
}
