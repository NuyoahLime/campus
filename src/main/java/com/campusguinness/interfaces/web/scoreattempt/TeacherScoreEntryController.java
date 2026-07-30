package com.campusguinness.interfaces.web.scoreattempt;

import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.interfaces.web.common.PageResponse;
import com.campusguinness.score.application.exception.ScoreEntryNotFoundException;
import com.campusguinness.score.application.query.model.TeacherScoreAttemptDetail;
import com.campusguinness.score.application.query.model.TeacherScoreAttemptItem;
import com.campusguinness.score.application.query.port.TeacherScoreEntryQueryPort;
import com.campusguinness.score.application.service.TeacherScoreEntryApplicationService;
import com.campusguinness.score.internal.domain.AttemptStatus;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1/teacher/score-attempts")
@PreAuthorize("hasRole('TEACHER')")
public class TeacherScoreEntryController {
    private final TeacherScoreEntryApplicationService service;
    private final TeacherScoreEntryQueryPort queryPort;
    private final CurrentActor currentActor;

    public TeacherScoreEntryController(
            TeacherScoreEntryApplicationService service,
            TeacherScoreEntryQueryPort queryPort,
            CurrentActor currentActor) {
        this.service = service;
        this.queryPort = queryPort;
        this.currentActor = currentActor;
    }

    @PostMapping
    public ResponseEntity<TeacherScoreAttemptDetail> createAndSubmit(
            @Valid @RequestBody CreateTeacherScoreRequest request) {
        request.assertNoUnknownFields();
        UUID actorId = currentActor.requireUserId();
        UUID attemptId = service.createAndSubmit(actorId, request.toCommand());
        return ResponseEntity.created(
                        URI.create("/api/v1/teacher/score-attempts/" + attemptId))
                .body(requireDetail(actorId, attemptId));
    }

    @GetMapping("/mine")
    public ResponseEntity<PageResponse<TeacherScoreAttemptItem>> mine(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID activityProjectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validatePagination(page, size);
        String normalizedStatus = normalizeStatus(status);
        String normalizedKeyword = normalizeKeyword(keyword);
        var result = queryPort.findMine(
                currentActor.requireUserId(),
                normalizedStatus,
                activityProjectId,
                normalizedKeyword,
                page,
                size);
        return ResponseEntity.ok(PageResponse.of(
                result.items(), result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/{attemptId}")
    public ResponseEntity<TeacherScoreAttemptDetail> detail(
            @PathVariable UUID attemptId) {
        UUID actorId = currentActor.requireUserId();
        return ResponseEntity.ok(requireDetail(actorId, attemptId));
    }

    @PatchMapping("/{attemptId}/draft")
    public ResponseEntity<TeacherScoreAttemptDetail> updateDraft(
            @PathVariable UUID attemptId,
            @Valid @RequestBody UpdateTeacherScoreRequest request) {
        request.assertNoUnknownFields();
        UUID actorId = currentActor.requireUserId();
        service.updateDraft(actorId, attemptId, request.toUpdateCommand());
        return ResponseEntity.ok(requireDetail(actorId, attemptId));
    }

    @PostMapping("/{attemptId}/submit")
    public ResponseEntity<TeacherScoreAttemptDetail> submitDraft(
            @PathVariable UUID attemptId) {
        UUID actorId = currentActor.requireUserId();
        service.submitDraft(actorId, attemptId);
        return ResponseEntity.ok(requireDetail(actorId, attemptId));
    }

    private TeacherScoreAttemptDetail requireDetail(
            UUID actorId, UUID attemptId) {
        return queryPort.findDetail(actorId, attemptId)
                .orElseThrow(ScoreEntryNotFoundException::new);
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

    private static String normalizeStatus(String status) {
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
