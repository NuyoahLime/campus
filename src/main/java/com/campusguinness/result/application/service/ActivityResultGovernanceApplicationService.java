package com.campusguinness.result.application.service;

import com.campusguinness.identity.application.service.PlatformGovernanceAuthorization;
import com.campusguinness.result.application.port.ActivityResultRepository;
import com.campusguinness.result.application.port.ResultReviewRecordRepository;
import com.campusguinness.result.application.port.ResultVersionRepository;
import com.campusguinness.result.application.result.ActivityResultResult;
import com.campusguinness.result.internal.domain.ActivityResult;
import com.campusguinness.result.internal.domain.ActivityResultId;
import com.campusguinness.result.internal.domain.ResultReviewRecord;
import com.campusguinness.result.internal.domain.ResultReviewRecordId;
import com.campusguinness.result.internal.domain.ResultVersion;
import com.campusguinness.result.internal.domain.ResultVersionId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class ActivityResultGovernanceApplicationService {
    private static final int TAKEDOWN_REASON_MAX = 2_000;

    private final ActivityResultRepository activityResults;
    private final ResultVersionRepository resultVersions;
    private final ResultReviewRecordRepository reviewRecords;
    private final PlatformGovernanceAuthorization platformAuthorization;
    private final Clock clock;

    public ActivityResultGovernanceApplicationService(
            ActivityResultRepository activityResults,
            ResultVersionRepository resultVersions,
            ResultReviewRecordRepository reviewRecords,
            PlatformGovernanceAuthorization platformAuthorization,
            Clock clock) {
        this.activityResults = activityResults;
        this.resultVersions = resultVersions;
        this.reviewRecords = reviewRecords;
        this.platformAuthorization = platformAuthorization;
        this.clock = clock;
    }

    public ActivityResultResult takedown(UUID resultId, String reason) {
        String normalizedReason = normalizeReason(reason);
        ActivityResult result = findResult(resultId);
        UUID reviewerId = platformAuthorization.requireSuperAdmin();
        ResultVersion publicVersion = requireCurrentPublicVersion(result);

        result.platformTakedown(publicVersion.id().value());
        Instant now = clock.instant();
        activityResults.save(result);
        reviewRecords.append(ResultReviewRecord.takedown(
                new ResultReviewRecordId(UUID.randomUUID()),
                result.id(),
                publicVersion.id(),
                reviewerId,
                now,
                normalizedReason));
        return new ActivityResultResult(
                result.id().value(), result.internalStatus().name(), result.publicStatus().name());
    }

    private ActivityResult findResult(UUID resultId) {
        if (resultId == null) throw new IllegalArgumentException("ActivityResult id required");
        return activityResults.findById(new ActivityResultId(resultId))
                .orElseThrow(() -> new IllegalArgumentException("ActivityResult not found: " + resultId));
    }

    private ResultVersion requireCurrentPublicVersion(ActivityResult result) {
        UUID publicVersionId = result.currentPublicVersionId();
        if (publicVersionId == null) {
            throw new IllegalStateException("currentPublicVersionId required");
        }
        if (result.publicVisibilityBlocked()) {
            throw new IllegalStateException("Public visibility is already blocked");
        }
        ResultVersion publicVersion = resultVersions.findById(new ResultVersionId(publicVersionId))
                .orElseThrow(() -> new IllegalStateException("Current public version not found"));
        if (!publicVersion.resultId().equals(result.id())) {
            throw new IllegalStateException("Public version does not belong to ActivityResult");
        }
        return publicVersion;
    }

    private static String normalizeReason(String reason) {
        if (reason == null) throw new IllegalArgumentException("reason required");
        String normalized = reason.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException("reason required");
        if (normalized.length() > TAKEDOWN_REASON_MAX) {
            throw new IllegalArgumentException("reason max 2000 chars");
        }
        return normalized;
    }
}
