package com.campusguinness.notification.application.port;
import com.campusguinness.notification.internal.domain.Notification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface NotificationRepository {
    void save(Notification n);
    Optional<Notification> findById(UUID id);
    List<Notification> findByRecipient(UUID recipientUserId);
    List<Notification> findUnreadByRecipient(UUID recipientUserId);
    long countUnreadByRecipient(UUID recipientUserId);
    int markAllRead(UUID recipientUserId);
}
