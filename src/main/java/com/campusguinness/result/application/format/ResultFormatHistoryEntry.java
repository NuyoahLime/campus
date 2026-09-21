package com.campusguinness.result.application.format;

import java.time.Instant;
import java.util.UUID;

public record ResultFormatHistoryEntry(
        UUID formatEditId,
        UUID resultId,
        UUID resultVersionId,
        int revision,
        ResultFormatPresentation presentation,
        String reason,
        UUID editedBy,
        Instant editedAt) {
}
