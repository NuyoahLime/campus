package com.campusguinness.result.application.query.model;

import com.campusguinness.result.application.format.ResultFormatPresentation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PublicActivityResultView(
        UUID activityId,
        UUID resultId,
        UUID versionId,
        int versionNumber,
        String title,
        String summaryText,
        List<String> scoreHighlights,
        Instant publishedPubliclyAt,
        ResultFormatPresentation presentation) {
    public PublicActivityResultView(UUID activityId, UUID resultId, UUID versionId, int versionNumber,
            String title, String summaryText, List<String> scoreHighlights, Instant publishedPubliclyAt) {
        this(activityId, resultId, versionId, versionNumber, title, summaryText, scoreHighlights, publishedPubliclyAt, null);
    }
}
