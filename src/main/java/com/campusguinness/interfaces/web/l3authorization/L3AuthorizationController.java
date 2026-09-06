package com.campusguinness.interfaces.web.l3authorization;

import com.campusguinness.interfaces.web.common.PageResponse;
import com.campusguinness.ranking.application.query.L3AuthorizationQueryService;
import com.campusguinness.ranking.application.service.L3AuthorizationApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
public class L3AuthorizationController {
    private final L3AuthorizationApplicationService service;
    private final L3AuthorizationQueryService queryService;

    public L3AuthorizationController(L3AuthorizationApplicationService service, L3AuthorizationQueryService queryService) {
        this.service = service;
        this.queryService = queryService;
    }

    @GetMapping("/api/v1/school-admin/l3-authorizations")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<PageResponse<L3AuthorizationResponse>> schoolList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID projectId) {
        var result = queryService.listSchool(status, projectId, page, size);
        var items = result.items().stream().map(L3AuthorizationResponse::summary).toList();
        return ResponseEntity.ok(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/api/v1/school-admin/l3-authorizations/{id}")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<L3AuthorizationResponse> schoolDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(L3AuthorizationResponse.detail(queryService.schoolDetail(id)));
    }

    @PostMapping("/api/v1/school-admin/l3-authorizations")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<L3AuthorizationResponse> create(@Valid @RequestBody CreateL3AuthorizationRequest req) {
        var result = service.create(req.projectId(), req.ruleVersionId(), req.dataScope(),
                value(req.allowSchoolName(), false), value(req.allowStudentName(), false));
        return ResponseEntity.created(URI.create("/api/v1/school-admin/l3-authorizations/" + result.id()))
                .body(L3AuthorizationResponse.command(result.id(), result.status()));
    }

    @PutMapping("/api/v1/school-admin/l3-authorizations/{id}")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<L3AuthorizationResponse> edit(@PathVariable UUID id,
                                                        @Valid @RequestBody UpdateL3AuthorizationRequest req) {
        var result = service.editDraft(id, req.dataScope(),
                value(req.allowSchoolName(), false), value(req.allowStudentName(), false));
        return ResponseEntity.ok(L3AuthorizationResponse.command(result.id(), result.status()));
    }

    @PostMapping("/api/v1/school-admin/l3-authorizations/{id}/submit")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<L3AuthorizationResponse> submit(@PathVariable UUID id) {
        var result = service.submit(id);
        return ResponseEntity.ok(L3AuthorizationResponse.command(result.id(), result.status()));
    }

    @PostMapping("/api/v1/school-admin/l3-authorizations/{id}/return-to-draft")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<L3AuthorizationResponse> returnToDraft(@PathVariable UUID id) {
        var result = service.returnToDraft(id);
        return ResponseEntity.ok(L3AuthorizationResponse.command(result.id(), result.status()));
    }

    @PostMapping("/api/v1/school-admin/l3-authorizations/{id}/withdraw")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<L3AuthorizationResponse> withdraw(@PathVariable UUID id,
                                                            @Valid @RequestBody WithdrawL3AuthorizationRequest req) {
        var result = service.withdraw(id, req.reason());
        return ResponseEntity.ok(L3AuthorizationResponse.command(result.id(), result.status()));
    }

    @GetMapping("/api/v1/super-admin/l3-authorizations")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PageResponse<L3AuthorizationResponse>> reviewList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID schoolId,
            @RequestParam(required = false) UUID projectId) {
        var result = queryService.listReview(status, schoolId, projectId, page, size);
        var items = result.items().stream().map(L3AuthorizationResponse::summary).toList();
        return ResponseEntity.ok(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/api/v1/super-admin/l3-authorizations/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<L3AuthorizationResponse> reviewDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(L3AuthorizationResponse.detail(queryService.reviewDetail(id)));
    }

    @PostMapping("/api/v1/super-admin/l3-authorizations/{id}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<L3AuthorizationResponse> approve(@PathVariable UUID id,
                                                           @Valid @RequestBody ApproveL3AuthorizationRequest req) {
        var result = service.approve(id, req.comment());
        return ResponseEntity.ok(L3AuthorizationResponse.command(result.id(), result.status()));
    }

    @PostMapping("/api/v1/super-admin/l3-authorizations/{id}/reject")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<L3AuthorizationResponse> reject(@PathVariable UUID id,
                                                          @Valid @RequestBody RejectL3AuthorizationRequest req) {
        var result = service.reject(id, req.reason());
        return ResponseEntity.ok(L3AuthorizationResponse.command(result.id(), result.status()));
    }

    @PostMapping("/api/v1/super-admin/l3-authorizations/{id}/resume")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<L3AuthorizationResponse> resume(@PathVariable UUID id) {
        var result = service.resume(id);
        return ResponseEntity.ok(L3AuthorizationResponse.command(result.id(), result.status()));
    }

    private boolean value(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }
}
