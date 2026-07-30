package com.campusguinness.interfaces.web.scoreattempt;

import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.interfaces.web.common.PageResponse;
import com.campusguinness.score.application.exception.ScoreEntryNotFoundException;
import com.campusguinness.score.application.query.model.SchoolAdminScoreAttemptDetail;
import com.campusguinness.score.application.query.model.SchoolAdminScoreAttemptItem;
import com.campusguinness.score.application.query.port.SchoolAdminScoreQueryPort;
import com.campusguinness.score.application.service.SchoolAdminScoreEntryApplicationService;
import com.campusguinness.score.internal.domain.AttemptStatus;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/school-admin/score-attempts")
@PreAuthorize("hasRole('SCHOOL_ADMIN')")
public class SchoolAdminScoreEntryController {
    private final SchoolAdminScoreEntryApplicationService service;
    private final SchoolAdminScoreQueryPort queryPort;
    private final CurrentActor currentActor;
    private final SchoolMembershipQueryPort memberships;

    public SchoolAdminScoreEntryController(
            SchoolAdminScoreEntryApplicationService service,
            SchoolAdminScoreQueryPort queryPort,
            CurrentActor currentActor,
            SchoolMembershipQueryPort memberships) {
        this.service = service;
        this.queryPort = queryPort;
        this.currentActor = currentActor;
        this.memberships = memberships;
    }

    @PostMapping("/drafts")
    public ResponseEntity<SchoolAdminScoreAttemptDetail> createDraft(
            @Valid @RequestBody CreateSchoolAdminScoreDraftRequest request) {
        request.assertNoUnknownFields();
        UUID actorId = currentActor.requireUserId();
        UUID attemptId = service.createDraft(actorId, request.toCommand());
        return ResponseEntity.created(
                        URI.create("/api/v1/school-admin/score-attempts/" + attemptId))
                .body(requireDetail(requireSchoolId(actorId), attemptId));
    }

    @PatchMapping("/{attemptId}/draft")
    public ResponseEntity<SchoolAdminScoreAttemptDetail> updateDraft(
            @PathVariable UUID attemptId,
            @Valid @RequestBody UpdateSchoolAdminScoreDraftRequest request) {
        request.assertNoUnknownFields();
        UUID actorId = currentActor.requireUserId();
        service.updateDraft(actorId, attemptId, request.toUpdateCommand());
        return ResponseEntity.ok(requireDetail(requireSchoolId(actorId), attemptId));
    }

    @PostMapping("/{attemptId}/submit")
    public ResponseEntity<SchoolAdminScoreAttemptDetail> submitDraft(
            @PathVariable UUID attemptId) {
        UUID actorId = currentActor.requireUserId();
        service.submitDraft(actorId, attemptId);
        return ResponseEntity.ok(requireDetail(requireSchoolId(actorId), attemptId));
    }

    @GetMapping("/mine")
    public ResponseEntity<PageResponse<SchoolAdminScoreAttemptItem>> mine(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID activityId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validatePagination(page, size);
        String normalizedStatus = normalizeOptionalStatus(status);
        String normalizedKeyword = normalizeKeyword(keyword);
        UUID actorId = currentActor.requireUserId();
        UUID schoolId = requireSchoolId(actorId);
        var result = queryPort.findEnteredBySchoolAdmin(
                schoolId, actorId, normalizedStatus, activityId, projectId,
                normalizedKeyword, page, size);
        return ResponseEntity.ok(PageResponse.of(
                result.items(), result.page(), result.size(), result.totalElements()));
    }

    private SchoolAdminScoreAttemptDetail requireDetail(UUID schoolId, UUID attemptId) {
        return queryPort.findDetail(schoolId, attemptId)
                .orElseThrow(ScoreEntryNotFoundException::new);
    }

    private UUID requireSchoolId(UUID actorId) {
        return memberships.findActiveSchoolAdminSchoolId(actorId)
                .orElseThrow(() -> new AccessDeniedException(
                        "No active SCHOOL_ADMIN membership"));
    }

    static void validatePagination(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
    }

    static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String normalized = keyword.trim();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException(
                    "keyword must not exceed 100 characters");
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeOptionalStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return AttemptStatus.valueOf(status.trim()).name();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("status is invalid");
        }
    }
}
