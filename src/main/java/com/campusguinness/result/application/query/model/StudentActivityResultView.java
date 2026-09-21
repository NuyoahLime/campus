package com.campusguinness.result.application.query.model;

import com.campusguinness.result.application.format.ResultFormatPresentation;

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
        Instant publishedPubliclyAt,
        ResultFormatPresentation presentation) {
    public StudentActivityResultView(UUID activityId, UUID resultId, UUID versionId, int versionNumber,
            String title, String summaryText, List<String> scoreHighlights, String visibilitySource,
            Instant publishedInternallyAt, Instant publishedPubliclyAt) {
        this(activityId, resultId, versionId, versionNumber, title, summaryText, scoreHighlights,
                visibilitySource, publishedInternallyAt, publishedPubliclyAt, null);
    }
}
