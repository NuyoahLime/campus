package com.campusguinness.score.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record ScoreReviewHistoryEntry(UUID reviewId,
                                      String result,
                                      UUID reviewerId,
                                      String reviewerUsername,
                                      String reviewComment,
                                      String rejectReason,
                                      Instant reviewedAt) {
}
