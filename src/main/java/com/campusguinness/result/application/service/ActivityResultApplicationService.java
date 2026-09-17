package com.campusguinness.result.application.service;

import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.internal.domain.Activity;
import com.campusguinness.activity.internal.domain.ActivityId;
import com.campusguinness.activity.internal.domain.ExecutionStatus;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.media.application.service.MediaEligibilityValidator;
import com.campusguinness.result.application.command.SaveActivityResultContentCommand;
import com.campusguinness.result.application.port.ActivityResultRepository;
import com.campusguinness.result.application.port.ResultVersionRepository;
import com.campusguinness.result.application.result.ActivityResultEditorResult;
import com.campusguinness.result.application.result.ActivityResultResult;
import com.campusguinness.result.internal.domain.ActivityResult;
import com.campusguinness.result.internal.domain.ActivityResultId;
import com.campusguinness.result.internal.domain.ResultVersion;
import com.campusguinness.result.internal.domain.ResultVersionId;
import com.campusguinness.result.internal.domain.ResultInternalStatus;
import com.campusguinness.result.internal.domain.ResultPublicStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class ActivityResultApplicationService {
    private static final int TITLE_MAX = 200;
    private static final int SUMMARY_MAX = 10_000;
    private static final int HIGHLIGHT_MAX_ITEMS = 20;
    private static final int HIGHLIGHT_MAX = 200;
    private static final int MEDIA_REF_MAX_ITEMS = 20;
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<List<UUID>> UUID_LIST = new TypeReference<>() {};

    private final ActivityResultRepository activityResults;
    private final ResultVersionRepository resultVersions;
    private final ActivityRepository activities;
    private final SchoolResourceAuthorization authorization;
    private final MediaEligibilityValidator mediaEligibilityValidator;
    private final ObjectMapper objectMapper;

    public ActivityResultApplicationService(
            ActivityResultRepository activityResults,
            ResultVersionRepository resultVersions,
            ActivityRepository activities,
            SchoolResourceAuthorization authorization,
            MediaEligibilityValidator mediaEligibilityValidator,
            ObjectMapper objectMapper) {
        this.activityResults = activityResults;
        this.resultVersions = resultVersions;
        this.activities = activities;
        this.authorization = authorization;
        this.mediaEligibilityValidator = mediaEligibilityValidator;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ActivityResultEditorResult readEditor(UUID activityId) {
        Activity activity = findActivity(activityId);
        authorization.requireSchoolAdmin(activity.schoolId());
        return activityResults.findByActivityId(activityId)
                .map(this::toEditorResult)
                .orElseGet(() -> emptyEditor(activityId));
    }

    public ActivityResultEditorResult saveEditorContent(UUID activityId, SaveActivityResultContentCommand command) {
        Activity activity = findActivity(activityId);
        authorization.requireSchoolAdmin(activity.schoolId());
        requireEditableActivity(activity);
        NormalizedContent content = normalize(command);
        mediaEligibilityValidator.requireEligibleForActivity(
                content.mediaRefs(), activity.schoolId(), activity.id().value());

        return activityResults.findByActivityId(activityId)
                .map(result -> saveExisting(result, content))
                .orElseGet(() -> saveFirst(activity, content));
    }

    public ActivityResultResult publishInternal(UUID id) {
        ActivityResult result = findResult(id);
        authorization.requireSchoolAdmin(result.schoolId());
        UUID candidateId = result.currentCandidateVersionId();
        if (candidateId == null) {
            throw new IllegalStateException("currentCandidateVersionId required");
        }
        ResultVersion candidate = resultVersions.findById(new ResultVersionId(candidateId))
                .orElseThrow(() -> new IllegalStateException("Current candidate version not found"));
        requireSameResult(candidate, result);

        result.publishInternal(candidateId);
        resultVersions.markPublishedInternally(candidate.id(), Instant.now());
        activityResults.save(result);
        return toLifecycleResult(result);
    }

    public ActivityResultResult withdrawInternal(UUID id) {
        ActivityResult result = findResult(id);
        authorization.requireSchoolAdmin(result.schoolId());
        result.withdrawInternal();
        activityResults.save(result);
        return toLifecycleResult(result);
    }

    public ActivityResultResult returnToDraft(UUID id) {
        ActivityResult result = findResult(id);
        authorization.requireSchoolAdmin(result.schoolId());
        result.returnToDraft();
        activityResults.save(result);
        return toLifecycleResult(result);
    }

    private ActivityResultEditorResult saveFirst(Activity activity, NormalizedContent content) {
        ActivityResult result = ActivityResult.create(new ActivityResult.Builder()
                .id(new ActivityResultId(UUID.randomUUID()))
                .schoolId(activity.schoolId())
                .activityId(activity.id().value()));
        activityResults.save(result);

        ResultVersion version = createVersion(result, 1, content);
        result.createFirstCandidate(version.id().value());
        activityResults.save(result);
        return toEditorResult(result, version);
    }

    private ActivityResultEditorResult saveExisting(ActivityResult result, NormalizedContent content) {
        requireCoreEditAllowed(result);
        ResultVersion currentCandidate = loadCandidateIfPresent(result);
        if (currentCandidate != null && contentMatches(currentCandidate, content)) {
            return toEditorResult(result, currentCandidate);
        }

        int nextVersion = resultVersions.nextVersionNumberFor(result.id());
        ResultVersion version = createVersion(result, nextVersion, content);
        result.replaceCandidateAfterCoreEdit(version.id().value());
        activityResults.save(result);
        return toEditorResult(result, version);
    }

    private ResultVersion createVersion(ActivityResult result, int versionNumber, NormalizedContent content) {
        ResultVersion version = ResultVersion.create(new ResultVersion.Builder()
                .id(new ResultVersionId(UUID.randomUUID()))
                .resultId(result.id())
                .versionNumber(versionNumber)
                .title(content.title())
                .summaryText(content.summaryText())
                .scoreHighlights(toJson(content.scoreHighlights()))
                .mediaRefs(toJson(content.mediaRefs())));
        resultVersions.create(version);
        return version;
    }

    private ResultVersion loadCandidateIfPresent(ActivityResult result) {
        UUID candidateId = result.currentCandidateVersionId();
        if (candidateId == null) {
            return null;
        }
        ResultVersion candidate = resultVersions.findById(new ResultVersionId(candidateId))
                .orElseThrow(() -> new IllegalStateException("Current candidate version not found"));
        requireSameResult(candidate, result);
        return candidate;
    }

    private Activity findActivity(UUID activityId) {
        if (activityId == null) throw new IllegalArgumentException("activityId required");
        return activities.findById(new ActivityId(activityId))
                .orElseThrow(() -> new IllegalArgumentException("Activity not found: " + activityId));
    }

    private ActivityResult findResult(UUID id) {
        if (id == null) throw new IllegalArgumentException("ActivityResult id required");
        return activityResults.findById(new ActivityResultId(id))
                .orElseThrow(() -> new IllegalArgumentException("ActivityResult not found: " + id));
    }

    private void requireEditableActivity(Activity activity) {
        ExecutionStatus status = activity.executionStatus();
        if (status != ExecutionStatus.PUBLISHED
                && status != ExecutionStatus.IN_PROGRESS
                && status != ExecutionStatus.ENDED) {
            throw new IllegalStateException("Cannot edit ActivityResult for activity execution status " + status);
        }
    }

    private void requireCoreEditAllowed(ActivityResult result) {
        if (result.internalStatus() == ResultInternalStatus.INTERNAL_WITHDRAWN) {
            throw new IllegalStateException("Cannot edit core content from internal status INTERNAL_WITHDRAWN");
        }
        if (result.publicStatus() == ResultPublicStatus.PENDING_PUBLIC_REVIEW
                || result.publicStatus() == ResultPublicStatus.PLATFORM_APPROVED) {
            throw new IllegalStateException(
                    "Cannot edit core content from public status " + result.publicStatus());
        }
    }

    private NormalizedContent normalize(SaveActivityResultContentCommand command) {
        if (command == null) throw new IllegalArgumentException("request body required");
        String title = trimRequired(command.title(), "title", TITLE_MAX);
        String summary = trimRequired(command.summaryText(), "summaryText", SUMMARY_MAX);
        List<String> highlights = normalizeHighlights(command.scoreHighlights());
        List<UUID> mediaRefs = normalizeMediaRefs(command.mediaRefs());
        return new NormalizedContent(title, summary, highlights, mediaRefs);
    }

    private static String trimRequired(String value, String field, int max) {
        if (value == null) throw new IllegalArgumentException(field + " required");
        String trimmed = value.trim();
        if (trimmed.isBlank()) throw new IllegalArgumentException(field + " required");
        if (trimmed.length() > max) throw new IllegalArgumentException(field + " max " + max + " chars");
        return trimmed;
    }

    private static List<String> normalizeHighlights(List<String> values) {
        if (values == null) return List.of();
        if (values.size() > HIGHLIGHT_MAX_ITEMS) {
            throw new IllegalArgumentException("scoreHighlights max 20 items");
        }
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            String trimmed = trimRequired(value, "scoreHighlights", HIGHLIGHT_MAX);
            normalized.add(trimmed);
        }
        return List.copyOf(normalized);
    }

    private static List<UUID> normalizeMediaRefs(List<UUID> values) {
        if (values == null) return List.of();
        if (values.size() > MEDIA_REF_MAX_ITEMS) {
            throw new IllegalArgumentException("mediaRefs max 20 items");
        }
        HashSet<UUID> seen = new HashSet<>();
        for (UUID value : values) {
            if (value == null) throw new IllegalArgumentException("mediaRefs must not contain null");
            if (!seen.add(value)) throw new IllegalArgumentException("duplicate mediaRef rejected");
        }
        return List.copyOf(values);
    }

    private boolean contentMatches(ResultVersion version, NormalizedContent content) {
        return Objects.equals(version.title(), content.title())
                && Objects.equals(version.summaryText(), content.summaryText())
                && Objects.equals(readStringList(version.scoreHighlights()), content.scoreHighlights())
                && Objects.equals(readUuidList(version.mediaRefs()), content.mediaRefs());
    }

    private ActivityResultEditorResult toEditorResult(ActivityResult result) {
        ResultVersion candidate = loadCandidateIfPresent(result);
        return toEditorResult(result, candidate);
    }

    private ActivityResultEditorResult toEditorResult(ActivityResult result, ResultVersion candidate) {
        return new ActivityResultEditorResult(
                result.activityId(),
                result.id().value(),
                result.internalStatus().name(),
                result.publicStatus().name(),
                result.currentCandidateVersionId(),
                result.currentInternalVersionId(),
                result.currentPublicVersionId(),
                result.publicVisibilityBlocked(),
                candidate == null ? null : toCandidateContent(candidate));
    }

    private ActivityResultEditorResult emptyEditor(UUID activityId) {
        return new ActivityResultEditorResult(activityId, null, "DRAFT", "NOT_SUBMITTED",
                null, null, null, false, null);
    }

    private ActivityResultEditorResult.CandidateContent toCandidateContent(ResultVersion version) {
        return new ActivityResultEditorResult.CandidateContent(
                version.id().value(),
                version.versionNumber(),
                version.title(),
                version.summaryText(),
                readStringList(version.scoreHighlights()),
                readUuidList(version.mediaRefs()));
    }

    private ActivityResultResult toLifecycleResult(ActivityResult result) {
        return new ActivityResultResult(
                result.id().value(),
                result.internalStatus().name(),
                result.publicStatus().name());
    }

    private void requireSameResult(ResultVersion version, ActivityResult result) {
        if (!version.resultId().equals(result.id())) {
            throw new IllegalStateException("Candidate version does not belong to ActivityResult");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid result content", e);
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored scoreHighlights are invalid", e);
        }
    }

    private List<UUID> readUuidList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, UUID_LIST);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored mediaRefs are invalid", e);
        }
    }

    private record NormalizedContent(
            String title,
            String summaryText,
            List<String> scoreHighlights,
            List<UUID> mediaRefs) {
    }
}
