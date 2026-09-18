package com.campusguinness.result.application.query.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ActivityResultVersionProjection(
        UUID versionId,
        int versionNumber,
        String title,
        String summaryText,
        List<String> scoreHighlights,
        List<UUID> mediaRefs,
        Instant publishedInternallyAt,
        Instant publishedPubliclyAt) {
}
