package com.campusguinness.result.application.service;

import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.internal.domain.ActivityId;
import com.campusguinness.identity.application.service.PlatformGovernanceAuthorization;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.result.application.port.ActivityResultRepository;
import com.campusguinness.result.application.port.ResultReviewRecordRepository;
import com.campusguinness.result.application.port.ResultVersionRepository;
import com.campusguinness.result.application.result.ActivityResultResult;
import com.campusguinness.result.internal.domain.ActivityResult;
import com.campusguinness.result.internal.domain.ActivityResultId;
import com.campusguinness.result.internal.domain.ResultReviewAction;
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
public class ActivityResultReviewApplicationService {
    private static final int REJECTION_REASON_MAX = 2_000;

    private final ActivityResultRepository activityResults;
    private final ActivityRepository activities;
    private final ResultVersionRepository resultVersions;
    private final ResultReviewRecordRepository reviewRecords;
    private final SchoolResourceAuthorization schoolAuthorization;
    private final PlatformGovernanceAuthorization platformAuthorization;
    private final Clock clock;

    public ActivityResultReviewApplicationService(
            ActivityResultRepository activityResults,
            ActivityRepository activities,
            ResultVersionRepository resultVersions,
            ResultReviewRecordRepository reviewRecords,
            SchoolResourceAuthorization schoolAuthorization,
            PlatformGovernanceAuthorization platformAuthorization,
            Clock clock) {
        this.activityResults = activityResults;
        this.activities = activities;
        this.resultVersions = resultVersions;
        this.reviewRecords = reviewRecords;
        this.schoolAuthorization = schoolAuthorization;
        this.platformAuthorization = platformAuthorization;
        this.clock = clock;
    }

    public ActivityResultResult submit(UUID resultId) {
        ActivityResult result = findResult(resultId);
        UUID actorId = schoolAuthorization.requireSchoolAdmin(result.schoolId());
        requireOrdinaryActivityState(result);
        ResultVersion candidate = requireCurrentCandidate(result);

        result.submitForReview(candidate.id().value());
        Instant now = clock.instant();
        activityResults.save(result);
        reviewRecords.append(ResultReviewRecord.submitted(
                new ResultReviewRecordId(UUID.randomUUID()),
                result.id(),
                candidate.id(),
                actorId,
                now));
        return toResult(result);
    }

    public ActivityResultResult approve(UUID resultId) {
        ActivityResult result = findResult(resultId);
        UUID reviewerId = platformAuthorization.requireSuperAdmin();
        requireOrdinaryActivityState(result);
        ResultVersion candidate = requireCurrentCandidate(result);
        requireOpenSubmission(result, candidate);

        result.platformApprove(candidate.id().value());
        Instant now = clock.instant();
        activityResults.save(result);
        reviewRecords.append(ResultReviewRecord.approved(
                new ResultReviewRecordId(UUID.randomUUID()),
                result.id(),
                candidate.id(),
                reviewerId,
                now));
        return toResult(result);
    }

    public ActivityResultResult reject(UUID resultId, String reason) {
        String normalizedReason = normalizeReason(reason);
        ActivityResult result = findResult(resultId);
        UUID reviewerId = platformAuthorization.requireSuperAdmin();
        requireOrdinaryActivityState(result);
        ResultVersion candidate = requireCurrentCandidate(result);
        requireOpenSubmission(result, candidate);

        result.platformReject(candidate.id().value());
        Instant now = clock.instant();
        activityResults.save(result);
        reviewRecords.append(ResultReviewRecord.rejected(
                new ResultReviewRecordId(UUID.randomUUID()),
                result.id(),
                candidate.id(),
                reviewerId,
                now,
                normalizedReason));
        return toResult(result);
    }

    private ActivityResult findResult(UUID resultId) {
        if (resultId == null) throw new IllegalArgumentException("ActivityResult id required");
        return activityResults.findById(new ActivityResultId(resultId))
                .orElseThrow(() -> new IllegalArgumentException("ActivityResult not found: " + resultId));
    }

    private void requireOrdinaryActivityState(ActivityResult result) {
        ActivityResultActivityStateGuard.requireOrdinaryMutationAllowed(activities.findById(new ActivityId(result.activityId()))
                .orElseThrow(() -> new IllegalStateException("ActivityResult activity not found")));
    }

    private ResultVersion requireCurrentCandidate(ActivityResult result) {
        UUID candidateId = result.currentCandidateVersionId();
        if (candidateId == null) {
            throw new IllegalStateException("currentCandidateVersionId required");
        }
        ResultVersion candidate = resultVersions.findById(new ResultVersionId(candidateId))
                .orElseThrow(() -> new IllegalStateException("Current candidate version not found"));
        if (!candidate.resultId().equals(result.id())) {
            throw new IllegalStateException("Candidate version does not belong to ActivityResult");
        }
        return candidate;
    }

    private void requireOpenSubmission(ActivityResult result, ResultVersion candidate) {
        var history = reviewRecords.findByResultAndVersion(result.id(), candidate.id());
        if (history.isEmpty()
                || history.get(history.size() - 1).action() != ResultReviewAction.SUBMITTED) {
            throw new IllegalStateException("Current candidate has no open public review submission");
        }
    }

    private static String normalizeReason(String reason) {
        if (reason == null) throw new IllegalArgumentException("reason required");
        String normalized = reason.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException("reason required");
        if (normalized.length() > REJECTION_REASON_MAX) {
            throw new IllegalArgumentException("reason max 2000 chars");
        }
        return normalized;
    }

    private static ActivityResultResult toResult(ActivityResult result) {
        return new ActivityResultResult(
                result.id().value(),
                result.internalStatus().name(),
                result.publicStatus().name());
    }
}
