package com.campusguinness.interfaces.web.school;

import com.campusguinness.interfaces.web.common.PageResponse;
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
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SchoolController {

    private final SchoolApplicationService service;
    private final SchoolQueryService queryService;

    public SchoolController(SchoolApplicationService service, SchoolQueryService queryService) {
        this.service = service;
        this.queryService = queryService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<SchoolListItem>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = queryService.listNormal(page, size);
        var items = result.items().stream()
                .map(r -> new SchoolListItem(r.id(), r.name(), r.schoolType(), r.region()))
                .toList();
        return ResponseEntity.ok(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SchoolResponse> get(@PathVariable UUID id) {
        var school = service.findById(id);
        return ResponseEntity.ok(new SchoolResponse(school.id().value(), school.name(), school.status().name()));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<SchoolResponse> activate(@PathVariable UUID id) {
        SchoolResult result = service.activate(id);
        return ResponseEntity.ok(new SchoolResponse(result.id(), result.name(), result.status()));
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<SchoolResponse> disable(@PathVariable UUID id, @Valid @RequestBody DisableSchoolRequest req) {
        SchoolResult result = service.disable(id, req.reason());
        return ResponseEntity.ok(new SchoolResponse(result.id(), result.name(), result.status()));
    }
}
