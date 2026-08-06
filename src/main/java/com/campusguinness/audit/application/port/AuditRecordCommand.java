package com.campusguinness.audit.application.port;

import java.time.Instant;
import java.util.UUID;

public record AuditRecordCommand(
        UUID id,
        UUID schoolId,
        UUID actorId,
        String action,
        String targetType,
        UUID targetId,
        String detail,
        Instant occurredAt
) {
}
