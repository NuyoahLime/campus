package com.campusguinness.notification.application.service;

import com.campusguinness.notification.application.port.NotificationRepository;
import com.campusguinness.notification.internal.domain.Notification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional
public class NotificationService {
    private final NotificationRepository repo;
    public NotificationService(NotificationRepository r) { this.repo = r; }

    public void notify(UUID recipientId, String type, String title, String content, String refType, UUID refId) {
        repo.save(Notification.create(UUID.randomUUID(), recipientId, type, title, content, refType, refId));
    }

    @Transactional(readOnly = true) public List<Notification> listMine(UUID userId, boolean unreadOnly) {
        return unreadOnly ? repo.findUnreadByRecipient(userId) : repo.findByRecipient(userId);
    }

    @Transactional(readOnly = true) public long unreadCount(UUID userId) { return repo.countUnreadByRecipient(userId); }

    public Optional<Notification> markRead(UUID id, UUID userId) {
        return repo.findById(id)
                .filter(n -> n.recipientUserId().equals(userId))
                .map(n -> { repo.save(new Notification(n.id(), n.recipientUserId(), n.type(), n.title(),
                        n.content(), n.referenceType(), n.referenceId(), n.createdAt(),
                        java.time.Instant.now(), true)); return n; });
    }

    public void markAllRead(UUID userId) { repo.markAllRead(userId); }
}
