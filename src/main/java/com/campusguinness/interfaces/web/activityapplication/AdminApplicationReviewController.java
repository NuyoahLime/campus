package com.campusguinness.interfaces.web.activityapplication;

import com.campusguinness.activity.application.query.port.AdminApplicationQueryPort;
import com.campusguinness.activity.application.service.ActivityApplicationService;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.interfaces.web.common.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/activity-applications")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminApplicationReviewController {

    private final AdminApplicationQueryPort queryPort;
    private final ActivityApplicationService service;
    private final CurrentActor currentActor;

    public AdminApplicationReviewController(AdminApplicationQueryPort queryPort,
            ActivityApplicationService service, CurrentActor currentActor) {
        this.queryPort = queryPort;
        this.service = service;
        this.currentActor = currentActor;
    }

    private static final Set<String> VALID_STATUSES = Set.of("DRAFT","SUBMITTED","APPROVED","REJECTED","WITHDRAWN");
    private static final Set<String> VALID_SORTS = Set.of("updated_desc","updated_asc","created_desc","created_asc");

    @GetMapping
    public ResponseEntity<PageResponse<AdminApplicationItem>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID schoolId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant submittedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant submittedTo,
            @RequestParam(required = false, defaultValue = "updated_desc") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0) throw new IllegalArgumentException("page >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size 1-100");
        if (status != null && !VALID_STATUSES.contains(status))
            throw new IllegalArgumentException("invalid status: " + status);
        if (sort != null && !VALID_SORTS.contains(sort))
            throw new IllegalArgumentException("invalid sort: " + sort);
        if (keyword != null && keyword.length() > 200) throw new IllegalArgumentException("keyword too long");
        if (submittedFrom != null && submittedTo != null && submittedFrom.isAfter(submittedTo))
            throw new IllegalArgumentException("submittedFrom > submittedTo");
        var result = queryPort.findApplications(status, schoolId, keyword, submittedFrom, submittedTo, sort, page, size);
        return ResponseEntity.ok(PageResponse.of(
                result.items().stream().map(a -> new AdminApplicationItem(a.applicationId(), a.schoolId(),
                        a.schoolName(), a.applicantUserId(), a.applicantName(), a.title(),
                        a.descriptionSummary(), a.status(), a.applicationVersion(),
                        a.createdActivityId(), a.reviewedAt(), a.createdAt(), a.updatedAt())).toList(),
                result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminApplicationDetail> getDetail(@PathVariable UUID id) {
        return queryPort.findById(id)
                .map(a -> ResponseEntity.ok(new AdminApplicationDetail(a.applicationId(), a.schoolId(),
                        a.schoolName(), a.applicantUserId(), a.applicantName(), a.title(),
                        a.description(), a.status(), a.applicationVersion(),
                        a.createdActivityId(), a.reviewedAt(), a.reviewComment(),
                        a.rejectReason(), a.createdAt(), a.updatedAt())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminStatsResponse> getStats() {
        var s = queryPort.getStats();
        return ResponseEntity.ok(new AdminStatsResponse(s.total(),s.draft(),s.submitted(),s.approved(),s.rejected(),s.withdrawn(),s.createdToday()));
    }

    @GetMapping("/schools")
    public List<SchoolOption> getSchools() {
        return queryPort.getApplicationSchools().stream()
                .map(s -> new SchoolOption(s.schoolId(), s.schoolName())).toList();
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<AdminApplicationDetail> approve(@PathVariable UUID id) {
        service.approve(id, currentActor.requireUserId());
        return queryPort.findById(id)
                .map(a -> ResponseEntity.ok(new AdminApplicationDetail(a.applicationId(), a.schoolId(),
                        a.schoolName(), a.applicantUserId(), a.applicantName(), a.title(),
                        a.description(), a.status(), a.applicationVersion(),
                        a.createdActivityId(), a.reviewedAt(), a.reviewComment(),
                        a.rejectReason(), a.createdAt(), a.updatedAt())))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<AdminApplicationDetail> reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest req) {
        service.reject(id, currentActor.requireUserId(), req.reason().trim());
        return queryPort.findById(id)
                .map(a -> ResponseEntity.ok(new AdminApplicationDetail(a.applicationId(), a.schoolId(),
                        a.schoolName(), a.applicantUserId(), a.applicantName(), a.title(),
                        a.description(), a.status(), a.applicationVersion(),
                        a.createdActivityId(), a.reviewedAt(), a.reviewComment(),
                        a.rejectReason(), a.createdAt(), a.updatedAt())))
                .orElse(ResponseEntity.notFound().build());
    }

    public record RejectRequest(@NotBlank String reason) {}

    public record AdminApplicationItem(UUID applicationId, UUID schoolId, String schoolName,
            UUID applicantUserId, String applicantName, String title, String descriptionSummary,
            String status, int applicationVersion, UUID createdActivityId,
            Instant reviewedAt, Instant createdAt, Instant updatedAt) {}

    public record AdminApplicationDetail(UUID applicationId, UUID schoolId, String schoolName,
            UUID applicantUserId, String applicantName, String title, String description,
            String status, int applicationVersion, UUID createdActivityId,
            Instant reviewedAt, String reviewComment, String rejectReason,
            Instant createdAt, Instant updatedAt) {}

    public record SchoolOption(UUID schoolId, String schoolName) {}
    public record AdminStatsResponse(int total, int draft, int submitted, int approved, int rejected, int withdrawn, int createdToday) {}
}
