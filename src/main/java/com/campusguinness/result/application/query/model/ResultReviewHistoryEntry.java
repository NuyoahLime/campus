package com.campusguinness.result.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record ResultReviewHistoryEntry(
        UUID id,
        UUID resultVersionId,
        String action,
        UUID submittedBy,
        Instant submittedAt,
        UUID reviewerId,
        Instant reviewedAt,
        String reason,
        Instant createdAt) {
}
