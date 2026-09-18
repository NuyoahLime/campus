package com.campusguinness.result.application.query.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StudentActivityResultView(
        UUID activityId,
        UUID resultId,
        UUID versionId,
        int versionNumber,
        String title,
        String summaryText,
        List<String> scoreHighlights,
        String visibilitySource,
        Instant publishedInternallyAt,
        Instant publishedPubliclyAt) {
}
