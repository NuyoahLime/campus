package com.campusguinness.notification.internal.persistence;

import com.campusguinness.notification.application.port.NotificationRepository;
import com.campusguinness.notification.internal.domain.Notification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Component
class NotificationRepositoryAdapter implements NotificationRepository {
    private final NotificationJpaRepository jpa;
    NotificationRepositoryAdapter(NotificationJpaRepository jpa) { this.jpa = jpa; }

    @Override @Transactional public void save(Notification n) { jpa.save(toEntity(n)); }

    @Override @Transactional(readOnly = true)
    public Optional<Notification> findById(UUID id) { return jpa.findById(id).map(this::toDomain); }

    @Override @Transactional(readOnly = true)
    public List<Notification> findByRecipient(UUID uid) {
        return jpa.findByRecipientIdOrderByCreatedAtDesc(uid).stream().map(this::toDomain).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<Notification> findUnreadByRecipient(UUID uid) {
        return jpa.findByRecipientIdAndReadIsFalseOrderByCreatedAtDesc(uid).stream().map(this::toDomain).toList();
    }

    @Override @Transactional(readOnly = true)
    public long countUnreadByRecipient(UUID uid) { return jpa.countByRecipientIdAndReadIsFalse(uid); }

    @Override @Transactional
    public int markAllRead(UUID uid) { return jpa.markAllRead(uid, Instant.now()); }

    private NotificationEntity toEntity(Notification n) {
        var e = new NotificationEntity(); e.setId(n.id()); e.setRecipientId(n.recipientUserId());
        e.setEventType(n.type()); e.setTitle(n.title()); e.setContent(n.content());
        e.setReferenceType(n.referenceType()); e.setReferenceId(n.referenceId());
        e.setCreatedAt(n.createdAt()); e.setReadAt(n.readAt()); e.setRead(n.read()); return e;
    }

    private Notification toDomain(NotificationEntity e) {
        return new Notification(e.getId(), e.getRecipientId(), e.getEventType(),
                e.getTitle(), e.getContent(), e.getReferenceType(), e.getReferenceId(),
                e.getCreatedAt(), e.getReadAt(), e.isRead());
    }
}
