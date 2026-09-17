package com.campusguinness.result.application.query.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PendingResultReviewDetail(
        UUID resultId,
        UUID schoolId,
        UUID activityId,
        UUID candidateVersionId,
        int candidateVersionNumber,
        String candidateTitle,
        String summaryText,
        List<String> scoreHighlights,
        List<UUID> mediaRefs,
        Instant submittedAt,
        UUID submittedBy,
        String publicStatus,
        List<ResultReviewHistoryEntry> history) {
}
