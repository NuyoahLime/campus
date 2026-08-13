package com.campusguinness.interfaces.web.schoolregistration;

import com.campusguinness.school.application.command.SubmitSchoolRegistrationCommand;
import com.campusguinness.school.application.query.SchoolRegistrationQueryService;
import com.campusguinness.school.application.query.model.SchoolRegistrationDetailResult;
import com.campusguinness.school.application.query.model.SchoolRegistrationListResult;
import com.campusguinness.school.application.result.SchoolRegistrationResult;
import com.campusguinness.school.application.service.SchoolRegistrationApplicationService;

import com.campusguinness.interfaces.web.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/school-registrations")
public class SchoolRegistrationController {

    private final SchoolRegistrationApplicationService service;
    private final SchoolRegistrationQueryService queryService;

    public SchoolRegistrationController(
            SchoolRegistrationApplicationService service,
            SchoolRegistrationQueryService queryService
    ) {
        this.service = service;
        this.queryService = queryService;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PageResponse<SchoolRegistrationListItemResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status
    ) {
        var result = queryService.list(page, size, status);
        var items = result.items().stream().map(this::listResponse).toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<SchoolRegistrationDetailResponse> detail(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(detailResponse(queryService.detail(id)));
    }

    @PostMapping
    public ResponseEntity<SchoolRegistrationResponse> submit(@Valid @RequestBody SubmitSchoolRegistrationRequest req) {
        var cmd = new SubmitSchoolRegistrationCommand(
                req.schoolName(), req.unifiedCodeType(), req.unifiedCode(), req.schoolType(),
                req.region(), req.address(), req.contactName(), req.contactPhone(), req.contactEmail(),
                req.description(), req.evidenceFileKey());
        SchoolRegistrationResult r = service.submit(cmd);
        return ResponseEntity.created(URI.create("/api/v1/school-registrations/" + r.id()))
                .body(new SchoolRegistrationResponse(r.id(), r.schoolName(), r.status(), r.createdSchoolId()));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<SchoolRegistrationResponse> approve(@PathVariable UUID id, @Valid @RequestBody ApproveSchoolRegistrationRequest req) {
        SchoolRegistrationResult r = service.approve(id, req.comment(), req.schoolId());
        return ResponseEntity.ok(new SchoolRegistrationResponse(r.id(), r.schoolName(), r.status(), r.createdSchoolId()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<SchoolRegistrationResponse> reject(@PathVariable UUID id, @Valid @RequestBody RejectSchoolRegistrationRequest req) {
        SchoolRegistrationResult r = service.reject(id, req.reason());
        return ResponseEntity.ok(new SchoolRegistrationResponse(r.id(), r.schoolName(), r.status(), r.createdSchoolId()));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("denyAll()")
    public ResponseEntity<SchoolRegistrationResponse> withdraw(@PathVariable UUID id) {
        SchoolRegistrationResult r = service.withdraw(id);
        return ResponseEntity.ok(new SchoolRegistrationResponse(r.id(), r.schoolName(), r.status(), r.createdSchoolId()));
    }

    private SchoolRegistrationListItemResponse listResponse(SchoolRegistrationListResult result) {
        return new SchoolRegistrationListItemResponse(
                result.id(), result.schoolName(), result.schoolType(), result.region(),
                result.contactName(), result.status(), result.createdAt()
        );
    }

    private SchoolRegistrationDetailResponse detailResponse(SchoolRegistrationDetailResult result) {
        return new SchoolRegistrationDetailResponse(
                result.id(), result.schoolName(), result.unifiedCodeType(), result.unifiedCode(),
                result.schoolType(), result.region(), result.address(), result.contactName(),
                result.contactPhone(), result.contactEmail(), result.description(), result.evidenceSubmitted(),
                result.status(), result.createdSchoolId(), result.reviewedBy(), result.reviewedAt(),
                result.reviewComment(), result.rejectReason(), result.createdAt(), result.updatedAt()
        );
    }
}
