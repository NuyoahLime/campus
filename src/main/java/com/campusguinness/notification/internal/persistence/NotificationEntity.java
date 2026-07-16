package com.campusguinness.notification.internal.persistence;

import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name = "notifications")
public class NotificationEntity {
    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "recipient_id", nullable = false) private UUID recipientId;
    @Column(name = "event_type", nullable = false, length = 64) private String eventType;
    @Column(name = "title", nullable = false, length = 300) private String title;
    @Column(name = "content", columnDefinition = "text") private String content;
    @Column(name = "reference_type", length = 32) private String referenceType;
    @Column(name = "reference_id") private UUID referenceId;
    @Column(name = "is_read", nullable = false) private boolean read;
    @Column(name = "read_at") private Instant readAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected NotificationEntity() {}
}
