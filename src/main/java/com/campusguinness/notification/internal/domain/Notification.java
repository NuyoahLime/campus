package com.campusguinness.notification.internal.domain;

import java.time.Instant;
import java.util.UUID;

public record Notification(UUID id, UUID recipientUserId, String type, String title,
        String content, String referenceType, UUID referenceId, Instant createdAt, Instant readAt, boolean read) {

    public static Notification create(UUID id, UUID recipientUserId, String type, String title,
            String content, String refType, UUID refId) {
        if (id == null || recipientUserId == null || type == null || type.isBlank()
                || title == null || title.isBlank())
            throw new IllegalArgumentException("Required notification fields missing");
        return new Notification(id, recipientUserId, type, title, content, refType, refId, Instant.now(), null, false);
    }
}
