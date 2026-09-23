package com.campusguinness.result.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record GovernanceActivityResultHistoryEntry(
        UUID id,
        UUID resultVersionId,
        String action,
        UUID reviewerId,
        Instant reviewedAt,
        String reason,
        Instant createdAt) {
}
