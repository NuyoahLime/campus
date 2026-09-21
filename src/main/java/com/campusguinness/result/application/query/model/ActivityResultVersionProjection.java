package com.campusguinness.result.application.query.model;

import com.campusguinness.result.application.format.ResultFormatPresentation;

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
        Instant publishedPubliclyAt,
        ResultFormatPresentation presentation,
        UUID currentFormatEditRecordId,
        Integer currentFormatRevision) {
    public ActivityResultVersionProjection(UUID versionId, int versionNumber, String title, String summaryText,
            List<String> scoreHighlights, List<UUID> mediaRefs, Instant publishedInternallyAt,
            Instant publishedPubliclyAt) {
        this(versionId, versionNumber, title, summaryText, scoreHighlights, mediaRefs,
                publishedInternallyAt, publishedPubliclyAt, null, null, null);
    }
}
