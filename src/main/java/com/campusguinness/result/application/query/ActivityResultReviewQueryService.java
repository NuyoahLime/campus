package com.campusguinness.result.application.query;

import com.campusguinness.identity.application.service.PlatformGovernanceAuthorization;
import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.result.application.query.model.PendingResultReviewDetail;
import com.campusguinness.result.application.query.model.PendingResultReviewSummary;
import com.campusguinness.result.application.query.port.ActivityResultReviewQueryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ActivityResultReviewQueryService {
    private final ActivityResultReviewQueryPort query;
    private final PlatformGovernanceAuthorization authorization;

    public ActivityResultReviewQueryService(
            ActivityResultReviewQueryPort query,
            PlatformGovernanceAuthorization authorization) {
        this.query = query;
        this.authorization = authorization;
    }

    public QueryPage<PendingResultReviewSummary> listPending(int page, int size) {
        authorization.requireSuperAdmin();
        if (page < 0) throw new IllegalArgumentException("page must not be negative");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        return query.findPending(page, size);
    }

    public PendingResultReviewDetail pendingDetail(UUID resultId) {
        authorization.requireSuperAdmin();
        return query.findPendingDetail(resultId)
                .orElseThrow(() -> new IllegalArgumentException("Pending ActivityResult review not found: " + resultId));
    }
}
