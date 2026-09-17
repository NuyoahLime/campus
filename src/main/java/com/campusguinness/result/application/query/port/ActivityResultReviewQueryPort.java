package com.campusguinness.result.application.query.port;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.result.application.query.model.PendingResultReviewDetail;
import com.campusguinness.result.application.query.model.PendingResultReviewSummary;

import java.util.Optional;
import java.util.UUID;

public interface ActivityResultReviewQueryPort {
    QueryPage<PendingResultReviewSummary> findPending(int page, int size);

    Optional<PendingResultReviewDetail> findPendingDetail(UUID resultId);
}
