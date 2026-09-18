package com.campusguinness.result.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record ActivityResultHistoryEntry(
        UUID id,
        String action,
        UUID resultVersionId,
        Instant occurredAt,
        String reason,
        UUID actorId) {
}
