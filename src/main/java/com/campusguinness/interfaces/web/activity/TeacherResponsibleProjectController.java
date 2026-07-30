package com.campusguinness.interfaces.web.activity;

import com.campusguinness.activity.application.query.model.TeacherProjectParticipantItem;
import com.campusguinness.activity.application.query.model.TeacherResponsibleProjectDetail;
import com.campusguinness.activity.application.query.model.TeacherResponsibleProjectItem;
import com.campusguinness.activity.application.query.port.TeacherResponsibleProjectQueryPort;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.interfaces.web.common.PageResponse;
import com.campusguinness.score.application.exception.ScoreEntryNotFoundException;
import com.campusguinness.score.internal.domain.AttemptStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teacher/responsible-projects")
@PreAuthorize("hasRole('TEACHER')")
public class TeacherResponsibleProjectController {
    private static final Set<String> EXECUTION_STATUSES = Set.of(
            "DRAFT", "PUBLISHED", "IN_PROGRESS", "ENDED", "CANCELLED");

    private final TeacherResponsibleProjectQueryPort queryPort;
    private final CurrentActor currentActor;

    public TeacherResponsibleProjectController(
            TeacherResponsibleProjectQueryPort queryPort,
            CurrentActor currentActor) {
        this.queryPort = queryPort;
        this.currentActor = currentActor;
    }

    @GetMapping
    public ResponseEntity<PageResponse<TeacherResponsibleProjectItem>> list(
            @RequestParam(required = false) String executionStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validatePagination(page, size);
        String normalizedStatus = normalizeExecutionStatus(executionStatus);
        String normalizedKeyword = normalizeKeyword(keyword);
        var result = queryPort.findResponsibleProjects(
                currentActor.requireUserId(),
                normalizedStatus,
                normalizedKeyword,
                page,
                size);
        return ResponseEntity.ok(PageResponse.of(
                result.items(), result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/{activityProjectId}")
    public ResponseEntity<TeacherResponsibleProjectDetail> detail(
            @PathVariable UUID activityProjectId) {
        return ResponseEntity.ok(queryPort.findResponsibleProject(
                        currentActor.requireUserId(), activityProjectId)
                .orElseThrow(ScoreEntryNotFoundException::new));
    }

    @GetMapping("/{activityProjectId}/participants")
    public ResponseEntity<PageResponse<TeacherProjectParticipantItem>> participants(
            @PathVariable UUID activityProjectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validatePagination(page, size);
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedStatus = normalizeParticipantStatus(status);
        UUID actorId = currentActor.requireUserId();
        if (queryPort.findResponsibleProject(actorId, activityProjectId).isEmpty()) {
            throw new ScoreEntryNotFoundException();
        }
        var result = queryPort.findProjectParticipants(
                actorId,
                activityProjectId,
                normalizedKeyword,
                normalizedStatus,
                page,
                size);
        return ResponseEntity.ok(PageResponse.of(
                result.items(), result.page(), result.size(), result.totalElements()));
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

    private static String normalizeExecutionStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim();
        if (!EXECUTION_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("executionStatus is invalid");
        }
        return normalized;
    }

    private static String normalizeParticipantStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim();
        if ("NO_SCORE".equals(normalized)) {
            return normalized;
        }
        try {
            return AttemptStatus.valueOf(normalized).name();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("status is invalid");
        }
    }
}
