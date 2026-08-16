package com.campusguinness.interfaces.web.school;

import com.campusguinness.interfaces.web.common.PageResponse;
import com.campusguinness.school.application.query.SchoolAdminGovernanceQueryService;
import com.campusguinness.school.application.query.SchoolQueryService;
import com.campusguinness.school.application.result.SchoolResult;
import com.campusguinness.school.application.service.SchoolApplicationService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/schools")
public class SchoolController {

    private final SchoolApplicationService service;
    private final SchoolQueryService queryService;
    private final SchoolAdminGovernanceQueryService governanceQueryService;

    public SchoolController(
            SchoolApplicationService service,
            SchoolQueryService queryService,
            SchoolAdminGovernanceQueryService governanceQueryService
    ) {
        this.service = service;
        this.queryService = queryService;
        this.governanceQueryService = governanceQueryService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<PublicSchoolSummaryResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = queryService.listNormal(page, size);
        var items = result.items().stream()
                .map(r -> new PublicSchoolSummaryResponse(r.id(), r.name(), r.schoolType(), r.region()))
                .toList();
        return ResponseEntity.ok(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/governance")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PageResponse<SchoolGovernanceListResponse>> governanceList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, name = "q") String search
    ) {
        var result = governanceQueryService.listSchools(page, size, status, search);
        var items = result.items().stream().map(SchoolGovernanceListResponse::from).toList();
        return ResponseEntity.ok(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<SchoolGovernanceDetailResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(SchoolGovernanceDetailResponse.from(governanceQueryService.schoolDetail(id)));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<SchoolResponse> activate(@PathVariable UUID id) {
        SchoolResult result = service.activate(id);
        return ResponseEntity.ok(new SchoolResponse(result.id(), result.name(), result.status()));
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<SchoolResponse> disable(@PathVariable UUID id, @Valid @RequestBody DisableSchoolRequest req) {
        SchoolResult result = service.disable(id, req.reason());
        return ResponseEntity.ok(new SchoolResponse(result.id(), result.name(), result.status()));
    }
}
