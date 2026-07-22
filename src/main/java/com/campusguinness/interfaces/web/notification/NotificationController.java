package com.campusguinness.interfaces.web.notification;

import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.notification.application.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService service;
    private final CurrentActor currentActor;

    public NotificationController(NotificationService service, CurrentActor currentActor) {
        this.service = service; this.currentActor = currentActor;
    }

    @GetMapping
    public List<Response> list(@RequestParam(defaultValue = "false") boolean unreadOnly) {
        UUID uid = currentActor.requireUserId();
        return service.listMine(uid, unreadOnly).stream()
                .map(n -> new Response(n.id(), n.type(), n.title(), n.content(),
                        n.referenceType(), n.referenceId(), n.createdAt(), n.readAt(), n.read())).toList();
    }

    @GetMapping("/unread-count")
    public UnreadCount unreadCount() {
        return new UnreadCount(service.unreadCount(currentActor.requireUserId()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Response> markRead(@PathVariable UUID id) {
        return service.markRead(id, currentActor.requireUserId())
                .map(n -> ResponseEntity.ok(new Response(n.id(), n.type(), n.title(), n.content(),
                        n.referenceType(), n.referenceId(), n.createdAt(), n.readAt(), n.read())))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        service.markAllRead(currentActor.requireUserId());
        return ResponseEntity.noContent().build();
    }

    public record Response(UUID id, String type, String title, String content,
            String referenceType, UUID referenceId, Instant createdAt, Instant readAt, boolean read) {}
    public record UnreadCount(long unreadCount) {}
}
