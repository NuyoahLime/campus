package com.campusguinness.interfaces.web.studentidentityapplication;

import com.campusguinness.identity.application.query.StudentIdentityApplicationDetail;
import com.campusguinness.identity.application.query.StudentIdentityApplicationSummary;
import com.campusguinness.identity.application.result.StudentIdentityApplicationReviewResult;
import com.campusguinness.identity.application.service.ApproveStudentIdentityApplicationService;
import com.campusguinness.identity.application.service.RejectStudentIdentityApplicationService;
import com.campusguinness.identity.application.service.StudentIdentityApplicationReviewService;
import com.campusguinness.interfaces.web.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/schools/{schoolId}/student-identity-applications")
public class StudentIdentityApplicationReviewController {

    private final StudentIdentityApplicationReviewService reviewService;
    private final ApproveStudentIdentityApplicationService approveService;
    private final RejectStudentIdentityApplicationService rejectService;

    public StudentIdentityApplicationReviewController(
            StudentIdentityApplicationReviewService reviewService,
            ApproveStudentIdentityApplicationService approveService,
            RejectStudentIdentityApplicationService rejectService
    ) {
        this.reviewService = reviewService;
        this.approveService = approveService;
        this.rejectService = rejectService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<StudentIdentityApplicationSummaryResponse>> list(
            @PathVariable UUID schoolId,
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var result = reviewService.list(schoolId, status, page, size);
        var items = result.items().stream()
                .map(this::summaryResponse)
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/{applicationId}")
    public ResponseEntity<StudentIdentityApplicationDetailResponse> detail(
            @PathVariable UUID schoolId,
            @PathVariable UUID applicationId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(detailResponse(reviewService.detail(schoolId, applicationId)));
    }

    @PostMapping("/{applicationId}/approve")
    public ResponseEntity<StudentIdentityApplicationReviewResponse> approve(
            @PathVariable UUID schoolId,
            @PathVariable UUID applicationId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(reviewResponse(approveService.approve(schoolId, applicationId)));
    }

    @PostMapping("/{applicationId}/reject")
    public ResponseEntity<StudentIdentityApplicationReviewResponse> reject(
            @PathVariable UUID schoolId,
            @PathVariable UUID applicationId,
            @Valid @RequestBody RejectStudentIdentityApplicationRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(reviewResponse(rejectService.reject(schoolId, applicationId, request.reason())));
    }

    private StudentIdentityApplicationSummaryResponse summaryResponse(StudentIdentityApplicationSummary summary) {
        return new StudentIdentityApplicationSummaryResponse(
                summary.applicationId(),
                summary.userId(),
                summary.schoolId(),
                summary.username(),
                summary.realName(),
                summary.studentNumber(),
                summary.grade(),
                summary.className(),
                summary.applicationStatus(),
                summary.submittedAt(),
                summary.reviewedAt()
        );
    }

    private StudentIdentityApplicationDetailResponse detailResponse(StudentIdentityApplicationDetail detail) {
        return new StudentIdentityApplicationDetailResponse(
                detail.applicationId(),
                detail.userId(),
                detail.schoolId(),
                detail.username(),
                detail.realName(),
                detail.studentNumber(),
                detail.grade(),
                detail.className(),
                detail.applicationStatus(),
                detail.submittedAt(),
                detail.reviewerId(),
                detail.reviewedAt(),
                detail.reviewReason(),
                detail.proofFileKeys().size(),
                detail.proofFileKeys()
        );
    }

    private StudentIdentityApplicationReviewResponse reviewResponse(StudentIdentityApplicationReviewResult result) {
        return new StudentIdentityApplicationReviewResponse(
                result.applicationId(),
                result.userId(),
                result.schoolId(),
                result.applicationStatus(),
                result.accountStatus(),
                result.membershipRole(),
                result.membershipStatus(),
                result.reason(),
                result.reviewedAt()
        );
    }
}
