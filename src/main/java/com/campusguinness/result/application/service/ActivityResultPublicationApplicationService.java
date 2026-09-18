package com.campusguinness.result.application.service;

import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.internal.domain.Activity;
import com.campusguinness.activity.internal.domain.ActivityId;
import com.campusguinness.activity.internal.domain.ExecutionStatus;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.result.application.port.ActivityResultRepository;
import com.campusguinness.result.application.port.ResultReviewRecordRepository;
import com.campusguinness.result.application.port.ResultVersionRepository;
import com.campusguinness.result.application.result.ActivityResultResult;
import com.campusguinness.result.internal.domain.ActivityResult;
import com.campusguinness.result.internal.domain.ActivityResultId;
import com.campusguinness.result.internal.domain.ResultReviewAction;
import com.campusguinness.result.internal.domain.ResultVersion;
import com.campusguinness.result.internal.domain.ResultVersionId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@Transactional
public class ActivityResultPublicationApplicationService {
    private final ActivityResultRepository activityResults;
    private final ResultVersionRepository resultVersions;
    private final ResultReviewRecordRepository reviewRecords;
    private final ActivityRepository activities;
    private final SchoolResourceAuthorization authorization;
    private final Clock clock;

    public ActivityResultPublicationApplicationService(
            ActivityResultRepository activityResults,
            ResultVersionRepository resultVersions,
            ResultReviewRecordRepository reviewRecords,
            ActivityRepository activities,
            SchoolResourceAuthorization authorization,
            Clock clock) {
        this.activityResults = activityResults;
        this.resultVersions = resultVersions;
        this.reviewRecords = reviewRecords;
        this.activities = activities;
        this.authorization = authorization;
        this.clock = clock;
    }

    public ActivityResultResult makePublic(UUID resultId) {
        ActivityResult result = findResult(resultId);
        Activity activity = findActivity(result.activityId());
        authorization.requireSchoolAdmin(result.schoolId());
        requirePublishableActivity(activity);

        UUID candidateId = result.currentCandidateVersionId();
        if (candidateId == null) {
            throw new IllegalStateException("currentCandidateVersionId required");
        }
        ResultVersionId versionId = new ResultVersionId(candidateId);
        ResultVersion candidate = resultVersions.findById(versionId)
                .orElseThrow(() -> new IllegalStateException("Current candidate version not found"));
        if (!candidate.resultId().equals(result.id())) {
            throw new IllegalStateException("Candidate version does not belong to ActivityResult");
        }
        if (!candidateId.equals(result.currentInternalVersionId())) {
            throw new IllegalStateException("Public candidate must be the current internal version");
        }
        boolean approved = reviewRecords.findByResultAndVersion(result.id(), versionId).stream()
                .anyMatch(record -> record.action() == ResultReviewAction.APPROVED);
        if (!approved) {
            throw new IllegalStateException("Current candidate has no exact approved review record");
        }

        result.makePublic(candidateId);
        resultVersions.markPublishedPublicly(versionId, clock.instant());
        activityResults.save(result);
        return new ActivityResultResult(
                result.id().value(), result.internalStatus().name(), result.publicStatus().name());
    }

    private ActivityResult findResult(UUID resultId) {
        if (resultId == null) throw new IllegalArgumentException("ActivityResult id required");
        return activityResults.findById(new ActivityResultId(resultId))
                .orElseThrow(() -> new IllegalArgumentException("ActivityResult not found: " + resultId));
    }

    private Activity findActivity(UUID activityId) {
        return activities.findById(new ActivityId(activityId))
                .orElseThrow(() -> new IllegalStateException("ActivityResult activity not found"));
    }

    private static void requirePublishableActivity(Activity activity) {
        ExecutionStatus status = activity.executionStatus();
        if (status != ExecutionStatus.PUBLISHED
                && status != ExecutionStatus.IN_PROGRESS
                && status != ExecutionStatus.ENDED) {
            throw new IllegalStateException(
                    "Cannot make ActivityResult public for activity execution status " + status);
        }
    }
}
