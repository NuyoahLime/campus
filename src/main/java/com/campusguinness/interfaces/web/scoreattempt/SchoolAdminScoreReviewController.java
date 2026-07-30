package com.campusguinness.interfaces.web.scoreattempt;

import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.interfaces.web.common.PageResponse;
import com.campusguinness.score.application.exception.ScoreReviewNotFoundException;
import com.campusguinness.score.application.query.model.SchoolAdminScoreAttemptDetail;
import com.campusguinness.score.application.query.port.SchoolAdminScoreQueryPort;
import com.campusguinness.score.application.service.ScoreReviewApplicationService;
import com.campusguinness.score.internal.domain.AttemptStatus;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/school-admin/score-attempts")
@PreAuthorize("hasRole('SCHOOL_ADMIN')")
public class SchoolAdminScoreReviewController {
    private final SchoolAdminScoreQueryPort queryPort;
    private final ScoreReviewApplicationService reviewService;
    private final CurrentActor currentActor;
    private final SchoolMembershipQueryPort memberships;

    public SchoolAdminScoreReviewController(
            SchoolAdminScoreQueryPort queryPort,
            ScoreReviewApplicationService reviewService,
            CurrentActor currentActor,
            SchoolMembershipQueryPort memberships) {
        this.queryPort = queryPort;
        this.reviewService = reviewService;
        this.currentActor = currentActor;
        this.memberships = memberships;
    }

    @GetMapping
    public ResponseEntity<PageResponse<com.campusguinness.score.application.query.model.SchoolAdminScoreAttemptItem>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID activityId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validatePagination(page, size);
        String normalizedStatus = normalizeStatus(status);
        String normalizedKeyword = normalizeKeyword(keyword);
        UUID schoolId = requireSchoolId();
        var result = queryPort.findBySchool(
                schoolId, normalizedStatus, activityId, projectId, normalizedKeyword, page, size);
        return ResponseEntity.ok(PageResponse.of(
                result.items(), result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/{attemptId}")
    public ResponseEntity<SchoolAdminScoreAttemptDetail> detail(@PathVariable UUID attemptId) {
        return ResponseEntity.ok(requireDetail(requireSchoolId(), attemptId));
    }

    @PostMapping("/{attemptId}/approve")
    public ResponseEntity<SchoolAdminScoreAttemptDetail> approve(
            @PathVariable UUID attemptId,
            @Valid @RequestBody ApproveScoreReviewRequest request) {
        UUID reviewerId = currentActor.requireUserId();
        reviewService.approve(
                attemptId, reviewerId, request.reviewComment(), request.makeCurrentEffective());
        return ResponseEntity.ok(requireDetail(requireSchoolId(), attemptId));
    }

    @PostMapping("/{attemptId}/reject")
    public ResponseEntity<SchoolAdminScoreAttemptDetail> reject(
            @PathVariable UUID attemptId,
            @Valid @RequestBody RejectScoreReviewRequest request) {
        UUID reviewerId = currentActor.requireUserId();
        reviewService.reject(
                attemptId, reviewerId, request.rejectReason(), request.reviewComment());
        return ResponseEntity.ok(requireDetail(requireSchoolId(), attemptId));
    }

    private SchoolAdminScoreAttemptDetail requireDetail(UUID schoolId, UUID attemptId) {
        return queryPort.findDetail(schoolId, attemptId)
                .orElseThrow(ScoreReviewNotFoundException::new);
    }

    private UUID requireSchoolId() {
        return memberships.findActiveSchoolAdminSchoolId(currentActor.requireUserId())
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException(
                        "No active SCHOOL_ADMIN membership"));
    }

    private static void validatePagination(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
    }

    private static String normalizeStatus(String status) {
        String normalized = status == null ? AttemptStatus.PENDING_REVIEW.name() : status.trim();
        try {
            return AttemptStatus.valueOf(normalized).name();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("status is invalid");
        }
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String normalized = keyword.trim();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("keyword must not exceed 100 characters");
        }
        return normalized.isEmpty() ? null : normalized;
    }
}
