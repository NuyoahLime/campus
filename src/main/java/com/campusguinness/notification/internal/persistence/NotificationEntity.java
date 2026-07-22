package com.campusguinness.notification.internal.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "notifications")
public class NotificationEntity {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "recipient_id", nullable = false) private UUID recipientId;
    @Column(name = "event_type", nullable = false, length = 64) private String eventType;
    @Column(name = "title", nullable = false, length = 300) private String title;
    @Column(name = "content", columnDefinition = "text") private String content;
    @Column(name = "reference_type", length = 32) private String referenceType;
    @Column(name = "reference_id") private UUID referenceId;
    @Column(name = "is_read", nullable = false) private boolean read = false;
    @Column(name = "read_at") private Instant readAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected NotificationEntity() {}
    public UUID getId() { return id; } public void setId(UUID v) { id = v; }
    public UUID getRecipientId() { return recipientId; } public void setRecipientId(UUID v) { recipientId = v; }
    public String getEventType() { return eventType; } public void setEventType(String v) { eventType = v; }
    public String getTitle() { return title; } public void setTitle(String v) { title = v; }
    public String getContent() { return content; } public void setContent(String v) { content = v; }
    public String getReferenceType() { return referenceType; } public void setReferenceType(String v) { referenceType = v; }
    public UUID getReferenceId() { return referenceId; } public void setReferenceId(UUID v) { referenceId = v; }
    public boolean isRead() { return read; } public void setRead(boolean v) { read = v; }
    public Instant getReadAt() { return readAt; } public void setReadAt(Instant v) { readAt = v; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant v) { createdAt = v; }
}
