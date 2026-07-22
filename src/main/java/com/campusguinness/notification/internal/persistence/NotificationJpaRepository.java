package com.campusguinness.notification.internal.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, UUID> {
    List<NotificationEntity> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);
    List<NotificationEntity> findByRecipientIdAndReadIsFalseOrderByCreatedAtDesc(UUID recipientId);
    long countByRecipientIdAndReadIsFalse(UUID recipientId);
    @Modifying @Query("UPDATE NotificationEntity n SET n.read = true, n.readAt = ?2 WHERE n.recipientId = ?1 AND n.read = false")
    int markAllRead(UUID recipientId, Instant now);
    @Modifying @Query("UPDATE NotificationEntity n SET n.read = true, n.readAt = ?2 WHERE n.id = ?1 AND n.recipientId = ?3")
    int markRead(UUID id, Instant now, UUID recipientId);
}
