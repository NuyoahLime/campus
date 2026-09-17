package com.campusguinness.result.application.result;

import java.util.List;
import java.util.UUID;

public record ActivityResultEditorResult(
        UUID activityId,
        UUID resultId,
        String internalStatus,
        String publicStatus,
        UUID currentCandidateVersionId,
        UUID currentInternalVersionId,
        UUID currentPublicVersionId,
        boolean publicVisibilityBlocked,
        CandidateContent candidateContent) {

    public record CandidateContent(
            UUID versionId,
            int versionNumber,
            String title,
            String summaryText,
            List<String> scoreHighlights,
            List<UUID> mediaRefs) {
    }
}
