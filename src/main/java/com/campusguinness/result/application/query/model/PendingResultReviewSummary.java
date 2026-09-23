package com.campusguinness.result.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record PendingResultReviewSummary(
        UUID resultId,
        UUID schoolId,
        UUID activityId,
        String schoolName,
        String activityTitle,
        String activityExecutionStatus,
        UUID candidateVersionId,
        int candidateVersionNumber,
        String candidateTitle,
        Instant submittedAt,
        UUID submittedBy,
        String publicStatus) {
    public PendingResultReviewSummary(
            UUID resultId,
            UUID schoolId,
            UUID activityId,
            UUID candidateVersionId,
            int candidateVersionNumber,
            String candidateTitle,
            Instant submittedAt,
            UUID submittedBy,
            String publicStatus) {
        this(resultId, schoolId, activityId, null, null, null, candidateVersionId,
                candidateVersionNumber, candidateTitle, submittedAt, submittedBy, publicStatus);
    }
}
